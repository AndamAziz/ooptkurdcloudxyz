-- Spaced review: a mistake is fixed only by right answers on two later days.
begin;
select plan(12);

insert into auth.users (id, email) values ('d1600000-0000-0000-0000-00000000000a', 'review.learner@example.test');
update public.assessment_questions set status = 'published', review_status = 'approved' where cefr_level = 'A1';
create temp table q as
  select id, version, correct_option from public.assessment_questions
   where cefr_level = 'A1' and status = 'published' and pool = 'practice' order by id limit 2;
create temp table q1 as select * from q order by id limit 1;
grant select on q, q1 to authenticated;

create function pg_temp.answer(p_correct boolean, p_at timestamptz) returns void language sql as $$
  with a as (
    insert into public.assessment_attempts (id, user_id, kind, cefr_level)
    values (gen_random_uuid(), 'd1600000-0000-0000-0000-00000000000a', 'practice', 'A1') returning id
  )
  insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct, received_at)
  select gen_random_uuid(), a.id, 'd1600000-0000-0000-0000-00000000000a', q1.id, q1.version,
         case when p_correct then q1.correct_option when q1.correct_option = 'A' then 'B' else 'A' end, p_correct, p_at
    from a, q1;
$$;

create function pg_temp.progress() returns table (state text, reviews int, due_at timestamptz) language sql as $$
  select p.state, p.reviews::int, p.due_at from public.question_progress p
   where p.user_id = 'd1600000-0000-0000-0000-00000000000a' and p.question_id = (select id from q1);
$$;

select pg_temp.answer(false, now() - interval '10 days');
select results_eq($$select state, reviews, due_at from pg_temp.progress()$$,
  $$values ('learning'::text, 0, now() - interval '9 days')$$, 'a wrong answer is due again 24 hours later');

select pg_temp.answer(true, now() - interval '10 days' + interval '1 minute');
select results_eq($$select state, reviews from pg_temp.progress()$$, $$values ('learning'::text, 0)$$,
  'answering right straight after seeing the answer does not fix the mistake');

select pg_temp.answer(true, now() - interval '8 days');
select results_eq($$select state, reviews, due_at from pg_temp.progress()$$,
  $$values ('learning'::text, 1, now() - interval '5 days')$$, 'a right answer when due brings it back 3 days later');

select pg_temp.answer(true, now() - interval '7 days');
select is((select reviews from pg_temp.progress()), 1, 'answering early in the 3-day wait does not count either');

select pg_temp.answer(true, now() - interval '4 days');
select results_eq($$select state, due_at from pg_temp.progress()$$, $$values ('known'::text, null::timestamptz)$$,
  'the second right review fixes the mistake');

select pg_temp.answer(false, now() - interval '3 days');
select is((select state from pg_temp.progress()), 'learning', 'a known question answered wrong becomes a mistake again');
select pg_temp.answer(true, now() - interval '2 days');
select pg_temp.answer(false, now() + interval '1 day');
select results_eq($$select state, reviews, due_at from pg_temp.progress()$$,
  $$values ('learning'::text, 0, now() + interval '2 days')$$, 'wrong at the second review starts the schedule again');

-- Accuracy towards the exam counts only known questions.
select set_config('request.jwt.claims', '{"sub":"d1600000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select results_eq($$select answered, correct from public.get_learning_overview_v2() where cefr_level = 'A1'$$,
  $$values (1, 0)$$, 'a question waiting for review does not count as right');
select is((select count(*)::int from public.get_review_schedule()), 1, 'the learner sees their review schedule');
select is((select count(*)::int from public.question_progress), 1, 'learners read only their own progress');
reset role;

-- Through the real RPC: an immediate re-answer of a mistake is stored but changes nothing.
set local role authenticated;
select is((select is_correct from public.submit_practice_response(gen_random_uuid(), gen_random_uuid(), (select id from q order by id desc limit 1),
           (select case when correct_option = 'A' then 'B' else 'A' end from q order by id desc limit 1))), false, 'a wrong practice answer');
select is((select state from public.question_progress where question_id = (select id from q order by id desc limit 1)), 'learning',
  'is scheduled for review by the trigger');
reset role;

select * from finish();
rollback;
