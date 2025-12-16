-- Create update_updated_at_column function if not exists
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

-- Create certificate type enum (drop if exists from failed migration)
DROP TYPE IF EXISTS public.certificate_type CASCADE;
CREATE TYPE public.certificate_type AS ENUM ('level_completion', 'course_completion', 'excellence');

-- Create certificate status enum
DROP TYPE IF EXISTS public.certificate_status CASCADE;
CREATE TYPE public.certificate_status AS ENUM ('earned', 'pending', 'revoked');

-- Drop and recreate certificates table
DROP TABLE IF EXISTS public.certificates CASCADE;

-- Create certificates table
CREATE TABLE public.certificates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  certificate_type certificate_type NOT NULL,
  cefr_level cefr_level NOT NULL,
  issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  certificate_number TEXT UNIQUE NOT NULL DEFAULT '',
  score_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
  total_questions_answered INTEGER NOT NULL DEFAULT 0,
  total_correct INTEGER NOT NULL DEFAULT 0,
  study_hours NUMERIC(6,2) NOT NULL DEFAULT 0,
  status certificate_status NOT NULL DEFAULT 'earned',
  pdf_url TEXT,
  verified_by_admin BOOLEAN NOT NULL DEFAULT false,
  admin_notes TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create indexes
CREATE INDEX idx_certificates_user_id ON public.certificates(user_id);
CREATE INDEX idx_certificates_certificate_number ON public.certificates(certificate_number);
CREATE INDEX idx_certificates_status ON public.certificates(status);

-- Enable RLS
ALTER TABLE public.certificates ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Users can view their own certificates"
ON public.certificates FOR SELECT
USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all certificates"
ON public.certificates FOR SELECT
USING (public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Admins can update certificates"
ON public.certificates FOR UPDATE
USING (public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Admins can insert certificates"
ON public.certificates FOR INSERT
WITH CHECK (public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Users can insert their own certificates"
ON public.certificates FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Function to generate certificate number
CREATE OR REPLACE FUNCTION public.generate_certificate_number()
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
  new_number TEXT;
  year_part TEXT;
  random_part TEXT;
BEGIN
  year_part := EXTRACT(YEAR FROM CURRENT_DATE)::TEXT;
  random_part := LPAD(FLOOR(RANDOM() * 100000)::TEXT, 5, '0');
  new_number := 'CEFR-' || year_part || '-' || random_part;
  
  WHILE EXISTS (SELECT 1 FROM public.certificates WHERE certificate_number = new_number) LOOP
    random_part := LPAD(FLOOR(RANDOM() * 100000)::TEXT, 5, '0');
    new_number := 'CEFR-' || year_part || '-' || random_part;
  END LOOP;
  
  RETURN new_number;
END;
$$;

-- Trigger to auto-generate certificate number
CREATE OR REPLACE FUNCTION public.set_certificate_number()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.certificate_number IS NULL OR NEW.certificate_number = '' THEN
    NEW.certificate_number := public.generate_certificate_number();
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_set_certificate_number
BEFORE INSERT ON public.certificates
FOR EACH ROW
EXECUTE FUNCTION public.set_certificate_number();

-- Trigger to update updated_at
CREATE TRIGGER update_certificates_updated_at
BEFORE UPDATE ON public.certificates
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();