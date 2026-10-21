-- In-app account deletion (Google Play account-deletion requirement).
--
-- delete_my_account(): a learner deletes their own account and all data linked to it: profile,
-- roles, practice attempts and answers, exams, certificates and their own activity-log entries.
-- Staff accounts (teacher/admin) cannot delete themselves in the app: an admin removes the role
-- first, so the project never loses its last admin and staff actions stay accountable.
--
-- The user's own log rows are deleted first: activity_logs is append-only (updates are refused),
-- so the ON DELETE SET NULL on activity_logs.user_id would otherwise block deleting the user.
-- Certificates go before the user because they reference exam attempts with ON DELETE RESTRICT.

create function public.delete_my_account()
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if exists (select 1 from public.user_roles r where r.user_id = v_uid and r.role in ('teacher', 'admin')) then
    raise exception 'staff accounts are removed by an admin' using errcode = 'P0001', hint = 'staff_account';
  end if;

  delete from public.certificates where user_id = v_uid;
  delete from public.activity_logs where user_id = v_uid;
  delete from auth.users where id = v_uid;
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
