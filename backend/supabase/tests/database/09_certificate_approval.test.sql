-- Certificates are issued only after a teacher or admin approves them.
begin;
select plan(17);

insert into auth.users (id, email, raw_user_meta_data) values
  ('a9000000-0000-0000-0000-00000000000a', 'cert.learner@example.test', '{"display_name":"Lana"}'),
  ('b9000000-0000-0000-0000-00000000000b', 'cert.other@example.test', '{"display_name":"Other"}'),
  ('c9000000-0000-0000-0000-00000000000c', 'cert.teacher@example.test', '{"display_name":"Teacher T"}'),
  ('d9000000-0000-0000-0000-00000000000d', 'cert.admin@example.test', '{"display_name":"Admin A"}');
insert into public.user_roles (user_id, role) values
  ('c9000000-0000-0000-0000-00000000000c', 'teacher'), ('d9000000-0000-0000-0000-00000000000d', 'admin');

-- Fixture: a submitted, passed A2 attempt for the learner and the teacher, with pending certificates.
insert into public.exam_attempts (id, user_id, kind, cefr_level, question_ids, status, expires_at, submitted_at, score, total, passed)
values ('a9000000-0000-0000-0000-0000000000e1', 'a9000000-0000-0000-0000-00000000000a', 'level_final', 'A2',
        array[gen_random_uuid()], 'submitted', now(), now(), 18, 20, true),
       ('c9000000-0000-0000-0000-0000000000e1', 'c9000000-0000-0000-0000-00000000000c', 'level_final', 'A2',
        array[gen_random_uuid()], 'submitted', now(), now(), 20, 20, true);
insert into public.certificates (id, user_id, cefr_level, exam_attempt_id, holder_name, score, total, verification_code) values
  ('a9000000-0000-0000-0000-0000000000c1', 'a9000000-0000-0000-0000-00000000000a', 'A2', 'a9000000-0000-0000-0000-0000000000e1',
   'Lana', 18, 20, 'EM-AAAA-BBBB-CCCC'),
  ('c9000000-0000-0000-0000-0000000000c1', 'c9000000-0000-0000-0000-00000000000c', 'A2', 'c9000000-0000-0000-0000-0000000000e1',
   'Teacher T', 20, 20, 'EM-DDDD-EEEE-FFFF');
select is((select status from public.certificates where id = 'a9000000-0000-0000-0000-0000000000c1'), 'pending',
  'new certificates start as pending');

-- Public verification ignores pending certificates.
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select is((select count(*)::int from public.verify_certificate('EM-AAAA-BBBB-CCCC')), 0, 'a pending certificate does not verify');
select throws_ok($$select * from public.staff_list_certificates()$$, '42501', null, 'anon cannot list certificate requests');
reset role;

-- A learner can neither list nor approve.
select set_config('request.jwt.claims', '{"sub":"b9000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select throws_ok($$select * from public.staff_list_certificates()$$, '42501', 'insufficient role', 'learners cannot list requests');
select throws_ok($$select * from public.staff_review_certificate('a9000000-0000-0000-0000-0000000000c1', true)$$,
  '42501', 'insufficient role', 'learners cannot approve certificates');
reset role;

-- The teacher corrects the learner's name, then sees the queue; cannot review their own request.
update public.profiles set display_name = 'Lana Karim' where user_id = 'a9000000-0000-0000-0000-00000000000a';
select set_config('request.jwt.claims', '{"sub":"c9000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*)::int from public.staff_list_certificates('pending') where user_id in
            ('a9000000-0000-0000-0000-00000000000a', 'c9000000-0000-0000-0000-00000000000c')), 2, 'teachers see pending requests');
select throws_ok($$select * from public.staff_review_certificate('c9000000-0000-0000-0000-0000000000c1', true)$$,
  'P0001', 'you cannot review your own certificate', 'nobody approves their own certificate');
select results_eq($$select status, verification_code from public.staff_review_certificate('a9000000-0000-0000-0000-0000000000c1', true, 'Well done')$$,
  $$values ('approved'::text, 'EM-AAAA-BBBB-CCCC'::text)$$, 'teacher approves the request');
select throws_ok($$select * from public.staff_review_certificate('a9000000-0000-0000-0000-0000000000c1', false)$$,
  'P0001', 'certificate was already reviewed', 'a reviewed certificate cannot be reviewed again');
select is((select reviewer_name from public.staff_list_certificates('approved') where user_id = 'a9000000-0000-0000-0000-00000000000a'),
  'Teacher T', 'the approved list shows who approved it');
reset role;
select is((select holder_name from public.certificates where id = 'a9000000-0000-0000-0000-0000000000c1'), 'Lana Karim',
  'approval uses the corrected name');
select ok(exists (select 1 from public.activity_logs where event_type = 'certificate.approved'
                   and entity_id = 'a9000000-0000-0000-0000-0000000000c1'), 'approval is logged');

set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select is((select holder_name from public.verify_certificate('EM-AAAA-BBBB-CCCC')), 'Lana Karim', 'an approved certificate verifies');
reset role;

-- The admin rejects the teacher's own request; the teacher may then take the exam again.
select set_config('request.jwt.claims', '{"sub":"d9000000-0000-0000-0000-00000000000d","role":"authenticated"}', true);
set local role authenticated;
select is((select status from public.staff_review_certificate('c9000000-0000-0000-0000-0000000000c1', false, 'Retake supervised')),
  'rejected', 'admin rejects a request');
reset role;
select set_config('request.jwt.claims', '{"sub":"c9000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select is((select certificate_status from public.get_learning_overview_v2() where cefr_level = 'A2'), null,
  'a rejected certificate is not shown to its owner as a certificate');
select throws_ok($$select * from public.start_level_exam('A2')$$, 'P0001', 'practise more before the exam',
  'after a rejection the normal exam rules apply again');
reset role;
select is((select count(*)::int from public.verify_certificate('EM-DDDD-EEEE-FFFF')), 0, 'a rejected certificate does not verify');

select * from finish();
rollback;
