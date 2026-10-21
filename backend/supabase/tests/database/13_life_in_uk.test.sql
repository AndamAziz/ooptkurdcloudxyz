-- Life in the UK: its own question set, practised like a level but never an English level exam,
-- certificate or placement question.
begin;
select plan(9);

insert into auth.users (id, email) values
  ('d1300000-0000-0000-0000-00000000000d', 'student.lituk@example.test'),
  ('d1300000-0000-0000-0000-0000000000d1', 'teacher.lituk@example.test');
insert into public.user_roles (user_id, role) values ('d1300000-0000-0000-0000-0000000000d1', 'teacher');

select ok((select count(*) from public.assessment_questions where cefr_level = 'LIFE_IN_UK') >= 400,
  'the Life in the UK question set is seeded');
select is((select count(*)::int from public.assessment_questions
            where cefr_level = 'LIFE_IN_UK' and not tags @> array['life_in_uk']), 0,
  'every Life in the UK question carries the life_in_uk tag');

-- Publish the set, as a teacher would after review.
update public.assessment_questions set status = 'published', review_status = 'approved'
 where cefr_level = 'LIFE_IN_UK';
-- Learners cannot read the bank (answer keys); remember one question and its key for later.
create temp table k as
  select id, correct_option from public.assessment_questions where cefr_level = 'LIFE_IN_UK' order by id limit 1;
grant select on k to authenticated;

select set_config('request.jwt.claims', '{"sub":"d1300000-0000-0000-0000-00000000000d","role":"authenticated"}', true);
set local role authenticated;
select ok((select count(*) from public.get_practice_questions_v3('LIFE_IN_UK', null, 200)) = 200,
  'learners download published Life in the UK questions');
select is((select count(*)::int from public.get_learning_overview_v2() where cefr_level = 'LIFE_IN_UK'), 0,
  'Life in the UK is not part of the English level overview');
select is((select count(*)::int from public.get_learning_overview() where cefr_level = 'LIFE_IN_UK'), 0,
  'nor of the overview older apps read');
select throws_ok($$select * from public.start_level_exam('LIFE_IN_UK')$$, 'P0001', 'practise more before the exam',
  'there is no level exam (or certificate) for Life in the UK');
select is((select count(*)::int
             from public.start_placement_test() p,
                  jsonb_array_elements(p.questions) e
            where (e->>'cefr_level') = 'LIFE_IN_UK'), 0,
  'the placement test never uses Life in the UK questions');
select is((select r.is_correct from public.submit_practice_response(
             gen_random_uuid(), gen_random_uuid(),
             (select id from k), (select correct_option from k)) r),
          true, 'answers are scored on the server like any practice question');
reset role;

select set_config('request.jwt.claims', '{"sub":"d1300000-0000-0000-0000-0000000000d1","role":"authenticated"}', true);
set local role authenticated;
select isnt(public.staff_create_question('LIFE_IN_UK', 'reading', 'Which flower is associated with Remembrance Day?',
            array['The poppy', 'The rose', 'The thistle', 'The daffodil'], 'A', 'Red poppies are worn in November.'),
            null, 'teachers can add Life in the UK questions');
reset role;

select * from finish();
rollback;
