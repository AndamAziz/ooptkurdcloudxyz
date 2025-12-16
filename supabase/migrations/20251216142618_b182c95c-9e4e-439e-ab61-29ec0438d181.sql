-- Fix 1: Remove overly permissive profiles policy that exposes all user data
-- Keep only owner-based and admin access policies
DROP POLICY IF EXISTS "Authenticated users can view leaderboard data" ON public.profiles;

-- Fix 2: Recreate leaderboard_view with security_definer to allow authenticated read
-- This view only exposes minimal leaderboard fields (no sensitive data)
DROP VIEW IF EXISTS public.leaderboard_view;

CREATE VIEW public.leaderboard_view 
WITH (security_barrier = true)
AS 
SELECT 
  user_id,
  username,
  display_name,
  xp,
  current_cefr
FROM public.profiles
ORDER BY xp DESC;

-- Grant select to authenticated users on the view
GRANT SELECT ON public.leaderboard_view TO authenticated;

-- Fix 3: Restrict questions table to only admins for direct access
-- Students must use questions_safe view which excludes correct_answer
DROP POLICY IF EXISTS "Anyone authenticated can read questions" ON public.questions;

-- Only admins can read full questions table (with answers)
CREATE POLICY "Only admins can read full questions"
ON public.questions
FOR SELECT
TO authenticated
USING (public.has_role(auth.uid(), 'admin'));

-- Ensure questions_safe view is accessible to authenticated users
-- This view already excludes correct_answer and explanation columns
GRANT SELECT ON public.questions_safe TO authenticated;