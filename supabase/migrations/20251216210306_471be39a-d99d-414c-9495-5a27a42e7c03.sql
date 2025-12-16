-- Tighten access: placement questions should be callable only for logged-in users.
REVOKE ALL ON FUNCTION public.get_placement_questions(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_placement_questions(uuid) TO authenticated;