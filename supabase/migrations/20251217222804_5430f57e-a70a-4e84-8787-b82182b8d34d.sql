-- Add trigger on profiles.xp to award +1 streak per 70 XP gained.

CREATE OR REPLACE FUNCTION public.award_streak_per_70xp()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  old_xp integer;
  new_xp integer;
  old_multiples integer;
  new_multiples integer;
  streak_to_add integer;
  curr_streak integer;
  longest integer;
BEGIN
  old_xp := COALESCE(OLD.xp, 0);
  new_xp := COALESCE(NEW.xp, 0);

  -- Calculate how many 70 XP milestones each value represents
  old_multiples := old_xp / 70;
  new_multiples := new_xp / 70;

  streak_to_add := new_multiples - old_multiples;

  IF streak_to_add > 0 THEN
    curr_streak := COALESCE(NEW.streak, 0);
    longest := COALESCE(NEW.longest_streak, 0);

    NEW.streak := curr_streak + streak_to_add;
    NEW.longest_streak := GREATEST(longest, curr_streak + streak_to_add);
  END IF;

  RETURN NEW;
END;
$$;

-- Create trigger on profiles xp column update
DROP TRIGGER IF EXISTS profiles_xp_streak_trigger ON public.profiles;

CREATE TRIGGER profiles_xp_streak_trigger
BEFORE UPDATE OF xp ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.award_streak_per_70xp();
