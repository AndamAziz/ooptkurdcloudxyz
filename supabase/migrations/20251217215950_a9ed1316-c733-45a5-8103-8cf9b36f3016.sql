-- Update the streak function to add 1 streak per 10 questions
CREATE OR REPLACE FUNCTION public.update_streak_on_session()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  current_streak INTEGER;
  longest_streak_val INTEGER;
  questions_streak INTEGER := 0;
  bonus_streak INTEGER := 0;
  accuracy_percent NUMERIC;
BEGIN
  -- Only run when session is completed (ended_at is set)
  IF NEW.ended_at IS NULL THEN
    RETURN NEW;
  END IF;

  -- Get current streak
  SELECT streak, longest_streak INTO current_streak, longest_streak_val
  FROM public.profiles
  WHERE user_id = NEW.user_id;

  current_streak := COALESCE(current_streak, 0);
  longest_streak_val := COALESCE(longest_streak_val, 0);

  -- Add 1 streak for every 10 questions practiced
  questions_streak := FLOOR(COALESCE(NEW.total_questions, 0) / 10);

  -- Calculate bonus streak based on accuracy when completing 50+ questions
  IF NEW.total_questions >= 50 THEN
    accuracy_percent := NEW.accuracy;
    
    IF accuracy_percent = 100 THEN
      bonus_streak := 10; -- Perfect score: +10 streak
    ELSIF accuracy_percent >= 70 THEN
      bonus_streak := 5; -- Good score (70%+): +5 streak
    END IF;
  END IF;

  -- Update profile with questions_streak + bonus_streak
  UPDATE public.profiles
  SET 
    streak = current_streak + questions_streak + bonus_streak,
    longest_streak = GREATEST(longest_streak_val, current_streak + questions_streak + bonus_streak),
    last_practice_date = CURRENT_DATE,
    last_active_at = NOW()
  WHERE user_id = NEW.user_id;

  RETURN NEW;
END;
$function$;