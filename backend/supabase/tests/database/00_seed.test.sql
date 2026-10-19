-- Seeded question bank sanity checks.
begin;
select plan(6);

select is((select count(*)::int from public.assessment_questions where external_ref like 'csv:%'), 84,
  'question bank seeded: 84 distinct items (template variants and ambiguous items removed)');
select is((select count(*)::int from (
            select cefr_level, lower(prompt) from public.assessment_questions where external_ref like 'csv:%'
            group by 1, 2 having count(*) > 1) d), 0,
  'no prompt appears twice within a level');
select is((select count(*)::int from public.assessment_questions where external_ref like 'csv:%' and source <> 'generated'), 0,
  'only generated items are seeded (OOPT-derived rows excluded)');
select is((select count(*)::int from public.assessment_questions where external_ref like 'csv:%' and review_status <> 'unreviewed'), 0,
  'seeded items are flagged unreviewed');
select is((select count(*)::int from public.assessment_questions where prompt ~ '\(v\d+\)\s*$'), 0,
  'template variant suffixes are stripped from prompts');
select is((select count(distinct cefr_level)::int from public.assessment_questions where external_ref like 'csv:%'), 6,
  'all six CEFR levels A1–C2 have practice items');

select * from finish();
rollback;
