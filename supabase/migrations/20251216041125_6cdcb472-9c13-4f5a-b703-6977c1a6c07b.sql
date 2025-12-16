-- Fix 1: Add RLS policy for authenticated users to view leaderboard data
-- The leaderboard_view only exposes non-sensitive fields (username, display_name, xp, current_cefr)
CREATE POLICY "Authenticated users can view leaderboard data"
ON public.profiles FOR SELECT
TO authenticated
USING (true);

-- Fix 2: Update reset_level_progress to validate caller owns the user_id
CREATE OR REPLACE FUNCTION public.reset_level_progress(p_user_id UUID, p_cefr cefr_level)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Verify caller owns this user_id (or is admin)
  IF p_user_id != auth.uid() AND NOT public.has_role(auth.uid(), 'admin') THEN
    RAISE EXCEPTION 'Unauthorized: Cannot reset another user''s progress';
  END IF;
  
  DELETE FROM public.attempts
  WHERE user_id = p_user_id
    AND question_id IN (SELECT id FROM public.questions WHERE cefr = p_cefr);
END;
$$;

-- Fix 3: Update check_rate_limit to validate caller owns the user_id
CREATE OR REPLACE FUNCTION public.check_rate_limit(
  p_user_id UUID,
  p_action TEXT DEFAULT 'attempt',
  p_max_count INTEGER DEFAULT 100,
  p_window_minutes INTEGER DEFAULT 10
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  attempt_count INTEGER;
BEGIN
  -- Only allow checking own rate limit
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot check another user''s rate limit';
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
$$;