-- Exam-only questions, checkpoints, no repeated questions on retakes, two-stage placement.
begin;
select plan(31);

insert into auth.users (id, email, raw_user_meta_data) values
  ('d1500000-0000-0000-0000-00000000000a', 'cp.learner@example.test', '{"display_name":"Checkpoint Learner"}'),
  ('d1500000-0000-0000-0000-00000000000b', 'place.good@example.test', '{"display_name":"Placement Good"}'),
  ('d1500000-0000-0000-0000-00000000000c', 'place.weak@example.test', '{"display_name":"Placement Weak"}');

update public.assessment_questions set status = 'published', review_status = 'approved'
 where cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2');

-- 80 exam-only A1 questions.
insert into public.assessment_questions (cefr_level, skill, prompt, options, correct_option, explanation, status, review_status, pool)
select 'A1', (array['grammar', 'vocabulary', 'reading', 'listening'])[1 + n % 4], 'Exam-only question ' || n || '?',
       array['one', 'two', 'three', 'four'], 'B', 'Because.', 'published', 'approved', 'exam'
  from generate_series(1, 80) n;

create temp table t (k text primary key, v jsonb);
grant select, insert, update on t to authenticated;
-- Answer keys, read with owner rights as a test fixture.
create temp table keys as select id, correct_option, pool, cefr_level from public.assessment_questions;
grant select on keys to authenticated;
create temp table practice_a1 as
  select id, version, correct_option from public.assessment_questions
   where cefr_level = 'A1' and status = 'published' and pool = 'practice' order by id;
insert into t values ('a1_practice', to_jsonb((select count(*) from practice_a1)));

select throws_ok($$insert into public.assessment_questions (cefr_level, skill, prompt, options, correct_option, pool)
                   values ('LIFE_IN_UK', 'reading', 'x?', array['a', 'b'], 'A', 'exam')$$,
  '23514', null, 'Life in the UK has no exam-only questions');

-- Learner A -------------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"d1500000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*)::int from public.get_practice_questions_v3('A1', null, 200) p
             join keys k on k.id = p.id where k.pool = 'exam'), 0, 'exam-only questions are never downloaded for practice');
select is((select published from public.get_question_pack_versions() where cefr_level = 'A1'),
          (select v::int from t where k = 'a1_practice'), 'pack versions count practice questions only');
select is((select published from public.get_learning_overview_v2() where cefr_level = 'A1'),
          (select v::int from t where k = 'a1_practice'), 'the 70% rule counts practice questions only');
select throws_ok(format($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(), %L, 'B')$$,
                        (select id from keys where pool = 'exam' limit 1)),
  '22023', 'question not available', 'exam-only questions cannot be answered as practice');
select results_eq($$select checkpoint, unlocked, passed from public.get_checkpoints() where cefr_level = 'A1' order by checkpoint$$,
  $$values (1, false, false), (2, false, false), (3, false, false)$$, 'checkpoints are locked before practice');
select throws_ok($$select * from public.start_checkpoint('A1', 1)$$, 'P0001', 'practise more before this checkpoint',
  'a locked checkpoint cannot be started');
reset role;

-- A answers 25% of A1 correctly.
insert into public.assessment_attempts (id, user_id, kind, cefr_level) values
  ('d1500000-0000-0000-0000-0000000000a1', 'd1500000-0000-0000-0000-00000000000a', 'practice', 'A1');
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
select gen_random_uuid(), 'd1500000-0000-0000-0000-0000000000a1', 'd1500000-0000-0000-0000-00000000000a', id, version, correct_option, true
  from practice_a1 limit (select ceil(v::int * 0.25) from t where k = 'a1_practice');

set local role authenticated;
select is((select unlocked from public.get_checkpoints() where cefr_level = 'A1' and checkpoint = 1), true,
  'checkpoint 1 opens at 25% of the level answered');
select is((select unlocked from public.get_checkpoints() where cefr_level = 'A1' and checkpoint = 2), false,
  'checkpoint 2 waits for 50% and for checkpoint 1');
insert into t select 'cp1', to_jsonb(c) from public.start_checkpoint('A1', 1) c;
select is((select jsonb_array_length(v -> 'questions') from t where k = 'cp1'), 15, 'a checkpoint has 15 questions');
select is((select count(*)::int from t, jsonb_array_elements(v -> 'questions') q join keys k on k.id = (q ->> 'id')::uuid
            where t.k = 'cp1' and k.pool = 'exam'), 0, 'checkpoints never use exam-only questions');
select is((select count(*)::int from t, jsonb_array_elements(v -> 'questions') q
            where t.k = 'cp1' and exists (select 1 from public.assessment_responses r
                                           where r.user_id = 'd1500000-0000-0000-0000-00000000000a'
                                             and r.question_id = (q ->> 'id')::uuid)), 0,
  'checkpoint questions are ones the learner has not practised');
select throws_ok(format($$select * from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(), %L, 'A')$$,
                        (select v -> 'questions' -> 0 ->> 'id' from t where k = 'cp1')),
  '22023', 'question not available', 'answers to a running checkpoint cannot be looked up through practice');
-- Fail it.
insert into t select 'cp1_fail', to_jsonb(r) from public.submit_checkpoint((select (v ->> 'attempt_id')::uuid from t where k = 'cp1'), '{}') r;
select is((select (v ->> 'passed')::boolean from t where k = 'cp1_fail'), false, 'no answers fails the checkpoint');
select throws_ok($$select * from public.start_checkpoint('A1', 1)$$, 'P0001', 'next attempt not yet available',
  'a failed checkpoint can be retried after a break');
reset role;

-- Two hours later A retries and passes.
update public.exam_attempts set started_at = started_at - interval '3 hours', submitted_at = submitted_at - interval '3 hours',
       expires_at = expires_at - interval '3 hours'
 where user_id = 'd1500000-0000-0000-0000-00000000000a' and kind = 'checkpoint';
set local role authenticated;
insert into t select 'cp1b', to_jsonb(c) from public.start_checkpoint('A1', 1) c;
select is((select count(*)::int from t a, jsonb_array_elements(a.v -> 'questions') q1, t b, jsonb_array_elements(b.v -> 'questions') q2
            where a.k = 'cp1' and b.k = 'cp1b' and q1 ->> 'id' = q2 ->> 'id'), 0,
  'the retry has different questions');
insert into t select 'cp1b_ok', to_jsonb(r) from public.submit_checkpoint(
  (select (v ->> 'attempt_id')::uuid from t where k = 'cp1b'),
  (select jsonb_object_agg(k.id::text, k.correct_option) from t, jsonb_array_elements(t.v -> 'questions') q
     join keys k on k.id = (q ->> 'id')::uuid where t.k = 'cp1b')) r;
select is((select (v ->> 'passed')::boolean from t where k = 'cp1b_ok'), true, 'all correct passes the checkpoint');
reset role;

-- A practises the whole level correctly and passes checkpoints 2 and 3 (fixture).
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
select gen_random_uuid(), 'd1500000-0000-0000-0000-0000000000a1', 'd1500000-0000-0000-0000-00000000000a', p.id, p.version, p.correct_option, true
  from practice_a1 p
 where not exists (select 1 from public.assessment_responses r
                    where r.user_id = 'd1500000-0000-0000-0000-00000000000a' and r.question_id = p.id);
set local role authenticated;
select is((select exam_unlocked from public.get_learning_overview_v2() where cefr_level = 'A1'), false,
  'the exam stays locked until all three checkpoints are passed');
reset role;
insert into public.exam_attempts (user_id, kind, cefr_level, checkpoint, question_ids, status, expires_at, submitted_at, score, total, passed)
select 'd1500000-0000-0000-0000-00000000000a', 'checkpoint', 'A1', k, array[(select id from practice_a1 limit 1)],
       'submitted', now(), now(), 15, 15, true
  from generate_series(2, 3) k;

set local role authenticated;
select is((select exam_unlocked from public.get_learning_overview_v2() where cefr_level = 'A1'), true,
  'with the checkpoints passed the exam unlocks');
insert into t select 'ex1', to_jsonb(e) from public.start_level_exam('A1') e;
select is((select count(*)::int from t, jsonb_array_elements(v -> 'questions') q join keys k on k.id = (q ->> 'id')::uuid
            where t.k = 'ex1' and k.pool = 'exam'), 40, 'the level exam uses exam-only questions');
select is((select count(distinct q ->> 'id')::int from t, jsonb_array_elements(v -> 'questions') q where t.k = 'ex1'), 40,
  'and no question twice');
insert into t select 'ex1_r', to_jsonb(r) from public.submit_level_exam((select (v ->> 'attempt_id')::uuid from t where k = 'ex1'), '{}') r;
reset role;
update public.exam_attempts set started_at = started_at - interval '2 days', submitted_at = submitted_at - interval '2 days',
       expires_at = expires_at - interval '2 days'
 where user_id = 'd1500000-0000-0000-0000-00000000000a' and kind = 'level_final';
set local role authenticated;
insert into t select 'ex2', to_jsonb(e) from public.start_level_exam('A1') e;
select is((select count(*)::int from t a, jsonb_array_elements(a.v -> 'questions') q1, t b, jsonb_array_elements(b.v -> 'questions') q2
            where a.k = 'ex1' and b.k = 'ex2' and q1 ->> 'id' = q2 ->> 'id'), 0,
  'a retake gets none of the questions from the first exam');
reset role;

-- Placement: B passes A1 and A2 in the screening test, then confirms A2 -------------------
select set_config('request.jwt.claims', '{"sub":"d1500000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
insert into t select 'p1', to_jsonb(p) from public.start_placement_test() p;
insert into t select 'p1_r', to_jsonb(r) from public.submit_placement_test(
  (select (v ->> 'attempt_id')::uuid from t where k = 'p1'),
  (select jsonb_object_agg(k.id::text, k.correct_option) from t, jsonb_array_elements(t.v -> 'questions') q
     join keys k on k.id = (q ->> 'id')::uuid where t.k = 'p1' and k.cefr_level in ('A1', 'A2'))) r;
select is((select confirm_level::text from public.get_placement_status_v2()), 'A2',
  'after screening, the highest level passed is offered for confirmation');
insert into t select 'c1', to_jsonb(c) from public.start_placement_confirmation() c;
select is((select count(*)::int from t, jsonb_array_elements(v -> 'questions') q join keys k on k.id = (q ->> 'id')::uuid
            where t.k = 'c1' and k.cefr_level = 'A2' and k.pool = 'practice'), 20,
  'the confirmation test has 20 practice questions at that level');
select is((select count(*)::int from t a, jsonb_array_elements(a.v -> 'questions') q1, t b, jsonb_array_elements(b.v -> 'questions') q2
            where a.k = 'p1' and b.k = 'c1' and q1 ->> 'id' = q2 ->> 'id'), 0,
  'and none of the screening questions');
select results_eq(
  format($$select confirmed, tested_level::text, recommended_level::text from public.submit_placement_confirmation(%L, %L::jsonb)$$,
         (select v ->> 'attempt_id' from t where k = 'c1'),
         (select jsonb_object_agg(k.id::text, k.correct_option) from t, jsonb_array_elements(t.v -> 'questions') q
            join keys k on k.id = (q ->> 'id')::uuid where t.k = 'c1')),
  $$values (true, 'A2'::text, 'B1'::text)$$, 'a confirmed level means studying the next one');
select results_eq($$select confirm_level, confirmed from public.get_placement_status_v2()$$,
  $$values (null::public.cefr_level, true)$$, 'nothing left to confirm');
select is((select placement_level::text from public.get_learner_profile()), 'B1', 'the profile has the confirmed result');
select throws_ok($$select * from public.start_placement_confirmation()$$, 'P0001', 'nothing to confirm',
  'one confirmation per placement test');
insert into t select 'p2', to_jsonb(p) from public.start_placement_test() p;
select is((select count(*)::int from t a, jsonb_array_elements(a.v -> 'questions') q1, t b, jsonb_array_elements(b.v -> 'questions') q2
            where a.k in ('p1', 'c1') and b.k = 'p2' and q1 ->> 'id' = q2 ->> 'id'), 0,
  'a placement retake avoids questions the learner has already seen');
reset role;

-- C passes A1 in screening but not the confirmation -----------------------------------------
select set_config('request.jwt.claims', '{"sub":"d1500000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
insert into t select 'cp', to_jsonb(p) from public.start_placement_test() p;
insert into t select 'cp_r', to_jsonb(r) from public.submit_placement_test(
  (select (v ->> 'attempt_id')::uuid from t where k = 'cp'),
  (select jsonb_object_agg(k.id::text, k.correct_option) from t, jsonb_array_elements(t.v -> 'questions') q
     join keys k on k.id = (q ->> 'id')::uuid where t.k = 'cp' and k.cefr_level = 'A1')) r;
insert into t select 'cc', to_jsonb(c) from public.start_placement_confirmation() c;
select results_eq(
  format($$select confirmed, recommended_level::text from public.submit_placement_confirmation(%L, '{}'::jsonb)$$,
         (select v ->> 'attempt_id' from t where k = 'cc')),
  $$values (false, 'A1'::text)$$, 'a level not confirmed is the level to study');
reset role;

select * from finish();
rollback;
