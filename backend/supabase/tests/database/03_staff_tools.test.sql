-- Staff tools: role checks, self-protection rules and audit logging.
begin;
select plan(24);

insert into auth.users (id, email, raw_user_meta_data) values
  ('a0000000-0000-0000-0000-00000000000a', 'admin@example.test',   '{"display_name":"Admin"}'),
  ('b0000000-0000-0000-0000-00000000000b', 'teacher@example.test', '{"display_name":"Teacher"}'),
  ('c0000000-0000-0000-0000-00000000000c', 'student@example.test', '{"display_name":"Student"}');
insert into public.user_roles (user_id, role) values
  ('a0000000-0000-0000-0000-00000000000a', 'admin'),
  ('b0000000-0000-0000-0000-00000000000b', 'teacher');
insert into public.assessment_questions (id, cefr_level, skill, prompt, options, correct_option, status) values
  ('00000000-0000-0000-0000-0000000000d1', 'PRE_A1', 'grammar', 'Staff test item', array['a','b'], 'A', 'published');

-- Student ---------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"c0000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
select is(public.get_my_roles(), array['student']::public.app_role[], 'student sees own roles');
select throws_ok($$select * from public.admin_list_users()$$, '42501', null, 'student cannot list users');
select throws_ok($$select public.admin_set_role('c0000000-0000-0000-0000-00000000000c', 'admin', true)$$,
  '42501', null, 'student cannot grant themselves admin');
select throws_ok($$select * from public.staff_review_question('00000000-0000-0000-0000-0000000000d1', 'approved')$$,
  '42501', null, 'student cannot review questions');

-- Teacher ---------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"b0000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
select results_eq($$select review_status, status from public.staff_review_question('00000000-0000-0000-0000-0000000000d1', 'approved')$$,
  $$values ('approved', 'published')$$, 'teacher can approve a question');
select throws_ok($$select * from public.staff_review_question('00000000-0000-0000-0000-0000000000d1', 'great')$$,
  '22023', null, 'invalid review status rejected');
select throws_ok($$select * from public.admin_list_users()$$, '42501', null, 'teacher cannot list users');
select throws_ok($$select public.admin_set_role('b0000000-0000-0000-0000-00000000000b', 'admin', true)$$,
  '42501', null, 'teacher cannot promote themselves');
select throws_ok($$select * from public.admin_recent_activity()$$, '42501', null, 'teacher cannot read the activity log');

-- Admin -----------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"a0000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
select is((select count(*)::int from public.admin_list_users()), 3, 'admin lists all users');
select is((select email from public.admin_list_users('teach')), 'teacher@example.test', 'search matches name or email');
select is((select count(*)::int from public.admin_list_users('100%')), 0, 'search wildcards are escaped');
select is(public.admin_set_role('c0000000-0000-0000-0000-00000000000c', 'teacher', true),
  array['student','teacher']::public.app_role[], 'admin grants teacher');
select is(public.admin_set_role('c0000000-0000-0000-0000-00000000000c', 'teacher', true),
  array['student','teacher']::public.app_role[], 'granting twice is harmless');
select is(public.admin_set_role('c0000000-0000-0000-0000-00000000000c', 'teacher', false),
  array['student']::public.app_role[], 'admin revokes teacher');
select throws_ok($$select public.admin_set_role('c0000000-0000-0000-0000-00000000000c', 'student', false)$$,
  '22023', null, 'the base student role cannot be revoked');
select throws_ok($$select public.admin_set_role('a0000000-0000-0000-0000-00000000000a', 'admin', false)$$,
  '42501', null, 'admin cannot remove their own admin role');
select throws_ok($$select public.admin_set_account_status('a0000000-0000-0000-0000-00000000000a', 'suspended')$$,
  '42501', null, 'admin cannot suspend themselves');
select is(public.admin_set_account_status('c0000000-0000-0000-0000-00000000000c', 'suspended'),
  'suspended'::public.account_status, 'admin suspends a student');
select throws_ok($$select public.admin_set_role(gen_random_uuid(), 'teacher', true)$$, '22023', null, 'unknown user rejected');
select cmp_ok((select count(*)::int from public.admin_recent_activity()), '>=', 4, 'admin reads recent activity');

-- Effects ---------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"c0000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
select throws_ok($$select * from public.get_practice_questions('PRE_A1')$$, '42501', null, 'suspended student is blocked');

reset role;
select is((select count(*)::int from public.activity_logs where event_type in
            ('admin.role_granted', 'admin.role_revoked', 'admin.account_suspended', 'content.question_reviewed')),
  5, 'every staff change is logged (2 grants, 1 revoke, 1 suspension, 1 review)');
select is((select granted_by from public.user_roles where user_id = 'b0000000-0000-0000-0000-00000000000b' and role = 'teacher'),
  null, 'roles granted outside the API have no granted_by');

select * from finish();
rollback;
