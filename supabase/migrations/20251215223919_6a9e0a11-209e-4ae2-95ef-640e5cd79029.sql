-- Fix function search path for get_cefr_for_xp
CREATE OR REPLACE FUNCTION public.get_cefr_for_xp(xp_value INTEGER)
RETURNS cefr_level
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT cefr FROM public.levels
  WHERE min_xp <= xp_value
  ORDER BY min_xp DESC
  LIMIT 1
$$;