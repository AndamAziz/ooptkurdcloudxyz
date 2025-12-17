-- Enforce account suspension at the database layer (block writes for suspended users)

-- 1) Helper function (reusable in RLS + security definer functions)
CREATE OR REPLACE FUNCTION public.is_account_active(p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT COALESCE((
    SELECT account_status
    FROM public.profiles
    WHERE user_id = p_user_id
    LIMIT 1
  ), 'active') = 'active';
$$;

-- 2) RLS policy updates: block INSERT/UPDATE when suspended

-- attempts
DROP POLICY IF EXISTS "Users can insert their own attempts" ON public.attempts;
CREATE POLICY "Users can insert their own attempts"
ON public.attempts
FOR INSERT
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- practice_sessions
DROP POLICY IF EXISTS "Users can insert their own sessions" ON public.practice_sessions;
CREATE POLICY "Users can insert their own sessions"
ON public.practice_sessions
FOR INSERT
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

DROP POLICY IF EXISTS "Users can update their own sessions" ON public.practice_sessions;
CREATE POLICY "Users can update their own sessions"
ON public.practice_sessions
FOR UPDATE
USING (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
)
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- placement_results
DROP POLICY IF EXISTS "Users can insert their own placement results" ON public.placement_results;
CREATE POLICY "Users can insert their own placement results"
ON public.placement_results
FOR INSERT
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- rate_limits
DROP POLICY IF EXISTS "Users can insert their own rate limits" ON public.rate_limits;
CREATE POLICY "Users can insert their own rate limits"
ON public.rate_limits
FOR INSERT
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- profiles (updates by user)
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile"
ON public.profiles
FOR UPDATE
USING (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
)
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- certificates (user insert, if used anywhere)
DROP POLICY IF EXISTS "Users can insert their own certificates" ON public.certificates;
CREATE POLICY "Users can insert their own certificates"
ON public.certificates
FOR INSERT
WITH CHECK (
  auth.uid() = user_id
  AND public.is_account_active(auth.uid())
);

-- 3) Harden SECURITY DEFINER RPC functions so they cannot be used by suspended users

-- increment_user_xp
CREATE OR REPLACE FUNCTION public.increment_user_xp(_user_id uuid, _xp_increment integer)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  -- Verify caller owns this user_id (prevent privilege escalation)
  IF _user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot increment another user''s XP';
  END IF;

  -- Block suspended users
  IF NOT public.is_account_active(auth.uid()) THEN
    RAISE EXCEPTION 'Account suspended';
  END IF;

  -- Validate XP increment is reasonable (prevent massive inflation)
  IF _xp_increment < 0 OR _xp_increment > 1000 THEN
    RAISE EXCEPTION 'Invalid XP increment value';
  END IF;

  UPDATE public.profiles
  SET xp = COALESCE(xp, 0) + _xp_increment
  WHERE user_id = _user_id;
END;
$function$;

-- complete_placement_test
CREATE OR REPLACE FUNCTION public.complete_placement_test(p_user_id uuid, p_correct_answers integer, p_level_scores jsonb, p_recommended_level cefr_level)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_result_id UUID;
  v_score NUMERIC;
BEGIN
  -- Verify caller owns this user_id
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot complete placement test for another user';
  END IF;

  -- Block suspended users
  IF NOT public.is_account_active(auth.uid()) THEN
    RAISE EXCEPTION 'Account suspended';
  END IF;

  -- Calculate score percentage
  v_score := (p_correct_answers::NUMERIC / 50) * 100;

  -- Insert placement result
  INSERT INTO public.placement_results (user_id, correct_answers, level_scores, recommended_level)
  VALUES (p_user_id, p_correct_answers, p_level_scores, p_recommended_level)
  RETURNING id INTO v_result_id;

  -- Update profile
  UPDATE public.profiles
  SET
    placement_completed = true,
    placement_score = v_score,
    placement_date = NOW(),
    current_cefr = p_recommended_level
  WHERE user_id = p_user_id;

  RETURN v_result_id;
END;
$function$;

-- skip_placement_test
CREATE OR REPLACE FUNCTION public.skip_placement_test(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  -- Verify caller owns this user_id
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot skip placement test for another user';
  END IF;

  -- Block suspended users
  IF NOT public.is_account_active(auth.uid()) THEN
    RAISE EXCEPTION 'Account suspended';
  END IF;

  -- Update profile to mark placement as completed (skipped)
  UPDATE public.profiles
  SET
    placement_completed = true,
    placement_date = NOW(),
    current_cefr = 'A1'::cefr_level
  WHERE user_id = p_user_id;
END;
$function$;

-- check_rate_limit (records action + enforces window)
CREATE OR REPLACE FUNCTION public.check_rate_limit(
  p_user_id uuid,
  p_action text DEFAULT 'attempt'::text,
  p_max_count integer DEFAULT 100,
  p_window_minutes integer DEFAULT 10
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  attempt_count INTEGER;
BEGIN
  -- Only allow checking own rate limit
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot check another user''s rate limit';
  END IF;

  -- Block suspended users
  IF NOT public.is_account_active(auth.uid()) THEN
    RAISE EXCEPTION 'Account suspended';
  END IF;

  -- Count recent actions
  SELECT COUNT(*) INTO attempt_count
  FROM public.rate_limits
  WHERE user_id = p_user_id
    AND action = p_action
    AND created_at > now() - (p_window_minutes || ' minutes')::interval;

  -- Record this action
  INSERT INTO public.rate_limits (user_id, action)
  VALUES (p_user_id, p_action);

  -- Clean up old records (older than 1 hour)
  DELETE FROM public.rate_limits
  WHERE created_at < now() - interval '1 hour';

  -- Return true if within limit
  RETURN attempt_count < p_max_count;
END;
$function$;
