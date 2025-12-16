
-- Add unique constraint to prevent duplicate attempts (if not exists)
DO $$ 
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'attempts_user_question_unique'
  ) THEN
    ALTER TABLE public.attempts ADD CONSTRAINT attempts_user_question_unique UNIQUE (user_id, question_id);
  END IF;
END $$;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_attempts_user_question ON public.attempts(user_id, question_id);
CREATE INDEX IF NOT EXISTS idx_questions_cefr ON public.questions(cefr);
CREATE INDEX IF NOT EXISTS idx_attempts_user_id ON public.attempts(user_id);

-- Create function to get question progress stats per level
CREATE OR REPLACE FUNCTION public.get_question_progress(p_user_id UUID)
RETURNS TABLE(
  cefr cefr_level,
  total_questions BIGINT,
  answered_questions BIGINT,
  remaining_questions BIGINT,
  correct_count BIGINT,
  accuracy NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT 
    q.cefr,
    COUNT(q.id) as total_questions,
    COUNT(a.id) as answered_questions,
    COUNT(q.id) - COUNT(a.id) as remaining_questions,
    COUNT(CASE WHEN a.is_correct = true THEN 1 END) as correct_count,
    CASE 
      WHEN COUNT(a.id) > 0 THEN ROUND((COUNT(CASE WHEN a.is_correct = true THEN 1 END)::NUMERIC / COUNT(a.id)::NUMERIC) * 100, 1)
      ELSE 0
    END as accuracy
  FROM questions q
  LEFT JOIN attempts a ON q.id = a.question_id AND a.user_id = p_user_id
  GROUP BY q.cefr
  ORDER BY q.cefr;
$$;

-- Create function to reset progress for a specific level
CREATE OR REPLACE FUNCTION public.reset_level_progress(p_user_id UUID, p_cefr cefr_level)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  DELETE FROM public.attempts
  WHERE user_id = p_user_id
    AND question_id IN (SELECT id FROM public.questions WHERE cefr = p_cefr);
END;
$$;

-- Update get_random_questions to return better results
CREATE OR REPLACE FUNCTION public.get_random_questions(p_user_id uuid, p_cefr cefr_level, p_limit integer DEFAULT 10)
RETURNS SETOF questions_safe
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT qs.*
  FROM questions_safe qs
  WHERE qs.cefr = p_cefr
    AND qs.id NOT IN (
      SELECT DISTINCT a.question_id 
      FROM attempts a 
      WHERE a.user_id = p_user_id
    )
  ORDER BY random()
  LIMIT p_limit;
$$;
