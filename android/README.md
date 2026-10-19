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

Not in this slice: placement test, courses/lessons, AI tutor, speaking/listening, certificates,
teacher dashboard. The project is laid out so these become new `feature:*` modules.

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
./gradlew test             # 53 JVM/Robolectric tests (no device needed)
# opt-in, against a live project (creates no data):
SUPABASE_LIVE_URL=https://<ref>.supabase.co SUPABASE_LIVE_KEY=<publishable key> ./gradlew :core:network:test
./gradlew lintDebug
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # minified (R8), unsigned
```

Robolectric's Android runtime jar is resolved by Gradle and tests run with `robolectric.offline=true`
(see root `build.gradle.kts`), which avoids Maven Central rate limits during tests.

Install on a device or emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Release signing

Signing material must stay outside the repository (`*.jks`, `keystore.properties` are git-ignored).
Create an upload key, then either sign the unsigned APK with `apksigner` or add a `signingConfig`
that reads `keystore.properties`. Prefer an Android App Bundle (`./gradlew bundleRelease`) for Play
distribution with Play App Signing.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` (declared by the app); `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE` are merged from WorkManager for reliable background sync. Backups and device
transfer are disabled.

## Application ID

`app.englishmaster` (confirmed by the project owner). Kotlin package names
(`xyz.kurdcloud.englishmaster.*`) are internal and independent of the application ID.
