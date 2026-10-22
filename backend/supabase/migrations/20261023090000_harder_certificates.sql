-- Harder certificates.
--
-- * The level exam unlocks only after the learner has answered at least 70% of the level's
--   published questions (it was 30 questions), still with at least 70% of their latest answers
--   right. practice_required in exam_rules is now that share in percent.
-- * The level exam has 40 questions in 60 minutes (it was 20 in 30). The pass mark stays 80%.
-- * Certificates already issued or waiting for approval are not affected; an exam already in
--   progress finishes with its own questions and deadline.

create or replace function private.exam_rules()
returns table (exam_questions int, exam_minutes int, pass_percent int, retry_hours int,
               practice_required int, practice_percent int, placement_per_level int, placement_minutes int)
language sql immutable
set search_path = ''
as $$ select 40, 60, 80, 24, 70, 70, 3, 20 $$;
revoke all on function private.exam_rules() from public, anon, authenticated;

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
