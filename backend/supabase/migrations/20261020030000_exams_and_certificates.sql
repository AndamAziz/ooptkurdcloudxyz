-- Level exams, certificates, placement test and the learner overview.
--
-- * A learner unlocks a level's final exam by practising: at least
--   least(30, published questions in the level) distinct questions answered, with the latest
--   answer to each correct for >= 70% of them.
-- * The final exam is 20 random published questions picked by the server, 30 minutes, scored
--   on the server; >= 80% passes and issues a certificate. After a failed attempt the next one
--   opens 24 hours later. One certificate per user and level.
-- * Certificates carry a random verification code. verify_certificate() is callable without
--   signing in (it is what the QR code on the PDF opens) and returns only what the
--   certificate itself shows.
-- * The placement test (3 questions per level, A1-C2, 20 minutes) recommends a starting level.
--
-- These are achievement certificates of this app, not official CEFR certifications.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
create table public.exam_attempts (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users (id) on delete cascade,
  kind          text not null check (kind in ('level_final', 'placement')),
  cefr_level    public.cefr_level,
  question_ids  uuid[] not null check (cardinality(question_ids) between 1 and 60),
  status        text not null default 'in_progress' check (status in ('in_progress', 'submitted', 'expired')),
  started_at    timestamptz not null default now(),
  expires_at    timestamptz not null,
  submitted_at  timestamptz,
  answers       jsonb,
  score         integer check (score >= 0),
  total         integer check (total > 0),
  passed        boolean,
  result_level  public.cefr_level,
  check ((kind = 'level_final') = (cefr_level is not null))
);
comment on table public.exam_attempts is
  'Server-run exams. Questions are chosen and scored by the server; rows are written only by SECURITY DEFINER RPCs.';
create index exam_attempts_user_idx on public.exam_attempts (user_id, kind, cefr_level, started_at desc);

create table public.certificates (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users (id) on delete cascade,
  cefr_level        public.cefr_level not null,
  exam_attempt_id   uuid not null unique references public.exam_attempts (id) on delete restrict,
  holder_name       text not null check (char_length(holder_name) between 1 and 80),
  score             integer not null,
  total             integer not null,
  verification_code text not null unique check (verification_code ~ '^EM-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$'),
  issued_at         timestamptz not null default now(),
  revoked_at        timestamptz,
  unique (user_id, cefr_level)
);
comment on table public.certificates is
  'Level achievement certificates. holder_name is a snapshot of the profile name at issue time.';

alter table public.profiles
  add column placement_level public.cefr_level,
  add column placed_at timestamptz;

alter table public.exam_attempts enable row level security;
alter table public.certificates enable row level security;

revoke all on public.exam_attempts, public.certificates from anon, authenticated;
grant select on public.exam_attempts, public.certificates to authenticated;

create policy "exam attempts: owner or admin read" on public.exam_attempts
  for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')));
create policy "certificates: owner or staff read" on public.certificates
  for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_role('admin')) or (select private.has_role('teacher')));

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
create function private.require_learner()
returns uuid
language plpgsql stable security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.is_active_user() then
    raise exception 'account suspended' using errcode = '42501';
  end if;
  return v_uid;
end;
$$;

-- 60 random bits from gen_random_uuid(), shown as EM-XXXX-XXXX-XXXX (no 0/O/1/I).
create function private.new_verification_code()
returns text
language plpgsql volatile
set search_path = ''
as $$
declare
  v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_bytes bytea := uuid_send(gen_random_uuid());
  v_positions constant int[] := array[0, 1, 2, 3, 4, 5, 10, 11, 12, 13, 14, 15];
  v_code text := '';
  i int;
begin
  for i in 1 .. 12 loop
    v_code := v_code || substr(v_alphabet, (get_byte(v_bytes, v_positions[i]) % 32) + 1, 1);
  end loop;
  return 'EM-' || substr(v_code, 1, 4) || '-' || substr(v_code, 5, 4) || '-' || substr(v_code, 9, 4);
end;
$$;

-- Practice standing per level: distinct questions answered and how many of those the learner's
-- latest answer got right. Only currently published questions count.
create function private.level_standing(p_user_id uuid)
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
   where q.status = 'published' and q.cefr_level <> 'PRE_A1'
   group by q.cefr_level;
$$;

revoke all on function private.require_learner(), private.new_verification_code(), private.level_standing(uuid)
  from public, anon, authenticated;

-- Exam rules, in one place.
create function private.exam_rules()
returns table (exam_questions int, exam_minutes int, pass_percent int, retry_hours int,
               practice_required int, practice_percent int, placement_per_level int, placement_minutes int)
language sql immutable
set search_path = ''
as $$ select 20, 30, 80, 24, 30, 70, 3, 20 $$;
revoke all on function private.exam_rules() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- RPC: learner overview for the home screen
-- ---------------------------------------------------------------------------
create function public.get_learning_overview()
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
  certificate_issued_at timestamptz
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
         least(v_rules.practice_required, s.published),
         v_rules.practice_percent,
         s.answered >= least(v_rules.practice_required, s.published)
           and s.answered > 0
           and s.correct * 100 >= v_rules.practice_percent * s.answered,
         -- A failed, late or abandoned attempt opens the next one after the retry delay, so
         -- abandoning an exam cannot be used to preview questions and start again at once.
         case when (le.status in ('submitted', 'expired') or le.expires_at <= now())
                   and not coalesce(le.passed, false)
              then coalesce(le.submitted_at, le.expires_at) + make_interval(hours => v_rules.retry_hours) end,
         case when le.status = 'in_progress' and le.expires_at > now() then le.id end,
         le.score,
         le.total,
         c.verification_code,
         c.issued_at
    from standing s
    left join last_exam le on le.cefr_level = s.cefr_level
    left join public.certificates c on c.user_id = v_uid and c.cefr_level = s.cefr_level and c.revoked_at is null
   order by s.cefr_level;
end;
$$;
revoke all on function public.get_learning_overview() from public, anon;
grant execute on function public.get_learning_overview() to authenticated;

-- Learner-level facts that are not per CEFR level.
create function public.get_learner_profile()
returns table (display_name text, placement_level public.cefr_level, placed_at timestamptz,
               total_answered integer, streak_days integer)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
begin
  return query
  with days as (
    select distinct (r.received_at at time zone 'UTC')::date as d
      from public.assessment_responses r where r.user_id = v_uid
  ),
  -- Consecutive days ending today or yesterday.
  numbered as (select d, d - (row_number() over (order by d))::int as grp from days),
  runs as (select grp, max(d) as last_day, count(*)::int as len from numbered group by grp)
  select p.display_name,
         p.placement_level,
         p.placed_at,
         (select count(*)::int from public.assessment_responses r where r.user_id = v_uid),
         coalesce((select len from runs
                    where last_day >= (now() at time zone 'UTC')::date - 1
                    order by last_day desc limit 1), 0)
    from public.profiles p where p.user_id = v_uid;
end;
$$;
revoke all on function public.get_learner_profile() from public, anon;
grant execute on function public.get_learner_profile() to authenticated;

-- ---------------------------------------------------------------------------
-- Exam question payload (no answer keys)
-- ---------------------------------------------------------------------------
create function private.exam_questions(p_ids uuid[])
returns jsonb
language sql stable security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'id', q.id, 'cefr_level', q.cefr_level, 'prompt', q.prompt, 'options', to_jsonb(q.options))
           order by o.ord), '[]'::jsonb)
    from unnest(p_ids) with ordinality as o(id, ord)
    join public.assessment_questions q on q.id = o.id;
$$;
revoke all on function private.exam_questions(uuid[]) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- RPC: start (or resume) a level's final exam
-- ---------------------------------------------------------------------------
create function public.start_level_exam(p_cefr_level public.cefr_level)
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
begin
  select * into v_rules from private.exam_rules();

  if exists (select 1 from public.certificates c
              where c.user_id = v_uid and c.cefr_level = p_cefr_level and c.revoked_at is null) then
    raise exception 'certificate already issued for this level' using errcode = 'P0001', hint = 'already_certified';
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

  select * into v_overview from public.get_learning_overview() o where o.cefr_level = p_cefr_level;
  if not found or not v_overview.exam_unlocked then
    raise exception 'practise more before the exam' using errcode = 'P0001', hint = 'not_unlocked';
  end if;
  if v_overview.exam_available_at is not null and v_overview.exam_available_at > now() then
    raise exception 'next attempt not yet available' using errcode = 'P0001', hint = 'retry_later';
  end if;

  select array_agg(id) into v_ids from (
    select q.id from public.assessment_questions q
     where q.cefr_level = p_cefr_level and q.status = 'published'
     order by random() limit v_rules.exam_questions
  ) t;
  if coalesce(cardinality(v_ids), 0) < v_rules.exam_questions then
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
revoke all on function public.start_level_exam(public.cefr_level) from public, anon;
grant execute on function public.start_level_exam(public.cefr_level) to authenticated;

-- ---------------------------------------------------------------------------
-- Scores p_answers ({"<question uuid>": "A", ...}) against an attempt's questions.
-- ---------------------------------------------------------------------------
create function private.score_answers(p_ids uuid[], p_answers jsonb)
returns table (level public.cefr_level, correct integer, total integer)
language sql stable security definer
set search_path = ''
as $$
  select q.cefr_level,
         count(*) filter (where upper(btrim(coalesce(p_answers ->> q.id::text, ''))) = q.correct_option)::int,
         count(*)::int
    from public.assessment_questions q
   where q.id = any (p_ids)
   group by rollup (q.cefr_level);
$$;
revoke all on function private.score_answers(uuid[], jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- RPC: submit a final exam. Idempotent: a second submit returns the stored result.
-- ---------------------------------------------------------------------------
create function public.submit_level_exam(p_attempt_id uuid, p_answers jsonb)
returns table (score integer, total integer, passed boolean, pass_percent integer,
               certificate_code text, next_attempt_at timestamptz, expired boolean)
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
  v_passed boolean;
  v_code text;
  v_name text;
  v_late boolean;
begin
  select * into v_rules from private.exam_rules();
  if p_answers is null or jsonb_typeof(p_answers) <> 'object' then
    raise exception 'answers must be a JSON object' using errcode = '22023';
  end if;

  select * into v_attempt from public.exam_attempts e
   where e.id = p_attempt_id and e.kind = 'level_final' for update;
  if not found or v_attempt.user_id <> v_uid then
    raise exception 'exam attempt not found' using errcode = 'P0001', hint = 'not_found';
  end if;

  if v_attempt.status = 'in_progress' then
    -- Two minutes of grace for slow networks; later submissions are recorded as expired.
    v_late := now() > v_attempt.expires_at + interval '2 minutes';
    select s.correct, s.total into v_score, v_total
      from private.score_answers(v_attempt.question_ids, p_answers) s where s.level is null;
    v_passed := not v_late and v_score * 100 >= v_rules.pass_percent * v_total;
    update public.exam_attempts
       set status = case when v_late then 'expired' else 'submitted' end,
           submitted_at = now(), answers = p_answers, score = v_score, total = v_total, passed = v_passed
     where id = v_attempt.id
     returning * into v_attempt;

    if v_passed then
      select p.display_name into v_name from public.profiles p where p.user_id = v_uid;
      loop
        v_code := private.new_verification_code();
        exit when not exists (select 1 from public.certificates c where c.verification_code = v_code);
      end loop;
      insert into public.certificates (user_id, cefr_level, exam_attempt_id, holder_name, score, total, verification_code)
      values (v_uid, v_attempt.cefr_level, v_attempt.id, v_name, v_score, v_total, v_code)
      on conflict (user_id, cefr_level) do nothing;
      perform private.log_event(v_uid, 'certificate.issued', 'exam_attempt', v_attempt.id,
                                jsonb_build_object('cefr_level', v_attempt.cefr_level, 'score', v_score, 'total', v_total));
    end if;
    perform private.log_event(v_uid, 'exam.submitted', 'exam_attempt', v_attempt.id,
                              jsonb_build_object('kind', 'level_final', 'cefr_level', v_attempt.cefr_level,
                                                 'score', v_score, 'total', v_total, 'passed', v_passed, 'late', v_late));
  end if;

  select c.verification_code into v_code from public.certificates c where c.exam_attempt_id = v_attempt.id;
  return query select v_attempt.score, v_attempt.total, coalesce(v_attempt.passed, false), v_rules.pass_percent, v_code,
    case when not coalesce(v_attempt.passed, false)
         then v_attempt.submitted_at + make_interval(hours => v_rules.retry_hours) end,
    v_attempt.status = 'expired';
end;
$$;
revoke all on function public.submit_level_exam(uuid, jsonb) from public, anon;
grant execute on function public.submit_level_exam(uuid, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- Placement test
-- ---------------------------------------------------------------------------
create function public.start_placement_test()
returns table (attempt_id uuid, expires_at timestamptz, questions jsonb)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid uuid := private.require_learner();
  v_rules record;
  v_attempt public.exam_attempts;
  v_ids uuid[];
begin
  select * into v_rules from private.exam_rules();

  select * into v_attempt from public.exam_attempts e
   where e.user_id = v_uid and e.kind = 'placement' and e.status = 'in_progress' and e.expires_at > now()
   order by e.started_at desc limit 1;
  if found then
    return query select v_attempt.id, v_attempt.expires_at, private.exam_questions(v_attempt.question_ids);
    return;
  end if;

  if (select count(*) from public.exam_attempts e
       where e.user_id = v_uid and e.kind = 'placement' and e.started_at > now() - interval '1 day') >= 3 then
    raise exception 'placement test limit reached for today' using errcode = 'P0001', hint = 'retry_later';
  end if;

  select array_agg(id order by lvl, rnd) into v_ids from (
    select q.id, q.cefr_level as lvl, random() as rnd,
           row_number() over (partition by q.cefr_level order by random()) as n
      from public.assessment_questions q
     where q.status = 'published' and q.cefr_level <> 'PRE_A1'
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
revoke all on function public.start_placement_test() from public, anon;
grant execute on function public.start_placement_test() to authenticated;

-- Result: the highest level reached by passing every level from A1 upwards with at least
-- 2 of 3 correct. Recommended start = the next level (or A1 when A1 itself was not passed).
create function public.submit_placement_test(p_attempt_id uuid, p_answers jsonb)
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
           passed = v_passed is not null, result_level = v_recommended
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
revoke all on function public.submit_placement_test(uuid, jsonb) from public, anon;
grant execute on function public.submit_placement_test(uuid, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- Certificates: own list, and public verification by code
-- ---------------------------------------------------------------------------
create function public.get_my_certificates()
returns table (cefr_level public.cefr_level, holder_name text, score integer, total integer,
               verification_code text, issued_at timestamptz)
language sql stable security definer
set search_path = ''
as $$
  select c.cefr_level, c.holder_name, c.score, c.total, c.verification_code, c.issued_at
    from public.certificates c
   where c.user_id = (select private.require_learner()) and c.revoked_at is null
   order by c.cefr_level;
$$;
revoke all on function public.get_my_certificates() from public, anon;
grant execute on function public.get_my_certificates() to authenticated;

-- Public: returns the certificate's own printed facts for a valid code, nothing else.
create function public.verify_certificate(p_code text)
returns table (valid boolean, holder_name text, cefr_level public.cefr_level, score integer, total integer,
               issued_at timestamptz, revoked boolean)
language sql stable security definer
set search_path = ''
as $$
  select true, c.holder_name, c.cefr_level, c.score, c.total, c.issued_at, c.revoked_at is not null
    from public.certificates c
   where c.verification_code = upper(btrim(coalesce(p_code, '')))
$$;
revoke all on function public.verify_certificate(text) from public;
grant execute on function public.verify_certificate(text) to anon, authenticated;
