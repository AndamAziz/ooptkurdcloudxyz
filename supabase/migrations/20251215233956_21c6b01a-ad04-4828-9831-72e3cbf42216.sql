-- 1. Create a secure view for questions without answers (students can't see correct_answer or explanation)
CREATE VIEW public.questions_safe AS
SELECT 
  id, 
  cefr, 
  difficulty, 
  q_type, 
  prompt, 
  options_a, 
  options_b, 
  options_c, 
  options_d, 
  tags,
  source,
  created_at
FROM public.questions;

-- Grant access to the view
GRANT SELECT ON public.questions_safe TO authenticated;

-- 2. Create a secure function to check answers (only reveals answer after submission)
CREATE OR REPLACE FUNCTION public.check_answer(
  p_question_id BIGINT, 
  p_chosen_answer TEXT
) 
RETURNS TABLE(is_correct BOOLEAN, correct_answer TEXT, explanation TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT 
    (UPPER(TRIM(p_chosen_answer)) = UPPER(TRIM(q.correct_answer))) as is_correct,
    q.correct_answer,
    q.explanation
  FROM questions q
  WHERE q.id = p_question_id;
$$;

-- 3. Create a public leaderboard view (only non-sensitive profile fields)
CREATE VIEW public.leaderboard_view AS
SELECT 
  user_id,
  username,
  display_name,
  xp,
  current_cefr
FROM public.profiles
ORDER BY xp DESC;

-- Grant access to the leaderboard view
GRANT SELECT ON public.leaderboard_view TO authenticated;
GRANT SELECT ON public.leaderboard_view TO anon;