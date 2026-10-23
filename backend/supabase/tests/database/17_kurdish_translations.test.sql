-- Kurdish translations: downloaded with practice questions, explanation only after answering,
-- never in exams, and adding one does not change the question's version.
begin;
select plan(9);

insert into auth.users (id, email) values ('d1700000-0000-0000-0000-00000000000a', 'kurdish.reader@example.test');
update public.assessment_questions set status = 'published', review_status = 'approved' where cefr_level = 'A1';
create temp table q as
  select id, version, options, correct_option, updated_at from public.assessment_questions
   where cefr_level = 'A1' and status = 'published' and pool = 'practice' order by id limit 1;
grant select on q to authenticated;

update public.assessment_questions a
   set prompt_ckb = 'وەرگێڕانی پرسیار', explanation_ckb = 'ڕوونکردنەوە',
       options_ckb = array_fill(null::text, array[cardinality(a.options)])
 where a.id = (select id from q);
update public.assessment_questions a set options_ckb[1] = 'یەکەم' where a.id = (select id from q);

select is((select version from public.assessment_questions where id = (select id from q)), (select version from q),
  'a translation does not change the question version');
select ok((select updated_at from public.assessment_questions where id = (select id from q)) >= (select updated_at from q),
  'but marks the pack as changed, so the app downloads it again');
select throws_ok($$update public.assessment_questions set options_ckb = array['x'] where id = (select id from q)$$,
  '23514', null, 'options_ckb must have one entry per option');

select set_config('request.jwt.claims', '{"sub":"d1700000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select results_eq($$select prompt_ckb, options_ckb[1] from public.get_practice_questions_v4('A1', null, 200) where id = (select id from q)$$,
  $$values ('وەرگێڕانی پرسیار'::text, 'یەکەم'::text)$$, 'v4 download carries the Kurdish question and answers');
select is((select count(*)::int from public.get_practice_questions_v4('A1', null, 200)),
          (select count(*)::int from public.get_practice_questions_v3('A1', null, 200)), 'v4 returns the same questions as v3');
select ok(not exists (
    select 1 from information_schema.routines r
     where r.routine_name = 'get_practice_questions_v4' and r.routine_definition ilike '%explanation_ckb%'),
  'the Kurdish explanation is not downloaded before answering');
select results_eq(
  $$select is_correct, explanation_ckb from public.submit_practice_response_v2(gen_random_uuid(), gen_random_uuid(),
      (select id from q), (select correct_option from q))$$,
  $$values (true, 'ڕوونکردنەوە'::text)$$, 'the answer comes back with the Kurdish explanation');
reset role;

select ok(not has_function_privilege('anon', 'public.get_practice_questions_v4(public.cefr_level, uuid, integer)', 'execute'),
  'anon cannot download questions');
select ok(not has_function_privilege('anon', 'public.submit_practice_response_v2(uuid, uuid, uuid, text, timestamptz)', 'execute'),
  'nor submit answers');

select * from finish();
rollback;
