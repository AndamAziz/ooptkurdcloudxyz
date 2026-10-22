-- AI-drafted question batches stay hidden from students until a teacher approves them.
begin;
select plan(17);

select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-01:%'), 125,
  'AI batch 01 seeded: 125 items');
select is((select count(*)::int from public.assessment_questions
            where external_ref like 'ai:%' and (status <> 'draft' or review_status <> 'unreviewed')), 0,
  'AI items are imported as unreviewed drafts');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-02:%' and status = 'draft'), 200,
  'AI batch 02 seeded as 200 drafts');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-02:%' and media_path like 'samples/%'), 21,
  'batch 02 picture and video questions point at the sample media');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-03:%' and status = 'draft'), 114,
  'AI batch 03 (stories, dialogues, Life in the UK, Britain) seeded as 114 drafts');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-03:%' and media_path like 'samples/%'), 15,
  'batch 03 picture and video questions point at the sample media');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-06:%' and pool = 'exam' and status = 'draft'), 720,
  'batch 06 adds 120 exam-only drafts per English level');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-07:%' and pool = 'practice'), 600,
  'batch 07 adds 300 practice drafts each for A1 and A2');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:%' and source not like 'ai-draft:%'), 0,
  'AI items are labelled with their batch as source');
select is((select count(*)::int from (
            select cefr_level, lower(prompt) from public.assessment_questions
             where status <> 'retired' group by 1, 2 having count(*) > 1) d), 0,
  'no AI prompt duplicates an existing prompt in the same level');
select is((select count(*)::int from public.assessment_questions
            where external_ref like 'ai:%' and cardinality(options) <> 4 and options <> array['True', 'False']), 0,
  'every AI item has four options, or is a True/False item');
select is((select count(*)::int from public.assessment_questions where external_ref like 'ai:ai-batch-04:%' and status = 'draft'), 400,
  'AI batch 04 (Life in the UK course) seeded as 400 drafts');
select is((select count(*)::int from public.assessment_questions where external_ref like 'lituk:S%' and status = 'draft'), 1066,
  'AI batch 05 (handbook-verified Life in the UK questions) seeded as 1066 drafts with stable lituk: refs');
select is((select count(*)::int from public.assessment_questions where external_ref like 'lituk:%' and cefr_level <> 'LIFE_IN_UK'), 0,
  'batch 05 questions all belong to the Life in the UK course');
select is((select count(*)::int from public.assessment_questions
            where options = array['True', 'False'] and (cefr_level <> 'LIFE_IN_UK' or prompt not like 'TRUE or FALSE: %')), 0,
  'True/False items belong to the Life in the UK course and say so in the prompt');

insert into auth.users (id, email) values
  ('eeeeeeee-0000-0000-0000-00000000000e', 'student.ai@example.test'),
  ('e0000000-0000-0000-0000-0000000000e1', 'teacher.ai@example.test');
insert into public.user_roles (user_id, role) values ('e0000000-0000-0000-0000-0000000000e1', 'teacher');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"eeeeeeee-0000-0000-0000-00000000000e","role":"authenticated"}', true);
select is((select count(*)::int from public.get_practice_questions('A2', null, 200)
            where prompt = 'I''ve lived here ___ 2019.'), 0,
  'students cannot download an unapproved AI draft');

reset role;
select set_config('request.jwt.claims', '{"sub":"e0000000-0000-0000-0000-0000000000e1","role":"authenticated"}', true);
set local role authenticated;
select public.staff_review_question(
  (select id from public.assessment_questions where prompt = 'I''ve lived here ___ 2019.'), 'approved', 'published');

reset role;
select set_config('request.jwt.claims', '{"sub":"eeeeeeee-0000-0000-0000-00000000000e","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*)::int from public.get_practice_questions('A2', null, 200)
            where prompt = 'I''ve lived here ___ 2019.'), 1,
  'once a teacher approves and publishes it, students can download it');

select * from finish();
rollback;
