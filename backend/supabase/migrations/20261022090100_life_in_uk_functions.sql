-- Life in the UK questions are a separate course: they are practised and mock-tested in the app,
-- but they are not an English level. Keep them out of the level overview (so no level exam or
-- certificate) and out of the placement test. Apps that do not know the LIFE_IN_UK value never
-- receive it from these functions.

-- Practice standing per level: distinct questions answered and how many of those the learner's
-- latest answer got right. Only currently published questions count.
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
   where q.status = 'published' and q.cefr_level not in ('PRE_A1', 'LIFE_IN_UK')
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
