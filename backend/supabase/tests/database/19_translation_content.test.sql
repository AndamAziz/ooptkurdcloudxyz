-- The content pipeline's translation function: it fills the Kurdish columns and nothing else, it
-- is idempotent, and only the pipeline (service_role) may call it.
begin;
select plan(10);

select has_function('public', 'content_set_question_translations', array['jsonb']);

-- Learners and teachers have no business calling it; their edits go through the staff RPCs.
select ok(not has_function_privilege('authenticated', 'public.content_set_question_translations(jsonb)', 'execute'),
  'learners and staff cannot apply translations');
select ok(not has_function_privilege('anon', 'public.content_set_question_translations(jsonb)', 'execute'),
  'anonymous callers cannot apply translations');
select ok(has_function_privilege('service_role', 'public.content_set_question_translations(jsonb)', 'execute'),
  'the content pipeline can apply translations');

create temp table target as
  select id, external_ref, options, correct_option, prompt
    from public.assessment_questions
   where external_ref is not null and cardinality(options) = 4
   order by external_ref limit 1;

create temp table incoming as
  select jsonb_build_array(jsonb_build_object(
    'external_ref', (select external_ref from target),
    'prompt_ckb', 'پرسیاری تاقیکردنەوە',
    'options_ckb', jsonb_build_array('یەک', 'دوو', null, 'چوار'),
    'explanation_ckb', 'ڕوونکردنەوە',
    'passage_ckb', null
  )) as rows;

select is(public.content_set_question_translations((select rows from incoming)), 1,
  'a question translation is applied');

select results_eq(
  $$select prompt_ckb, options_ckb, explanation_ckb from public.assessment_questions
     where external_ref = (select external_ref from target)$$,
  $$values ('پرسیاری تاقیکردنەوە', array['یەک', 'دوو', null, 'چوار']::text[], 'ڕوونکردنەوە')$$,
  'the Kurdish prompt, options (an untranslated entry stays null) and explanation are stored');

-- The English content is what the answer key is based on, so it must be untouched.
select results_eq(
  $$select q.prompt, q.options, q.correct_option from public.assessment_questions q join target t on t.id = q.id$$,
  $$select t.prompt, t.options, t.correct_option from target t$$,
  'the English question, options and answer key are left alone');

-- The workflow runs on every push, so the same content must be a no-op the second time.
select is(public.content_set_question_translations((select rows from incoming)), 0,
  'applying the same translation again updates nothing');

-- A wrong number of options is dropped rather than shown against the English list.
select lives_ok(
  $$select public.content_set_question_translations(jsonb_build_array(jsonb_build_object(
      'external_ref', (select external_ref from target),
      'prompt_ckb', 'پرسیار', 'options_ckb', jsonb_build_array('تەنها یەک'),
      'explanation_ckb', null, 'passage_ckb', null)))$$,
  'a half-translated option list is accepted');
select is((select options_ckb from public.assessment_questions where external_ref = (select external_ref from target)),
  null, 'and is not stored, so the question keeps its English options');

select * from finish();
rollback;
