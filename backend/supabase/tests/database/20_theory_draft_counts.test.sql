-- Staff need to see which chapters still hold questions waiting to be published: the page used to
-- show only the published count, so a chapter full of drafts looked exactly like an empty one.
begin;
select plan(7);

select has_function('public', 'staff_theory_draft_counts', array[]::text[]);

insert into auth.users (id, email) values
  ('e2000000-0000-0000-0000-00000000000a', 'drafts.learner@example.test'),
  ('e2000000-0000-0000-0000-00000000000b', 'drafts.teacher@example.test');
insert into public.user_roles (user_id, role) values ('e2000000-0000-0000-0000-00000000000b', 'teacher');

-- Start from a known state: nothing published anywhere, so every question counts as waiting.
update public.theory_questions set status = 'draft';

set local role authenticated;
set local request.jwt.claims = '{"sub": "e2000000-0000-0000-0000-00000000000b"}';

select ok((select drafts from public.staff_theory_draft_counts() where section_slug = 'alertness') > 0,
  'a chapter with drafts is reported');
select is((select count(*)::int from public.staff_theory_draft_counts() where section_slug = 'video-clips'), 0,
  'a chapter with no questions at all is left out');

-- Publishing a chapter takes it off the list, which is what tells staff the job is done.
select lives_ok($$select public.staff_publish_theory_drafts('alertness')$$, 'a teacher publishes a chapter');
select is((select count(*)::int from public.staff_theory_draft_counts() where section_slug = 'alertness'), 0,
  'and it no longer has anything waiting');

reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "e2000000-0000-0000-0000-00000000000a"}';
select throws_ok($$select * from public.staff_theory_draft_counts()$$, null,
  'a learner cannot see what is still being checked');

reset role;
select ok(not has_function_privilege('anon', 'public.staff_theory_draft_counts()', 'execute'),
  'and nor can a signed-out caller');

select * from finish();
rollback;
