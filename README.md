# EnglishMaster

An Android app for people preparing to live, work and drive in the UK. It teaches English from
A1 to C2, the Life in the UK test, and the DVSA car theory test, in English with a Sorani
Kurdish translation beside every question, and it keeps working when the phone is offline.

<p align="center">
  <a href="https://github.com/AndamAziz/ooptkurdcloudxyz/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/AndamAziz/ooptkurdcloudxyz?label=latest&style=flat-square"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=flat-square">
  <img alt="Supabase" src="https://img.shields.io/badge/Supabase-Postgres-3ECF8E?style=flat-square">
</p>

---

## What a learner gets

| | |
|---|---|
| **English practice** | A placement test puts the learner at A1–C2, then practice sessions, checkpoints and exams follow their level. Answers sync when the phone comes back online. |
| **Life in the UK** | The official syllabus as practice questions and a 24-question mock test on the real pass mark. |
| **Driving theory** | All fifteen chapters of the DVSA car test: study a chapter, test a chapter, or sit a 50-question mock paper timed like the real thing. |
| **Both languages** | Every question carries a Sorani translation the learner can show or hide. The app's own text follows the phone's language. |
| **Offline** | A downloaded chapter — questions, sign pictures, video clips — works with no connection. Answers queue up and sync later. |
| **Spoken** | A speaker reads the English question and answer aloud, for learners who are stronger by ear. |

### The driving theory course

869 questions over fifteen chapters, each with its answer, a short explanation and a Sorani
translation:

| # | Chapter | Questions | | # | Chapter | Questions |
|---:|---|---:|---|---:|---|---:|
| 1 | Alertness | 26 | | 9 | Motorway rules | 57 |
| 2 | Attitude | 39 | | 10 | Rules of the road | 67 |
| 3 | Safety and your vehicle | 82 | | 11 | Road and traffic signs | 216 |
| 4 | Safety margins | 34 | | 12 | Essential documents | 28 |
| 5 | Hazard awareness | 78 | | 13 | Incidents, accidents and emergencies | 56 |
| 6 | Vulnerable road users | 71 | | 14 | Vehicle loading | 21 |
| 7 | Other types of vehicle | 22 | | 15 | Video clips | 27 |
| 8 | Vehicle handling | 45 | | | **Total** | **869** |

Chapter 11 teaches all 150 signs of the Highway Code and then asks how the system works — what a
blue circle orders, what a triangle warns of — and tests traffic lights, police and arm signals,
motorway signals and the markings painted on the road. Chapter 15 plays a video clip and asks
three questions about what it showed, the way the real test does.

---

## How it is built

```
android/            Kotlin, Jetpack Compose, Room, Retrofit; minSdk 26, targetSdk 36
├── app/            navigation, the in-app updater, release notes
├── core/
│   ├── model/      the domain: questions, levels, theory, review schedule
│   ├── data/       repositories; offline-first, Room is the source of truth
│   ├── database/   Room (schema v7, every version auto-migrated and exported)
│   ├── network/    Retrofit against Supabase PostgREST
│   └── designsystem/  theme, shared composables, the speaker
└── feature/        auth · practice · exam · admin (staff tools)

backend/supabase/   26 migrations, row-level security, 23 pgTAP test files (327 tests)
content/            the question banks and their media, as JSON the pipeline publishes
scripts/            db/ seed and test · release/ publish content and APKs · media/ upload
.github/workflows/  CI · content · release
```

**Offline-first.** Every screen reads from Room. The network fills Room and takes answers away
again; nothing on screen waits for a request. A chapter counts as out of date when it holds
fewer questions than the course publishes, or when the course changed after the device saved it,
so a correction reaches a learner who downloaded the chapter months ago.

**The questions are data, not code.** They live in `content/` as JSON. A push to `main` runs the
content workflow, which uploads the pictures and clips to the `question-media` bucket and inserts
the questions as drafts; a teacher publishes a chapter from Staff tools when they have checked
it. A correction in the repository reaches rows the repository owns; a question a teacher has
edited keeps the teacher's wording.

**Updates reach people.** Tagging `vX.Y.Z` builds a signed APK, attaches it to the release with
its checksum, and registers it for the in-app updater with the "What's new" text in both
languages. The app checks on launch and again when it returns to the foreground.

---

## Working on it

```sh
# the app
cd android && ./gradlew test lintDebug assembleDebug

# the database: starts Postgres in Docker, applies every migration, runs the pgTAP suite
scripts/db/test-local.sh
```

`android/local.properties` holds the Supabase URL and publishable key (copy
`local.properties.example`); release signing comes from `keystore.properties` or CI secrets.
Neither is committed.

### Releasing

1. Merge the change into `main`.
2. Tag `vX.Y.Z` — it must match `versionName` in `android/app/build.gradle.kts`, and
   `android/release-notes/<versionCode>.{en,ckb}.txt` must exist, or the build fails.
3. The release workflow signs the APK, verifies the signer, publishes it and registers it for
   in-app updates.

### Adding questions

Put them in the right file under `content/theory/` or `content/questions/`, with the pictures
under `content/media/`, and push. The publisher refuses a question that is missing its picture,
whose `answerIndex` does not point at its answer, or whose answer is a bare letter with nothing
to choose from. Nothing is ever deleted: a question that should go is retired, so the answers
people have already given to it are kept.

---

## Licence and content

The app's code is the project owner's. The driving theory material is derived from the Highway
Code and DVSA practice questions, and the English and Life in the UK banks were written for this
project.
