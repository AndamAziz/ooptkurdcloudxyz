-- Stricter assessment: an exam-only question bank, no repeated questions on retakes,
-- checkpoints inside each level and a two-stage placement test.
--
-- * assessment_questions.pool: 'practice' (downloaded for practice, as before) or 'exam' (only
--   ever used in level exams; never downloaded, never answerable as practice). The practice
--   download, pack versions, practice answers, the level standing (the 70% rule) and the
--   placement screening only use the practice pool.
-- * Level exams draw questions the learner has not met before (exam-only first), spread over the
--   skills, so a retake gets different questions.
-- * Checkpoints: 3 short tests per level (at 25/50/70% of the level answered) must be passed
--   before the level exam unlocks.
-- * Placement: the screening test now avoids questions from earlier placement tests, and a
--   20-question confirmation test at the level the learner passed confirms the result.
-- * Practice answers are refused for a question that is in the learner's running test.

alter table public.assessment_questions
  add column pool text not null default 'practice' check (pool in ('practice', 'exam'));
alter table public.assessment_questions
  add constraint assessment_questions_exam_pool_levels
  check (pool = 'practice' or cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'));
create index assessment_questions_pool_idx on public.assessment_questions (cefr_level, pool, status);

alter table public.exam_attempts drop constraint exam_attempts_kind_check;
alter table public.exam_attempts
  add constraint exam_attempts_kind_check check (kind in ('level_final', 'placement', 'placement_confirm', 'checkpoint'));
alter table public.exam_attempts drop constraint exam_attempts_check;
alter table public.exam_attempts
  add column checkpoint integer check (checkpoint between 1 and 3),
  add column based_on uuid references public.exam_attempts (id) on delete cascade,
  add column passed_level public.cefr_level,
  add constraint exam_attempts_level_check check ((kind = 'placement') = (cefr_level is null)),
  add constraint exam_attempts_checkpoint_kind check ((kind = 'checkpoint') = (checkpoint is not null)),
  add constraint exam_attempts_based_on_kind check ((kind = 'placement_confirm') = (based_on is not null));
create index exam_attempts_based_on_idx on public.exam_attempts (based_on) where based_on is not null;

-- The highest level each finished placement test passed (66% per level, as scored before).
do $$
declare
  v_levels constant public.cefr_level[] := array['A1', 'A2', 'B1', 'B2', 'C1', 'C2']::public.cefr_level[];
  a record;
  v_row record;
  v_passed public.cefr_level;
begin
  for a in select id, question_ids, answers from public.exam_attempts
            where kind = 'placement' and status = 'submitted' and answers is not null loop
    v_passed := null;
    for i in 1 .. 6 loop
      select s.correct, s.total into v_row
        from private.score_answers(a.question_ids, a.answers) s where s.level = v_levels[i];
      exit when v_row is null or v_row.correct * 3 < v_row.total * 2;
      v_passed := v_levels[i];
    end loop;
    update public.exam_attempts set passed_level = v_passed where id = a.id;
  end loop;
end;
$$;


create or replace function public.get_practice_questions(
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
  updated_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.is_active_user() then
    raise exception 'account suspended' using errcode = '42501';
  end if;
  if p_limit is null or p_limit < 1 or p_limit > 200 then
    raise exception 'p_limit must be between 1 and 200' using errcode = '22023';
  end if;

  return query
    select q.id, q.cefr_level, q.skill, q.prompt, q.options, q.version, q.review_status, q.updated_at
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and q.pool = 'practice'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions(public.cefr_level, uuid, integer) to authenticated;


create or replace function public.get_practice_questions_v2(
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
  media_caption text
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
           q.passage, q.media_type, q.media_path, q.media_caption
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and q.pool = 'practice'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions_v2(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions_v2(public.cefr_level, uuid, integer) to authenticated;


create or replace function public.get_practice_questions_v3(
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
  tags text[]
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
           q.passage, q.media_type, q.media_path, q.media_caption, q.tags
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and q.pool = 'practice'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions_v3(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions_v3(public.cefr_level, uuid, integer) to authenticated;


create or replace function public.submit_practice_response(
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
  duplicate boolean
)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := auth.uid();
  v_question public.assessment_questions;
  v_attempt_owner uuid;
  v_existing public.assessment_responses;
  v_chosen text := upper(btrim(coalesce(p_chosen_option, '')));
  v_client_at timestamptz := p_client_answered_at;
  v_recent integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.is_active_user() then
    raise exception 'account suspended' using errcode = '42501';
  end if;
  if p_response_id is null or p_attempt_id is null or p_question_id is null then
    raise exception 'response, attempt and question ids are required' using errcode = '22023';
  end if;
  if v_chosen !~ '^[A-F]$' then
    raise exception 'chosen option must be a single letter A-F' using errcode = '22023';
  end if;

  -- Replay of an already stored response: return the stored result.
  select * into v_existing from public.assessment_responses r where r.id = p_response_id;
  if found then
    if v_existing.user_id <> v_uid then
      raise exception 'response id belongs to another user' using errcode = '42501';
    end if;
    select q.correct_option, q.explanation into correct_option, explanation
      from public.assessment_questions q where q.id = v_existing.question_id;
    response_id := v_existing.id;
    is_correct := v_existing.is_correct;
    received_at := v_existing.received_at;
    duplicate := true;
    return next;
    return;
  end if;

  select count(*) into v_recent
    from public.assessment_responses r
   where r.user_id = v_uid and r.received_at > now() - interval '1 minute';
  if v_recent >= 120 then
    raise exception 'rate limit exceeded' using errcode = '54000';
  end if;

  select * into v_question from public.assessment_questions q where q.id = p_question_id;
  if not found or v_question.status <> 'published' or v_question.pool <> 'practice' then
    raise exception 'question not available' using errcode = '22023';
  end if;
  -- No looking up answers to a test that is running.
  if exists (select 1 from public.exam_attempts e
              where e.user_id = v_uid and e.status = 'in_progress' and e.expires_at > now()
                and p_question_id = any (e.question_ids)) then
    raise exception 'question not available' using errcode = '22023';
  end if;
  if ascii(v_chosen) - ascii('A') >= cardinality(v_question.options) then
    raise exception 'chosen option out of range' using errcode = '22023';
  end if;

  -- Client clocks are not trusted; drop timestamps more than 5 minutes in the future.
  if v_client_at is not null and v_client_at > now() + interval '5 minutes' then
    v_client_at := null;
  end if;

  insert into public.assessment_attempts (id, user_id, kind, cefr_level, client_started_at)
  values (p_attempt_id, v_uid, 'practice', v_question.cefr_level, v_client_at)
  on conflict (id) do nothing;

  select a.user_id into v_attempt_owner from public.assessment_attempts a where a.id = p_attempt_id;
  if v_attempt_owner <> v_uid then
    raise exception 'attempt belongs to another user' using errcode = '42501';
  end if;

  -- Same question already answered in this attempt under another id: first answer wins.
  select * into v_existing
    from public.assessment_responses r
   where r.attempt_id = p_attempt_id and r.question_id = p_question_id;
  if found then
    response_id := v_existing.id;
    is_correct := v_existing.is_correct;
    correct_option := v_question.correct_option;
    explanation := v_question.explanation;
    received_at := v_existing.received_at;
    duplicate := true;
    return next;
    return;
  end if;

  insert into public.assessment_responses
    (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct, client_answered_at)
  values
    (p_response_id, p_attempt_id, v_uid, p_question_id, v_question.version, v_chosen,
     v_chosen = v_question.correct_option, v_client_at)
  returning assessment_responses.received_at into received_at;

  perform private.log_event(v_uid, 'practice.response_submitted', 'assessment_response', p_response_id,
                            jsonb_build_object('attempt_id', p_attempt_id, 'cefr_level', v_question.cefr_level),
                            p_attempt_id);

  response_id := p_response_id;
  is_correct := v_chosen = v_question.correct_option;
  correct_option := v_question.correct_option;
  explanation := v_question.explanation;
  duplicate := false;
  return next;
end;
$$;
revoke all on function public.submit_practice_response(uuid, uuid, uuid, text, timestamptz) from public, anon;
grant execute on function public.submit_practice_response(uuid, uuid, uuid, text, timestamptz) to authenticated;


create or replace function private.level_standing(p_user_id uuid)
returns table (cefr_level public.cefr_level, published integer, answered integer, correct integer)
language sql stable security definer
set search_path = ''
as $$
  with latest as (
    select distinct on (r.question_id) r.question_id, r.is_correct
      from public.assessment_responses r
     where r.user_id = p_user_id
     order by r.question_id, r.received_at desc
  )
  select q.cefr_level,
         count(*)::int,
         count(l.question_id)::int,
         count(*) filter (where l.is_correct)::int
    from public.assessment_questions q
    left join latest l on l.question_id = q.id
   where q.status = 'published' and q.pool = 'practice' and q.cefr_level not in ('PRE_A1', 'LIFE_IN_UK')
   group by q.cefr_level;
$$;


create or replace function public.get_question_pack_versions()
returns table (cefr_level public.cefr_level, published integer, last_changed_at timestamptz)
language sql stable security definer
set search_path = ''
as $$
  select q.cefr_level, count(*)::int, max(q.updated_at)
    from public.assessment_questions q
   where q.status = 'published' and q.pool = 'practice' and (select private.require_learner()) is not null
   group by q.cefr_level;
$$;


create or replace function public.start_placement_test()
returns table (attempt_id uuid, expires_at timestamptz, questions jsonb)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_attempt public.exam_attempts;
  v_window record;
  v_ids uuid[];
begin
  select * into v_rules from private.exam_rules();

  -- Resume an unfinished, unexpired attempt (same questions, same deadline).
  select * into v_attempt from public.exam_attempts e
   where e.user_id = v_uid and e.kind = 'placement' and e.status = 'in_progress' and e.expires_at > now()
   order by e.started_at desc limit 1;
  if found then
    return query select v_attempt.id, v_attempt.expires_at, private.exam_questions(v_attempt.question_ids);
    return;
  end if;

  -- Close attempts that were left unfinished (past the 2-minute submit grace period).
  update public.exam_attempts e set status = 'expired'
   where e.user_id = v_uid and e.kind = 'placement' and e.status = 'in_progress'
     and e.expires_at < now() - interval '2 minutes';

  select * into v_window from private.placement_window(v_uid);
  if v_window.attempts_left <= 0 then
    raise exception 'placement test limit reached for today' using errcode = 'P0001', hint = 'retry_later',
      detail = to_char(v_window.next_attempt_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
  end if;

  select array_agg(id order by lvl, rnd) into v_ids from (
    select q.id, q.cefr_level as lvl, random() as rnd,
           -- Questions from the learner's earlier placement tests come last.
           row_number() over (partition by q.cefr_level
                              order by exists (select 1 from public.exam_attempts e
                                                where e.user_id = v_uid and e.kind in ('placement', 'placement_confirm')
                                                  and q.id = any (e.question_ids)), random()) as n
      from public.assessment_questions q
     where q.status = 'published' and q.pool = 'practice' and q.cefr_level not in ('PRE_A1', 'LIFE_IN_UK')
  ) t where n <= v_rules.placement_per_level;
  if coalesce(cardinality(v_ids), 0) < v_rules.placement_per_level * 6 then
    raise exception 'not enough questions for a placement test' using errcode = 'P0001', hint = 'not_enough_questions';
  end if;

  insert into public.exam_attempts (user_id, kind, question_ids, expires_at)
  values (v_uid, 'placement', v_ids, now() + make_interval(mins => v_rules.placement_minutes))
  returning * into v_attempt;
  perform private.log_event(v_uid, 'exam.started', 'exam_attempt', v_attempt.id, jsonb_build_object('kind', 'placement'));

  return query select v_attempt.id, v_attempt.expires_at, private.exam_questions(v_ids);
end;
$$;


create or replace function public.submit_placement_test(p_attempt_id uuid, p_answers jsonb)
returns table (score integer, total integer, passed_level public.cefr_level, recommended_level public.cefr_level)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_attempt public.exam_attempts;
  v_levels constant public.cefr_level[] := array['A1', 'A2', 'B1', 'B2', 'C1', 'C2']::public.cefr_level[];
  v_passed public.cefr_level;
  v_recommended public.cefr_level;
  v_row record;
  v_score int;
  v_total int;
  i int;
begin
  if p_answers is null or jsonb_typeof(p_answers) <> 'object' then
    raise exception 'answers must be a JSON object' using errcode = '22023';
  end if;
  select * into v_attempt from public.exam_attempts e
   where e.id = p_attempt_id and e.kind = 'placement' for update;
  if not found or v_attempt.user_id <> v_uid then
    raise exception 'placement attempt not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  if v_attempt.status = 'in_progress' then
    select s.correct, s.total into v_score, v_total
      from private.score_answers(v_attempt.question_ids, p_answers) s where s.level is null;
    for i in 1 .. 6 loop
      select s.correct, s.total into v_row
        from private.score_answers(v_attempt.question_ids, p_answers) s where s.level = v_levels[i];
      exit when v_row is null or v_row.correct * 3 < v_row.total * 2;
      v_passed := v_levels[i];
    end loop;
    v_recommended := case when v_passed is null then 'A1'
                          when v_passed = 'C2' then 'C2'
                          else v_levels[array_position(v_levels, v_passed) + 1] end;
    update public.exam_attempts
       set status = 'submitted', submitted_at = now(), answers = p_answers, score = v_score, total = v_total,
           passed = v_passed is not null, result_level = v_recommended, passed_level = v_passed
     where id = v_attempt.id
     returning * into v_attempt;
    update public.profiles set placement_level = v_recommended, placed_at = now() where user_id = v_uid;
    perform private.log_event(v_uid, 'exam.submitted', 'exam_attempt', v_attempt.id,
                              jsonb_build_object('kind', 'placement', 'score', v_score, 'total', v_total,
                                                 'recommended', v_recommended));
    return query select v_score, v_total, v_passed, v_recommended;
    return;
  end if;

  -- Replay: recompute the passed level from the stored answers.
  for i in 1 .. 6 loop
    select s.correct, s.total into v_row
      from private.score_answers(v_attempt.question_ids, v_attempt.answers) s where s.level = v_levels[i];
    exit when v_row is null or v_row.correct * 3 < v_row.total * 2;
    v_passed := v_levels[i];
  end loop;
  return query select v_attempt.score, v_attempt.total, v_passed, v_attempt.result_level;
end;
$$;


create or replace function public.start_level_exam(p_cefr_level public.cefr_level)
returns table (attempt_id uuid, cefr_level public.cefr_level, expires_at timestamptz, questions jsonb)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_overview record;
  v_attempt public.exam_attempts;
  v_ids uuid[];
  v_cert_status text;
begin
  select * into v_rules from private.exam_rules();

  select c.status into v_cert_status from public.certificates c
   where c.user_id = v_uid and c.cefr_level = p_cefr_level and c.revoked_at is null;
  if v_cert_status = 'approved' then
    raise exception 'certificate already issued for this level' using errcode = 'P0001', hint = 'already_certified';
  elsif v_cert_status = 'pending' then
    raise exception 'certificate is waiting for approval' using errcode = 'P0001', hint = 'pending_approval';
  end if;

  -- Resume an unfinished, unexpired attempt (same questions, same deadline).
  select * into v_attempt from public.exam_attempts e
   where e.user_id = v_uid and e.kind = 'level_final' and e.cefr_level = p_cefr_level
     and e.status = 'in_progress'
   order by e.started_at desc limit 1;
  if found then
    if v_attempt.expires_at > now() then
      return query select v_attempt.id, v_attempt.cefr_level, v_attempt.expires_at, private.exam_questions(v_attempt.question_ids);
      return;
    end if;
    update public.exam_attempts set status = 'expired', passed = false where id = v_attempt.id;
  end if;

  select * into v_overview from public.get_learning_overview_v2() o where o.cefr_level = p_cefr_level;
  if not found or not v_overview.exam_unlocked then
    raise exception 'practise more before the exam' using errcode = 'P0001', hint = 'not_unlocked';
  end if;
  if v_overview.exam_available_at is not null and v_overview.exam_available_at > now() then
    raise exception 'next attempt not yet available' using errcode = 'P0001', hint = 'retry_later';
  end if;

  v_ids := private.pick_questions(v_uid, p_cefr_level, v_rules.exam_questions, 'final');
  if cardinality(v_ids) < v_rules.exam_questions then
    raise exception 'not enough questions for an exam at this level' using errcode = 'P0001', hint = 'not_enough_questions';
  end if;

  insert into public.exam_attempts (user_id, kind, cefr_level, question_ids, expires_at)
  values (v_uid, 'level_final', p_cefr_level, v_ids, now() + make_interval(mins => v_rules.exam_minutes))
  returning * into v_attempt;

  perform private.log_event(v_uid, 'exam.started', 'exam_attempt', v_attempt.id,
                            jsonb_build_object('kind', 'level_final', 'cefr_level', p_cefr_level));

  return query select v_attempt.id, v_attempt.cefr_level, v_attempt.expires_at, private.exam_questions(v_ids);
end;
$$;


-- ---------------------------------------------------------------------------
-- Questions chosen for a test: new to the learner first, and spread over the skills.
--   'final'      (level exam): exam-only questions not in the learner's earlier exams, then
--                practice questions the learner has never answered nor met in a test, then the
--                rest (oldest-exposure tiers last). A retake therefore gets different questions.
--   'checkpoint' / 'confirm': practice questions only, never answered and never met in a test
--                first, then ones not met in a test, then the rest.
-- Within a tier the skills take turns (grammar, vocabulary, reading, listening), then random.
-- ---------------------------------------------------------------------------
create or replace function private.pick_questions(p_uid uuid, p_level public.cefr_level, p_count integer, p_purpose text)
returns uuid[]
language sql volatile security definer
set search_path = ''
as $$
  with met as (
    select distinct unnest(e.question_ids) as id from public.exam_attempts e where e.user_id = p_uid
  ), answered as (
    select distinct r.question_id as id from public.assessment_responses r where r.user_id = p_uid
  ), ranked as (
    select q.id, q.skill,
           case
             when q.pool = 'exam' and m.id is null then 0
             when q.pool = 'practice' and a.id is null and m.id is null then 1
             when q.pool = 'exam' then 2
             when q.pool = 'practice' and m.id is null then 3
             else 4
           end as tier
      from public.assessment_questions q
      left join met m on m.id = q.id
      left join answered a on a.id = q.id
     where q.cefr_level = p_level and q.status = 'published'
       and (q.pool = 'practice' or p_purpose = 'final')
  ), ordered as (
    select id, tier, row_number() over (partition by tier, skill order by random()) as rn from ranked
  ), chosen as (
    select id from ordered order by tier, rn, random() limit p_count
  )
  select coalesce(array_agg(id order by random()), '{}') from chosen;
$$;
revoke all on function private.pick_questions(uuid, public.cefr_level, integer, text) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Checkpoints: three short tests inside each level, unlocked at 25%, 50% and 70% of the level's
-- practice questions answered. 15 questions in 15 minutes, pass with 70%; after a fail the next
-- try opens 2 hours later. All three must be passed before the level exam unlocks.
-- ---------------------------------------------------------------------------
create or replace function private.checkpoint_rules()
returns table (questions integer, minutes integer, pass_percent integer, retry_hours integer, coverage integer[])
language sql immutable
set search_path = ''
as $$ select 15, 15, 70, 2, array[25, 50, 70] $$;
revoke all on function private.checkpoint_rules() from public, anon, authenticated;

create or replace function private.checkpoint_standing(p_uid uuid)
returns table (cefr_level public.cefr_level, checkpoint integer, required integer, unlocked boolean, passed boolean,
               last_score integer, last_total integer, available_at timestamptz, in_progress uuid)
language sql stable security definer
set search_path = ''
as $$
  with r as (select * from private.checkpoint_rules()),
  standing as (select * from private.level_standing(p_uid)),
  cp as (
    select s.cefr_level, k.n as checkpoint, ceil(s.published * r.coverage[k.n] / 100.0)::int as required, s.answered,
           exists (select 1 from public.exam_attempts e
                    where e.user_id = p_uid and e.kind = 'checkpoint' and e.cefr_level = s.cefr_level
                      and e.checkpoint = k.n and e.passed) as passed
      from standing s cross join r cross join generate_series(1, 3) as k(n)
  ),
  last_try as (
    select distinct on (e.cefr_level, e.checkpoint) e.cefr_level, e.checkpoint, e.status, e.score, e.total, e.passed,
           e.submitted_at, e.expires_at, e.id
      from public.exam_attempts e
     where e.user_id = p_uid and e.kind = 'checkpoint'
     order by e.cefr_level, e.checkpoint, e.started_at desc
  )
  select cp.cefr_level, cp.checkpoint, cp.required,
         cp.answered >= cp.required
           and (cp.checkpoint = 1 or coalesce((select p.passed from cp p
                                                where p.cefr_level = cp.cefr_level and p.checkpoint = cp.checkpoint - 1), false)),
         cp.passed,
         lt.score, lt.total,
         case when not cp.passed and (lt.status in ('submitted', 'expired') or lt.expires_at <= now())
              then coalesce(lt.submitted_at, lt.expires_at) + make_interval(hours => (select retry_hours from r)) end,
         case when lt.status = 'in_progress' and lt.expires_at > now() then lt.id end
    from cp
    left join last_try lt on lt.cefr_level = cp.cefr_level and lt.checkpoint = cp.checkpoint
   order by cp.cefr_level, cp.checkpoint;
$$;
revoke all on function private.checkpoint_standing(uuid) from public, anon, authenticated;

create or replace function public.get_checkpoints()
returns table (cefr_level public.cefr_level, checkpoint integer, required integer, unlocked boolean, passed boolean,
               last_score integer, last_total integer, available_at timestamptz, in_progress uuid)
language sql stable security definer
set search_path = ''
as $$ select * from private.checkpoint_standing((select private.require_learner())); $$;
revoke all on function public.get_checkpoints() from public, anon;
grant execute on function public.get_checkpoints() to authenticated;

create or replace function public.start_checkpoint(p_cefr_level public.cefr_level, p_checkpoint integer)
returns table (attempt_id uuid, cefr_level public.cefr_level, checkpoint integer, expires_at timestamptz, questions jsonb)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_cp record;
  v_attempt public.exam_attempts;
  v_ids uuid[];
begin
  select * into v_rules from private.checkpoint_rules();

  select * into v_attempt from public.exam_attempts e
   where e.user_id = v_uid and e.kind = 'checkpoint' and e.cefr_level = p_cefr_level and e.checkpoint = p_checkpoint
     and e.status = 'in_progress'
   order by e.started_at desc limit 1;
  if found then
    if v_attempt.expires_at > now() then
      return query select v_attempt.id, v_attempt.cefr_level, v_attempt.checkpoint, v_attempt.expires_at,
                          private.exam_questions(v_attempt.question_ids);
      return;
    end if;
    update public.exam_attempts set status = 'expired', passed = false where id = v_attempt.id;
  end if;

  select * into v_cp from private.checkpoint_standing(v_uid) s
   where s.cefr_level = p_cefr_level and s.checkpoint = p_checkpoint;
  if not found or not v_cp.unlocked then
    raise exception 'practise more before this checkpoint' using errcode = 'P0001', hint = 'not_unlocked';
  end if;
  if v_cp.passed then
    raise exception 'checkpoint already passed' using errcode = 'P0001', hint = 'already_passed';
  end if;
  if v_cp.available_at is not null and v_cp.available_at > now() then
    raise exception 'next attempt not yet available' using errcode = 'P0001', hint = 'retry_later';
  end if;

  v_ids := private.pick_questions(v_uid, p_cefr_level, v_rules.questions, 'checkpoint');
  if cardinality(v_ids) < v_rules.questions then
    raise exception 'not enough questions for a checkpoint' using errcode = 'P0001', hint = 'not_enough_questions';
  end if;

  insert into public.exam_attempts (user_id, kind, cefr_level, checkpoint, question_ids, expires_at)
  values (v_uid, 'checkpoint', p_cefr_level, p_checkpoint, v_ids, now() + make_interval(mins => v_rules.minutes))
  returning * into v_attempt;
  perform private.log_event(v_uid, 'exam.started', 'exam_attempt', v_attempt.id,
                            jsonb_build_object('kind', 'checkpoint', 'cefr_level', p_cefr_level, 'checkpoint', p_checkpoint));

  return query select v_attempt.id, v_attempt.cefr_level, v_attempt.checkpoint, v_attempt.expires_at, private.exam_questions(v_ids);
end;
$$;
revoke all on function public.start_checkpoint(public.cefr_level, integer) from public, anon;
grant execute on function public.start_checkpoint(public.cefr_level, integer) to authenticated;

create or replace function public.submit_checkpoint(p_attempt_id uuid, p_answers jsonb)
returns table (score integer, total integer, passed boolean, pass_percent integer, next_attempt_at timestamptz, expired boolean)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_attempt public.exam_attempts;
  v_score int;
  v_total int;
  v_late boolean;
begin
  select * into v_rules from private.checkpoint_rules();
  if p_answers is null or jsonb_typeof(p_answers) <> 'object' then
    raise exception 'answers must be a JSON object' using errcode = '22023';
  end if;
  select * into v_attempt from public.exam_attempts e
   where e.id = p_attempt_id and e.kind = 'checkpoint' for update;
  if not found or v_attempt.user_id <> v_uid then
    raise exception 'checkpoint attempt not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  if v_attempt.status = 'in_progress' then
    v_late := now() > v_attempt.expires_at + interval '2 minutes';
    select s.correct, s.total into v_score, v_total
      from private.score_answers(v_attempt.question_ids, p_answers) s where s.level is null;
    update public.exam_attempts
       set status = case when v_late then 'expired' else 'submitted' end,
           submitted_at = now(), answers = p_answers, score = v_score, total = v_total,
           passed = not v_late and v_score * 100 >= v_rules.pass_percent * v_total
     where id = v_attempt.id
     returning * into v_attempt;
    perform private.log_event(v_uid, 'exam.submitted', 'exam_attempt', v_attempt.id,
                              jsonb_build_object('kind', 'checkpoint', 'cefr_level', v_attempt.cefr_level,
                                                 'checkpoint', v_attempt.checkpoint, 'score', v_score, 'total', v_total,
                                                 'passed', v_attempt.passed, 'late', v_late));
  end if;

  return query select v_attempt.score, v_attempt.total, coalesce(v_attempt.passed, false), v_rules.pass_percent,
    case when not coalesce(v_attempt.passed, false)
         then v_attempt.submitted_at + make_interval(hours => v_rules.retry_hours) end,
    v_attempt.status = 'expired';
end;
$$;
revoke all on function public.submit_checkpoint(uuid, jsonb) from public, anon;
grant execute on function public.submit_checkpoint(uuid, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- Placement, stage 2: a 20-question confirmation test at the highest level the screening test
-- says the learner has passed. 70% or more confirms it (study the next level); less means the
-- learner should study that level. One confirmation per placement test (a second try only if the
-- first was left unfinished).
-- ---------------------------------------------------------------------------
create or replace function private.confirm_rules()
returns table (questions integer, minutes integer, pass_percent integer)
language sql immutable
set search_path = ''
as $$ select 20, 20, 70 $$;
revoke all on function private.confirm_rules() from public, anon, authenticated;

-- The latest finished screening test and, if it still needs one, the level to confirm.
create or replace function private.placement_to_confirm(p_uid uuid)
returns table (placement_id uuid, level public.cefr_level, confirmed_attempts integer, started_attempts integer)
language sql stable security definer
set search_path = ''
as $$
  select p.id, p.passed_level,
         (select count(*)::int from public.exam_attempts c where c.based_on = p.id and c.status = 'submitted'),
         (select count(*)::int from public.exam_attempts c where c.based_on = p.id)
    from public.exam_attempts p
   where p.user_id = p_uid and p.kind = 'placement' and p.status = 'submitted'
   order by p.submitted_at desc limit 1;
$$;
revoke all on function private.placement_to_confirm(uuid) from public, anon, authenticated;

create or replace function public.start_placement_confirmation()
returns table (attempt_id uuid, cefr_level public.cefr_level, expires_at timestamptz, questions jsonb)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_p record;
  v_attempt public.exam_attempts;
  v_ids uuid[];
begin
  select * into v_rules from private.confirm_rules();
  select * into v_p from private.placement_to_confirm(v_uid);
  if not found or v_p.level is null or v_p.confirmed_attempts > 0 then
    raise exception 'nothing to confirm' using errcode = 'P0001', hint = 'nothing_to_confirm';
  end if;

  select * into v_attempt from public.exam_attempts e
   where e.based_on = v_p.placement_id and e.status = 'in_progress' and e.expires_at > now()
   order by e.started_at desc limit 1;
  if found then
    return query select v_attempt.id, v_attempt.cefr_level, v_attempt.expires_at, private.exam_questions(v_attempt.question_ids);
    return;
  end if;
  if v_p.started_attempts >= 2 then
    raise exception 'take a new placement test' using errcode = 'P0001', hint = 'retry_later';
  end if;
  update public.exam_attempts e set status = 'expired'
   where e.based_on = v_p.placement_id and e.status = 'in_progress';

  v_ids := private.pick_questions(v_uid, v_p.level, v_rules.questions, 'confirm');
  if cardinality(v_ids) < v_rules.questions then
    raise exception 'not enough questions for a confirmation test' using errcode = 'P0001', hint = 'not_enough_questions';
  end if;

  insert into public.exam_attempts (user_id, kind, cefr_level, based_on, question_ids, expires_at)
  values (v_uid, 'placement_confirm', v_p.level, v_p.placement_id, v_ids, now() + make_interval(mins => v_rules.minutes))
  returning * into v_attempt;
  perform private.log_event(v_uid, 'exam.started', 'exam_attempt', v_attempt.id,
                            jsonb_build_object('kind', 'placement_confirm', 'cefr_level', v_p.level));
  return query select v_attempt.id, v_attempt.cefr_level, v_attempt.expires_at, private.exam_questions(v_ids);
end;
$$;
revoke all on function public.start_placement_confirmation() from public, anon;
grant execute on function public.start_placement_confirmation() to authenticated;

create or replace function public.submit_placement_confirmation(p_attempt_id uuid, p_answers jsonb)
returns table (score integer, total integer, confirmed boolean, tested_level public.cefr_level, recommended_level public.cefr_level)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_attempt public.exam_attempts;
  v_levels constant public.cefr_level[] := array['A1', 'A2', 'B1', 'B2', 'C1', 'C2']::public.cefr_level[];
  v_score int;
  v_total int;
  v_ok boolean;
  v_recommended public.cefr_level;
begin
  select * into v_rules from private.confirm_rules();
  if p_answers is null or jsonb_typeof(p_answers) <> 'object' then
    raise exception 'answers must be a JSON object' using errcode = '22023';
  end if;
  select * into v_attempt from public.exam_attempts e
   where e.id = p_attempt_id and e.kind = 'placement_confirm' for update;
  if not found or v_attempt.user_id <> v_uid then
    raise exception 'confirmation attempt not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  if v_attempt.status = 'in_progress' then
    select s.correct, s.total into v_score, v_total
      from private.score_answers(v_attempt.question_ids, p_answers) s where s.level is null;
    v_ok := now() <= v_attempt.expires_at + interval '2 minutes' and v_score * 100 >= v_rules.pass_percent * v_total;
    v_recommended := case when not v_ok then v_attempt.cefr_level
                          when v_attempt.cefr_level = 'C2' then 'C2'
                          else v_levels[array_position(v_levels, v_attempt.cefr_level) + 1] end;
    update public.exam_attempts
       set status = 'submitted', submitted_at = now(), answers = p_answers, score = v_score, total = v_total,
           passed = v_ok, result_level = v_recommended
     where id = v_attempt.id
     returning * into v_attempt;
    update public.profiles set placement_level = v_recommended, placed_at = now() where user_id = v_uid;
    perform private.log_event(v_uid, 'exam.submitted', 'exam_attempt', v_attempt.id,
                              jsonb_build_object('kind', 'placement_confirm', 'cefr_level', v_attempt.cefr_level,
                                                 'score', v_score, 'total', v_total, 'confirmed', v_ok));
  end if;
  return query select v_attempt.score, v_attempt.total, coalesce(v_attempt.passed, false), v_attempt.cefr_level, v_attempt.result_level;
end;
$$;
revoke all on function public.submit_placement_confirmation(uuid, jsonb) from public, anon;
grant execute on function public.submit_placement_confirmation(uuid, jsonb) to authenticated;

-- get_placement_status_v2 adds the confirmation step (v1 stays for apps up to 0.14).
create or replace function public.get_placement_status_v2()
returns table (attempts_left integer, next_attempt_at timestamptz, in_progress boolean,
               confirm_level public.cefr_level, confirm_in_progress boolean, confirmed boolean)
language sql stable security definer
set search_path = ''
as $$
  with me as (select private.require_learner() as uid),
  p as (select c.* from me, private.placement_to_confirm(me.uid) c)
  select w.attempts_left, w.next_attempt_at,
         exists (select 1 from public.exam_attempts e, me
                  where e.user_id = me.uid and e.kind = 'placement' and e.status = 'in_progress' and e.expires_at > now()),
         (select p.level from p where p.confirmed_attempts = 0 and p.started_attempts < 2
                                  or exists (select 1 from public.exam_attempts e
                                              where e.based_on = p.placement_id and e.status = 'in_progress'
                                                and e.expires_at > now())),
         exists (select 1 from public.exam_attempts e, p
                  where e.based_on = p.placement_id and e.status = 'in_progress' and e.expires_at > now()),
         coalesce((select e.passed from public.exam_attempts e, p
                    where e.based_on = p.placement_id and e.status = 'submitted' limit 1), false)
    from me, private.placement_window(me.uid) w;
$$;
revoke all on function public.get_placement_status_v2() from public, anon;
grant execute on function public.get_placement_status_v2() to authenticated;


create or replace function public.get_learning_overview_v2()
returns table (
  cefr_level        public.cefr_level,
  published         integer,
  answered          integer,
  correct           integer,
  required          integer,
  required_percent  integer,
  exam_unlocked     boolean,
  exam_available_at timestamptz,
  exam_in_progress  uuid,
  last_exam_score   integer,
  last_exam_total   integer,
  certificate_code  text,
  certificate_issued_at timestamptz,
  certificate_status text
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
begin
  select * into v_rules from private.exam_rules();
  return query
  with standing as (select * from private.level_standing(v_uid)),
  last_exam as (
    select distinct on (e.cefr_level) e.cefr_level, e.score, e.total, e.passed, e.submitted_at, e.status, e.id, e.expires_at
      from public.exam_attempts e
     where e.user_id = v_uid and e.kind = 'level_final'
     order by e.cefr_level, e.started_at desc
  )
  select s.cefr_level,
         s.published,
         s.answered,
         s.correct,
         ceil(s.published * v_rules.practice_required / 100.0)::int,
         v_rules.practice_percent,
         s.answered >= ceil(s.published * v_rules.practice_required / 100.0)::int
           and s.answered > 0
           and s.correct * 100 >= v_rules.practice_percent * s.answered
           and coalesce((select bool_and(cp.passed) from private.checkpoint_standing(v_uid) cp
                          where cp.cefr_level = s.cefr_level), false),
         case when (le.status in ('submitted', 'expired') or le.expires_at <= now())
                   and not coalesce(le.passed, false)
              then coalesce(le.submitted_at, le.expires_at) + make_interval(hours => v_rules.retry_hours) end,
         case when le.status = 'in_progress' and le.expires_at > now() then le.id end,
         le.score,
         le.total,
         -- The code is only revealed once a teacher has approved the certificate.
         case when c.status = 'approved' then c.verification_code end,
         c.issued_at,
         c.status
    from standing s
    left join last_exam le on le.cefr_level = s.cefr_level
    left join public.certificates c on c.user_id = v_uid and c.cefr_level = s.cefr_level
                                   and c.revoked_at is null and c.status in ('pending', 'approved')
   order by s.cefr_level;
end;
$$;
revoke all on function public.get_learning_overview_v2() from public, anon;
grant execute on function public.get_learning_overview_v2() to authenticated;
