-- Applying the Kurdish translations from the content pipeline.
--
-- The translations live in content/translations/ckb/*.json (one file per level, keyed by the
-- question's external_ref). Until now they were rendered to SQL and run by hand; this function
-- lets the EnglishMaster content workflow apply them on every push, the same way question drafts
-- and media are published.
--
-- It only ever writes the *_ckb columns: the English question, its options and its answer key are
-- never touched, so a re-run cannot change what a learner was scored against. It is granted to
-- service_role alone — learners and staff have no reason to call it, and a teacher's own edits go
-- through the staff RPCs as before.
--
-- A translated option list is stored only when it has exactly as many entries as the English
-- options, so a half-translated question shows English options rather than a mismatched set.
create function public.content_set_question_translations(p_rows jsonb)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  if p_rows is null or jsonb_typeof(p_rows) <> 'array' then
    raise exception 'p_rows must be a JSON array' using errcode = '22023';
  end if;

  with incoming as (
    select r.external_ref,
           r.prompt_ckb,
           case
             when r.options_ckb is null or jsonb_typeof(r.options_ckb) <> 'array' then null
             else array(
               -- A null entry means that one option is not translated yet; it stays null.
               select case when jsonb_typeof(e.value) = 'null' then null else e.value #>> '{}' end
                 from jsonb_array_elements(r.options_ckb) as e(value)
             )
           end as options_ckb,
           r.explanation_ckb,
           r.passage_ckb
      from jsonb_to_recordset(p_rows)
           as r(external_ref text, prompt_ckb text, options_ckb jsonb, explanation_ckb text, passage_ckb text)
  )
  update public.assessment_questions q
     set prompt_ckb      = i.prompt_ckb,
         options_ckb     = case when cardinality(i.options_ckb) = cardinality(q.options) then i.options_ckb end,
         explanation_ckb = i.explanation_ckb,
         passage_ckb     = i.passage_ckb
    from incoming i
   where q.external_ref = i.external_ref
     and (q.prompt_ckb, q.options_ckb, q.explanation_ckb, q.passage_ckb) is distinct from (
           i.prompt_ckb,
           case when cardinality(i.options_ckb) = cardinality(q.options) then i.options_ckb end,
           i.explanation_ckb,
           i.passage_ckb
         );
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
comment on function public.content_set_question_translations(jsonb) is
  'Content pipeline: fills prompt_ckb/options_ckb/explanation_ckb/passage_ckb from content/translations/ckb/*.json.';
revoke all on function public.content_set_question_translations(jsonb) from public, anon, authenticated;
grant execute on function public.content_set_question_translations(jsonb) to service_role;
