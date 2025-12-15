-- Drop the existing restrictive policy
DROP POLICY IF EXISTS "Admins can manage questions" ON public.questions;

-- Create a permissive INSERT policy for admins
CREATE POLICY "Admins can insert questions"
ON public.questions
FOR INSERT
WITH CHECK (has_role(auth.uid(), 'admin'::app_role));

-- Create a permissive UPDATE policy for admins  
CREATE POLICY "Admins can update questions"
ON public.questions
FOR UPDATE
USING (has_role(auth.uid(), 'admin'::app_role));

-- Create a permissive DELETE policy for admins
CREATE POLICY "Admins can delete questions"
ON public.questions
FOR DELETE
USING (has_role(auth.uid(), 'admin'::app_role));