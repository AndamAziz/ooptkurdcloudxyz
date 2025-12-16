-- Add new columns to profiles table
ALTER TABLE public.profiles 
ADD COLUMN IF NOT EXISTS last_active_at timestamp with time zone DEFAULT now(),
ADD COLUMN IF NOT EXISTS total_practice_time_minutes integer DEFAULT 0,
ADD COLUMN IF NOT EXISTS longest_streak integer DEFAULT 0,
ADD COLUMN IF NOT EXISTS account_status text DEFAULT 'active' CHECK (account_status IN ('active', 'inactive', 'suspended')),
ADD COLUMN IF NOT EXISTS registration_source text DEFAULT 'organic' CHECK (registration_source IN ('organic', 'referral', 'social'));

-- Create admin_logs table
CREATE TABLE IF NOT EXISTS public.admin_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_id uuid NOT NULL,
  action_type text NOT NULL CHECK (action_type IN ('view_student', 'export_data', 'verify_cert', 'suspend_user', 'award_cert', 'delete_user')),
  target_user_id uuid,
  details jsonb DEFAULT '{}',
  created_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Enable RLS on admin_logs
ALTER TABLE public.admin_logs ENABLE ROW LEVEL SECURITY;

-- Only admins can view and insert admin logs
CREATE POLICY "Admins can view admin logs"
ON public.admin_logs FOR SELECT
TO authenticated
USING (has_role(auth.uid(), 'admin'));

CREATE POLICY "Admins can insert admin logs"
ON public.admin_logs FOR INSERT
TO authenticated
WITH CHECK (has_role(auth.uid(), 'admin'));

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_admin_logs_created_at ON public.admin_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_logs_admin_id ON public.admin_logs(admin_id);
CREATE INDEX IF NOT EXISTS idx_profiles_last_active ON public.profiles(last_active_at DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_account_status ON public.profiles(account_status);