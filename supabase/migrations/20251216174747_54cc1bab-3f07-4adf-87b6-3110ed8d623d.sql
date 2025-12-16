-- Add placement columns to profiles table
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS placement_completed BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS placement_score NUMERIC;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS placement_date TIMESTAMP WITH TIME ZONE;

-- Create placement_results table
CREATE TABLE public.placement_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  total_questions INTEGER DEFAULT 50,
  correct_answers INTEGER DEFAULT 0,
  level_scores JSONB DEFAULT '{}'::jsonb,
  recommended_level cefr_level NOT NULL DEFAULT 'A1',
  completed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Enable RLS
ALTER TABLE public.placement_results ENABLE ROW LEVEL SECURITY;

-- RLS policies for placement_results
CREATE POLICY "Users can view their own placement results"
ON public.placement_results
FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own placement results"
ON public.placement_results
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Admins can view all placement results"
ON public.placement_results
FOR SELECT
TO authenticated
USING (public.has_role(auth.uid(), 'admin'));

-- Create function to get placement test questions (excludes correct_answer for security)
CREATE OR REPLACE FUNCTION public.get_placement_questions(p_user_id UUID)
RETURNS TABLE (
  id BIGINT,
  cefr cefr_level,
  prompt TEXT,
  options_a TEXT,
  options_b TEXT,
  options_c TEXT,
  options_d TEXT,
  difficulty INTEGER
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'A1'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 8
  )
  UNION ALL
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'A2'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 9
  )
  UNION ALL
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'B1'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 10
  )
  UNION ALL
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'B2'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 10
  )
  UNION ALL
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'C1'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 8
  )
  UNION ALL
  (
    SELECT q.id, q.cefr, q.prompt, q.options_a, q.options_b, q.options_c, q.options_d, q.difficulty
    FROM questions q
    WHERE q.cefr = 'C2'::cefr_level
    AND q.id NOT IN (SELECT a.question_id FROM attempts a WHERE a.user_id = p_user_id)
    ORDER BY RANDOM()
    LIMIT 5
  );
END;
$$;

-- Grant execute permission
GRANT EXECUTE ON FUNCTION public.get_placement_questions(UUID) TO authenticated;

-- Function to complete placement test and update profile
CREATE OR REPLACE FUNCTION public.complete_placement_test(
  p_user_id UUID,
  p_correct_answers INTEGER,
  p_level_scores JSONB,
  p_recommended_level cefr_level
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_result_id UUID;
  v_score NUMERIC;
BEGIN
  -- Verify caller owns this user_id
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot complete placement test for another user';
  END IF;

  -- Calculate score percentage
  v_score := (p_correct_answers::NUMERIC / 50) * 100;

  -- Insert placement result
  INSERT INTO placement_results (user_id, correct_answers, level_scores, recommended_level)
  VALUES (p_user_id, p_correct_answers, p_level_scores, p_recommended_level)
  RETURNING id INTO v_result_id;

  -- Update profile
  UPDATE profiles
  SET 
    placement_completed = true,
    placement_score = v_score,
    placement_date = NOW(),
    current_cefr = p_recommended_level
  WHERE user_id = p_user_id;

  RETURN v_result_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.complete_placement_test(UUID, INTEGER, JSONB, cefr_level) TO authenticated;

-- Function to skip placement test
CREATE OR REPLACE FUNCTION public.skip_placement_test(p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Verify caller owns this user_id
  IF p_user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot skip placement test for another user';
  END IF;

  -- Update profile to mark placement as completed (skipped)
  UPDATE profiles
  SET 
    placement_completed = true,
    placement_date = NOW(),
    current_cefr = 'A1'::cefr_level
  WHERE user_id = p_user_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.skip_placement_test(UUID) TO authenticated;