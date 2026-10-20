-- Level exams, certificates, verification and the placement test.
begin;
select plan(38);

insert into auth.users (id, email, raw_user_meta_data) values
  ('a1000000-0000-0000-0000-00000000000a', 'exam.a@example.test', '{"display_name":"Sara Ahmed"}'),
  ('b1000000-0000-0000-0000-00000000000b', 'exam.b@example.test', '{"display_name":"Karwan Ali"}'),
  ('c1000000-0000-0000-0000-00000000000c', 'exam.c@example.test', '{"display_name":"New Learner"}');

-- Practice fixtures: A and B answer every published A1 question (A correctly, B 80% correctly).
insert into public.assessment_attempts (id, user_id, kind, cefr_level) values
  ('a1000000-0000-0000-0000-0000000000a1', 'a1000000-0000-0000-0000-00000000000a', 'practice', 'A1'),
  ('b1000000-0000-0000-0000-0000000000b1', 'b1000000-0000-0000-0000-00000000000b', 'practice', 'A1');
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
select gen_random_uuid(), 'a1000000-0000-0000-0000-0000000000a1', 'a1000000-0000-0000-0000-00000000000a',
       q.id, q.version, q.correct_option, true
  from public.assessment_questions q where q.cefr_level = 'A1' and q.status = 'published';
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
select gen_random_uuid(), 'b1000000-0000-0000-0000-0000000000b1', 'b1000000-0000-0000-0000-00000000000b',
       q.id, q.version, q.correct_option, (row_number() over (order by q.id)) % 5 <> 0
  from public.assessment_questions q where q.cefr_level = 'A1' and q.status = 'published';

create temp table t (k text primary key, v jsonb);
grant select, insert, update on t to authenticated, anon;
insert into t select 'a1_published', to_jsonb(count(*)) from public.assessment_questions where cefr_level = 'A1' and status = 'published';

-- Anonymous -----------------------------------------------------------------
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select throws_ok($$select * from public.start_level_exam('A1')$$, '42501', null, 'anon cannot start an exam');
select throws_ok($$select * from public.certificates$$, '42501', null, 'anon cannot read certificates');
select is((select count(*)::int from public.verify_certificate('EM-AAAA-AAAA-AAAA')), 0, 'unknown code verifies as nothing');
reset role;

-- Learner C has not practised --------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"c1000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select is((select exam_unlocked from public.get_learning_overview() where cefr_level = 'A1'), false,
  'exam is locked without practice');
select is((select required from public.get_learning_overview() where cefr_level = 'A1'),
          (select least(30, v::int) from t where k = 'a1_published'),
  'required practice = least(30, published questions)');
select throws_ok($$select * from public.start_level_exam('A1')$$, 'P0001', 'practise more before the exam',
  'locked exam cannot be started');
select throws_ok($$insert into public.exam_attempts (user_id, kind, cefr_level, question_ids, expires_at)
                   values ('c1000000-0000-0000-0000-00000000000c', 'level_final', 'A1', array[gen_random_uuid()], now())$$,
  '42501', null, 'learners cannot write exam attempts directly');
reset role;

-- Learner A passes ----------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select is((select exam_unlocked from public.get_learning_overview() where cefr_level = 'A1'), true,
  'practice at >= 70% unlocks the exam');
insert into t select 'a_exam', to_jsonb(e) from public.start_level_exam('A1') e;
select is((select jsonb_array_length(v -> 'questions') from t where k = 'a_exam'), 20, 'exam has 20 questions');
select is((select count(*)::int from t, jsonb_array_elements(v -> 'questions') q where k = 'a_exam' and q ? 'correct_option'), 0,
  'exam questions carry no answer key');
select is((select (v ->> 'attempt_id') from t where k = 'a_exam'),
          (select attempt_id::text from public.start_level_exam('A1')), 'starting again resumes the same attempt');
select isnt((select exam_in_progress from public.get_learning_overview() where cefr_level = 'A1'), null,
  'overview reports the exam in progress');
reset role;

-- Correct answers for A's attempt (read with owner rights, as a test fixture).
insert into t select 'a_answers', jsonb_object_agg(q.id::text, q.correct_option)
  from public.exam_attempts e, unnest(e.question_ids) qid join public.assessment_questions q on q.id = qid
 where e.user_id = 'a1000000-0000-0000-0000-00000000000a' and e.kind = 'level_final';

set local role authenticated;
insert into t select 'a_result', to_jsonb(r) from public.submit_level_exam(
  (select (v ->> 'attempt_id')::uuid from t where k = 'a_exam'), (select v from t where k = 'a_answers')) r;
select is((select (v ->> 'score')::int from t where k = 'a_result'), 20, 'all answers scored correct');
select is((select (v ->> 'passed')::boolean from t where k = 'a_result'), true, '20/20 passes');
select is((select v ->> 'certificate_code' from t where k = 'a_result'), null,
  'a pass requests a certificate; no code until a teacher approves it');
select is((select count(*)::int from public.get_my_certificates()), 0, 'a pending certificate is not downloadable');
select is((select certificate_status from public.get_learning_overview_v2() where cefr_level = 'A1'), 'pending',
  'overview shows the certificate as waiting for approval');
select throws_ok($$select * from public.start_level_exam('A1')$$, 'P0001', 'certificate is waiting for approval',
  'a level waiting for approval cannot be examined again');
reset role;

-- A teacher approves the certificate.
insert into auth.users (id, email, raw_user_meta_data) values
  ('d1000000-0000-0000-0000-00000000000d', 'exam.teacher@example.test', '{"display_name":"Teacher"}');
insert into public.user_roles (user_id, role) values ('d1000000-0000-0000-0000-00000000000d', 'teacher');
select set_config('request.jwt.claims', '{"sub":"d1000000-0000-0000-0000-00000000000d","role":"authenticated"}', true);
set local role authenticated;
insert into t select 'a_review', to_jsonb(r) from public.staff_review_certificate(
  (select id from public.staff_list_certificates('pending') where display_name = 'Sara Ahmed'), true) r;
reset role;
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select matches((select v ->> 'verification_code' from t where k = 'a_review'), '^EM-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$',
  'an approved certificate has a verification code');
select is((select v ->> 'certificate_code' from public.submit_level_exam(
             (select (v ->> 'attempt_id')::uuid from t where k = 'a_exam'), '{}'::jsonb) r, lateral (select to_jsonb(r) v) x),
          (select v ->> 'verification_code' from t where k = 'a_review'),
  're-submitting returns the stored result and the approved certificate');
select is((select count(*)::int from public.get_my_certificates()), 1, 'learner sees their certificate');
select is((select holder_name from public.get_my_certificates()), 'Sara Ahmed', 'certificate carries the profile name');
select throws_ok($$select * from public.start_level_exam('A1')$$, 'P0001', 'certificate already issued for this level',
  'a certified level cannot be examined again');
select is((select certificate_code from public.get_learning_overview() where cefr_level = 'A1'),
          (select v ->> 'verification_code' from t where k = 'a_review'), 'overview shows the certificate');
select is((select streak_days from public.get_learner_profile()), 1, 'practice today counts as a 1-day streak');
reset role;

-- Public verification ------------------------------------------------------------------
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select results_eq(
  format($$select valid, holder_name, cefr_level::text, score, total, revoked from public.verify_certificate(%L)$$,
         lower((select v ->> 'verification_code' from t where k = 'a_review'))),
  $$values (true, 'Sara Ahmed'::text, 'A1'::text, 20, 20, false)$$,
  'anyone can verify a certificate by its code (case-insensitive)');
reset role;

-- Learner B fails, and cannot touch A's attempt -----------------------------------------
select set_config('request.jwt.claims', '{"sub":"b1000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is((select exam_unlocked from public.get_learning_overview() where cefr_level = 'A1'), true,
  '80% correct practice also unlocks the exam');
select throws_ok(format($$select * from public.submit_level_exam(%L, '{}'::jsonb)$$, (select v ->> 'attempt_id' from t where k = 'a_exam')),
  'P0001', 'exam attempt not found', 'another learner''s attempt cannot be submitted');
select is((select count(*)::int from public.certificates), 0, 'learners cannot read other learners'' certificates');
insert into t select 'b_exam', to_jsonb(e) from public.start_level_exam('A1') e;
insert into t select 'b_result', to_jsonb(r) from public.submit_level_exam((select (v ->> 'attempt_id')::uuid from t where k = 'b_exam'), '{}'::jsonb) r;
select is((select (v ->> 'passed')::boolean from t where k = 'b_result'), false, 'no answers fails');
select is((select v ->> 'certificate_code' from t where k = 'b_result'), null, 'a failed exam issues no certificate');
select ok((select (v ->> 'next_attempt_at')::timestamptz between now() + interval '23 hours' and now() + interval '25 hours'
             from t where k = 'b_result'), 'next attempt opens 24 hours later');
select throws_ok($$select * from public.start_level_exam('A1')$$, 'P0001', 'next attempt not yet available',
  'a failed exam cannot be retaken immediately');
reset role;

-- A late submission is recorded as expired and does not pass.
update public.exam_attempts set status = 'in_progress', submitted_at = null, score = null, passed = null,
       expires_at = now() - interval '10 minutes'
 where id = (select (v ->> 'attempt_id')::uuid from t where k = 'b_exam');
set local role authenticated;
select results_eq(
  format($$select passed, expired from public.submit_level_exam(%L, '{}'::jsonb)$$, (select v ->> 'attempt_id' from t where k = 'b_exam')),
  $$values (false, true)$$, 'late submissions are expired and fail');
reset role;

-- Placement test (learner C) ----------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"c1000000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
insert into t select 'c_place', to_jsonb(p) from public.start_placement_test() p;
select is((select jsonb_array_length(v -> 'questions') from t where k = 'c_place'), 18, 'placement test: 3 questions x 6 levels');
reset role;
-- Answer A1 and A2 correctly, everything else wrong.
insert into t select 'c_answers', jsonb_object_agg(q.id::text,
         case when q.cefr_level in ('A1', 'A2') then q.correct_option
              else case q.correct_option when 'A' then 'B' else 'A' end end)
  from public.exam_attempts e, unnest(e.question_ids) qid join public.assessment_questions q on q.id = qid
 where e.user_id = 'c1000000-0000-0000-0000-00000000000c' and e.kind = 'placement';
set local role authenticated;
select results_eq(
  format($$select score, passed_level::text, recommended_level::text from public.submit_placement_test(%L, %L::jsonb)$$,
         (select v ->> 'attempt_id' from t where k = 'c_place'), (select v::text from t where k = 'c_answers')),
  $$values (6, 'A2'::text, 'B1'::text)$$, 'placement recommends the level after the highest one passed');
select is((select placement_level::text from public.get_learner_profile()), 'B1', 'placement result is saved on the profile');
select results_eq(
  format($$select score, recommended_level::text from public.submit_placement_test(%L, '{}'::jsonb)$$,
         (select v ->> 'attempt_id' from t where k = 'c_place')),
  $$values (6, 'B1'::text)$$, 're-submitting the placement test returns the stored result');
reset role;

select * from finish();
rollback;
