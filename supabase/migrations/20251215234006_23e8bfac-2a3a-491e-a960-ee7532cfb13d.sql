-- Fix security definer view warnings by explicitly setting security invoker
ALTER VIEW public.questions_safe SET (security_invoker = on);
ALTER VIEW public.leaderboard_view SET (security_invoker = on);