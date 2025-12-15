-- 1. Performance indexes
CREATE INDEX IF NOT EXISTS idx_questions_cefr ON public.questions(cefr);
CREATE INDEX IF NOT EXISTS idx_attempts_user ON public.attempts(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_attempts_question ON public.attempts(question_id);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON public.practice_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_user ON public.profiles(user_id);

-- 2. Function to get random questions excluding already answered ones
CREATE OR REPLACE FUNCTION public.get_random_questions(
  p_user_id UUID,
  p_cefr cefr_level,
  p_limit INTEGER DEFAULT 10
)
RETURNS SETOF questions_safe
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT qs.*
  FROM questions_safe qs
  WHERE qs.cefr = p_cefr
    AND qs.id NOT IN (
      SELECT DISTINCT a.question_id 
      FROM attempts a 
      WHERE a.user_id = p_user_id
    )
  ORDER BY random()
  LIMIT p_limit;
$$;

-- 3. Rate limiting table for tracking attempt frequency
CREATE TABLE IF NOT EXISTS public.rate_limits (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  action TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;

-- Users can only insert their own rate limit records
CREATE POLICY "Users can insert their own rate limits"
ON public.rate_limits
FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Users can view their own rate limits
CREATE POLICY "Users can view their own rate limits"
ON public.rate_limits
FOR SELECT
USING (auth.uid() = user_id);

-- Index for efficient rate limit queries
CREATE INDEX IF NOT EXISTS idx_rate_limits_user_action ON public.rate_limits(user_id, action, created_at);

-- 4. Function to check rate limit (max 100 attempts per 10 minutes)
CREATE OR REPLACE FUNCTION public.check_rate_limit(
  p_user_id UUID,
  p_action TEXT DEFAULT 'attempt',
  p_max_count INTEGER DEFAULT 100,
  p_window_minutes INTEGER DEFAULT 10
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  attempt_count INTEGER;
BEGIN
  -- Count recent actions
  SELECT COUNT(*) INTO attempt_count
  FROM public.rate_limits
  WHERE user_id = p_user_id
    AND action = p_action
    AND created_at > now() - (p_window_minutes || ' minutes')::interval;

  -- Record this action
  INSERT INTO public.rate_limits (user_id, action)
  VALUES (p_user_id, p_action);

  -- Clean up old records (older than 1 hour)
  DELETE FROM public.rate_limits
  WHERE created_at < now() - interval '1 hour';

  -- Return true if within limit
  RETURN attempt_count < p_max_count;
END;
$$;

-- Grant access
GRANT EXECUTE ON FUNCTION public.get_random_questions TO authenticated;
GRANT EXECUTE ON FUNCTION public.check_rate_limit TO authenticated;