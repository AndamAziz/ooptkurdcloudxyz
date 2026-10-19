-- EnglishMaster AI — staff tools for the admin section of the Android app.
--
-- Every operation is an RPC that checks the caller's role on the server; the app only
-- hides screens for convenience. All changes are written to activity_logs.
--   admin:            list users, grant/revoke teacher or admin, suspend/activate accounts,
--                     read recent activity
--   teacher or admin: review questions (approve / mark unreviewed / publish / retire)
-- Safety rules: an admin cannot remove their own admin role or suspend themselves, so the
-- project can never lose its last way back in through the app.

create function private.require_staff(p_admin_only boolean)
returns uuid
language plpgsql stable security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.is_active_user() then
    raise exception 'account suspended' using errcode = '42501';
  end if;
  if not (private.has_role('admin') or (not p_admin_only and private.has_role('teacher'))) then
    raise exception 'insufficient role' using errcode = '42501';
  end if;
  return v_uid;
end;
$$;
revoke all on function private.require_staff(boolean) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Own roles (any signed-in user). Lets the app decide which screens to offer.
-- ---------------------------------------------------------------------------
create function public.get_my_roles()
returns public.app_role[]
language sql stable security invoker
set search_path = ''
as $$
  select coalesce(array_agg(r.role order by r.role), '{}')
    from public.user_roles r
   where r.user_id = (select auth.uid());
$$;
revoke all on function public.get_my_roles() from public, anon;
grant execute on function public.get_my_roles() to authenticated;

-- ---------------------------------------------------------------------------
-- Admin: users with roles and practice totals.
-- ---------------------------------------------------------------------------
create function public.admin_list_users(
  p_search text default null,
  p_limit integer default 50,
  p_offset integer default 0
) returns table (
  user_id uuid,
  email text,
  display_name text,
  roles public.app_role[],
  account_status public.account_status,
  created_at timestamptz,
  answered bigint,
  correct bigint,
  last_answer_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_pattern text;
begin
  perform private.require_staff(true);
  if p_limit is null or p_limit < 1 or p_limit > 200 or p_offset is null or p_offset < 0 then
    raise exception 'invalid paging' using errcode = '22023';
  end if;
  if p_search is not null and btrim(p_search) <> '' then
    v_pattern := '%' || replace(replace(replace(btrim(p_search), '\', '\\'), '%', '\%'), '_', '\_') || '%';
  end if;

  return query
    select p.user_id, u.email::text, p.display_name,
           coalesce((select array_agg(r.role order by r.role) from public.user_roles r where r.user_id = p.user_id), '{}'),
           p.account_status, p.created_at,
           (select count(*) from public.assessment_responses x where x.user_id = p.user_id),
           (select count(*) from public.assessment_responses x where x.user_id = p.user_id and x.is_correct),
           (select max(x.received_at) from public.assessment_responses x where x.user_id = p.user_id)
      from public.profiles p
      join auth.users u on u.id = p.user_id
     where v_pattern is null or p.display_name ilike v_pattern or u.email ilike v_pattern
     order by p.created_at desc, p.user_id
     limit p_limit offset p_offset;
end;
$$;
revoke all on function public.admin_list_users(text, integer, integer) from public, anon;
grant execute on function public.admin_list_users(text, integer, integer) to authenticated;

-- ---------------------------------------------------------------------------
-- Admin: grant or revoke the teacher/admin role. Returns the user's roles afterwards.
-- ---------------------------------------------------------------------------
create function public.admin_set_role(p_user_id uuid, p_role public.app_role, p_granted boolean)
returns public.app_role[]
language plpgsql security definer
set search_path = ''
as $$
declare
  v_admin uuid := private.require_staff(true);
  v_roles public.app_role[];
begin
  if p_role is null or p_role = 'student' or p_granted is null then
    raise exception 'only teacher or admin can be granted or revoked' using errcode = '22023';
  end if;
  if not exists (select 1 from public.profiles where user_id = p_user_id) then
    raise exception 'user not found' using errcode = '22023';
  end if;
  if p_user_id = v_admin and p_role = 'admin' and not p_granted then
    raise exception 'you cannot remove your own admin role' using errcode = '42501';
  end if;

  if p_granted then
    insert into public.user_roles (user_id, role, granted_by) values (p_user_id, p_role, v_admin)
    on conflict (user_id, role) do nothing;
  else
    delete from public.user_roles where user_id = p_user_id and role = p_role;
  end if;

  perform private.log_event(v_admin, case when p_granted then 'admin.role_granted' else 'admin.role_revoked' end,
                            'profile', p_user_id, jsonb_build_object('role', p_role));

  select coalesce(array_agg(role order by role), '{}') into v_roles from public.user_roles where user_id = p_user_id;
  return v_roles;
end;
$$;
revoke all on function public.admin_set_role(uuid, public.app_role, boolean) from public, anon;
grant execute on function public.admin_set_role(uuid, public.app_role, boolean) to authenticated;

-- ---------------------------------------------------------------------------
-- Admin: suspend or reactivate an account (not your own).
-- ---------------------------------------------------------------------------
create function public.admin_set_account_status(p_user_id uuid, p_status public.account_status)
returns public.account_status
language plpgsql security definer
set search_path = ''
as $$
declare
  v_admin uuid := private.require_staff(true);
begin
  if p_status is null then
    raise exception 'status required' using errcode = '22023';
  end if;
  if p_user_id = v_admin then
    raise exception 'you cannot change your own account status' using errcode = '42501';
  end if;
  update public.profiles set account_status = p_status where user_id = p_user_id;
  if not found then
    raise exception 'user not found' using errcode = '22023';
  end if;
  perform private.log_event(v_admin, case when p_status = 'suspended' then 'admin.account_suspended' else 'admin.account_activated' end,
                            'profile', p_user_id);
  return p_status;
end;
$$;
revoke all on function public.admin_set_account_status(uuid, public.account_status) from public, anon;
grant execute on function public.admin_set_account_status(uuid, public.account_status) to authenticated;

-- ---------------------------------------------------------------------------
-- Admin: recent activity with actor names.
-- ---------------------------------------------------------------------------
create function public.admin_recent_activity(p_limit integer default 100)
returns table (
  occurred_at timestamptz,
  event_type text,
  actor_id uuid,
  actor_name text,
  entity_type text,
  entity_id uuid,
  metadata jsonb
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
begin
  perform private.require_staff(true);
  if p_limit is null or p_limit < 1 or p_limit > 500 then
    raise exception 'p_limit must be between 1 and 500' using errcode = '22023';
  end if;
  return query
    select l.occurred_at, l.event_type, l.user_id, p.display_name, l.entity_type, l.entity_id, l.metadata
      from public.activity_logs l
      left join public.profiles p on p.user_id = l.user_id
     order by l.occurred_at desc
     limit p_limit;
end;
$$;
revoke all on function public.admin_recent_activity(integer) from public, anon;
grant execute on function public.admin_recent_activity(integer) to authenticated;

-- ---------------------------------------------------------------------------
-- Teacher/admin: review a question. Content (prompt/options/key) is not changed here,
-- so the version stays the same. Retired questions can no longer be answered.
-- ---------------------------------------------------------------------------
create function public.staff_review_question(
  p_question_id uuid,
  p_review_status text,
  p_status text default null
) returns table (id uuid, review_status text, status text)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_staff uuid := private.require_staff(false);
begin
  if p_review_status is null or p_review_status not in ('unreviewed', 'approved') then
    raise exception 'review status must be unreviewed or approved' using errcode = '22023';
  end if;
  if p_status is not null and p_status not in ('draft', 'published', 'retired') then
    raise exception 'status must be draft, published or retired' using errcode = '22023';
  end if;

  update public.assessment_questions q
     set review_status = p_review_status,
         status = coalesce(p_status, q.status)
   where q.id = p_question_id;
  if not found then
    raise exception 'question not found' using errcode = '22023';
  end if;

  perform private.log_event(v_staff, 'content.question_reviewed', 'assessment_question', p_question_id,
                            jsonb_build_object('review_status', p_review_status, 'status', p_status));

  return query select q.id, q.review_status, q.status from public.assessment_questions q where q.id = p_question_id;
end;
$$;
revoke all on function public.staff_review_question(uuid, text, text) from public, anon;
grant execute on function public.staff_review_question(uuid, text, text) to authenticated;
