# EnglishMaster on the Apple App Store

There is no iPhone app yet: EnglishMaster is an Android app (Kotlin + Jetpack Compose). This page
lists what Apple requires, what is already in place, and the route to an iOS version.

## What is already ready

| Apple requirement | State |
|---|---|
| App icon 1024×1024, no transparency, no rounded corners | `docs/brand/ios-app-icon-1024.png` (from `scripts/media/make_logo.py`) |
| Account deletion inside the app (guideline 5.1.1(v)) | backend `delete_my_account()`; the Android screen shows the flow to copy |
| Privacy policy URL | `docs/privacy-policy.html`, the same page as for Google Play |
| No third-party or social login, so Sign in with Apple is **not** required (guideline 4.8) | email and password only |
| No ads, no tracking: App Tracking Transparency prompt not needed | none in the app |
| Backend shared with Android | Supabase: the same RPCs, rules and question bank |

## What an iOS version needs

1. **Apple Developer Program**: USD 99 per year, enrolled as an individual or an organisation.
2. **A Mac with Xcode** (or a macOS CI runner, such as GitHub Actions `macos-15`) to build, sign and upload.
   iOS apps cannot be built or tested on Linux.
3. **The app itself.** The recommended route is **Compose Multiplatform**:
   * move `core:model`, the network layer (Ktor instead of OkHttp) and the database (Room supports
     iOS since 2.7) into shared Kotlin modules;
   * reuse the Compose screens (`feature:*`) on iOS, with a small Swift entry point;
   * replace Android-only parts: text to speech (AVSpeechSynthesizer), PDF certificates
     (UIGraphicsPDFRenderer), the in-app updater (not allowed on iOS: the App Store updates apps).
4. **App Store Connect listing**: name, subtitle (30 characters), description, keywords, support URL,
   privacy policy URL, age rating questionnaire (Education, 4+), and App Privacy details
   (name, email address, and app interactions; used for app functionality, not for tracking).
5. **Screenshots**: 6.9-inch iPhone (1320×2868 or 1290×2796) is required; iPad screenshots only if
   the app supports iPad.
6. **App Review**: give the reviewer a test learner account (email and password) in the review notes.

## Cheaper alternative

A web version (the same Supabase backend in a browser, installable to the iPhone home screen as a
Progressive Web App) avoids the Apple fee and App Review, but it is not listed on the App Store.
