-- Seeded question bank sanity checks.
begin;
select plan(5);

select cmp_ok((select count(*)::int from public.assessment_questions where external_ref like 'csv:%'), '>=', 1400,
  'question bank seeded (1410 unique items after de-duplicating template variants)');
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
