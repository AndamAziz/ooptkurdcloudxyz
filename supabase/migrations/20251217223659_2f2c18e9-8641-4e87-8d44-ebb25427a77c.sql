-- Fix streak rule: +1 streak per every 70 XP EARNED (tracked via bank), not based on lifetime XP milestones.

-- Add bank column to accumulate XP earned toward next streak
ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS streak_xp_bank integer NOT NULL DEFAULT 0;

-- Award streaks based on XP delta (NEW.xp - OLD.xp)
CREATE OR REPLACE FUNCTION public.award_streak_per_70xp()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  delta_xp integer;
  bank integer;
  streak_to_add integer;
  curr_streak integer;
  longest integer;
BEGIN
  -- Never award streaks when the account is suspended
  IF NEW.account_status = 'suspended' THEN
    RETURN NEW;
  END IF;

  delta_xp := COALESCE(NEW.xp, 0) - COALESCE(OLD.xp, 0);

  -- Only award on XP increases
  IF delta_xp <= 0 THEN
    RETURN NEW;
  END IF;

  bank := COALESCE(NEW.streak_xp_bank, OLD.streak_xp_bank, 0) + delta_xp;
  streak_to_add := bank / 70;
  bank := bank % 70;

  NEW.streak_xp_bank := bank;

  IF streak_to_add > 0 THEN
    curr_streak := COALESCE(NEW.streak, OLD.streak, 0);
    longest := COALESCE(NEW.longest_streak, OLD.longest_streak, 0);

    NEW.streak := curr_streak + streak_to_add;
    NEW.longest_streak := GREATEST(longest, curr_streak + streak_to_add);
  END IF;

  RETURN NEW;
END;
$$;

-- Ensure trigger exists and points to the updated function
DROP TRIGGER IF EXISTS profiles_xp_streak_trigger ON public.profiles;

CREATE TRIGGER profiles_xp_streak_trigger
BEFORE UPDATE OF xp ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.award_streak_per_70xp();