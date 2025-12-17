-- Ensure streak updates run automatically when a practice session is completed

-- Drop existing triggers if any
DROP TRIGGER IF EXISTS practice_sessions_streak_after_update ON public.practice_sessions;
DROP TRIGGER IF EXISTS practice_sessions_streak_after_insert ON public.practice_sessions;

-- Fire when a session is completed (ended_at transitions from NULL -> value)
CREATE TRIGGER practice_sessions_streak_after_update
AFTER UPDATE OF ended_at ON public.practice_sessions
FOR EACH ROW
WHEN (NEW.ended_at IS NOT NULL AND OLD.ended_at IS NULL)
EXECUTE FUNCTION public.update_streak_on_session();

-- Safety: if a session is ever inserted already completed
CREATE TRIGGER practice_sessions_streak_after_insert
AFTER INSERT ON public.practice_sessions
FOR EACH ROW
WHEN (NEW.ended_at IS NOT NULL)
EXECUTE FUNCTION public.update_streak_on_session();