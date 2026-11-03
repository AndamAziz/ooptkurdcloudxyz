-- Real answer options for a theory question.
--
-- Until now the app made the four options itself, by borrowing answers from other questions in
-- the same chapter. That works, but it cannot tell apart two questions that share a correct
-- answer: "What does third-party insurance cover?" and "What's covered by third-party insurance?"
-- are two questions in the official test, both answered "Damage to other vehicles", and only
-- their wrong options tell them apart.
--
-- A question may now carry its own options. Where it does, the app shows exactly those; where it
-- does not, it borrows as before, so the 670 questions written without options keep working.
alter table public.theory_questions
  add column options text[] check (options is null or (cardinality(options) between 2 and 6)),
  add column options_ckb text[],
  -- 0-based index into options; both columns are set together or neither is.
  add column correct_option smallint,
  add constraint theory_questions_options_complete check (
    (options is null and correct_option is null)
    or (options is not null and correct_option between 0 and cardinality(options) - 1)
  ),
  add constraint theory_questions_options_ckb_matches check (
    options_ckb is null or (options is not null and cardinality(options_ckb) = cardinality(options))
  );

comment on column public.theory_questions.options is
  'The four options as the official test shows them; null means the app borrows options instead.';
comment on column public.theory_questions.correct_option is
  'Index into options of the right one. The answer column still holds its text.';

-- The learner's download needs the options, so it can show the real ones. The returned row type
-- changes, which Postgres will not do in place, so the old one goes first.
drop function if exists public.get_theory_questions(text, uuid, integer);
create function public.get_theory_questions(
  p_section_slug text,
  p_after uuid default null,
  p_limit integer default 100
) returns table (
  id uuid,
  section_slug text,
  sort_order integer,
  prompt text,
  prompt_ckb text,
  answer text,
  answer_ckb text,
  options text[],
  options_ckb text[],
  correct_option smallint,
  explanation text,
  explanation_ckb text,
  media_path text,
  version integer,
  updated_at timestamptz
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
    select q.id, s.slug, q.sort_order, q.prompt, q.prompt_ckb, q.answer, q.answer_ckb,
           q.options, q.options_ckb, q.correct_option,
           q.explanation, q.explanation_ckb, q.media_path, q.version, q.updated_at
      from public.theory_questions q
      join public.theory_sections s on s.id = q.section_id
     where s.slug = p_section_slug
       and s.status = 'published'
       and q.status = 'published'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_theory_questions(text, uuid, integer) from public, anon;
grant execute on function public.get_theory_questions(text, uuid, integer) to authenticated;
