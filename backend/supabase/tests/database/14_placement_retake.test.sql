-- Placement retakes: only finished attempts count towards the daily limit, the app is told when
-- the next attempt opens, and learners can see which question packs changed.
begin;
select plan(13);

insert into auth.users (id, email) values
  ('d1400000-0000-0000-0000-00000000000a', 'left.unfinished@example.test'),
  ('d1400000-0000-0000-0000-00000000000b', 'finished.three@example.test'),
  ('d1400000-0000-0000-0000-00000000000c', 'started.ten@example.test');

-- Published questions for the placement test (3 per English level).
update public.assessment_questions set status = 'published', review_status = 'approved'
 where cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2');
create temp table q1 as select (select id from public.assessment_questions where cefr_level = 'A1' limit 1) as id;

-- a: three attempts opened and left unfinished in the last day.
insert into public.exam_attempts (user_id, kind, question_ids, started_at, expires_at)
select 'd1400000-0000-0000-0000-00000000000a', 'placement', array[(select id from q1)],
       now() - make_interval(hours => h), now() - make_interval(hours => h) + interval '20 minutes'
  from generate_series(1, 3) h;
-- b: three finished attempts, the oldest 20 hours ago.
insert into public.exam_attempts (user_id, kind, question_ids, status, started_at, expires_at, submitted_at)
select 'd1400000-0000-0000-0000-00000000000b', 'placement', array[(select id from q1)], 'submitted',
       now() - make_interval(hours => h), now() - make_interval(hours => h) + interval '20 minutes',
       now() - make_interval(hours => h) + interval '10 minutes'
  from unnest(array[20, 5, 2]) h;
-- c: ten attempts started and left, the oldest 23 hours ago.
insert into public.exam_attempts (user_id, kind, question_ids, started_at, expires_at)
select 'd1400000-0000-0000-0000-00000000000c', 'placement', array[(select id from q1)],
       now() - make_interval(hours => h), now() - make_interval(hours => h) + interval '20 minutes'
  from generate_series(14, 23) h;

-- a
select set_config('request.jwt.claims', '{"sub":"d1400000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select results_eq($$select attempts_left, next_attempt_at, in_progress from public.get_placement_status()$$,
  $$values (3, null::timestamptz, false)$$, 'unfinished attempts do not use up the daily placement tests');
select lives_ok($$select * from public.start_placement_test()$$, 'a learner who left unfinished attempts can take the test');
select is((select in_progress from public.get_placement_status()), true, 'the new attempt is reported as in progress');
reset role;
select is((select count(*)::int from public.exam_attempts
            where user_id = 'd1400000-0000-0000-0000-00000000000a' and status = 'expired'), 3,
  'the unfinished attempts are closed as expired');

-- b
select set_config('request.jwt.claims', '{"sub":"d1400000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select results_eq($$select attempts_left, next_attempt_at from public.get_placement_status()$$,
  $$values (0, now() + interval '4 hours')$$, 'after three finished tests the next one opens 24 hours after the first');
select throws_ok($$select * from public.start_placement_test()$$, 'P0001', 'placement test limit reached for today',
  'a fourth finished test in a day is refused');
reset role;

-- c
select set_config('request.jwt.claims', '{"sub":"d1400000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select results_eq($$select attempts_left, next_attempt_at from public.get_placement_status()$$,
  $$values (0, now() + interval '1 hour')$$, 'starting and leaving ten tests in a day reaches the cap');
select throws_ok($$select * from public.start_placement_test()$$, 'P0001', 'placement test limit reached for today',
  'so questions cannot be collected by starting and leaving');

-- Pack versions
select ok((select count(*) from public.get_question_pack_versions()) >= 6, 'learners see a version for each level');
reset role;
select is((select published from public.get_question_pack_versions() where cefr_level = 'A1'),
          (select count(*)::int from public.assessment_questions where cefr_level = 'A1' and status = 'published'),
  'the version counts the published questions');
select is((select last_changed_at from public.get_question_pack_versions() where cefr_level = 'A1'),
          (select max(updated_at) from public.assessment_questions where cefr_level = 'A1' and status = 'published'),
  'and carries the last change');

set local role anon;
select throws_ok($$select * from public.get_question_pack_versions()$$, '42501', null, 'anonymous callers cannot read pack versions');
select throws_ok($$select * from public.get_placement_status()$$, '42501', null, 'nor placement status');
reset role;

select * from finish();
rollback;
