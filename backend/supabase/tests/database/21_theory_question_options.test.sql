-- A theory question may carry the official four options. Two questions in the official test share
-- a correct answer and are told apart only by their wrong options, which is why this exists.
begin;
select plan(8);

select has_column('public', 'theory_questions', 'options', 'questions can carry their own options');
select has_column('public', 'theory_questions', 'correct_option', 'and which of them is right');

create temp table target as
  select id from public.theory_questions limit 1;

-- Options and the index that picks the right one are set together or not at all.
select throws_ok(
  $$update public.theory_questions set correct_option = 1 where id = (select id from target)$$,
  null, 'an index without options is refused');
select throws_ok(
  $$update public.theory_questions set options = array['a','b'], correct_option = 5
     where id = (select id from target)$$,
  null, 'an index past the end of the options is refused');
select throws_ok(
  $$update public.theory_questions set options = array['a','b'], correct_option = 0,
      options_ckb = array['یەک'] where id = (select id from target)$$,
  null, 'a Sorani list of a different length is refused');

select lives_ok(
  $$update public.theory_questions
       set options = array['Damage to your vehicle','Damage to other vehicles'],
           options_ckb = array['زیان بە ئۆتۆمبێلی خۆت','زیان بە ئۆتۆمبێلی تر'],
           correct_option = 1
     where id = (select id from target)$$,
  'a complete set of options is accepted');

-- A learner downloading a chapter gets the options, so the app can show the real ones.
insert into auth.users (id, email) values ('e2100000-0000-0000-0000-00000000000a', 'options.learner@example.test');
update public.theory_questions set status = 'published'
 where section_id = (select id from public.theory_sections where slug = 'alertness');

set local role authenticated;
set local request.jwt.claims = '{"sub": "e2100000-0000-0000-0000-00000000000a"}';
select has_function('public', 'get_theory_questions', array['text', 'uuid', 'integer']);
select lives_ok(
  $$select options, options_ckb, correct_option from public.get_theory_questions('alertness', null, 5)$$,
  'the download carries the options');

reset role;
select * from finish();
rollback;
