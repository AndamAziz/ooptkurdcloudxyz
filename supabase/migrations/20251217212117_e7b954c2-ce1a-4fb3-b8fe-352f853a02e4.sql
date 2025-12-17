-- Add suspend_reason column to profiles table
ALTER TABLE public.profiles
ADD COLUMN suspend_reason text DEFAULT NULL;