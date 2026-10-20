# EnglishMaster AI — Android student app

Kotlin · Jetpack Compose (Material 3) · single activity · ViewModel + StateFlow · Navigation
Compose · Room · DataStore + Android Keystore · Retrofit/OkHttp · WorkManager.

## What works in this slice

* Welcome with **interface-language choice** (English / کوردی سۆرانی, RTL), stored per app via AppCompat.
* Register (with consent), sign in, password-reset request, "check your e-mail" flow. Session tokens
  are stored encrypted (AES-GCM, key in Android Keystore); tokens are refreshed automatically and an
  expired session returns the user to sign-in with an explanation.
* Home: levels A1–C2, per-level **download** (paged, cancellable, atomic swap — a failed download never
  leaves a partial pack), update and remove; offline banner; marked-answer progress; pending-answer
  count with "Send now".
* Practice: 10 questions per session from downloaded content (unanswered first). English content
  stays LTR inside the Sorani UI. Answers are **scored only by the server**:
  * online → immediate verdict, correct option and explanation;
  * offline → "saved, will be marked when online"; WorkManager uploads when a network is available,
    and the open session updates live when the verdict arrives.
  * Uploads are idempotent (device-generated response id), so retries cannot create duplicates.
* No answer keys, service keys or secrets are on the device.

* **Staff tools** (menu → Staff tools, shown only when the server reports a staff role): admins
  manage users (search, teacher/admin roles with confirmation, suspend/reactivate) and see the
  activity log; teachers and admins review questions (approve, retire, publish again). The server
  enforces every permission; the admin cannot demote or suspend themselves.

* **In-app updates** (direct distribution): checks Supabase for a newer signed release, downloads
  it with SHA-256 verification and installs it through Android's installer. See
  [`docs/releasing.md`](../docs/releasing.md).

* **Level exams and certificates** (online): a level's final exam unlocks after practising at least
  30 of its questions (or all, if fewer) with the latest answer correct for >= 70%. The exam is 20
  random questions, 30 minutes, chosen and marked by the server; >= 80% passes and issues a
  certificate. Certificates are rendered on the device as an A4 PDF with a QR code that opens the
  public check `functions/v1/verify-certificate?code=EM-…`. A failed (or abandoned) attempt opens
  the next one after 24 hours.
* **Placement test**: 18 questions (3 per level, A1–C2), recommends a starting level.
* **Dashboard**: streak, answers, certificates, per-level progress towards the exam, "how to earn a
  certificate", and **Review mistakes** (downloaded questions whose latest marked answer was wrong;
  works offline).
* **Richer questions**: reading passages, pictures (saved with the level pack, so they work offline)
  and short videos (streamed). Shown in practice, exams and staff review.
* **Staff**: teachers and admins can correct learners' names (used on future certificates), add
  questions with a picture or video upload, and publish all reviewed drafts of a level at once.
  Role and suspension changes remain admin-only.
* Design: indigo/teal theme with level colours, rounded cards, dark mode.

Not in this slice: courses/lessons, AI tutor, speaking/listening, teacher dashboard. The project is laid out so these become new `feature:*` modules.

## Modules

| Module | Responsibility |
|---|---|
| `core:model` | Pure Kotlin domain types and input rules (JVM tests) |
| `core:network` | Supabase Auth + PostgREST services, token refresh, error mapping |
| `core:database` | Room schema (`schemas/` is exported and committed) |
| `core:data` | Repositories, encrypted session store, connectivity, sync engine + WorkManager worker |
| `core:designsystem` | Theme and shared loading/error/empty/offline components; declares RTL support |
| `feature:auth` | Welcome, sign-in, register, reset, check-email |
| `feature:practice` | Home (levels, downloads, sync) and practice session |
| `feature:admin` | Staff tools: users and roles, question review, activity log |
| `feature:exam` | Level exams, placement test, certificates (PDF + QR via zxing, shared through a FileProvider) |
| `app` | Application, manual DI container, navigation, manifest, resources |

## Toolchain (verified 2026-10-19)

| | Version |
|---|---|
| JDK | 17+ (built with OpenJDK 21) |
| Gradle (wrapper) | 9.8.2, checksum-pinned |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Kotlin / Compose compiler | 2.4.21 |
| Compose BOM | 2026.09.00 — requires **compileSdk 37** |
| compileSdk / targetSdk / minSdk | 37.2 / 36 / 26 |

SDK packages: `platforms;android-37.2`, `build-tools;36.0.0`, `platform-tools`.

## Configure

Every build connects to project `nefpgqoykfndjvrdultp` by default (`englishmaster.supabase.*` in
`gradle.properties`; the publishable key is public by design). To point a build elsewhere, set
`supabase.url` / `supabase.anonKey` in `local.properties` or the `SUPABASE_URL` /
`SUPABASE_ANON_KEY` environment variables. `sdk.dir` goes in `local.properties`
(see `local.properties.example`).

## Build and test

```bash
./gradlew test             # 117 JVM/Robolectric tests (no device needed)
# opt-in, against a live project (creates no data):
SUPABASE_LIVE_URL=https://<ref>.supabase.co SUPABASE_LIVE_KEY=<publishable key> ./gradlew :core:network:test
./gradlew lintDebug
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # minified (R8), unsigned
```

Robolectric's Android runtime jar is resolved by Gradle and tests run with `robolectric.offline=true`
(see root `build.gradle.kts`), which avoids Maven Central rate limits during tests.

Install on a device or emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Release signing and publishing

Release builds are signed when `keystore.properties` or the `ANDROID_KEYSTORE_*` environment
variables are present (both git-ignored / CI secrets). Releases are published by pushing a
`v<versionName>` tag — see [`docs/releasing.md`](../docs/releasing.md). R8 minification is
currently off for release builds until a minified build has been verified on a device.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` (in-app updates; the user confirms every install); `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE` are merged from WorkManager for reliable background sync. Backups and device
transfer are disabled.

## Application ID

`app.englishmaster` (confirmed by the project owner). Kotlin package names
(`xyz.kurdcloud.englishmaster.*`) are internal and independent of the application ID.
