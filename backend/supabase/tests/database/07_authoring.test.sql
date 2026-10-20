-- Passages and media, staff question authoring, bulk publishing, learner name corrections.
begin;
select plan(17);

insert into auth.users (id, email, raw_user_meta_data) values
  ('d1000000-0000-0000-0000-00000000000a', 'learner.d@example.test', '{"display_name":"sara"}'),
  ('d1000000-0000-0000-0000-0000000000a7', 'teacher.d@example.test', '{"display_name":"Teacher"}');
insert into public.user_roles (user_id, role) values ('d1000000-0000-0000-0000-0000000000a7', 'teacher');

select throws_ok($$insert into public.assessment_questions (cefr_level, skill, prompt, options, correct_option, media_type)
                   values ('A1', 'vocabulary', 'x', array['a','b'], 'A', 'image')$$,
  '23514', null, 'media type needs a media path');
select throws_ok($$insert into public.assessment_questions (cefr_level, skill, prompt, options, correct_option, media_type, media_path)
                   values ('A1', 'vocabulary', 'x', array['a','b'], 'A', 'image', '../secret.png')$$,
  '23514', null, 'media paths are restricted to plain object names');
select is((select public from storage.buckets where id = 'question-media'), true, 'question-media bucket is public-read');

-- Learner --------------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"d1000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select throws_ok($$select public.staff_create_question('A1', 'grammar', 'p', array['a','b'], 'A')$$, '42501', null,
  'learners cannot create questions');
select throws_ok($$select public.staff_set_display_name('d1000000-0000-0000-0000-00000000000a', 'Sara Ahmed')$$, '42501', null,
  'learners cannot rename via the staff RPC');
select throws_ok($$insert into storage.objects (bucket_id, name) values ('question-media', 'x.png')$$, '42501', null,
  'learners cannot upload question media');
reset role;

-- Teacher ----------------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"d1000000-0000-0000-0000-0000000000a7","role":"authenticated"}', true);
set local role authenticated;
select lives_ok($$insert into storage.objects (bucket_id, name) values ('question-media', 'samples/clock.png')$$,
  'teachers can upload question media');
select throws_ok($$select public.staff_create_question('A1', 'grammar', 'p', array['a','a'], 'A')$$, '22023', null,
  'duplicate options are rejected');
select throws_ok($$select public.staff_create_question('A1', 'grammar', 'p', array['a','b'], 'C')$$, '22023', null,
  'the key must point to an option');
create temp table ids (k text, id uuid);
grant all on ids to authenticated;
insert into ids select 'pub', public.staff_create_question('A1', 'vocabulary', 'What time is it? ___', array['three o''clock','half past three'],
  'a', 'The short hand points to 3.', null, 'image', 'samples/clock.png', 'A clock', true);
insert into ids select 'draft', public.staff_create_question('A1', 'reading', 'Where does Ali live?', array['In Erbil','In Duhok'],
  'B', null, 'Ali lives in Duhok with his family.', null, null, null, false);
select is((select status || '/' || review_status from public.assessment_questions where id = (select id from ids where k = 'pub')),
  'published/approved', 'teacher-authored questions can be published directly');
select is((select status from public.assessment_questions where id = (select id from ids where k = 'draft')), 'draft',
  'or saved as drafts');
select ok((select public.staff_publish_drafts('A1')) >= 1, 'teachers can publish all drafts of a level at once');
select is((select status || '/' || review_status from public.assessment_questions where id = (select id from ids where k = 'draft')),
  'published/approved', 'bulk publishing approves the drafts');
select is(public.staff_set_display_name('d1000000-0000-0000-0000-00000000000a', '  Sara Ahmed  '), 'Sara Ahmed',
  'teachers can correct a learner''s name');
reset role;

select is((select display_name from public.profiles where user_id = 'd1000000-0000-0000-0000-00000000000a'), 'Sara Ahmed',
  'the profile carries the corrected name');
select is((select count(*)::int from public.activity_logs
            where event_type = 'staff.display_name_changed' and metadata = '{}'::jsonb), 1,
  'the change is logged without copying names');

-- Learner downloads the new question with its passage/media fields.
select set_config('request.jwt.claims', '{"sub":"d1000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select results_eq(
  $$select media_type, media_path, passage from public.get_practice_questions_v2('A1', null, 200) where prompt like 'Where does Ali%'
    union all
    select media_type, media_path, passage from public.get_practice_questions_v2('A1', null, 200) where prompt like 'What time is it?%'$$,
  $$values (null::text, null::text, 'Ali lives in Duhok with his family.'::text), ('image', 'samples/clock.png', null)$$,
  'v2 download includes passages and media');
reset role;

select * from finish();
rollback;
