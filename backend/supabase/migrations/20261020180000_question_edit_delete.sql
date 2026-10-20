-- Staff can edit and delete questions.
--
-- * staff_update_question (teacher or admin): changes level, skill, prompt, options, key,
--   explanation, passage and media of an existing question. When what learners see or how it is
--   scored changes, the version is bumped, so earlier answers keep the version they were scored
--   against. Review and publish state are kept.
-- * staff_delete_question (admin only): a question nobody has answered or met in an exam is deleted
--   for good. Otherwise it is archived (deleted_at set, status retired): hidden from practice,
--   exams and the staff list, while learners' answer history stays intact.

alter table public.assessment_questions add column deleted_at timestamptz;
comment on column public.assessment_questions.deleted_at is
  'Set when an admin deletes a question that already has answers: it is kept (retired) for history only.';

create function public.staff_update_question(
  p_question_id uuid,
  p_cefr_level public.cefr_level,
  p_skill text,
  p_prompt text,
  p_options text[],
  p_correct_option text,
  p_explanation text default null,
  p_passage text default null,
  p_media_type text default null,
  p_media_path text default null,
  p_media_caption text default null
) returns integer
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_old public.assessment_questions;
  v_new public.assessment_questions;
  v_options text[];
  v_changed boolean;
begin
  select * into v_old from public.assessment_questions q where q.id = p_question_id for update;
  if not found or v_old.deleted_at is not null then
    raise exception 'question not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  select array_agg(btrim(o) order by n) into v_options from unnest(p_options) with ordinality as t(o, n);
  if p_cefr_level = 'PRE_A1' or coalesce(btrim(p_prompt), '') = '' then
    raise exception 'level A1-C2 and a prompt are required' using errcode = '22023';
  end if;
  if v_options is null or cardinality(v_options) not between 2 and 6
     or exists (select 1 from unnest(v_options) o where o = '')
     or (select count(distinct lower(o)) from unnest(v_options) o) <> cardinality(v_options) then
    raise exception '2-6 distinct, non-empty options are required' using errcode = '22023';
  end if;
  if upper(coalesce(p_correct_option, '')) !~ '^[A-F]$'
     or ascii(upper(p_correct_option)) - ascii('A') >= cardinality(v_options) then
    raise exception 'correct option must point to one of the options' using errcode = '22023';
  end if;

  v_new := v_old;
  v_new.cefr_level    := p_cefr_level;
  v_new.skill         := p_skill;
  v_new.prompt        := btrim(p_prompt);
  v_new.options       := v_options;
  v_new.correct_option := upper(p_correct_option);
  v_new.explanation   := nullif(btrim(p_explanation), '');
  v_new.passage       := nullif(btrim(p_passage), '');
  v_new.media_type    := nullif(btrim(p_media_type), '');
  v_new.media_path    := nullif(btrim(p_media_path), '');
  v_new.media_caption := case when nullif(btrim(p_media_path), '') is null then null else nullif(btrim(p_media_caption), '') end;

  v_changed := (v_new.prompt, v_new.options, v_new.correct_option, v_new.passage, v_new.media_path, v_new.cefr_level)
               is distinct from
               (v_old.prompt, v_old.options, v_old.correct_option, v_old.passage, v_old.media_path, v_old.cefr_level);

  update public.assessment_questions
     set cefr_level = v_new.cefr_level, skill = v_new.skill, prompt = v_new.prompt, options = v_new.options,
         correct_option = v_new.correct_option, explanation = v_new.explanation, passage = v_new.passage,
         media_type = v_new.media_type, media_path = v_new.media_path, media_caption = v_new.media_caption,
         version = version + case when v_changed then 1 else 0 end
   where id = p_question_id
   returning * into v_new;

  perform private.log_event(v_uid, 'staff.question_edited', 'assessment_question', p_question_id,
                            jsonb_build_object('cefr_level', v_new.cefr_level, 'version', v_new.version));
  return v_new.version;
end;
$$;
revoke all on function public.staff_update_question(uuid, public.cefr_level, text, text, text[], text, text, text, text, text, text)
  from public, anon;
grant execute on function public.staff_update_question(uuid, public.cefr_level, text, text, text[], text, text, text, text, text, text)
  to authenticated;

create function public.staff_delete_question(p_question_id uuid)
returns text
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(true);
  v_q public.assessment_questions;
  v_used boolean;
begin
  select * into v_q from public.assessment_questions q where q.id = p_question_id for update;
  if not found or v_q.deleted_at is not null then
    raise exception 'question not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  v_used := exists (select 1 from public.assessment_responses r where r.question_id = p_question_id)
         or exists (select 1 from public.exam_attempts e where p_question_id = any (e.question_ids));

  if v_used then
    update public.assessment_questions set status = 'retired', deleted_at = now() where id = p_question_id;
  else
    delete from public.assessment_questions where id = p_question_id;
  end if;

  perform private.log_event(v_uid, 'staff.question_deleted', 'assessment_question', p_question_id,
                            jsonb_build_object('cefr_level', v_q.cefr_level, 'archived', v_used));
  return case when v_used then 'archived' else 'deleted' end;
end;
$$;
revoke all on function public.staff_delete_question(uuid) from public, anon;
grant execute on function public.staff_delete_question(uuid) to authenticated;
