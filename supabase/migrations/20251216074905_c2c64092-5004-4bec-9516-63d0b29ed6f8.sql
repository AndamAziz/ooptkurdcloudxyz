-- Create atomic increment function to prevent XP race conditions
CREATE OR REPLACE FUNCTION public.increment_user_xp(
  _user_id UUID, 
  _xp_increment INTEGER
)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE profiles 
  SET xp = COALESCE(xp, 0) + _xp_increment 
  WHERE user_id = _user_id;
$$;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION public.increment_user_xp TO authenticated;