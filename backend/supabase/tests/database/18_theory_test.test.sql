-- Driving theory test: section list with the learner's standing, paged download, scoring that the
-- app reports, and staff authoring. Learners never read the tables directly.
begin;
select plan(15);

insert into auth.users (id, email) values
  ('e1800000-0000-0000-0000-00000000000a', 'theory.learner@example.test'),
  ('e1800000-0000-0000-0000-00000000000b', 'theory.teacher@example.test');
insert into public.user_roles (user_id, role) values ('e1800000-0000-0000-0000-00000000000b', 'teacher');

select is((select count(*)::int from public.theory_sections), 15, 'the fifteen chapters of the test are listed');
select ok((select count(*) from public.theory_questions) >= 500, 'the imported questions are present');
select is((select status from public.theory_questions where external_ref = 'theory:alertness:1'), 'draft',
  'imported questions wait for a teacher');

-- A learner only ever sees published questions in published sections.
update public.theory_questions set status = 'published'
 where section_id = (select id from public.theory_sections where slug = 'alertness');
create temp table q as
  select id, prompt, answer from public.theory_questions
   where section_id = (select id from public.theory_sections where slug = 'alertness')
   order by sort_order limit 1;
grant select on q to authenticated;

select set_config('request.jwt.claims', '{"sub":"e1800000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;

select is_empty($$select 1 from public.theory_questions$$,
  'learners read nothing from the question table directly');

select is((select published from public.get_theory_sections() where slug = 'alertness'), 26,
  'the section reports how many questions it has');
select is((select published from public.get_theory_sections() where slug = 'attitude'), 0,
  'a section whose questions are still drafts counts none');
select is((select count(*)::int from public.get_theory_sections()), 15,
  'sections with no questions yet are still listed');
select is((select count(*)::int from public.get_theory_questions('alertness', null, 200)), 26,
  'the download returns the published questions of the section');
select is((select count(*)::int from public.get_theory_questions('attitude', null, 200)), 0,
  'and nothing for a section with no published question');
select throws_ok($$select 1 from public.get_theory_questions('alertness', null, 500)$$, '22023', null,
  'the page size is bounded');

-- The app scores the answer and reports it; the section standing follows.
select lives_ok($$select public.submit_theory_response((select id from q), true, 'study')$$,
  'an answer is accepted');
select results_eq(
  $$select answered, correct from public.get_theory_sections() where slug = 'alertness'$$,
  $$values (1, 1)$$, 'the section shows what the learner has answered and got right');

-- Only the latest answer per question counts, so a second look replaces the first.
select public.submit_theory_response((select id from q), false, 'test');
select results_eq(
  $$select answered, correct from public.get_theory_sections() where slug = 'alertness'$$,
  $$values (1, 0)$$, 'answering again replaces the earlier result');

select is(public.reset_theory_section('alertness'), 2, 'a learner can start a section over');
reset role;

-- Staff authoring.
select set_config('request.jwt.claims', '{"sub":"e1800000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select lives_ok(
  $$select public.staff_upsert_theory_question('vehicle-loading', 'Who is responsible for the load on a vehicle?',
      'The driver of the vehicle', null, null, null, null, null, null, true)$$,
  'a teacher adds a question to an empty section');
reset role;

select * from finish();
rollback;
