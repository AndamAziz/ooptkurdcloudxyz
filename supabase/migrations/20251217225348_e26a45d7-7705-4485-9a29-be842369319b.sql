-- Update the XP award trigger to use new rules: +10 correct, -5 wrong
-- XP is awarded per attempt based on is_correct flag

CREATE OR REPLACE FUNCTION public.award_xp_on_attempt()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  xp_change integer;
  current_xp integer;
  new_xp integer;
BEGIN
  -- Only award XP for active accounts
  IF NOT public.is_account_active(NEW.user_id) THEN
    RETURN NEW;
  END IF;

  -- Calculate XP change based on correct/wrong
  IF NEW.is_correct = true THEN
    xp_change := 10;
  ELSE
    xp_change := -5;
  END IF;

  -- Get current XP
  SELECT COALESCE(xp, 0) INTO current_xp
  FROM public.profiles
  WHERE user_id = NEW.user_id;

  -- Calculate new XP (never go below 0)
  new_xp := GREATEST(0, current_xp + xp_change);

  UPDATE public.profiles
  SET
    xp = new_xp,
    last_practice_date = CURRENT_DATE,
    last_active_at = NOW()
  WHERE user_id = NEW.user_id;

  RETURN NEW;
END;
$$;