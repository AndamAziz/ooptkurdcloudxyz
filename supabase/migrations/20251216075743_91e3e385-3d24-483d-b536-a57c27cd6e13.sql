-- Fix increment_user_xp function to prevent privilege escalation
-- Add authorization check to verify caller owns the user_id

CREATE OR REPLACE FUNCTION public.increment_user_xp(
  _user_id UUID, 
  _xp_increment INTEGER
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Verify caller owns this user_id (prevent privilege escalation)
  IF _user_id != auth.uid() THEN
    RAISE EXCEPTION 'Unauthorized: Cannot increment another user''s XP';
  END IF;
  
  -- Validate XP increment is reasonable (prevent massive inflation)
  IF _xp_increment < 0 OR _xp_increment > 1000 THEN
    RAISE EXCEPTION 'Invalid XP increment value';
  END IF;
  
  UPDATE profiles 
  SET xp = COALESCE(xp, 0) + _xp_increment 
  WHERE user_id = _user_id;
END;
$$;