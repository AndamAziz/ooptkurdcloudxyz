
-- Function to check and award certificate after each attempt
CREATE OR REPLACE FUNCTION public.check_and_award_certificate()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_total_questions INTEGER;
  v_answered_questions INTEGER;
  v_correct_answers INTEGER;
  v_accuracy NUMERIC;
  v_cefr cefr_level;
  v_user_id UUID;
  v_existing_cert UUID;
  v_study_hours NUMERIC;
BEGIN
  v_user_id := NEW.user_id;
  
  -- Get the CEFR level of the question just answered
  SELECT cefr INTO v_cefr FROM questions WHERE id = NEW.question_id;
  
  -- Count total questions in this level
  SELECT COUNT(*) INTO v_total_questions FROM questions WHERE cefr = v_cefr;
  
  -- Count how many distinct questions user has answered in this level
  SELECT COUNT(DISTINCT a.question_id) INTO v_answered_questions
  FROM attempts a
  JOIN questions q ON a.question_id = q.id
  WHERE a.user_id = v_user_id AND q.cefr = v_cefr;
  
  -- Only proceed if user has answered ALL questions in this level
  IF v_answered_questions >= v_total_questions THEN
    
    -- Calculate accuracy (best attempt per question - prioritize correct answers)
    SELECT COUNT(*) INTO v_correct_answers
    FROM (
      SELECT DISTINCT ON (a.question_id) a.question_id, a.is_correct
      FROM attempts a
      JOIN questions q ON a.question_id = q.id
      WHERE a.user_id = v_user_id AND q.cefr = v_cefr
      ORDER BY a.question_id, a.is_correct DESC, a.created_at DESC
    ) best_attempts
    WHERE is_correct = true;
    
    v_accuracy := (v_correct_answers::NUMERIC / v_total_questions) * 100;
    
    -- Check if accuracy >= 90%
    IF v_accuracy >= 90 THEN
      
      -- Check if certificate already exists for this level
      SELECT id INTO v_existing_cert 
      FROM certificates 
      WHERE user_id = v_user_id 
        AND cefr_level = v_cefr 
        AND certificate_type = 'level_completion'
        AND status = 'earned';
      
      -- Award certificate if not already awarded
      IF v_existing_cert IS NULL THEN
        -- Calculate study hours from practice sessions
        SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (ended_at - started_at)) / 3600), 0)
        INTO v_study_hours
        FROM practice_sessions
        WHERE user_id = v_user_id AND cefr = v_cefr AND ended_at IS NOT NULL;
        
        INSERT INTO certificates (
          user_id,
          certificate_type,
          cefr_level,
          score_percentage,
          total_questions_answered,
          total_correct,
          study_hours,
          status,
          issued_at
        ) VALUES (
          v_user_id,
          'level_completion',
          v_cefr,
          v_accuracy,
          v_total_questions,
          v_correct_answers,
          v_study_hours,
          'earned',
          NOW()
        );
      END IF;
      
    END IF;
  END IF;
  
  RETURN NEW;
END;
$$;

-- Create trigger after each attempt
DROP TRIGGER IF EXISTS check_certificate_trigger ON attempts;
CREATE TRIGGER check_certificate_trigger
AFTER INSERT ON attempts
FOR EACH ROW
EXECUTE FUNCTION public.check_and_award_certificate();
