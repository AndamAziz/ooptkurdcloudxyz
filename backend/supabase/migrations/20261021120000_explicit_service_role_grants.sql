-- Explicit Data API grants for the server-side publishing jobs.
--
-- From 30 October 2026 Supabase no longer grants Data API access to tables automatically when a
-- schema is created from migrations (new projects, preview branches, `supabase db reset`).
-- Learner and staff access was always granted explicitly in the migrations; the GitHub workflows
-- that use the service_role key relied on the automatic grants, so they are made explicit here:
--   * englishmaster-content (scripts/release/publish_questions.py): inserts new question drafts,
--     upsert with ignore-duplicates on external_ref, returning the inserted rows.
--   * englishmaster-release (scripts/release/publish_apk.sh): registers a published APK.
-- On the live project these privileges already exist, so this changes nothing there.

grant select, insert on public.assessment_questions to service_role;
grant select, insert on public.app_releases to service_role;
