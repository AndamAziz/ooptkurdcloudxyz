-- Staff edit and delete questions; answer history is never lost.
begin;
select plan(16);

insert into auth.users (id, email, raw_user_meta_data) values
  ('a1100000-0000-0000-0000-00000000000a', 'edit.student@example.test', '{"display_name":"Student"}'),
  ('b1100000-0000-0000-0000-00000000000b', 'edit.teacher@example.test', '{"display_name":"Teacher"}'),
  ('c1100000-0000-0000-0000-00000000000c', 'edit.admin@example.test', '{"display_name":"Admin"}');
insert into public.user_roles (user_id, role) values
  ('b1100000-0000-0000-0000-00000000000b', 'teacher'), ('c1100000-0000-0000-0000-00000000000c', 'admin');

insert into public.assessment_questions (id, cefr_level, skill, prompt, options, correct_option, status, review_status, media_type, media_path, media_caption)
values ('e1100000-0000-0000-0000-0000000000a1', 'A1', 'grammar', 'what is the activation code ?', array['1','2','3','4'], 'A',
        'published', 'approved', 'image', 'staff/x.png', '1'),
       ('e1100000-0000-0000-0000-0000000000a2', 'A1', 'grammar', 'Unused question ___.', array['a','b'], 'A', 'published', 'approved',
        null, null, null),
       ('e1100000-0000-0000-0000-0000000000a3', 'A1', 'grammar', 'Answered question ___.', array['a','b'], 'B', 'published', 'approved',
        null, null, null);
insert into public.assessment_attempts (id, user_id, kind, cefr_level)
values ('e1100000-0000-0000-0000-0000000000f1', 'a1100000-0000-0000-0000-00000000000a', 'practice', 'A1');
insert into public.assessment_responses (id, attempt_id, user_id, question_id, question_version, chosen_option, is_correct)
values (gen_random_uuid(), 'e1100000-0000-0000-0000-0000000000f1', 'a1100000-0000-0000-0000-00000000000a',
        'e1100000-0000-0000-0000-0000000000a3', 1, 'B', true);

-- Students cannot edit or delete.
select set_config('request.jwt.claims', '{"sub":"a1100000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select throws_ok($$select public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'grammar', 'x?', array['a','b'], 'A')$$,
  '42501', 'insufficient role', 'learners cannot edit questions');
select throws_ok($$select public.staff_delete_question('e1100000-0000-0000-0000-0000000000a2')$$,
  '42501', 'insufficient role', 'learners cannot delete questions');
reset role;

-- A teacher edits; content changes bump the version, an explanation-only change does not.
select set_config('request.jwt.claims', '{"sub":"b1100000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is(public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'vocabulary', ' What is the activation code? ',
            array['837631','837613','873631','837361'], 'a', 'Read the code under the map.', null, 'image', 'staff/x.png', 'Union Flag'),
          2, 'editing the prompt and options bumps the version');
select is(public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'vocabulary', 'What is the activation code?',
            array['837631','837613','873631','837361'], 'A', 'Look at the big gold numbers.', null, 'image', 'staff/x.png', 'Union Flag'),
          2, 'an explanation-only edit keeps the version');
select throws_ok($$select public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'grammar', 'x?', array['a','a'], 'A')$$,
  '22023', null, 'duplicate options are refused');
select throws_ok($$select public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'grammar', 'x?', array['a','b'], 'C')$$,
  '22023', null, 'the key must point to an option');
select throws_ok($$select public.staff_delete_question('e1100000-0000-0000-0000-0000000000a2')$$,
  '42501', 'insufficient role', 'teachers cannot delete questions');
reset role;
select results_eq($$select prompt, options, correct_option, status, review_status, media_caption, skill
                     from public.assessment_questions where id = 'e1100000-0000-0000-0000-0000000000a1'$$,
  $$values ('What is the activation code?'::text, array['837631','837613','873631','837361']::text[], 'A'::text,
            'published'::text, 'approved'::text, 'Union Flag'::text, 'vocabulary'::text)$$,
  'edit is saved and keeps the review/publish state');
-- Removing the picture also clears its caption.
select set_config('request.jwt.claims', '{"sub":"b1100000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is(public.staff_update_question('e1100000-0000-0000-0000-0000000000a1', 'A1', 'vocabulary', 'What is the activation code?',
            array['837631','837613','873631','837361'], 'A', null, null, null, null, 'ignored'), 3, 'removing the media bumps the version');
reset role;
select results_eq($$select media_type, media_path, media_caption from public.assessment_questions where id = 'e1100000-0000-0000-0000-0000000000a1'$$,
  $$values (null::text, null::text, null::text)$$, 'media and caption are removed together');

-- An admin deletes: unused questions disappear, answered ones are archived.
select set_config('request.jwt.claims', '{"sub":"c1100000-0000-0000-0000-00000000000c","role":"authenticated"}', true);
set local role authenticated;
select is(public.staff_delete_question('e1100000-0000-0000-0000-0000000000a2'), 'deleted', 'an unused question is deleted');
select is(public.staff_delete_question('e1100000-0000-0000-0000-0000000000a3'), 'archived', 'an answered question is archived');
select throws_ok($$select public.staff_delete_question('e1100000-0000-0000-0000-0000000000a3')$$,
  'P0001', 'question not found', 'an archived question cannot be deleted again');
select throws_ok($$select public.staff_update_question('e1100000-0000-0000-0000-0000000000a3', 'A1', 'grammar', 'x?', array['a','b'], 'A')$$,
  'P0001', 'question not found', 'an archived question cannot be edited');
reset role;
select is((select count(*)::int from public.assessment_questions where id = 'e1100000-0000-0000-0000-0000000000a2'), 0,
  'the unused question row is gone');
select results_eq($$select q.status, q.deleted_at is not null, (select count(*)::int from public.assessment_responses r where r.question_id = q.id)
                     from public.assessment_questions q where q.id = 'e1100000-0000-0000-0000-0000000000a3'$$,
  $$values ('retired'::text, true, 1)$$, 'the archived question is retired and its answers are kept');

select * from finish();
rollback;
