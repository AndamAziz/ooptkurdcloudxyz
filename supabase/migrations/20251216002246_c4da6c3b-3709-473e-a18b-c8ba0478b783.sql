-- Fix search_path for generate_certificate_number function
CREATE OR REPLACE FUNCTION public.generate_certificate_number()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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