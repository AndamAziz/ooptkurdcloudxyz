-- Identity, roles, profile RLS, admin bootstrap and audit-log integrity.
begin;
select plan(17);

-- Fixtures (as database owner). The sign-up trigger provisions profile + role.
insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'alice@example.test', '{"display_name":"  Alice  ","role":"admin"}'),
  ('22222222-2222-2222-2222-222222222222', 'bob@example.test',   '{}');

select is(
  (select array_agg(role::text) from public.user_roles where user_id = '11111111-1111-1111-1111-111111111111'),
  array['student'],
  'sign-up metadata cannot grant a role: new user is student only');

select is(
  (select display_name from public.profiles where user_id = '11111111-1111-1111-1111-111111111111'),
  'Alice', 'display_name is trimmed');

select is(
  (select display_name from public.profiles where user_id = '22222222-2222-2222-2222-222222222222'),
  'bob', 'display_name falls back to email local part');

-- As Alice ---------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.profiles), 1, 'student sees only own profile');

select throws_ok(
  $$update public.profiles set account_status = 'active', display_name = 'x' where true$$,
  '42501', null, 'student cannot update profiles directly');

select is((public.update_my_profile('Alice B', 'ckb')).display_name, 'Alice B', 'update_my_profile updates own name');

select throws_ok($$select public.update_my_profile('   ')$$, '22023', null, 'blank display_name rejected');

select throws_ok(
  $$insert into public.user_roles (user_id, role) values ('11111111-1111-1111-1111-111111111111', 'admin')$$,
  '42501', null, 'student cannot grant themselves a role');

select is((select count(*)::int from public.activity_logs), 0, 'student cannot read activity logs');

select throws_ok($$select private.bootstrap_first_admin('alice@example.test')$$,
  '42501', null, 'authenticated cannot call admin bootstrap');

-- As anon ----------------------------------------------------------------
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);

select throws_ok($$select * from public.profiles$$, '42501', null, 'anon cannot read profiles');
select throws_ok($$select public.update_my_profile('x')$$, '42501', null, 'anon cannot call update_my_profile');

-- Bootstrap (as owner) ---------------------------------------------------
reset role;
select lives_ok($$select private.bootstrap_first_admin('BOB@example.test')$$, 'owner can bootstrap first admin');
select throws_like($$select private.bootstrap_first_admin('alice@example.test')$$,
  '%already exists%', 'second bootstrap is refused');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.profiles), 2, 'admin reads all profiles');

-- Audit log integrity ----------------------------------------------------
reset role;
select throws_ok($$update public.activity_logs set event_type = 'x.y' where true$$,
  '42501', null, 'activity_logs is append-only even for the owner');

set local role service_role;
select throws_ok($$delete from public.activity_logs where true$$,
  '42501', null, 'service_role cannot delete activity logs');

reset role;
select * from finish();
rollback;
