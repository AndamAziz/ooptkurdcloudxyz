-- Add streak tracking columns to profiles
ALTER TABLE public.profiles 
ADD COLUMN IF NOT EXISTS streak INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS last_practice_date DATE;

-- Function to update streak when user practices
CREATE OR REPLACE FUNCTION public.update_streak_on_session()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  current_streak INTEGER;
  last_date DATE;
BEGIN
  -- Get current streak and last practice date
  SELECT streak, last_practice_date INTO current_streak, last_date
  FROM public.profiles
  WHERE user_id = NEW.user_id;

  -- Calculate new streak
  IF last_date IS NULL THEN
    -- First practice ever
    current_streak := 1;
  ELSIF last_date = CURRENT_DATE THEN
    -- Already practiced today, no change
    NULL;
  ELSIF last_date = CURRENT_DATE - INTERVAL '1 day' THEN
    -- Practiced yesterday, increment streak
    current_streak := current_streak + 1;
  ELSE
    -- Streak broken, reset to 1
    current_streak := 1;
  END IF;

  -- Update profile
  UPDATE public.profiles
  SET streak = current_streak, last_practice_date = CURRENT_DATE
  WHERE user_id = NEW.user_id;

  RETURN NEW;
END;
$$;

-- Trigger to update streak when a practice session ends
CREATE TRIGGER update_streak_on_practice
  AFTER UPDATE OF ended_at ON public.practice_sessions
  FOR EACH ROW
  WHEN (NEW.ended_at IS NOT NULL AND OLD.ended_at IS NULL)
  EXECUTE FUNCTION public.update_streak_on_session();