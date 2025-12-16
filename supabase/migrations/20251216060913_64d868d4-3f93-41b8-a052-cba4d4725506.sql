-- Drop the existing function first
DROP FUNCTION IF EXISTS public.get_question_progress(uuid);

-- Create the updated function with certificate info
CREATE OR REPLACE FUNCTION public.get_question_progress(p_user_id uuid)
RETURNS TABLE (
  cefr cefr_level,
  total_questions bigint,
  answered_questions bigint,
  remaining_questions bigint,
  correct_count bigint,
  wrong_count bigint,
  accuracy numeric,
  completion_percent numeric,
  certificate_target bigint,
  correct_needed_for_cert bigint,
  can_earn_certificate boolean
) 
LANGUAGE sql
STABLE 
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  WITH level_totals AS (
    SELECT 
      q.cefr,
      COUNT(*) as total_q
    FROM questions q
    GROUP BY q.cefr
  ),
  user_attempts AS (
    SELECT 
      q.cefr,
      COUNT(DISTINCT a.question_id) as answered,
      COUNT(DISTINCT CASE WHEN a.is_correct = true THEN a.question_id END) as correct
    FROM attempts a
    JOIN questions q ON a.question_id = q.id
    WHERE a.user_id = p_user_id
    GROUP BY q.cefr
  )
  SELECT 
    lt.cefr,
    lt.total_q as total_questions,
    COALESCE(ua.answered, 0) as answered_questions,
    lt.total_q - COALESCE(ua.answered, 0) as remaining_questions,
    COALESCE(ua.correct, 0) as correct_count,
    COALESCE(ua.answered, 0) - COALESCE(ua.correct, 0) as wrong_count,
    CASE 
      WHEN COALESCE(ua.answered, 0) > 0 
      THEN ROUND((COALESCE(ua.correct, 0)::NUMERIC / ua.answered) * 100, 1)
      ELSE 0 
    END as accuracy,
    ROUND((COALESCE(ua.answered, 0)::NUMERIC / lt.total_q) * 100, 1) as completion_percent,
    CEIL(lt.total_q * 0.9)::BIGINT as certificate_target,
    GREATEST(0, CEIL(lt.total_q * 0.9)::BIGINT - COALESCE(ua.correct, 0)) as correct_needed_for_cert,
    COALESCE(ua.answered, 0) >= lt.total_q AND 
      COALESCE(ua.correct, 0) >= CEIL(lt.total_q * 0.9) as can_earn_certificate
  FROM level_totals lt
  LEFT JOIN user_attempts ua ON lt.cefr = ua.cefr
  ORDER BY 
    CASE lt.cefr 
      WHEN 'A1' THEN 1 WHEN 'A2' THEN 2 WHEN 'B1' THEN 3 
      WHEN 'B2' THEN 4 WHEN 'C1' THEN 5 WHEN 'C2' THEN 6 
    END;
$$;