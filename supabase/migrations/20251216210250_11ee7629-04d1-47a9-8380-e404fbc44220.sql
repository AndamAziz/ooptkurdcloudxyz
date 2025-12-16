-- Update placement question fetching to return ALL available questions (e.g., 230) every time,
-- for both students and admins, without excluding previously attempted questions.

CREATE OR REPLACE FUNCTION public.get_placement_questions(p_user_id uuid)
RETURNS TABLE(
  id bigint,
  cefr cefr_level,
  prompt text,
  options_a text,
  options_b text,
  options_c text,
  options_d text,
  difficulty integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT
    q.id::bigint AS id,
    q.cefr,
    q.prompt,
    q.options_a,
    q.options_b,
    q.options_c,
    q.options_d,
    q.difficulty
  FROM public.questions q
  ORDER BY
    CASE q.cefr
      WHEN 'A1' THEN 1
      WHEN 'A2' THEN 2
      WHEN 'B1' THEN 3
      WHEN 'B2' THEN 4
      WHEN 'C1' THEN 5
      WHEN 'C2' THEN 6
      ELSE 99
    END,
    q.id;
$$;

-- Ensure authenticated users can call it
GRANT EXECUTE ON FUNCTION public.get_placement_questions(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_placement_questions(uuid) TO anon;