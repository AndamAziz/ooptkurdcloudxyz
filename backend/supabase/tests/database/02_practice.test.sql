-- Question bank access and server-scored, idempotent practice submissions.
begin;
select plan(23);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-00000000000a', 'student.a@example.test'),
  ('bbbbbbbb-0000-0000-0000-00000000000b', 'student.b@example.test');

-- Fixtures use PRE_A1 so they never collide with seeded A1–C2 content.
insert into public.assessment_questions (id, cefr_level, skill, prompt, options, correct_option, explanation, status) values
  ('00000000-0000-0000-0000-0000000000c1', 'PRE_A1', 'grammar', 'I ___ a student.', array['is','am','are','be'], 'B', 'Use am with I.', 'published'),
  ('00000000-0000-0000-0000-0000000000c2', 'PRE_A1', 'vocabulary', 'Opposite of hot?', array['cold','warm','big'], 'A', null, 'published'),
  ('00000000-0000-0000-0000-0000000000c3', 'PRE_A1', 'grammar', 'Draft item', array['a','b'], 'A', null, 'draft');

-- As student A --------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-0000-0000-00000000000a","role":"authenticated"}', true);

select is((select count(*)::int from public.assessment_questions), 0, 'student cannot read the question bank (answer keys)');

select is((select count(*)::int from public.get_practice_questions('PRE_A1')), 2, 'download returns published questions only');

select throws_ok($$select correct_option from public.get_practice_questions('PRE_A1')$$,
  '42703', null, 'downloaded questions carry no answer key');

select throws_ok($$select * from public.get_practice_questions('PRE_A1', null, 500)$$,
  '22023', null, 'page size is bounded');

select results_eq(
  $$select is_correct, correct_option, duplicate from public.submit_practice_response(
      '00000000-0000-0000-0000-00000000e001', '00000000-0000-0000-0000-0000000000a1',
      '00000000-0000-0000-0000-0000000000c1', 'b')$$,
  $$values (true, 'B', false)$$,
  'correct answer is scored on the server (case-insensitive option)');

select results_eq(
  $$select is_correct, duplicate from public.submit_practice_response(
      '00000000-0000-0000-0000-00000000e001', '00000000-0000-0000-0000-0000000000a1',
      '00000000-0000-0000-0000-0000000000c1', 'B')$$,
  $$values (true, true)$$,
  'replaying the same response id is idempotent');

select results_eq(
  $$select response_id, is_correct, duplicate from public.submit_practice_response(
      '00000000-0000-0000-0000-00000000e999', '00000000-0000-0000-0000-0000000000a1',
      '00000000-0000-0000-0000-0000000000c1', 'A')$$,
  $$values ('00000000-0000-0000-0000-00000000e001'::uuid, true, true)$$,
  'second answer to the same question in an attempt returns the first stored answer');

select is((select count(*)::int from public.assessment_responses), 1, 'no duplicate rows were stored');

select results_eq(
  $$select is_correct, correct_option from public.submit_practice_response(
      '00000000-0000-0000-0000-00000000e002', '00000000-0000-0000-0000-0000000000a1',
      '00000000-0000-0000-0000-0000000000c2', 'C')$$,
  $$values (false, 'A')$$,
  'wrong answer is marked incorrect');

select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1',
  '00000000-0000-0000-0000-0000000000c1', 'Z')$$, '22023', null, 'invalid option letter rejected');

select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c2', 'D')$$, '22023', null, 'option outside the question''s options rejected');

select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c3', 'A')$$, '22023', null, 'draft question cannot be answered');

select throws_ok($$insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
  values (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'aaaaaaaa-0000-0000-0000-00000000000a',
          '00000000-0000-0000-0000-0000000000c3', 1, 'A', true)$$,
  '42501', null, 'student cannot write responses directly (no self-grading)');

select results_eq($$select cefr_level::text, answered, correct from public.get_my_practice_summary()$$,
  $$values ('PRE_A1', 2::bigint, 1::bigint)$$, 'summary is computed from stored responses');

-- As student B --------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"bbbbbbbb-0000-0000-0000-00000000000b","role":"authenticated"}', true);

select is((select count(*)::int from public.assessment_responses), 0, 'student B cannot see student A''s responses');
select is((select count(*)::int from public.assessment_attempts), 0, 'student B cannot see student A''s attempts');

select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1',
  '00000000-0000-0000-0000-0000000000c2', 'A')$$, '42501', null, 'cannot add responses to another user''s attempt');

select throws_ok($$select * from public.submit_practice_response('00000000-0000-0000-0000-00000000e001', gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c1', 'B')$$, '42501', null, 'cannot replay another user''s response id');

-- Unauthenticated / anon ----------------------------------------------------
select set_config('request.jwt.claims', '', true);
select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c1', 'B')$$, '28000', null, 'missing JWT subject is rejected');

reset role;
set local role anon;
select throws_ok($$select * from public.get_practice_questions('PRE_A1')$$, '42501', null, 'anon cannot download questions');

-- Server-side records (as owner) -------------------------------------------
reset role;
select is((select count(*)::int from public.activity_logs
            where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a' and event_type = 'practice.response_submitted'),
  2, 'each stored response is logged once');

-- Rate limit: 120 responses in the last minute blocks further submissions.
insert into public.assessment_attempts (id, user_id, kind, cefr_level)
  select gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-00000000000b', 'practice', 'PRE_A1' from generate_series(1, 120);
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
  select gen_random_uuid(), a.id, a.user_id, '00000000-0000-0000-0000-0000000000c1', 1, 'B', true
    from public.assessment_attempts a where a.user_id = 'bbbbbbbb-0000-0000-0000-00000000000b';

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"bbbbbbbb-0000-0000-0000-00000000000b","role":"authenticated"}', true);
select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c2', 'A')$$, '54000', null, 'per-user rate limit enforced');

-- Suspension ---------------------------------------------------------------
reset role;
update public.profiles set account_status = 'suspended' where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a';
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-0000-0000-00000000000a","role":"authenticated"}', true);
select throws_ok($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(),
  '00000000-0000-0000-0000-0000000000c2', 'A')$$, '42501', null, 'suspended account cannot submit');

reset role;
select * from finish();
rollback;
