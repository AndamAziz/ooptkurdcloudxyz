# Publishing EnglishMaster on Google Play

Checklist of what Google Play asks for, and where each item is in this repository.

## 1. Build for Play

Play installs and updates the app itself, so the in-app updater is switched off and the
`REQUEST_INSTALL_PACKAGES` permission is removed (`app/src/play/AndroidManifest.xml`):

```sh
cd android
./gradlew bundleRelease -Penglishmaster.selfUpdate=false   # → app/build/outputs/bundle/release/app-release.aab
```

Upload the **.aab** (not the APK). The release workflow builds it on every `v*` tag next to the
direct-download APK and attaches it to the run as `englishmaster-<version>-google-play`.
The direct-download build (GitHub Releases / Supabase link) keeps `selfUpdate=true`.

**Play App Signing:** when Play Console asks for the app signing key, choose *Use a key from
Android Studio / Java keystore* and upload the existing release key (the one the APK is signed
with; Play Console shows the PEPK steps). Then people who installed the APK directly can update
from Play, because the signature is the same. If Play generates a new key instead, they must
uninstall the APK first.

| Requirement | State |
|---|---|
| Target API level | `targetSdk = 36` (Play requires 35+ for new apps and updates) |
| 64-bit, app bundle | pure Kotlin, no native code; `bundleRelease` |
| Adaptive + themed icon | `core/designsystem/src/main/res/drawable/ic_launcher_*.xml` (monochrome layer included) |
| Account deletion in the app | My account → Delete account (`delete_my_account()`, migration `20261021090000`) |
| Privacy policy | in the app (My account → Privacy policy) and `docs/privacy-policy.html` to host on a public URL |
| No ads, no third-party analytics | none in the app |

## 2. Store listing

* **App name:** EnglishMaster
* **Short description (EN, ≤80):** Learn English step by step, A1 to C2 — practice, exams and certificates.
* **Short description (ckb):** ئینگلیزی هەنگاو بە هەنگاو فێربە، لە A1 تا C2 — ڕاهێنان، تاقیکردنەوە و بڕوانامە.
* **Full description (EN):**
  > EnglishMaster helps Kurdish and English speakers learn English step by step, from A1 to C2.
  > • Thousands of grammar, vocabulary and reading questions, with stories, dialogues, pictures and short videos
  > • A placement test finds your level, and a short confirmation test makes sure of it
  > • Smart mistake review: a wrong answer comes back after 24 hours and again 3 days later
  > • Three checkpoints in every level, then a timed final exam with questions you have never practised
  > • A certificate for every level, as a PDF with a QR code anyone can check (issued after a teacher approves)
  > • Your path from A1 to C2: the next level is ready as soon as you finish one
  > • Practise by topic: Life in the UK, Britain, history and our world
  > • Listen button reads questions aloud in British English
  > • Works offline once a level is downloaded
  > • App in Kurdish (Sorani) or English
  > EnglishMaster certificates are achievements in this app, aligned to CEFR descriptors; they are not official CEFR or Life in the UK certificates.
* **Full description (ckb):**
  > ئینگلیشماستەر یارمەتی قسەکەرانی کوردی و ئینگلیزی دەدات هەنگاو بە هەنگاو ئینگلیزی فێربن، لە A1 تا C2.
  > • هەزاران پرسیاری ڕێزمان، وشە و خوێندنەوە، لەگەڵ چیرۆک، گفتوگۆ، وێنە و ڤیدیۆی کورت
  > • تاقیکردنەوەی دیاریکردنی ئاست، و تاقیکردنەوەیەکی کورتی دڵنیابوونەوە
  > • پێداچوونەوەی زیرەکی هەڵەکان: وەڵامی هەڵە دوای 24 کاتژمێر و دوای 3 ڕۆژ دەگەڕێتەوە
  > • سێ خاڵی پشکنین لە هەر ئاستێک، پاشان تاقیکردنەوەی کۆتایی بە کات و بە پرسیاری نوێ
  > • بڕوانامە بۆ هەر ئاستێک، وەک PDF لەگەڵ QR کە هەر کەسێک دەتوانێت پشکنینی بۆ بکات (دوای ڕەزامەندی مامۆستا)
  > • ڕێگاکەت لە A1 بۆ C2: ئاستی داهاتوو ئامادە دەبێت هەر کە ئاستێک تەواو دەکەیت
  > • دوگمەی گوێگرتن پرسیارەکان بە ئینگلیزی بەریتانی دەخوێنێتەوە
  > • دوای داگرتنی ئاستێک بێ ئینتەرنێت کار دەکات
  > • ئەپەکە بە کوردی (سۆرانی) یان ئینگلیزی
* **Graphics:** `docs/brand/icon-512.png` (hi-res icon), `docs/brand/feature-graphic-1024x500.png`
  (full logo with the name: `docs/brand/logo-full.png`; all from `scripts/media/make_logo.py`),
  phone screenshots in `docs/brand/screenshots/` (1080×1920, English and Sorani). They are the real
  screens with sample data, rendered by `feature/practice/.../StoreScreenshotTest.kt`:
  `STORE_SCREENSHOTS=$PWD/../docs/brand/screenshots ./gradlew :feature:practice:testDebugUnitTest --tests '*StoreScreenshotTest*'`.
  Play needs 2 to 8; screenshots from a real phone (exam result, certificate) can be added.
* **Category:** Education. **Tags:** Language learning.
* **Contact email:** required — the same address as in the privacy policy.

## 3. App content forms

* **Privacy policy URL:** host `docs/privacy-policy.html` (replace `CONTACT_EMAIL`) on your site, e.g. `https://kurdcloud.xyz/englishmaster/privacy`.
* **Ads:** No.
* **App access:** sign-in required — give reviewers a test learner account (email + password).
* **Target audience:** 13+ (choosing under-13 brings the Families policy and extra requirements).
* **Content rating (IARC):** Education; no violence, no user-to-user chat; teachers can add questions (moderated, not public).
* **Data safety:**

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Name | Yes | No | Account management, app functionality | No |
| Email address | Yes | No | Account management | No |
| App interactions (answers, progress, exams) | Yes | No | App functionality | No |

  Encrypted in transit: **Yes**. Users can request deletion: **Yes** (in the app, and by email).
  Supabase is a service provider (processing on our behalf), which Play does not count as "sharing".
* **Account deletion URL** (Play asks for a web link too): the privacy-policy page's "Deleting your data" section.

## 4. Before each release

1. `scripts/db/test-local.sh` and `./gradlew testDebugUnitTest lintDebug` pass (CI does both).
2. Bump `versionCode`/`versionName`, add `release-notes/<code>.en.txt` and `.ckb.txt`.
3. Build the bundle as above and upload to an internal-testing track first.
