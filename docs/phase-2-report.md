# EnglishMaster AI — Phase 2 report: first end-to-end slice

Date: 2026-10-19 · Branch: `claude/zen-ptolemy-87atl6`

**Slice:** a student registers or signs in on Android, downloads a level, practises offline or
online, the server scores every answer, and offline answers synchronise exactly once. The UI is in
English or Sorani (RTL).

## What was added

| Path | Content |
|---|---|
| `backend/supabase/migrations/` | 2 migrations: identity/roles/profiles/audit log; question bank + server-scored practice |
| `backend/supabase/seed/` | 1,410 de-duplicated, licence-safe practice questions (A1–C2), flagged unreviewed |
| `backend/supabase/tests/database/` | 45 pgTAP assertions (RLS isolation, no self-grading, idempotency, rate limit, suspension, admin bootstrap, audit integrity, seed sanity) |
| `scripts/db/` | seed generator; Supabase emulation + local test runner (no Docker needed) |
| `android/` | 8-module Gradle project (see `android/README.md`), 53 tests |
| `.github/workflows/englishmaster-ci.yml` | runs both suites + builds APKs on GitHub |
| `docs/api-contract.md`, `backend/README.md` | contract, schema/policy table, deployment and admin bootstrap |

The Lovable web app (`src/`, `supabase/`) is untouched.

## Test results (executed in this environment)

| Suite | Result |
|---|---|
| pgTAP on PostgreSQL 16 + Supabase emulation (`scripts/db/test-local.sh`) | **45/45 passed** (seed 5, identity 17, practice 23) |
| Android JVM/Robolectric (`./gradlew test`, forced re-run, no cache) | **53/53 passed** — model 5, network 8, database 5, data 16, auth 7, practice 9, app 3 |
| Android Lint (`lintDebug`) | 0 errors; 1 warning (`OldTargetApi`: targetSdk 36 kept on purpose until API 37 behaviour is tested on a device) |
| `assembleDebug` | ✅ `app-debug.apk` 15 MB, debug-signed, verified with `apksigner`/`aapt2` |
| `assembleRelease` (R8, minified) | ✅ `app-release-unsigned.apk` 2.2 MB; **unsigned** (no keystore exists); runtime of the minified build not tested |
| Secret scan of both APKs (`service_role`, `sb_secret`, JWTs) | none found |
| Install/run on device or emulator, instrumented tests | **not run** — no KVM/device in this environment |
| Against the hosted Supabase project | **not run** — migrations not applied yet (awaiting approval) |
| GitHub Actions workflow | added; first run happens on push |

Defects found and fixed during the work (all caught by the tests or lint before commit): RTL not
applied without `supportsRtl`; `DataStore` not exposed by `core:data`; missing
`ACCESS_NETWORK_STATE` in the library manifest; a worker could start before the session was
loaded; the trailing-lambda regression that the clean build caught; ungrammatical count strings.

## Content findings

* 620 bank rows come from OOPT (Oxford Online Placement Test) documents → excluded pending licence.
* 1,970 of 3,380 "generated" rows were duplicates once the `(vNN)` variant suffix was removed.
* Spot checks found ambiguous items; all seeded items are flagged `unreviewed` and labelled in the
  app. They are suitable for practice, not for placement decisions or certificates.

## Configuration still required

1. **Approval to apply** the two migrations and the seed to `nefpgqoykfndjvrdultp`; then run the security advisors.
2. Auth settings: e-mail confirmation on, minimum password length 8.
3. Supabase anon/publishable key in `android/local.properties` (or CI env) to connect the app.
4. Run `private.bootstrap_first_admin('<owner e-mail>')` once after the owner signs up.
5. Confirm the application ID `xyz.kurdcloud.englishmaster`.

## Known gaps / risks

* **Password reset** sends the user to the project's Site URL; no reset page exists for the new project yet.
* No device test yet: Keystore encryption, WorkManager scheduling and the per-app locale switch on a
  real Android version are verified only by unit/Robolectric tests (Robolectric has no Keystore;
  a fake cipher is used there).
* Sorani copy was written by me and needs review by a native speaker; the system font is used for
  Arabic script (no bundled Kurdish font yet).
* `assessment_questions` has no teacher/admin write API yet; content changes go through SQL/seed.
* The web app still runs on the old project with the defects listed in the phase-1 audit.

## Proposed next step

Apply the backend (after approval), connect a debug build to it and run the slice on a physical
device. Then add, in this order: the placement assessment (configurable thresholds and teacher
review), the teacher review queue for question content, and the course/lesson package format.
