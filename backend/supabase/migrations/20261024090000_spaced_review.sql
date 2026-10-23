-- Spaced review of mistakes.
--
-- A mistake can no longer be "fixed" by answering the same question again straight after seeing
-- the correct answer. Every practice answer now moves the question through a short schedule:
--
--   * first answer right                 -> known
--   * an answer wrong                    -> learning, due again 24 hours later
--   * right when due (first review)      -> due again 3 days later
--   * right when due (second review)     -> known again (the mistake is fixed)
--   * wrong when due                     -> back to the start (24 hours)
--   * any answer before it is due        -> stored, but does not change anything
--
-- The level standing (accuracy towards the level exam) now counts a question as right only when
-- it is known, so a fixed mistake has been answered correctly on two separate days.
-- Existing answers are replayed through the same rules.

create table public.question_progress (
  user_id        uuid not null references auth.users (id) on delete cascade,
  question_id    uuid not null references public.assessment_questions (id) on delete cascade,
  state          text not null check (state in ('known', 'learning')),
  reviews        smallint not null default 0 check (reviews between 0 and 1),
  due_at         timestamptz,
  last_answer_at timestamptz not null,
  primary key (user_id, question_id),
  check ((state = 'learning') = (due_at is not null))
);
comment on table public.question_progress is
  'Spaced review of practice mistakes, maintained by a trigger on assessment_responses.';
alter table public.question_progress enable row level security;
create policy "question progress: owner reads own" on public.question_progress
  for select to authenticated using (user_id = (select auth.uid()));
grant select on public.question_progress to authenticated;

create or replace function private.review_rules()
returns table (first_delay interval, second_delay interval)
language sql immutable
set search_path = ''
as $$ select interval '24 hours', interval '3 days' $$;
revoke all on function private.review_rules() from public, anon, authenticated;

create or replace function private.apply_review(p_uid uuid, p_question uuid, p_correct boolean, p_at timestamptz)
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  v public.question_progress;
  r record;
begin
  select * into r from private.review_rules();
  select * into v from public.question_progress q
   where q.user_id = p_uid and q.question_id = p_question for update;
  if not found then
    insert into public.question_progress (user_id, question_id, state, due_at, last_answer_at)
    values (p_uid, p_question, case when p_correct then 'known' else 'learning' end,
            case when p_correct then null else p_at + r.first_delay end, p_at);
    return;
  end if;

  if v.state = 'known' then
    if p_correct then
      update public.question_progress set last_answer_at = p_at where user_id = p_uid and question_id = p_question;
    else
      update public.question_progress set state = 'learning', reviews = 0, due_at = p_at + r.first_delay, last_answer_at = p_at
       where user_id = p_uid and question_id = p_question;
    end if;
    return;
  end if;

  -- Learning: an answer before the review is due does not count.
  if p_at < v.due_at then
    return;
  end if;
  if not p_correct then
    update public.question_progress set reviews = 0, due_at = p_at + r.first_delay, last_answer_at = p_at
     where user_id = p_uid and question_id = p_question;
  elsif v.reviews = 0 then
    update public.question_progress set reviews = 1, due_at = p_at + r.second_delay, last_answer_at = p_at
     where user_id = p_uid and question_id = p_question;
  else
    update public.question_progress set state = 'known', reviews = 0, due_at = null, last_answer_at = p_at
     where user_id = p_uid and question_id = p_question;
  end if;
end;
$$;
revoke all on function private.apply_review(uuid, uuid, boolean, timestamptz) from public, anon, authenticated;

-- Replay existing answers in order.
do $$
declare
  a record;
begin
  for a in select user_id, question_id, is_correct, received_at from public.assessment_responses
            order by user_id, question_id, received_at, id loop
    perform private.apply_review(a.user_id, a.question_id, a.is_correct, a.received_at);
  end loop;
end;
$$;

create or replace function private.on_response_review()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  perform private.apply_review(new.user_id, new.question_id, new.is_correct, new.received_at);
  return new;
end;
$$;
revoke all on function private.on_response_review() from public, anon, authenticated;

create trigger assessment_responses_review
  after insert on public.assessment_responses
  for each row execute function private.on_response_review();

-- Accuracy counts questions that are known (never wrong, or fixed by two spaced reviews).
create or replace function private.level_standing(p_user_id uuid)
returns table (cefr_level public.cefr_level, published integer, answered integer, correct integer)
language sql stable security definer
set search_path = ''
as $$
  select q.cefr_level,
         count(*)::int,
         count(p.question_id)::int,
         count(*) filter (where p.state = 'known')::int
    from public.assessment_questions q
    left join public.question_progress p on p.question_id = q.id and p.user_id = p_user_id
   where q.status = 'published' and q.pool = 'practice' and q.cefr_level not in ('PRE_A1', 'LIFE_IN_UK')
   group by q.cefr_level;
$$;

-- The learner's review schedule (for other devices; the app also computes it offline).
create or replace function public.get_review_schedule()
returns table (question_id uuid, state text, reviews integer, due_at timestamptz)
language sql stable security definer
set search_path = ''
as $$
  select p.question_id, p.state, p.reviews::int, p.due_at
    from public.question_progress p
   where p.user_id = (select private.require_learner()) and p.state = 'learning';
$$;
revoke all on function public.get_review_schedule() from public, anon;
grant execute on function public.get_review_schedule() to authenticated;
