# Releasing EnglishMaster (in-app updates)

The Android app updates itself when it is distributed directly (not through Google Play):

1. On start (and from **Menu → Check for updates**) it reads the newest published row of
   `public.app_releases` (public, read-only).
2. If `version_code` is higher than the installed one it offers the update; it is **mandatory**
   when the installed version is below `min_supported_version_code`.
3. It downloads the APK from the public Storage bucket `app-releases`, checks the exact size and
   SHA-256 from the release row, and deletes the file on any mismatch.
4. It hands the APK to Android's `PackageInstaller`. Android always shows a confirmation; the first
   time it also asks the user to allow "Install unknown apps" for EnglishMaster.

Android installs an update only if it is signed with **the same key** as the installed app, so
every release must be signed with the release key (SHA-256 fingerprint
`d4c2a652fb12c56926856c912b0b488995bf4bea3b2a06073b4857940a1085d7`). Losing that key means users
must uninstall and reinstall. Keep at least two offline backups of the keystore and its password.

## One-time setup (repository owner)

GitHub → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_ENCRYPTION_KEY` | passphrase that decrypts `android/release-signing/englishmaster-release.jks.enc` (AES-256, PBKDF2 600k). Alternative: `ANDROID_KEYSTORE_BASE64` with the whole keystore as base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `englishmaster` |
| `ANDROID_KEY_PASSWORD` | key password (same as keystore password) |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase → Project Settings → API Keys → a **secret** key (`sb_secret_…`) or the legacy `service_role` key |

These values never go into the repository or the app. The keystore in the repository is encrypted;
without the passphrase it is unusable (and the keystore itself is also password-protected).
Every push runs a **Release secrets check** job in CI that decrypts the key and tests the Supabase
key without publishing anything, so problems show up before a tag is created. The service key is used only by the release
job to upload the APK and register the release.

## Publishing a release

1. Raise `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Optional: `android/release-notes/<versionCode>.en.txt` and `.ckb.txt` (shown in the update
   dialog), and `<versionCode>.min-supported` containing a version code to force older apps to update.
3. Commit, then tag and push: `git tag v<versionName> && git push origin v<versionName>`.

The **EnglishMaster release** workflow then: checks all secrets, checks the tag matches
`versionName`, runs the tests, builds and signs the release, refuses any APK not signed with the
release key, uploads it (never overwriting an existing file) and registers it as published.
Installed apps see it on their next start.

To withdraw a release, set `published = false` on its row in the Supabase table editor.

## Release history

| Version | Code | Published | Verified |
|---|---|---|---|
| 0.3.2 | 6 | 2026-10-19 by tag `v0.3.2` | Downloaded APK matches the `app_releases` row (size 10,768,200 B, SHA-256 `8de6d3ba…26d7a`) and the release signer; **in-app update installed successfully on the owner's phone** |

Devices that still run a debug build (CI `app-debug-apk` artifact) are signed with a different key
and cannot update in place: uninstall once, then install the published APK.

## Where to download the APK

Two links, both the same file:

* the release page on GitHub, which carries `englishmaster-<version>-<code>.apk` and a `.sha256`
  file beside it;
* `https://nefpgqoykfndjvrdultp.supabase.co/storage/v1/object/public/app-releases/englishmaster-<version>-<code>.apk`,
  which is also where the in-app updater fetches from.

Hand out one of those links rather than the file itself. The in-app updater checks the size and
SHA-256 before it installs anything; a copy passed through a chat app has nothing checking it.

## "App not installed"

Android gives this one dialog for several different refusals and never says which. The app's icon
and name appearing in the dialog only means the APK could be read, not that it is intact. In order
of how often it is the answer:

1. **Something else is installed under `app.englishmaster` with a different signing key.** Most
   often a debug build, or an APK from before the release key existed. Android will never replace
   it with a differently signed one. Remove the app and install again — answers and progress are on
   the server, so signing in brings them back. This is the only cause that uninstalling fixes.
2. **The file arrived altered.** A chat app that recompressed or truncated it, or a download that
   stopped early. The signature then fails and the install is refused. Compare the download against
   the `.sha256` on the release page (`sha256sum` on a computer, a file manager that shows hashes on
   a phone) and download again from one of the links above if it differs.
3. **An older version over a newer one.** Android refuses a lower `versionCode`. Check *My account*
   in the app for the version that is installed.
4. **No free storage**, which Android reports the same way.

To tell 1 from 2, check the checksum first: it is a definite answer either way. If the file is
right, it is a signature clash and the app has to be removed first.

## Local signed build

Create `android/keystore.properties` (git-ignored):

```
storeFile=/absolute/path/englishmaster-release.jks
storePassword=…
keyAlias=englishmaster
keyPassword=…
```

then `./gradlew assembleRelease`. Without it, release builds are unsigned.

## Google Play

Play forbids apps that update themselves outside Play. For a Play build set
`englishmaster.selfUpdate=false` in `android/gradle.properties` (disables the check and menu entry)
and remove `REQUEST_INSTALL_PACKAGES` from the manifest.
