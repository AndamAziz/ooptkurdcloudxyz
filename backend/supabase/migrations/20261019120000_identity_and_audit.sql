-- EnglishMaster AI — identity, roles, profiles and activity log.
--
-- Security model
--   * Helper functions live in schema `private`, which is NOT exposed through the
--     Data API, so they cannot be called as RPCs.
--   * Clients never write to profiles, user_roles or activity_logs directly.
--     Profile edits go through public.update_my_profile(); roles change only through
--     privileged server code; log rows are written by SECURITY DEFINER functions.
--   * A role is never taken from client-supplied sign-up metadata.

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Types
-- ---------------------------------------------------------------------------
create type public.app_role as enum ('student', 'teacher', 'admin');
create type public.cefr_level as enum ('PRE_A1', 'A1', 'A2', 'B1', 'B2', 'C1', 'C2');
create type public.ui_language as enum ('en', 'ckb');
create type public.account_status as enum ('active', 'suspended');

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
create table public.profiles (
  user_id            uuid primary key references auth.users (id) on delete cascade,
  display_name       text not null check (char_length(display_name) between 1 and 80),
  interface_language public.ui_language not null default 'en',
  account_status     public.account_status not null default 'active',
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
comment on table public.profiles is
  'One row per auth user. Owner may read; edits only via update_my_profile(). Admins read all.';

create table public.user_roles (
  user_id    uuid not null references auth.users (id) on delete cascade,
  role       public.app_role not null,
  granted_by uuid references auth.users (id) on delete set null,
  granted_at timestamptz not null default now(),
  primary key (user_id, role)
);
comment on table public.user_roles is
  'Role assignments. Owner may read own roles; no client writes. Changed only by privileged server code.';

create table public.activity_logs (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid references auth.users (id) on delete set null,
  event_type     text not null check (event_type ~ '^[a-z_]+(\.[a-z_]+)+$'),
  entity_type    text check (entity_type ~ '^[a-z_]+$'),
  entity_id      uuid,
  correlation_id uuid,
  metadata       jsonb not null default '{}'::jsonb check (pg_column_size(metadata) <= 2048),
  occurred_at    timestamptz not null default now()
);
comment on table public.activity_logs is
  'Append-only event log with server timestamps. Admin read only; rows written by SECURITY DEFINER functions. Never store tokens, passwords or free text answers here.';
create index activity_logs_user_time_idx on public.activity_logs (user_id, occurred_at desc);
create index activity_logs_type_time_idx on public.activity_logs (event_type, occurred_at desc);

-- ---------------------------------------------------------------------------
-- Private helpers
-- ---------------------------------------------------------------------------
create function private.has_role(p_role public.app_role)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.user_roles r
    where r.user_id = (select auth.uid()) and r.role = p_role
  );
$$;

create function private.is_active_user()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.user_id = (select auth.uid()) and p.account_status = 'active'
  );
$$;

create function private.log_event(
  p_user_id uuid,
  p_event_type text,
  p_entity_type text default null,
  p_entity_id uuid default null,
  p_metadata jsonb default '{}'::jsonb,
  p_correlation_id uuid default null
) returns void
language sql security definer
set search_path = ''
as $$
  insert into public.activity_logs (user_id, event_type, entity_type, entity_id, metadata, correlation_id)
  values (p_user_id, p_event_type, p_entity_type, p_entity_id, coalesce(p_metadata, '{}'::jsonb), p_correlation_id);
$$;

create function private.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create function private.forbid_log_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception 'activity_logs is append-only' using errcode = '42501';
end;
$$;

revoke all on all functions in schema private from public, anon, authenticated;
grant execute on function private.has_role(public.app_role) to authenticated;
grant execute on function private.is_active_user() to authenticated;

create trigger profiles_touch_updated_at
  before update on public.profiles
  for each row execute function private.touch_updated_at();

create trigger activity_logs_append_only
  before update on public.activity_logs
  for each row execute function private.forbid_log_update();

-- ---------------------------------------------------------------------------
-- New-user provisioning. Every new account is a student; display_name comes from
-- metadata only as a label and is length-limited. Any 'role' in metadata is ignored.
-- ---------------------------------------------------------------------------
create function private.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  v_name text;
begin
  v_name := left(btrim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), 80);
  if v_name = '' then
    v_name := left(split_part(coalesce(new.email, 'learner'), '@', 1), 80);
  end if;
  if v_name = '' then
    v_name := 'Learner';
  end if;

  insert into public.profiles (user_id, display_name) values (new.id, v_name);
  insert into public.user_roles (user_id, role) values (new.id, 'student');
  perform private.log_event(new.id, 'auth.account_created', 'profile', new.id);
  return new;
end;
$$;
revoke all on function private.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------------------
-- Admin bootstrap. Run once from the SQL editor (as the database owner):
--   select private.bootstrap_first_admin('owner@example.com');
-- Refuses when an admin already exists. Not callable by anon/authenticated/service_role.
-- ---------------------------------------------------------------------------
create function private.bootstrap_first_admin(p_email text)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  v_user uuid;
begin
  if exists (select 1 from public.user_roles where role = 'admin') then
    raise exception 'an administrator already exists; use the admin workflow instead';
  end if;
  select id into v_user from auth.users where lower(email) = lower(btrim(p_email));
  if v_user is null then
    raise exception 'no auth user with that email; sign up first';
  end if;
  insert into public.user_roles (user_id, role) values (v_user, 'admin');
  perform private.log_event(v_user, 'admin.bootstrap_admin_granted', 'profile', v_user);
  return v_user;
end;
$$;
revoke all on function private.bootstrap_first_admin(text) from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- RLS and privileges
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table public.activity_logs enable row level security;

revoke all on public.profiles, public.user_roles, public.activity_logs from anon, authenticated;
grant select on public.profiles, public.user_roles, public.activity_logs to authenticated;
revoke update, delete, truncate on public.activity_logs from service_role;

create policy "profiles: owner reads own"
  on public.profiles for select to authenticated
  using (user_id = (select auth.uid()));

create policy "profiles: admin reads all"
  on public.profiles for select to authenticated
  using ((select private.has_role('admin')));

create policy "user_roles: owner reads own"
  on public.user_roles for select to authenticated
  using (user_id = (select auth.uid()));

create policy "user_roles: admin reads all"
  on public.user_roles for select to authenticated
  using ((select private.has_role('admin')));

create policy "activity_logs: admin reads"
  on public.activity_logs for select to authenticated
  using ((select private.has_role('admin')));

-- ---------------------------------------------------------------------------
-- RPC: update own profile (only whitelisted columns)
-- ---------------------------------------------------------------------------
create function public.update_my_profile(
  p_display_name text default null,
  p_interface_language public.ui_language default null
) returns public.profiles
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.profiles;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.is_active_user() then
    raise exception 'account suspended' using errcode = '42501';
  end if;
  if p_display_name is not null and char_length(btrim(p_display_name)) not between 1 and 80 then
    raise exception 'display_name must be 1-80 characters' using errcode = '22023';
  end if;

  update public.profiles
     set display_name       = coalesce(btrim(p_display_name), display_name),
         interface_language = coalesce(p_interface_language, interface_language)
   where user_id = v_uid
  returning * into v_row;

  return v_row;
end;
$$;
revoke all on function public.update_my_profile(text, public.ui_language) from public, anon;
grant execute on function public.update_my_profile(text, public.ui_language) to authenticated;
