-- The content pipeline brings content/theory/*.json into the database, but never over the top of
-- a question a teacher has taken charge of.
begin;
select plan(8);

select has_function('public', 'content_sync_theory_questions', array['jsonb']);
select ok(not has_function_privilege('authenticated', 'public.content_sync_theory_questions(jsonb)', 'execute'),
  'learners and staff cannot run the sync');
select ok(has_function_privilege('service_role', 'public.content_sync_theory_questions(jsonb)', 'execute'),
  'the content pipeline can');

create temp table target as
  select id, external_ref, version from public.theory_questions
   where external_ref = 'theory:alertness:1';

select is((select source from public.theory_questions where id = (select id from target)),
  'content:uk-theory-questions', 'the question starts out owned by the repository');

create temp table rows as
  select jsonb_build_array(jsonb_build_object(
    'external_ref', (select external_ref from target),
    'sort_order', 1,
    'prompt', 'A corrected question?',
    'answer', 'A corrected answer',
    'options', jsonb_build_array('A corrected answer', 'Something else'),
    'options_ckb', jsonb_build_array('وەڵامی چاککراو', 'شتێکی تر'),
    'correct_option', 0
  )) as payload;

select is(public.content_sync_theory_questions((select payload from rows)), 1,
  'a correction in the repository reaches the database');
select results_eq(
  $$select prompt, answer, options, correct_option from public.theory_questions
     where external_ref = 'theory:alertness:1'$$,
  $$values ('A corrected question?', 'A corrected answer',
            array['A corrected answer','Something else']::text[], 0::smallint)$$,
  'the wording and the options are both updated');
select ok((select version from public.theory_questions where id = (select id from target))
          > (select version from target),
  'and the version is bumped, so devices fetch the chapter again');

-- Once a teacher edits a question it is theirs, and the pipeline must leave it where it is.
update public.theory_questions set source = 'staff:someone' where external_ref = 'theory:alertness:1';
select is(public.content_sync_theory_questions((select payload from rows)), 0,
  'a question a teacher has edited is left alone');

select * from finish();
rollback;
