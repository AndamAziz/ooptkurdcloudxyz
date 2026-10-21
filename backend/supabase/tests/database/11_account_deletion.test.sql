-- Learners can delete their account and everything linked to it; staff cannot self-delete.
begin;
select plan(9);

insert into auth.users (id, email, raw_user_meta_data) values
  ('a1200000-0000-0000-0000-00000000000a', 'leaving@example.test', '{"display_name":"Leaving Learner"}'),
  ('b1200000-0000-0000-0000-00000000000b', 'staying@example.test', '{"display_name":"Staying"}'),
  ('c1200000-0000-0000-0000-00000000000c', 'teacher.del@example.test', '{"display_name":"Teacher"}');
insert into public.user_roles (user_id, role) values ('c1200000-0000-0000-0000-00000000000c', 'teacher');

-- Fixture: answers, a passed exam with an approved certificate, and log entries.
insert into public.assessment_attempts (id, user_id, kind, cefr_level)
values ('a1200000-0000-0000-0000-0000000000f1', 'a1200000-0000-0000-0000-00000000000a', 'practice', 'A1'),
       ('b1200000-0000-0000-0000-0000000000f1', 'b1200000-0000-0000-0000-00000000000b', 'practice', 'A1');
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
select gen_random_uuid(), a.id, a.user_id, q.id, q.version, q.correct_option, true
  from (values ('a1200000-0000-0000-0000-0000000000f1'::uuid, 'a1200000-0000-0000-0000-00000000000a'::uuid),
               ('b1200000-0000-0000-0000-0000000000f1'::uuid, 'b1200000-0000-0000-0000-00000000000b'::uuid)) a(id, user_id),
       (select id, version, correct_option from public.assessment_questions where status = 'published' limit 3) q;
insert into public.exam_attempts (id, user_id, kind, cefr_level, question_ids, status, expires_at, submitted_at, score, total, passed)
values ('a1200000-0000-0000-0000-0000000000e1', 'a1200000-0000-0000-0000-00000000000a', 'level_final', 'A1',
        array[gen_random_uuid()], 'submitted', now(), now(), 20, 20, true);
insert into public.certificates (user_id, cefr_level, exam_attempt_id, holder_name, score, total, verification_code, status)
values ('a1200000-0000-0000-0000-00000000000a', 'A1', 'a1200000-0000-0000-0000-0000000000e1', 'Leaving Learner', 20, 20,
        'EM-ZZZZ-YYYY-XXXX', 'approved');
select ok((select count(*) from public.activity_logs where user_id = 'a1200000-0000-0000-0000-00000000000a') > 0,
  'fixture: the learner has activity-log entries');

set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select throws_ok($$select public.delete_my_account()$$, '42501', null, 'anon cannot call account deletion');
reset role;

-- Staff cannot delete themselves.
select set_config('request.jwt.claims', '{"sub":"c1200000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select throws_ok($$select public.delete_my_account()$$, 'P0001', 'staff accounts are removed by an admin',
  'a teacher cannot delete their own account in the app');
reset role;

-- The learner deletes their account.
select set_config('request.jwt.claims', '{"sub":"a1200000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select lives_ok($$select public.delete_my_account()$$, 'a learner deletes their account');
reset role;

select is((select count(*)::int from auth.users where id = 'a1200000-0000-0000-0000-00000000000a'), 0, 'the auth user is gone');
select is((select count(*)::int from public.profiles where user_id = 'a1200000-0000-0000-0000-00000000000a'), 0, 'the profile is gone');
select is((select count(*)::int from public.assessment_responses where user_id = 'a1200000-0000-0000-0000-00000000000a')
        + (select count(*)::int from public.exam_attempts where user_id = 'a1200000-0000-0000-0000-00000000000a')
        + (select count(*)::int from public.certificates where user_id = 'a1200000-0000-0000-0000-00000000000a')
        + (select count(*)::int from public.activity_logs where user_id = 'a1200000-0000-0000-0000-00000000000a'), 0,
  'answers, exams, certificates and log entries are gone');
select is((select count(*)::int from public.verify_certificate('EM-ZZZZ-YYYY-XXXX')), 0, 'the deleted certificate no longer verifies');
select is((select count(*)::int from public.assessment_responses where user_id = 'b1200000-0000-0000-0000-00000000000b'), 3,
  'other learners keep their data');

select * from finish();
rollback;
