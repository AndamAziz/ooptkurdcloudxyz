-- Fix streak logic: award +1 streak per 10 answered questions via RPC (frontend), and keep only bonus streaks on session completion.

-- 1) Create RPC to increment streak safely
CREATE OR REPLACE FUNCTION public.increment_user_streak(_user_id uuid, _streak_increment integer)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_current_streak integer;
  v_new_streak integer;
  v_longest integer;
BEGIN
  -- Verify caller owns this user_id
  IF _user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot increment another user''s streak';
  END IF;

  -- Block suspended users
  IF NOT public.is_account_active(auth.uid()) THEN
    RAISE EXCEPTION 'Account suspended';
  END IF;

  -- Validate increment
  IF _streak_increment IS NULL OR _streak_increment <= 0 OR _streak_increment > 50 THEN
    RAISE EXCEPTION 'Invalid streak increment value';
  END IF;

  SELECT COALESCE(streak, 0), COALESCE(longest_streak, 0)
  INTO v_current_streak, v_longest
  FROM public.profiles
  WHERE user_id = _user_id;

  v_new_streak := v_current_streak + _streak_increment;

  UPDATE public.profiles
  SET
    streak = v_new_streak,
    longest_streak = GREATEST(v_longest, v_new_streak),
    last_practice_date = CURRENT_DATE,
    last_active_at = NOW()
  WHERE user_id = _user_id;
END;
$$;


-- 2) Update session-completion trigger function: only add bonus streaks (50+ questions), no base per-10 award here
CREATE OR REPLACE FUNCTION public.update_streak_on_session()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  current_streak INTEGER;
  longest_streak_val INTEGER;
  bonus_streak INTEGER := 0;
  accuracy_percent NUMERIC;
BEGIN
  -- Only run when session is completed (ended_at is set)
  IF NEW.ended_at IS NULL THEN
    RETURN NEW;
  END IF;

  -- Avoid double-processing on updates
  IF TG_OP = 'UPDATE' AND OLD.ended_at IS NOT NULL THEN
    RETURN NEW;
  END IF;

  -- Bonus streak only for 50+ questions
  IF COALESCE(NEW.total_questions, 0) >= 50 THEN
    accuracy_percent := COALESCE(NEW.accuracy, 0);

    IF accuracy_percent = 100 THEN
      bonus_streak := 10;
    ELSIF accuracy_percent >= 70 THEN
      bonus_streak := 5;
    END IF;
  END IF;

  SELECT COALESCE(streak, 0), COALESCE(longest_streak, 0)
  INTO current_streak, longest_streak_val
  FROM public.profiles
  WHERE user_id = NEW.user_id;

  UPDATE public.profiles
  SET
    streak = current_streak + bonus_streak,
    longest_streak = GREATEST(longest_streak_val, current_streak + bonus_streak),
    last_practice_date = CURRENT_DATE,
    last_active_at = NOW()
  WHERE user_id = NEW.user_id;

  RETURN NEW;
END;
$$;


-- 3) Clean up and ensure correct triggers on practice_sessions
DROP TRIGGER IF EXISTS update_streak_on_practice ON public.practice_sessions;
DROP TRIGGER IF EXISTS practice_sessions_streak_after_insert ON public.practice_sessions;
DROP TRIGGER IF EXISTS practice_sessions_streak_after_update ON public.practice_sessions;

CREATE TRIGGER practice_sessions_streak_after_insert
AFTER INSERT ON public.practice_sessions
FOR EACH ROW
WHEN (NEW.ended_at IS NOT NULL)
EXECUTE FUNCTION public.update_streak_on_session();

CREATE TRIGGER practice_sessions_streak_after_update
AFTER UPDATE OF ended_at ON public.practice_sessions
FOR EACH ROW
WHEN (NEW.ended_at IS NOT NULL AND OLD.ended_at IS NULL)
EXECUTE FUNCTION public.update_streak_on_session();
