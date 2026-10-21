-- The publishing workflows (service_role) can do exactly what they need without automatic grants.
begin;
select plan(4);

select ok(has_table_privilege('service_role', 'public.assessment_questions', 'insert'), 'content workflow can insert question drafts');
select ok(has_table_privilege('service_role', 'public.assessment_questions', 'select'), 'content workflow can read back inserted drafts');
select ok(has_table_privilege('service_role', 'public.app_releases', 'insert'), 'release workflow can register a release');
select ok(not has_table_privilege('anon', 'public.assessment_questions', 'select'), 'anon still cannot read questions (answer keys)');

select * from finish();
rollback;
