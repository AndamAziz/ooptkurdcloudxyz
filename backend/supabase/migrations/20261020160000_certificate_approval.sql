-- Certificates need a teacher's or admin's approval.
--
-- * Passing a level exam now creates a *pending* certificate. It is not shown to the learner as a
--   certificate, cannot be downloaded and does not verify until a teacher or admin approves it.
--   Certificates issued before this change stay approved.
-- * staff_list_certificates / staff_review_certificate (teacher or admin): approve or reject.
--   Nobody can review their own certificate. Approval takes the learner's current name (so a
--   teacher can correct it first) and sets the issue date.
-- * After a rejection the learner may take the exam again; a new pass reuses the certificate row.
-- * get_learning_overview_v2 adds certificate_status ('pending' | 'approved'); v1 and the other
--   learner functions keep their signatures and only treat approved certificates as issued.

alter table public.certificates
  add column status      text not null default 'approved' check (status in ('pending', 'approved', 'rejected')),
  add column reviewed_by uuid references auth.users (id) on delete set null,
  add column reviewed_at timestamptz,
  add column review_note text check (char_length(review_note) <= 300);
alter table public.certificates alter column status set default 'pending';
create index certificates_pending_idx on public.certificates (issued_at) where status = 'pending';

-- ---------------------------------------------------------------------------
-- Learner side: only approved certificates count as issued.
-- ---------------------------------------------------------------------------
create function public.get_learning_overview_v2()
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
         least(v_rules.practice_required, s.published),
         v_rules.practice_percent,
         s.answered >= least(v_rules.practice_required, s.published)
           and s.answered > 0
           and s.correct * 100 >= v_rules.practice_percent * s.answered,
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

-- Older app builds: same columns as before, approved certificates only.
create or replace function public.get_learning_overview()
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
language sql stable security definer
set search_path = ''
as $$
  select o.cefr_level, o.published, o.answered, o.correct, o.required, o.required_percent, o.exam_unlocked,
         o.exam_available_at, o.exam_in_progress, o.last_exam_score, o.last_exam_total,
         case when o.certificate_status = 'approved' then o.certificate_code end,
         case when o.certificate_status = 'approved' then o.certificate_issued_at end
    from public.get_learning_overview_v2() o;
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

-- A pass creates a pending certificate (or re-opens a rejected one); the code is returned only
-- once approved, so the result of a new pass carries certificate_code = null.
create or replace function public.submit_level_exam(p_attempt_id uuid, p_answers jsonb)
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
      insert into public.certificates (user_id, cefr_level, exam_attempt_id, holder_name, score, total, verification_code, status)
      values (v_uid, v_attempt.cefr_level, v_attempt.id, v_name, v_score, v_total, v_code, 'pending')
      on conflict (user_id, cefr_level) do update
         set exam_attempt_id = excluded.exam_attempt_id, holder_name = excluded.holder_name,
             score = excluded.score, total = excluded.total, verification_code = excluded.verification_code,
             status = 'pending', issued_at = now(), reviewed_by = null, reviewed_at = null, review_note = null
       where public.certificates.status = 'rejected';
      perform private.log_event(v_uid, 'certificate.requested', 'exam_attempt', v_attempt.id,
                                jsonb_build_object('cefr_level', v_attempt.cefr_level, 'score', v_score, 'total', v_total));
    end if;
    perform private.log_event(v_uid, 'exam.submitted', 'exam_attempt', v_attempt.id,
                              jsonb_build_object('kind', 'level_final', 'cefr_level', v_attempt.cefr_level,
                                                 'score', v_score, 'total', v_total, 'passed', v_passed, 'late', v_late));
  end if;

  select c.verification_code into v_code from public.certificates c
   where c.exam_attempt_id = v_attempt.id and c.status = 'approved' and c.revoked_at is null;
  return query select v_attempt.score, v_attempt.total, coalesce(v_attempt.passed, false), v_rules.pass_percent, v_code,
    case when not coalesce(v_attempt.passed, false)
         then v_attempt.submitted_at + make_interval(hours => v_rules.retry_hours) end,
    v_attempt.status = 'expired';
end;
$$;

create or replace function public.get_my_certificates()
returns table (cefr_level public.cefr_level, holder_name text, score integer, total integer,
               verification_code text, issued_at timestamptz)
language sql stable security definer
set search_path = ''
as $$
  select c.cefr_level, c.holder_name, c.score, c.total, c.verification_code, c.issued_at
    from public.certificates c
   where c.user_id = (select private.require_learner()) and c.revoked_at is null and c.status = 'approved'
   order by c.cefr_level;
$$;

-- Public check by code: pending or rejected certificates do not verify.
create or replace function public.verify_certificate(p_code text)
returns table (valid boolean, holder_name text, cefr_level public.cefr_level, score integer, total integer,
               issued_at timestamptz, revoked boolean)
language sql stable security definer
set search_path = ''
as $$
  select true, c.holder_name, c.cefr_level, c.score, c.total, c.issued_at, c.revoked_at is not null
    from public.certificates c
   where c.verification_code = upper(btrim(coalesce(p_code, ''))) and c.status = 'approved'
$$;

-- ---------------------------------------------------------------------------
-- Staff (teacher or admin): review certificate requests.
-- ---------------------------------------------------------------------------
create function public.staff_list_certificates(p_status text default 'pending', p_limit integer default 100)
returns table (id uuid, user_id uuid, email text, display_name text, cefr_level public.cefr_level,
               score integer, total integer, requested_at timestamptz, status text,
               reviewed_at timestamptz, reviewer_name text)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
begin
  perform private.require_staff(false);
  if p_status not in ('pending', 'approved', 'rejected') then
    raise exception 'unknown status' using errcode = '22023';
  end if;
  if p_limit is null or p_limit < 1 or p_limit > 200 then
    raise exception 'p_limit must be between 1 and 200' using errcode = '22023';
  end if;
  return query
    select c.id, c.user_id, u.email::text, p.display_name, c.cefr_level, c.score, c.total,
           coalesce(e.submitted_at, c.issued_at), c.status, c.reviewed_at, rp.display_name
      from public.certificates c
      join auth.users u on u.id = c.user_id
      left join public.profiles p on p.user_id = c.user_id
      left join public.exam_attempts e on e.id = c.exam_attempt_id
      left join public.profiles rp on rp.user_id = c.reviewed_by
     where c.status = p_status and c.revoked_at is null
     order by case when p_status = 'pending' then coalesce(e.submitted_at, c.issued_at) end asc,
              c.reviewed_at desc nulls last
     limit p_limit;
end;
$$;
revoke all on function public.staff_list_certificates(text, integer) from public, anon;
grant execute on function public.staff_list_certificates(text, integer) to authenticated;

create function public.staff_review_certificate(p_certificate_id uuid, p_approve boolean, p_note text default null)
returns table (status text, verification_code text)
language plpgsql security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_staff uuid := private.require_staff(false);
  v_cert public.certificates;
  v_name text;
  v_note text := nullif(btrim(coalesce(p_note, '')), '');
begin
  if p_approve is null then
    raise exception 'decision required' using errcode = '22023';
  end if;
  if char_length(v_note) > 300 then
    raise exception 'note too long' using errcode = '22023';
  end if;
  select * into v_cert from public.certificates c where c.id = p_certificate_id for update;
  if not found then
    raise exception 'certificate not found' using errcode = 'P0001', hint = 'not_found';
  end if;
  if v_cert.user_id = v_staff then
    raise exception 'you cannot review your own certificate' using errcode = 'P0001', hint = 'own_certificate';
  end if;
  if v_cert.status <> 'pending' or v_cert.revoked_at is not null then
    raise exception 'certificate was already reviewed' using errcode = 'P0001', hint = 'not_pending';
  end if;

  if p_approve then
    -- The certificate carries the learner's name as it is when approved (staff may correct it first).
    select nullif(btrim(p.display_name), '') into v_name from public.profiles p where p.user_id = v_cert.user_id;
    update public.certificates
       set status = 'approved', holder_name = coalesce(left(v_name, 80), holder_name), issued_at = now(),
           reviewed_by = v_staff, reviewed_at = now(), review_note = v_note
     where id = v_cert.id
     returning * into v_cert;
  else
    update public.certificates
       set status = 'rejected', reviewed_by = v_staff, reviewed_at = now(), review_note = v_note
     where id = v_cert.id
     returning * into v_cert;
  end if;

  perform private.log_event(v_staff, case when p_approve then 'certificate.approved' else 'certificate.rejected' end,
                            'certificate', v_cert.id,
                            jsonb_build_object('cefr_level', v_cert.cefr_level, 'learner', v_cert.user_id));
  return query select v_cert.status, case when v_cert.status = 'approved' then v_cert.verification_code end;
end;
$$;
revoke all on function public.staff_review_certificate(uuid, boolean, text) from public, anon;
grant execute on function public.staff_review_certificate(uuid, boolean, text) to authenticated;
