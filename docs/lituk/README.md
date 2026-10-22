# Life in the UK question bank: process and tools

## Where things are

| What | Path |
|---|---|
| Questions (import source) | `content/questions/ai-batch-*.json` (level `LIFE_IN_UK`) |
| Review data per question (id, section, verification, evidence, flags, duplicates) | `content/lituk/question-meta.json` |
| Working outline of the handbook (chapters, sections, mapping keywords) | `content/lituk/handbook-outline.json` |
| Audit output | `docs/lituk/audit-report.md`, `docs/lituk/coverage-checklist.md`, `content/lituk/audit.json` |
| Tools | `scripts/lituk/lituk.py`, tests in `scripts/lituk/test_lituk.py` |
| The handbook file itself | `content/lituk/private/` (git-ignored, **never commit it**) |

## How the questions reach the app

1. `scripts/db/build_ai_question_seed.py` validates every batch and writes `backend/supabase/seed/*.sql`
   (local and CI databases). Re-running it only updates questions that are still unreviewed drafts.
2. On push, the `englishmaster-content` workflow runs `scripts/release/publish_questions.py`, which
   **inserts new questions as drafts and never changes or deletes existing rows** (conflict on
   `external_ref` → ignored). `--dry-run` reports what would be inserted without writing.
3. A teacher or admin publishes drafts in the app (Staff tools → Questions → Life in the UK).
4. Learners download published questions (`get_practice_questions_v3('LIFE_IN_UK')`); answers are
   scored on the server (`submit_practice_response`). Chapter practice uses the `lituk_*` tags; the
   mock test draws 24 questions from the whole set.

Safety checks: `lituk.py validate` and its unit tests run in CI; `scripts/db/test-local.sh`
re-imports every seed file and fails if anything changes; pgTAP test 13 covers the course.

## Verification statuses

`pending_handbook` (default) → `verified` only with a handbook section **and page** in `source`.
`corroborated_external` = confirmed by a current official page (URL and quote in `evidence`); it
still needs the handbook check. `needs_review` = a recorded doubt; `rejected` = to be retired by
staff in the app (the tools never delete anything). Duplicates are marked with `duplicate_of`
and kept until the bank is finalised.

## Adding or correcting questions from the handbook

1. Put the PDF in `content/lituk/private/`.
2. Confirm the table of contents: correct `handbook-outline.json` (headings, page ranges) and set
   `toc_status` to `CONFIRMED`.
3. Work section by section: inventory the examinable facts with page numbers; check each existing
   question mapped to the section (set `verification`, `source.section`, `source.page`, `evidence`);
   correct or reject wrong ones; write new questions only for facts not yet covered.
4. New questions go in a new batch with `"verified_only": true`. Each item has a stable `ref`
   (e.g. `lituk:C3-0042`), `source` `{chapter, section, page}`, `verification`, optional
   `difficulty` (`easy`/`medium`/`hard`), `notes` and `explanation_ckb`. Only verified items with a
   page are imported.
5. Run `python3 scripts/lituk/lituk.py audit`, `python3 -m unittest scripts/lituk/test_lituk.py`,
   `python3 scripts/db/build_ai_question_seed.py` and `scripts/db/test-local.sh`; tick the
   section in the coverage checklist only after it has been read in the book.
6. Compare with the live database: run the SQL from `lituk.py live-sql`, save the JSON result and
   run `lituk.py compare-live result.json` (reports missing, extra and changed rows).

## Not yet supported (needs a decision)

- Sorani explanations are stored in the review data only; showing them in the app needs a new
  column (e.g. `explanation_ckb`) and an app change.
- Difficulty is not stored in the database or used by the app.
