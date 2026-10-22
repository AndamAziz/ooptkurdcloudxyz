-- Fairer placement retakes, and question pack versions for automatic updates.
--
-- * start_placement_test used to count every started attempt (also ones the learner left or that
--   ran out of time) towards the 3-a-day limit, so a learner who opened the test and left could be
--   locked out without ever finishing it. Now only finished (submitted) attempts count: 3 per
--   rolling 24 hours. A higher cap on started attempts (10 per 24 hours) stops collecting the
--   questions by starting and leaving. Stale unfinished attempts are closed as expired.
-- * get_placement_status tells the app how many attempts are left and exactly when the next one
--   opens, so it can say so instead of a vague "not open yet".
-- * get_question_pack_versions returns, per level, the number of published questions and the last
--   change, so the app can see that a downloaded pack is out of date and refresh it by itself.

create or replace function private.placement_window(p_uid uuid)
returns table (finished integer, started integer, attempts_left integer, next_attempt_at timestamptz)
language sql stable
set search_path = ''
as $$
  with recent as (
    select e.status, e.started_at, e.submitted_at
      from public.exam_attempts e
     where e.user_id = p_uid and e.kind = 'placement' and e.started_at > now() - interval '1 day'
  ), f as (
    select count(*)::int as n,
           -- The oldest finished attempt in the window drops out 24 hours after it started.
           min(started_at) filter (where status = 'submitted') + interval '1 day' as frees_at
      from recent where status = 'submitted'
  ), s as (
    select count(*)::int as n, min(started_at) + interval '1 day' as frees_at from recent
  )
  select f.n, s.n,
         greatest(0, least(3 - f.n, 10 - s.n)),
         case when f.n >= 3 and s.n >= 10 then greatest(f.frees_at, s.frees_at)
              when f.n >= 3 then f.frees_at
              when s.n >= 10 then s.frees_at end
    from f, s;
$$;
revoke all on function private.placement_window(uuid) from public, anon, authenticated;

create or replace function public.get_placement_status()
returns table (attempts_left integer, next_attempt_at timestamptz, in_progress boolean)
language sql stable security definer
set search_path = ''
as $$
  select w.attempts_left, w.next_attempt_at,
         exists (select 1 from public.exam_attempts e
                  where e.user_id = (select private.require_learner()) and e.kind = 'placement'
                    and e.status = 'in_progress' and e.expires_at > now())
    from private.placement_window((select private.require_learner())) w;
$$;
revoke all on function public.get_placement_status() from public, anon;
grant execute on function public.get_placement_status() to authenticated;

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
           row_number() over (partition by q.cefr_level order by random()) as n
      from public.assessment_questions q
     where q.status = 'published' and q.cefr_level not in ('PRE_A1', 'LIFE_IN_UK')
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

create or replace function public.get_question_pack_versions()
returns table (cefr_level public.cefr_level, published integer, last_changed_at timestamptz)
language sql stable security definer
set search_path = ''
as $$
  select q.cefr_level, count(*)::int, max(q.updated_at)
    from public.assessment_questions q
   where q.status = 'published' and (select private.require_learner()) is not null
   group by q.cefr_level;
$$;
revoke all on function public.get_question_pack_versions() from public, anon;
grant execute on function public.get_question_pack_versions() to authenticated;
