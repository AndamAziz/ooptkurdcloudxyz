-- Keeping the driving-theory questions in step with the repository.
--
-- The content workflow inserts new questions and ignores ones it has seen before, so correcting
-- the wording of a question already in the database did nothing: the file said one thing and
-- learners saw another. This closes that gap for the questions the repository owns, and only
-- those.
--
-- A question is the repository's while its source still says so. The staff editor now stamps its
-- own source when a teacher changes a question, so from that moment the question is the teacher's
-- and the pipeline leaves it alone.
create or replace function public.staff_upsert_theory_question(
  p_section_slug text,
  p_prompt text,
  p_answer text,
  p_id uuid default null,
  p_prompt_ckb text default null,
  p_answer_ckb text default null,
  p_explanation text default null,
  p_explanation_ckb text default null,
  p_media_path text default null,
  p_publish boolean default false
) returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_section uuid;
  v_id uuid;
begin
  select id into v_section from public.theory_sections where slug = p_section_slug;
  if v_section is null then
    raise exception 'unknown chapter %', p_section_slug using errcode = '22023';
  end if;
  if p_prompt is null or char_length(btrim(p_prompt)) = 0 then
    raise exception 'p_prompt is required' using errcode = '22023';
  end if;
  if p_answer is null or char_length(btrim(p_answer)) = 0 then
    raise exception 'p_answer is required' using errcode = '22023';
  end if;

  if p_id is null then
    insert into public.theory_questions (
      section_id, sort_order, prompt, prompt_ckb, answer, answer_ckb,
      explanation, explanation_ckb, media_path, status, source
    ) values (
      v_section,
      coalesce((select max(sort_order) + 1 from public.theory_questions where section_id = v_section), 1),
      btrim(p_prompt), p_prompt_ckb, btrim(p_answer), p_answer_ckb,
      p_explanation, p_explanation_ckb, p_media_path,
      case when p_publish then 'published' else 'draft' end,
      'staff:' || v_uid::text
    )
    returning id into v_id;
  else
    update public.theory_questions q
       set section_id   = v_section,
           prompt       = btrim(p_prompt),
           prompt_ckb   = p_prompt_ckb,
           answer       = btrim(p_answer),
           answer_ckb   = p_answer_ckb,
           explanation  = p_explanation,
           explanation_ckb = p_explanation_ckb,
           media_path   = p_media_path,
           status       = case when p_publish then 'published' else q.status end,
           -- The question is the teacher's from now on; the content pipeline stops touching it.
           source       = 'staff:' || v_uid::text,
           version      = q.version + case when q.prompt is distinct from btrim(p_prompt)
                                             or q.answer is distinct from btrim(p_answer)
                                        then 1 else 0 end
     where q.id = p_id
     returning q.id into v_id;
    if v_id is null then
      raise exception 'unknown question %', p_id using errcode = '22023';
    end if;
  end if;

  perform private.log_event(v_uid, 'theory.question_upsert', 'theory_question', v_id,
                            jsonb_build_object('section', p_section_slug, 'published', p_publish));
  return v_id;
end;
$$;
revoke all on function public.staff_upsert_theory_question(text, text, text, uuid, text, text, text, text, text, boolean) from public, anon;
grant execute on function public.staff_upsert_theory_question(text, text, text, uuid, text, text, text, text, text, boolean) to authenticated;

-- Brings the repository's own questions up to date. Rows a teacher has edited are skipped, and a
-- row whose text changes has its version bumped so devices download the chapter again.
create function public.content_sync_theory_questions(p_rows jsonb)
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
    select r.external_ref, r.sort_order, r.prompt, r.prompt_ckb, r.answer, r.answer_ckb,
           r.options, r.options_ckb, r.correct_option,
           r.explanation, r.explanation_ckb, r.media_path
      from jsonb_to_recordset(p_rows) as r(
        external_ref text, sort_order integer, prompt text, prompt_ckb text,
        answer text, answer_ckb text, options text[], options_ckb text[], correct_option smallint,
        explanation text, explanation_ckb text, media_path text)
  )
  update public.theory_questions q
     set sort_order = i.sort_order,
         prompt = i.prompt, prompt_ckb = i.prompt_ckb,
         answer = i.answer, answer_ckb = i.answer_ckb,
         options = i.options, options_ckb = i.options_ckb, correct_option = i.correct_option,
         explanation = i.explanation, explanation_ckb = i.explanation_ckb,
         media_path = i.media_path,
         -- A new question or answer is what invalidates an earlier attempt at it.
         version = q.version + case when q.prompt is distinct from i.prompt
                                      or q.answer is distinct from i.answer
                                 then 1 else 0 end
    from incoming i
   where q.external_ref = i.external_ref
     and q.source like 'content:%'
     and (q.sort_order, q.prompt, q.prompt_ckb, q.answer, q.answer_ckb, q.options, q.options_ckb,
          q.correct_option, q.explanation, q.explanation_ckb, q.media_path)
         is distinct from
         (i.sort_order, i.prompt, i.prompt_ckb, i.answer, i.answer_ckb, i.options, i.options_ckb,
          i.correct_option, i.explanation, i.explanation_ckb, i.media_path);
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
comment on function public.content_sync_theory_questions(jsonb) is
  'Content pipeline: brings content/theory/*.json into the database, skipping teacher-edited rows.';
revoke all on function public.content_sync_theory_questions(jsonb) from public, anon, authenticated;
grant execute on function public.content_sync_theory_questions(jsonb) to service_role;
