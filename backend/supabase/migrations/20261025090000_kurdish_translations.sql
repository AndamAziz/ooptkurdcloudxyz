-- Kurdish (Sorani) translations of practice questions.
--
-- Learners can show a Kurdish translation under the English question, answers and reading passage
-- while practising and reviewing (never in exams, whose payload does not carry them).
--
--   * prompt_ckb, passage_ckb: the question and its reading text in Sorani.
--   * options_ckb: one entry per option; the whole array is null when the options are English
--     forms whose translation means nothing (am / is / are), and single entries may be null.
--   * explanation_ckb: returned only after answering (submit_practice_response_v2), like the
--     English explanation, because it gives the answer away.
--
-- Translations do not change the question, so `version` stays; updated_at moves (trigger), which
-- tells the app its downloaded pack is out of date and makes it fetch the translations.

alter table public.assessment_questions
  add column prompt_ckb      text check (char_length(prompt_ckb) <= 3000),
  add column options_ckb     text[],
  add column explanation_ckb text check (char_length(explanation_ckb) <= 4000),
  add column passage_ckb     text check (char_length(passage_ckb) <= 5000),
  add constraint assessment_questions_options_ckb_match
    check (options_ckb is null or cardinality(options_ckb) = cardinality(options));
comment on column public.assessment_questions.options_ckb is
  'Sorani translation per option, or null when the options are not worth translating (grammar forms).';

-- Practice download with the translations (v3 stays for older app builds).
create or replace function public.get_practice_questions_v4(
  p_cefr_level public.cefr_level,
  p_after uuid default null,
  p_limit integer default 100
) returns table (
  id uuid,
  cefr_level public.cefr_level,
  skill text,
  prompt text,
  options text[],
  version integer,
  review_status text,
  updated_at timestamptz,
  passage text,
  media_type text,
  media_path text,
  media_caption text,
  tags text[],
  prompt_ckb text,
  options_ckb text[],
  passage_ckb text
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
begin
  perform private.require_learner();
  if p_limit is null or p_limit < 1 or p_limit > 200 then
    raise exception 'p_limit must be between 1 and 200' using errcode = '22023';
  end if;
  return query
    select q.id, q.cefr_level, q.skill, q.prompt, q.options, q.version, q.review_status, q.updated_at,
           q.passage, q.media_type, q.media_path, q.media_caption, q.tags,
           q.prompt_ckb, q.options_ckb, q.passage_ckb
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and q.pool = 'practice'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions_v4(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions_v4(public.cefr_level, uuid, integer) to authenticated;

-- Marking a practice answer, with the Kurdish explanation (same rules as submit_practice_response).
create or replace function public.submit_practice_response_v2(
  p_response_id uuid,
  p_attempt_id uuid,
  p_question_id uuid,
  p_chosen_option text,
  p_client_answered_at timestamptz default null
) returns table (
  response_id uuid,
  is_correct boolean,
  correct_option text,
  explanation text,
  received_at timestamptz,
  duplicate boolean,
  explanation_ckb text
)
language sql security definer
set search_path = ''
as $$
  select r.response_id, r.is_correct, r.correct_option, r.explanation, r.received_at, r.duplicate, q.explanation_ckb
    from public.submit_practice_response(p_response_id, p_attempt_id, p_question_id, p_chosen_option, p_client_answered_at) r
    left join public.assessment_questions q on q.id = p_question_id;
$$;
revoke all on function public.submit_practice_response_v2(uuid, uuid, uuid, text, timestamptz) from public, anon;
grant execute on function public.submit_practice_response_v2(uuid, uuid, uuid, text, timestamptz) to authenticated;
