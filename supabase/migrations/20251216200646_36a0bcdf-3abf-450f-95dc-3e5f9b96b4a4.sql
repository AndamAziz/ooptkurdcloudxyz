-- Fix: Make get_random_questions function SECURITY DEFINER so students can use placement test
-- The underlying questions table has RLS that only allows admins, but this function should work for all authenticated users

CREATE OR REPLACE FUNCTION public.get_random_questions(p_user_id uuid, p_cefr cefr_level, p_limit integer DEFAULT 10)
RETURNS SETOF questions_safe
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
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

-- Ensure authenticated users can call this function
GRANT EXECUTE ON FUNCTION public.get_random_questions(uuid, cefr_level, integer) TO authenticated;

-- Ensure the questions_safe view is accessible
GRANT SELECT ON public.questions_safe TO authenticated;