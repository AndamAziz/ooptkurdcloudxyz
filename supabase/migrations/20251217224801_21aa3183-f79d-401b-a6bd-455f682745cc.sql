-- Award XP immediately on every answered question (attempt insert)
-- and let streak be computed via profiles_xp_streak_trigger (+1/70 XP bank).

CREATE OR REPLACE FUNCTION public.award_xp_on_attempt()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  -- Only award XP for active accounts
  IF NOT public.is_account_active(NEW.user_id) THEN
    RETURN NEW;
  END IF;

  UPDATE public.profiles
  SET
    xp = COALESCE(xp, 0) + 70,
    last_practice_date = CURRENT_DATE,
    last_active_at = NOW()
  WHERE user_id = NEW.user_id;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS attempts_award_xp_after_insert ON public.attempts;

CREATE TRIGGER attempts_award_xp_after_insert
AFTER INSERT ON public.attempts
FOR EACH ROW
EXECUTE FUNCTION public.award_xp_on_attempt();