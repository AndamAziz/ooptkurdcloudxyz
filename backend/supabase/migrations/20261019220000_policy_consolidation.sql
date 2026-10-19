-- PENDING on nefpgqoykfndjvrdultp: contains DROP POLICY, which the MCP tooling cannot confirm.
-- Apply with `supabase db push --workdir backend` (confirm when prompted).
-- Performance advisor follow-up (no change in who can read what):
-- * one SELECT policy per table instead of separate owner/admin policies, so Postgres
--   evaluates a single expression per row;
-- * index for the user_roles.granted_by foreign key.

drop policy "profiles: owner reads own" on public.profiles;
drop policy "profiles: admin reads all" on public.profiles;
create policy "profiles: owner or admin reads"
  on public.profiles for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')));

drop policy "user_roles: owner reads own" on public.user_roles;
drop policy "user_roles: admin reads all" on public.user_roles;
create policy "user_roles: owner or admin reads"
  on public.user_roles for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')));

drop policy "attempts: owner reads own" on public.assessment_attempts;
drop policy "attempts: admin reads all" on public.assessment_attempts;
create policy "attempts: owner or admin reads"
  on public.assessment_attempts for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')));

drop policy "responses: owner reads own" on public.assessment_responses;
drop policy "responses: admin reads all" on public.assessment_responses;
create policy "responses: owner or admin reads"
  on public.assessment_responses for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')));

create index user_roles_granted_by_idx on public.user_roles (granted_by);
