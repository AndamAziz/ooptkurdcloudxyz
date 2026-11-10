#!/usr/bin/env python3
"""Insert the driving-theory questions (content/theory/*.json) into Supabase.

Road-sign questions carry a media_path into the question-media bucket, which
scripts/media/upload_question_media.sh fills from content/media before this runs.

Questions are inserted as drafts, so a teacher publishes a chapter once they have checked it
(Staff -> Driving theory -> Publish the drafts). Rows that already exist (same external_ref) are
left untouched, so a teacher's edits or publishing are never overwritten. Uses the PostgREST API
with the service-role key (CI secret), like publish_questions.py.

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
Usage: publish_theory_questions.py [--dry-run]
  --dry-run  only reads: reports how many questions are new and how many already exist.

Questions already in the database are also brought back in step with the files, so correcting a
question in the repository reaches learners. A question a teacher has edited in Staff tools keeps
their wording: the editor stamps it as theirs and the sync skips it.
"""
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from supabase_http import read as http_read

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    ROOT / "content" / "theory" / "uk-theory-questions.json",
    ROOT / "content" / "theory" / "road-and-traffic-signs.json",
    ROOT / "content" / "theory" / "essential-documents.json",
    ROOT / "content" / "theory" / "incidents.json",
]

# The source names its chapters; the database keys them by slug. Keep in step with
# scripts/db/build_theory_seed.py.
SLUGS = {
    "Alertness": "alertness",
    "Attitude": "attitude",
    "Safety & Your Vehicle": "safety-and-vehicle",
    "Safety Margins": "safety-margins",
    "Hazard Awareness": "hazard-awareness",
    "Vulnerable Road Users": "vulnerable-road-users",
    "Other Types of Vehicle": "other-vehicles",
    "Vehicle Handling": "vehicle-handling",
    "Motorway Rules": "motorway-rules",
    "Rules of the Road": "rules-of-the-road",
    "Road and Traffic Signs": "road-and-traffic-signs",
    "Essential Documents": "essential-documents",
    "Incidents, Accidents and Emergencies": "incidents",
}

URL = os.environ["SUPABASE_URL"].rstrip("/")
KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]


def auth() -> dict:
    return {"apikey": KEY, **({} if KEY.startswith("sb_secret_") else {"Authorization": f"Bearer {KEY}"})}


def get(path: str) -> list[dict]:
    req = urllib.request.Request(f"{URL}/rest/v1/{path}", headers=auth())
    return json.loads(http_read(req, timeout=60) or b"[]")


def sync(rows: list[dict]) -> int:
    """Brings rows the repository owns up to date; teacher-edited ones are skipped server-side."""
    body = [
        {k: r[k] for k in (
            "external_ref", "sort_order", "prompt", "prompt_ckb", "answer", "answer_ckb",
            "options", "options_ckb", "correct_option", "explanation", "explanation_ckb", "media_path")}
        for r in rows
    ]
    req = urllib.request.Request(
        f"{URL}/rest/v1/rpc/content_sync_theory_questions",
        data=json.dumps({"p_rows": body}).encode(),
        method="POST",
        headers={**auth(), "Content-Type": "application/json"},
    )
    return int(json.loads(http_read(req, timeout=120) or b"0") or 0)


def post(rows: list[dict]) -> int:
    req = urllib.request.Request(
        f"{URL}/rest/v1/theory_questions?on_conflict=external_ref&columns=" + ",".join(rows[0].keys()),
        data=json.dumps(rows).encode(),
        method="POST",
        headers={
            **auth(),
            "Content-Type": "application/json",
            "Prefer": "resolution=ignore-duplicates,return=representation",
        },
    )
    return len(json.loads(http_read(req, timeout=60) or b"[]"))


def existing(refs: list[str]) -> set[str]:
    """external_refs already in the database (read only)."""
    found: set[str] = set()
    for i in range(0, len(refs), 50):
        chunk = ",".join('"' + r.replace('"', "") + '"' for r in refs[i:i + 50])
        query = urllib.parse.urlencode({"select": "external_ref", "external_ref": f"in.({chunk})"})
        found |= {r["external_ref"] for r in get(f"theory_questions?{query}")}
    return found


def text(value) -> str | None:
    if not isinstance(value, str):
        return None
    collapsed = " ".join(value.split())
    return collapsed or None


def rows() -> list[dict]:
    sections = {s["slug"]: s["id"] for s in get("theory_sections?select=id,slug")}
    out: list[dict] = []
    for path in SOURCES:
        data = json.loads(path.read_text(encoding="utf-8"))
        for section in data["sections"]:
            slug = SLUGS.get(section["category"])
            if slug is None:
                raise SystemExit(f"unknown chapter {section['category']!r}; add it to SLUGS")
            if slug not in sections:
                raise SystemExit(f"chapter {slug!r} is not in the database; apply the theory migration first")
            for position, question in enumerate(section["questions"], start=1):
                prompt, answer = text(question.get("questionEn")), text(question.get("correctAnswer"))
                if not prompt or not answer:
                    raise SystemExit(f"{slug} #{question['id']} has an empty question or answer")
                # A sign question is the picture: without it there is nothing to answer.
                media = text(question.get("media"))
                if media and not (ROOT / "content" / "media" / media).is_file():
                    raise SystemExit(f"{slug} #{question['id']} names {media}, which is not in content/media")
                # A question may bring the official options; the app borrows some when it does not.
                options = question.get("options")
                options_ckb = question.get("optionsKu")
                correct = question.get("answerIndex")
                if options is not None:
                    if correct is None or not 0 <= correct < len(options):
                        raise SystemExit(f"{slug} #{question['id']} has options but no usable answerIndex")
                    if options[correct] != answer:
                        raise SystemExit(f"{slug} #{question['id']}: answerIndex points at {options[correct]!r}, not the answer")
                    if options_ckb is not None and len(options_ckb) != len(options):
                        raise SystemExit(f"{slug} #{question['id']} has {len(options_ckb)} Sorani options for {len(options)}")
                elif correct is not None:
                    raise SystemExit(f"{slug} #{question['id']} has an answerIndex but no options")
                out.append(
                    {
                        "external_ref": f"theory:{slug}:{question['id']}",
                        "section_id": sections[slug],
                        "sort_order": position,
                        "prompt": prompt,
                        "prompt_ckb": text(question.get("questionKu")),
                        "answer": answer,
                        "answer_ckb": text(question.get("correctAnswerKu")),
                        "options": options,
                        "options_ckb": options_ckb,
                        "correct_option": correct,
                        "explanation": text(question.get("explanationEn")),
                        "explanation_ckb": text(question.get("explanationKu")),
                        "media_path": media,
                        "status": "draft",
                        "source": f"content:{path.stem}",
                    },
                )
    return out


def main() -> None:
    questions = rows()
    if "--dry-run" in sys.argv[1:]:
        present = existing([r["external_ref"] for r in questions])
        print(f"theory: {len(questions)} questions, {len(questions) - len(present)} would be inserted, {len(present)} already present")
        return
    inserted = sum(post(questions[i:i + 50]) for i in range(0, len(questions), 50))
    # Inserting ignores a question already in the database, so a correction in the repository is
    # applied separately. Questions a teacher has edited are left as the teacher left them.
    updated = sum(sync(questions[i:i + 50]) for i in range(0, len(questions), 50))
    print(f"theory: {len(questions)} questions, {inserted} newly inserted, "
          f"{len(questions) - inserted} already present, {updated} brought up to date")


if __name__ == "__main__":
    main()
