-- Create app_role enum for secure role management
CREATE TYPE public.app_role AS ENUM ('student', 'admin');

-- Create CEFR level enum
CREATE TYPE public.cefr_level AS ENUM ('A1', 'A2', 'B1', 'B2', 'C1', 'C2');

-- Create profiles table
CREATE TABLE public.profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL UNIQUE,
  username TEXT UNIQUE,
  display_name TEXT,
  current_cefr cefr_level DEFAULT 'A1',
  xp INTEGER DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Create user_roles table (separate from profiles for security)
CREATE TABLE public.user_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  role app_role NOT NULL DEFAULT 'student',
  UNIQUE (user_id, role)
);

-- Create questions table
CREATE TABLE public.questions (
  id BIGSERIAL PRIMARY KEY,
  cefr cefr_level NOT NULL,
  difficulty INTEGER DEFAULT 2,
  q_type TEXT DEFAULT 'MCQ',
  prompt TEXT NOT NULL,
  options_a TEXT,
  options_b TEXT,
  options_c TEXT,
  options_d TEXT,
  correct_answer TEXT,
  explanation TEXT,
  tags TEXT,
  source TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Create attempts table
CREATE TABLE public.attempts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  question_id BIGINT REFERENCES public.questions(id) ON DELETE CASCADE NOT NULL,
  chosen_answer TEXT,
  is_correct BOOLEAN,
  time_spent_sec INTEGER DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Create practice_sessions table
CREATE TABLE public.practice_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  cefr cefr_level NOT NULL,
  mode TEXT DEFAULT '10_questions',
  started_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  ended_at TIMESTAMP WITH TIME ZONE,
  total_questions INTEGER DEFAULT 0,
  correct_count INTEGER DEFAULT 0,
  accuracy NUMERIC(5, 2) DEFAULT 0,
  xp_earned INTEGER DEFAULT 0,
  summary_text TEXT
);

-- Create levels table for CEFR thresholds
CREATE TABLE public.levels (
  cefr cefr_level PRIMARY KEY,
  min_xp INTEGER NOT NULL,
  description TEXT
);

-- Insert level thresholds
INSERT INTO public.levels (cefr, min_xp, description) VALUES
  ('A1', 0, 'Beginner - Can understand and use familiar everyday expressions'),
  ('A2', 800, 'Elementary - Can communicate in simple and routine tasks'),
  ('B1', 1800, 'Intermediate - Can deal with most situations while traveling'),
  ('B2', 3200, 'Upper Intermediate - Can interact with fluency and spontaneity'),
  ('C1', 4800, 'Advanced - Can express ideas fluently and spontaneously'),
  ('C2', 6500, 'Proficient - Can understand virtually everything heard or read');

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.practice_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.levels ENABLE ROW LEVEL SECURITY;

-- Security definer function to check roles
CREATE OR REPLACE FUNCTION public.has_role(_user_id UUID, _role app_role)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.user_roles
    WHERE user_id = _user_id
      AND role = _role
  )
$$;

-- Function to get user's current CEFR based on XP
CREATE OR REPLACE FUNCTION public.get_cefr_for_xp(xp_value INTEGER)
RETURNS cefr_level
LANGUAGE sql
STABLE
AS $$
  SELECT cefr FROM public.levels
  WHERE min_xp <= xp_value
  ORDER BY min_xp DESC
  LIMIT 1
$$;

-- Function to update user's CEFR when XP changes
CREATE OR REPLACE FUNCTION public.update_user_cefr()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  NEW.current_cefr := public.get_cefr_for_xp(NEW.xp);
  RETURN NEW;
END;
$$;

-- Trigger to auto-update CEFR level
CREATE TRIGGER update_cefr_on_xp_change
  BEFORE UPDATE OF xp ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.update_user_cefr();

-- Function to handle new user signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (user_id, username, display_name)
  VALUES (
    NEW.id,
    NEW.raw_user_meta_data ->> 'username',
    COALESCE(NEW.raw_user_meta_data ->> 'display_name', NEW.raw_user_meta_data ->> 'username')
  );
  
  INSERT INTO public.user_roles (user_id, role)
  VALUES (NEW.id, 'student');
  
  RETURN NEW;
END;
$$;

-- Trigger for new user signup
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

-- RLS Policies for profiles
CREATE POLICY "Users can view their own profile"
  ON public.profiles FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Users can update their own profile"
  ON public.profiles FOR UPDATE
  USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all profiles"
  ON public.profiles FOR SELECT
  USING (public.has_role(auth.uid(), 'admin'));

-- RLS Policies for user_roles
CREATE POLICY "Users can view their own roles"
  ON public.user_roles FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all roles"
  ON public.user_roles FOR SELECT
  USING (public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Admins can manage roles"
  ON public.user_roles FOR ALL
  USING (public.has_role(auth.uid(), 'admin'));

-- RLS Policies for questions
CREATE POLICY "Anyone authenticated can read questions"
  ON public.questions FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Admins can manage questions"
  ON public.questions FOR ALL
  USING (public.has_role(auth.uid(), 'admin'));

-- RLS Policies for attempts
CREATE POLICY "Users can view their own attempts"
  ON public.attempts FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own attempts"
  ON public.attempts FOR INSERT
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Admins can view all attempts"
  ON public.attempts FOR SELECT
  USING (public.has_role(auth.uid(), 'admin'));

-- RLS Policies for practice_sessions
CREATE POLICY "Users can view their own sessions"
  ON public.practice_sessions FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own sessions"
  ON public.practice_sessions FOR INSERT
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own sessions"
  ON public.practice_sessions FOR UPDATE
  USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all sessions"
  ON public.practice_sessions FOR SELECT
  USING (public.has_role(auth.uid(), 'admin'));

-- RLS Policies for levels (public read)
CREATE POLICY "Anyone can read levels"
  ON public.levels FOR SELECT
  TO authenticated
  USING (true);

-- Create indexes for performance
CREATE INDEX idx_questions_cefr ON public.questions(cefr);
CREATE INDEX idx_questions_difficulty ON public.questions(difficulty);
CREATE INDEX idx_attempts_user_id ON public.attempts(user_id);
CREATE INDEX idx_attempts_created_at ON public.attempts(created_at);
CREATE INDEX idx_practice_sessions_user_id ON public.practice_sessions(user_id);
CREATE INDEX idx_profiles_user_id ON public.profiles(user_id);