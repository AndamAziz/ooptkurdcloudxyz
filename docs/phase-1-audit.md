# EnglishMaster AI — Phase 1 Audit (Repository & Environment)

Date: 2026-10-19 · Branch: `claude/zen-ptolemy-87atl6` · Scope: inspection only, no application code changed.

## 1. What the repository actually is

A **Lovable-generated React web app** ("CEFR Practice") backed by Supabase. There is **no Android code** of any kind
(no Gradle files, no Kotlin, no manifest, no application ID, no build variants).

| Area | Found |
|---|---|
| Frontend | Vite 5 + React 19 + TypeScript 5.8 + shadcn/ui + Tailwind 3 + TanStack Query, PWA (`vite-plugin-pwa`) |
| Routes | `/`, `/auth`, `/reset-password`, `/leaderboard`, `/install`, `/verify/:certificateNumber?`, protected `/dashboard`, `/practice`, `/certificates`, `/admin`, `/admin/activity-log`, `/admin/students/:userId` |
| Learning model | MCQ question bank (`questions`, ~230 rows seeded from `public/data/*.csv/xlsx`), XP/streak gamification, placement test, level certificates |
| Backend | Supabase project ref `eismzvkrtisofnfdubqn` (`supabase/config.toml`), 31 migrations (2025-12-15 → 2025-12-17), 2 Edge Functions |
| Edge Functions | `admin-user-management` (reset password / delete / suspend / activate, Resend e-mail), `certificate-notification` (Lovable AI gateway → e-mail text) |
| Roles | `app_role` enum = `student`, `admin` only (no `teacher`) |
| Localization | English only; no Sorani/RTL |
| Tests | **None** (no test runner, no test files) |
| `.env` | Committed; contains only `VITE_SUPABASE_URL`, project id and the **publishable (anon) key** — public by design, no service-role key found in the repo |

### Schema (from migrations)
Tables: `profiles`, `user_roles`, `questions`, `attempts`, `practice_sessions`, `levels`, `certificates`, `admin_logs`, `rate_limits`.
Views: `questions_safe`, `leaderboard_view`. RLS is enabled on all nine tables.

## 2. Baseline checks (actually executed)

| Check | Result | Notes |
|---|---|---|
| `npm ci` | **FAILED** | `ERESOLVE`: `next-themes@0.3.0` peer-requires React ≤18, project uses React 19. Pre-existing. (`bun.lockb` suggests Lovable installs with bun.) |
| `npm ci --legacy-peer-deps` | PASSED | Used for the rest of the baseline; no tracked files changed |
| `eslint .` (project ESLint 9) | **FAILED** — 22 errors, 14 warnings | 18× `no-explicit-any`, 2× `no-empty-object-type`, 1× `prefer-const`, 1× `no-require-imports` (`tailwind.config.ts`); warnings: `exhaustive-deps`, `only-export-components`. Pre-existing |
| `tsc -p tsconfig.app.json --noEmit` (TS 5.8.3) | PASSED | But `strict: false`, `noImplicitAny: false`, `strictNullChecks: false` — weak guarantee |
| `vite build` | PASSED | Main chunk 2.43 MB (706 kB gzip) — size warning |
| Unit / integration / E2E tests | NOT RUN — none exist | |
| Live DB comparison | BLOCKED | The Supabase connector available to this session reaches a different project only; `eismzvkrtisofnfdubqn` is not accessible (likely Lovable Cloud-managed) |

## 3. Defects found in existing code (distinct from missing features)

Severity is my assessment; items marked *(verify on live DB)* are derived from migrations and could differ if the live schema drifted.

### Critical — integrity / authorization
1. **Students can forge certificates.** RLS policy `"Users can insert their own certificates"` lets any active user `INSERT` into `certificates` with arbitrary `score_percentage`, `cefr_level`, `status='earned'` and even `verified_by_admin=true`. The client (`src/hooks/useCertificateAward.ts`) is what decides eligibility.
2. **Students can set their own XP/level.** `"Users can update their own profile"` has no column restriction, so a user can `UPDATE profiles SET xp=…, current_cefr=…, placement_completed=…, streak=…` directly. `increment_user_xp` additionally allows +1000 XP per call, unlimited calls.
3. **Answer correctness is client-asserted.** `attempts.is_correct` is supplied by the client on insert; the XP trigger and certificate trigger trust it.
4. **Placement result is client-asserted.** `complete_placement_test(p_correct_answers, p_level_scores, p_recommended_level)` takes the score and the level from the client.
5. **Answer key is harvestable.** `check_answer` is `SECURITY DEFINER` and callable by any authenticated user for any question id without recording an attempt.

### High
6. **Schema drift — migrations are not reproducible.** `placement_results` and `profiles.placement_completed/placement_score/placement_date` are used by migrations and present in `src/integrations/supabase/types.ts`, but **no migration creates them**. A fresh `supabase db reset` would fail.
7. **Admin audit logging silently fails.** `admin_logs.action_type` CHECK allows only `view_student, export_data, verify_cert, suspend_user, award_cert, delete_user`, but `admin-user-management` writes `password_reset`, `user_deleted`, `user_suspended`, `user_activated`; the insert result is not checked.
8. **Public certificate verification likely doesn't work for third parties.** `/verify` uses the `certificates` table directly, whose RLS allows only owner/admin; anonymous verifiers get no row. *(verify on live DB)*
9. **`leaderboard_view` was recreated without `security_invoker`**, so it runs as owner and exposes every user's `user_id`, username and XP to all authenticated users (intentional for a leaderboard, but `user_id` is unnecessary).
10. `certificate-notification` checks admin with `.single()` on `user_roles` — breaks for multi-role users; uses a Lovable-only AI gateway key.

### Medium / hygiene
11. `npm ci` peer-dependency failure; `@types/react@18` with `react@19`.
12. 22 lint errors; TypeScript strictness disabled.
13. Admin password reset accepts ≥6 characters; CORS `*` on admin function.
14. `README.md` is the Lovable template with placeholder project id.

## 4. Can this environment build an Android APK?

**Yes for build + JVM unit tests; no for install/instrumented/UI tests.** Verified with a throwaway project in the scratchpad (not committed):

| Item | Value |
|---|---|
| JDK | OpenJDK 21.0.12 |
| System Gradle | 8.14.3 (too old for AGP 9 — use the wrapper) |
| Gradle wrapper used | 9.8.2 |
| AGP | 9.4.1 (built-in Kotlin) |
| Kotlin Compose compiler plugin | 2.4.21 |
| Compose BOM | 2026.09.00 — **requires compileSdk 37** (build failed on 36, passed on 37.2) |
| SDK | cmdline-tools, platform-tools, `platforms;android-37.2`, `build-tools;36.0.0` downloaded from dl.google.com (not preinstalled) |
| Result | `assembleDebug` → `app-debug.apk` (11.5 MB, debug-signed, verified with `aapt2`/`apksigner`); `testDebugUnitTest` 1/1 passed |
| Emulator / device | **Not possible**: no `/dev/kvm`, no USB device. Instrumented and Compose UI tests and the "install on device" step must run on your machine or in CI with an emulator (e.g. GitHub Actions + KVM) |

## 5. Recommended smallest end-to-end slice (Phase 2 + minimum of Phase 3)

**"Student signs in on Android, answers a practice question scored by the server, works offline, and the attempt syncs exactly once."**

That exercises auth, RLS, server-authoritative scoring, Room, WorkManager, idempotent sync, i18n (EN + Sorani RTL) and tests — without placeholder screens.

### Backend (new migrations, additive, no data deletion)
- `0001_baseline_drift.sql` — `CREATE TABLE IF NOT EXISTS placement_results` + missing `profiles` columns so migrations are reproducible.
- `0002_server_scored_attempts.sql` — `attempts.client_id uuid UNIQUE` (idempotency key) and RPC `submit_attempt(client_id, question_id, chosen_answer, answered_at)` that computes `is_correct` server-side, returns the explanation, and is a no-op on a duplicate `client_id`.
- `0003_lock_down_integrity.sql` (**needs your approval: changes live behaviour of the web app**) — drop student INSERT on `certificates`; column-restrict student `UPDATE` on `profiles` (e.g. `display_name`, `username` only); revoke `increment_user_xp`/`check_answer` from `authenticated` once the web app uses `submit_attempt`; fix `admin_logs` CHECK.
- `supabase/tests/rls_*.sql` (pgTAP) — student isolation, no self-XP, no self-certificate.

### Android (new `android/` directory; web app untouched)
```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew + wrapper
  app/                 single activity, Navigation Compose, Hilt, BuildConfig SUPABASE_URL/ANON_KEY from local.properties
  core/designsystem    Material 3 theme, Vazirmatn/Noto Naskh Arabic font for Sorani
  core/network         supabase-kt (Auth + PostgREST) or Retrofit+OkHttp against PostgREST/RPC
  core/database        Room: questions, pending_attempts (client_id, sync_state)
  core/data            QuestionRepository, AttemptRepository, SyncWorker (WorkManager)
  feature/auth         sign-in / register / reset (Supabase Auth, session in DataStore + Keystore-wrapped)
  feature/practice     question list → answer → server result; offline: queue + "pending sync" badge
  values/strings.xml + values-ckb/strings.xml (supportsRtl=true)
```
Tests in this slice: ViewModel state tests, Room DAO tests (Robolectric), SyncWorker idempotency test with MockWebServer, pgTAP RLS tests; Compose UI tests written but executed only where an emulator exists.

### Configuration you will need to supply
- Which Supabase project to target (see open questions) and its URL + anon key in `android/local.properties` (git-ignored).
- For the release phase only: upload keystore outside the repo.

## 6. Open questions / blockers

1. **Which Supabase project?** The repo's project `eismzvkrtisofnfdubqn` is not reachable from this session. Options: (a) connect/transfer it so migrations can be checked against the live DB, or (b) create a fresh project for EnglishMaster AI and keep the Lovable app on the old one.
2. **Is the existing web app in production with real students?** If yes, defects 1–5 should be fixed first as a hotfix migration, and the web client must be updated in the same change (it currently inserts certificates and `is_correct` itself).
3. **Teacher dashboard:** extend this web app (add `teacher` role) or start the separate React/TS dashboard the spec describes? Extending is the smaller step.
4. Minimum Android version (proposal: `minSdk 26`).
