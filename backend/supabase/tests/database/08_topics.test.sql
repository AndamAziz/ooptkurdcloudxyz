-- Topic practice: v3 download carries tags; drafts stay hidden; anon cannot call it.
begin;
select plan(4);

insert into auth.users (id, email) values ('f8000000-0000-0000-0000-00000000000f', 'student.topics@example.test');
update public.assessment_questions set status = 'published', review_status = 'approved'
 where cefr_level = 'A2' and prompt = 'How many countries make up the United Kingdom?';

select set_config('request.jwt.claims', '{"sub":"f8000000-0000-0000-0000-00000000000f","role":"authenticated"}', true);
set local role authenticated;
select ok((select tags @> array['life_in_uk', 'story'] from public.get_practice_questions_v3('A2', null, 200)
            where prompt = 'How many countries make up the United Kingdom?'),
  'v3 download includes the topic tags');
select is((select count(*)::int from public.get_practice_questions_v3('A2', null, 200)
            where prompt = 'In the Newroz story, who was Kawa?'), 0,
  'unapproved drafts are not downloaded');
select is((select count(*)::int from public.get_practice_questions_v3('A2', null, 200)),
          (select count(*)::int from public.get_practice_questions_v2('A2', null, 200)),
  'v3 returns the same questions as v2');
reset role;

select ok(not has_function_privilege('anon', 'public.get_practice_questions_v3(public.cefr_level, uuid, integer)', 'execute'),
  'anon cannot download questions');

select * from finish();
rollback;
