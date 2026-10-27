-- UK driving theory test: a second course beside English practice and Life in the UK.
--
-- These questions carry only the correct answer, not a set of options, because learners study them
-- to memorise the question and its answer. The app offers two modes over the same rows:
--
--   * study  - a card shows the question, the learner thinks, then reveals the answer;
--   * test   - the app builds four options by borrowing answers from other questions in the same
--              section, so it feels like the real multiple-choice exam.
--
-- Scoring is therefore the app's: it sends whether the learner got it right, and the server stores
-- it against the question's version. Sections are ordered and can exist with no questions yet
-- (shown as "coming soon"), so the app lists the same chapters as the official test from day one.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.theory_sections (
  id         uuid primary key default gen_random_uuid(),
  slug       text not null unique check (slug ~ '^[a-z0-9-]{2,60}$'),
  sort_order   integer not null check (sort_order between 1 and 999),
  name_en    text not null check (char_length(name_en) between 1 and 120),
  name_ckb   text check (char_length(name_ckb) <= 120),
  status     text not null default 'published' check (status in ('draft', 'published')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
comment on table public.theory_sections is
  'Chapters of the driving theory test (Alertness, Attitude, ...). A section with no published question is listed as empty.';
create unique index theory_sections_sort_order_idx on public.theory_sections (sort_order);

create trigger theory_sections_touch_updated_at
  before update on public.theory_sections
  for each row execute function private.touch_updated_at();

create table public.theory_questions (
  id           uuid primary key default gen_random_uuid(),
  external_ref text unique,
  section_id   uuid not null references public.theory_sections (id) on delete restrict,
  sort_order     integer not null default 1 check (sort_order between 1 and 9999),
  prompt       text not null check (char_length(prompt) between 1 and 2000),
  prompt_ckb   text check (char_length(prompt_ckb) <= 3000),
  answer       text not null check (char_length(answer) between 1 and 1000),
  answer_ckb   text check (char_length(answer_ckb) <= 1500),
  explanation  text check (char_length(explanation) <= 2000),
  explanation_ckb text check (char_length(explanation_ckb) <= 3000),
  media_path   text check (char_length(media_path) <= 400),
  version      integer not null default 1 check (version >= 1),
  status       text not null default 'draft' check (status in ('draft', 'published', 'retired')),
  source       text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
comment on table public.theory_questions is
  'Driving theory questions with the correct answer only; the app builds test options from other answers in the same section.';
comment on column public.theory_questions.media_path is
  'Object name in the question-media bucket, for the sign and junction pictures some questions need.';
comment on column public.theory_questions.version is
  'Bump when the prompt or the answer changes, so stored responses keep the version they were scored against.';
create index theory_questions_section_idx on public.theory_questions (section_id, status, sort_order, id);

create trigger theory_questions_touch_updated_at
  before update on public.theory_questions
  for each row execute function private.touch_updated_at();

create table public.theory_responses (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  question_id      uuid not null references public.theory_questions (id) on delete cascade,
  question_version integer not null check (question_version >= 1),
  is_correct       boolean not null,
  mode             text not null check (mode in ('study', 'test')),
  received_at      timestamptz not null default clock_timestamp()
);
comment on table public.theory_responses is
  'One row per answered theory question; the latest row per question is what the section progress counts.';
create index theory_responses_user_idx on public.theory_responses (user_id, question_id, received_at desc);

-- ---------------------------------------------------------------------------
-- Row level security: learners never read these tables directly, staff manage them.
-- ---------------------------------------------------------------------------

alter table public.theory_sections  enable row level security;
alter table public.theory_questions enable row level security;
alter table public.theory_responses enable row level security;

revoke all on public.theory_sections, public.theory_questions, public.theory_responses
  from anon, authenticated;
grant select on public.theory_sections, public.theory_questions, public.theory_responses
  to authenticated;

create policy theory_sections_staff_read on public.theory_sections
  for select to authenticated using (private.has_role('teacher') or private.has_role('admin'));
create policy theory_questions_staff_read on public.theory_questions
  for select to authenticated using (private.has_role('teacher') or private.has_role('admin'));
create policy theory_responses_own_read on public.theory_responses
  for select to authenticated using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Learner functions
-- ---------------------------------------------------------------------------

-- The chapter list with the caller's standing, so the app can show "26 of 26 answered, 100%".
create function public.get_theory_sections()
returns table (
  id uuid,
  slug text,
  sort_order integer,
  name_en text,
  name_ckb text,
  published integer,
  answered integer,
  correct integer,
  updated_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
begin
  return query
    with latest as (
      select distinct on (r.question_id) r.question_id, r.is_correct
        from public.theory_responses r
       where r.user_id = v_uid
       order by r.question_id, r.received_at desc
    )
    select s.id, s.slug, s.sort_order, s.name_en, s.name_ckb,
           count(q.id)::int,
           count(l.question_id)::int,
           count(*) filter (where l.is_correct)::int,
           greatest(s.updated_at, coalesce(max(q.updated_at), s.updated_at))
      from public.theory_sections s
      left join public.theory_questions q
             on q.section_id = s.id and q.status = 'published'
      left join latest l on l.question_id = q.id
     where s.status = 'published'
     group by s.id
     order by s.sort_order;
end;
$$;
revoke all on function public.get_theory_sections() from public, anon;
grant execute on function public.get_theory_sections() to authenticated;

-- A section's questions, for the app to keep offline. Paged like the practice download.
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

-- The app scores the answer (it knows which option it offered), so it reports the outcome.
create function public.submit_theory_response(
  p_question_id uuid,
  p_is_correct boolean,
  p_mode text
) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_learner();
  v_version integer;
begin
  if p_is_correct is null then
    raise exception 'p_is_correct is required' using errcode = '22023';
  end if;
  if p_mode is null or p_mode not in ('study', 'test') then
    raise exception 'p_mode must be study or test' using errcode = '22023';
  end if;

  select q.version into v_version
    from public.theory_questions q
    join public.theory_sections s on s.id = q.section_id
   where q.id = p_question_id and q.status = 'published' and s.status = 'published';
  if not found then
    raise exception 'question not available' using errcode = '22023';
  end if;

  insert into public.theory_responses (user_id, question_id, question_version, is_correct, mode)
  values (v_uid, p_question_id, v_version, p_is_correct, p_mode);
end;
$$;
revoke all on function public.submit_theory_response(uuid, boolean, text) from public, anon;
grant execute on function public.submit_theory_response(uuid, boolean, text) to authenticated;

-- Lets a learner start a section again from scratch.
create function public.reset_theory_section(p_section_slug text)
returns integer
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_learner();
  v_deleted integer;
begin
  delete from public.theory_responses r
   using public.theory_questions q, public.theory_sections s
   where r.user_id = v_uid
     and r.question_id = q.id
     and q.section_id = s.id
     and s.slug = p_section_slug;
  get diagnostics v_deleted = row_count;
  return v_deleted;
end;
$$;
revoke all on function public.reset_theory_section(text) from public, anon;
grant execute on function public.reset_theory_section(text) to authenticated;

-- ---------------------------------------------------------------------------
-- Staff functions: author and publish theory questions from the app.
-- ---------------------------------------------------------------------------

create function public.staff_upsert_theory_question(
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
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_section uuid;
  v_id uuid;
begin
  select id into v_section from public.theory_sections where slug = p_section_slug;
  if not found then
    raise exception 'unknown section' using errcode = '22023';
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
           -- Only a changed question or answer invalidates what learners were scored against.
           version      = q.version + case when q.prompt is distinct from btrim(p_prompt)
                                             or q.answer is distinct from btrim(p_answer)
                                        then 1 else 0 end
     where q.id = p_id
     returning q.id into v_id;
    if v_id is null then
      raise exception 'unknown question' using errcode = '22023';
    end if;
  end if;

  perform private.log_event(v_uid, 'theory.question_upsert', 'theory_question', v_id,
                            jsonb_build_object('section', p_section_slug));
  return v_id;
end;
$$;
revoke all on function public.staff_upsert_theory_question(text, text, text, uuid, text, text, text, text, text, boolean) from public, anon;
grant execute on function public.staff_upsert_theory_question(text, text, text, uuid, text, text, text, text, text, boolean) to authenticated;

create function public.staff_publish_theory_drafts(p_section_slug text)
returns integer
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_count integer;
begin
  update public.theory_questions q
     set status = 'published'
    from public.theory_sections s
   where q.section_id = s.id and s.slug = p_section_slug and q.status = 'draft';
  get diagnostics v_count = row_count;
  perform private.log_event(v_uid, 'theory.drafts_published', null, null,
                            jsonb_build_object('section', p_section_slug, 'count', v_count));
  return v_count;
end;
$$;
revoke all on function public.staff_publish_theory_drafts(text) from public, anon;
grant execute on function public.staff_publish_theory_drafts(text) to authenticated;

-- ---------------------------------------------------------------------------
-- The chapters of the test. The five without questions yet are listed as empty.
-- ---------------------------------------------------------------------------

insert into public.theory_sections (slug, sort_order, name_en, name_ckb) values
  ('alertness',             1,  'Alertness',                        'ئاگاداری'),
  ('attitude',              2,  'Attitude',                         'هەڵسوکەوت'),
  ('safety-and-vehicle',    3,  'Safety and your vehicle',          'سەلامەتی و ئۆتۆمبێل'),
  ('safety-margins',        4,  'Safety margins',                   'مەودای سەلامەتی'),
  ('hazard-awareness',      5,  'Hazard awareness',                 'ئاگاداری لە مەترسی'),
  ('vulnerable-road-users', 6,  'Vulnerable road users',            'بەکارهێنەرانی لاوازی ڕێگا'),
  ('other-vehicles',        7,  'Other types of vehicle',           'جۆرەکانی تری ئۆتۆمبێل'),
  ('vehicle-handling',      8,  'Vehicle handling',                 'بەڕێوەبردنی ئۆتۆمبێل'),
  ('motorway-rules',        9,  'Motorway rules',                   'یاساکانی ڕێگای خێرا'),
  ('rules-of-the-road',     10, 'Rules of the road',                'یاساکانی ڕێگا'),
  ('road-and-traffic-signs',11, 'Road and traffic signs',           'تابلۆ و نیشانەکانی ڕێگا'),
  ('essential-documents',   12, 'Essential documents',              'بەڵگەنامە پێویستەکان'),
  ('incidents',             13, 'Incidents, accidents and emergencies', 'ڕووداو و دۆخی لەناکاو'),
  ('vehicle-loading',       14, 'Vehicle loading',                  'بارکردنی ئۆتۆمبێل'),
  ('video-clips',           15, 'Video clips',                      'ڤیدیۆکان');
