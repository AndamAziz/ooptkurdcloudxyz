#!/usr/bin/env python3
"""Insert AI-drafted question batches (content/questions/ai-batch-*.json) into Supabase.

New questions are inserted as drafts; rows that already exist (same external_ref) are left
untouched, so a teacher's approval, edits or retirement are never overwritten. Uses the
PostgREST API with the service-role key (CI secret).

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
Usage: publish_questions.py [--dry-run] [batch.json ...]
  --dry-run  only reads: reports how many questions are new and how many already exist.
"""
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "db"))
import build_ai_question_seed as seed  # noqa: E402

URL = os.environ["SUPABASE_URL"].rstrip("/")
KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]


def post(rows: list[dict]) -> int:
    req = urllib.request.Request(
        f"{URL}/rest/v1/assessment_questions?on_conflict=external_ref&columns="
        + ",".join(rows[0].keys()),
        data=json.dumps(rows).encode(),
        method="POST",
        headers={
            "apikey": KEY,
            "Content-Type": "application/json",
            "Prefer": "resolution=ignore-duplicates,return=representation",
            **({} if KEY.startswith("sb_secret_") else {"Authorization": f"Bearer {KEY}"}),
        },
    )
    with urllib.request.urlopen(req, timeout=60) as res:
        return len(json.loads(res.read() or b"[]"))


def existing(refs: list[str]) -> set[str]:
    """external_refs already in the database (read only)."""
    found = set()
    for i in range(0, len(refs), 50):
        chunk = ",".join('"' + r.replace('"', '') + '"' for r in refs[i:i + 50])
        query = urllib.parse.urlencode({"select": "external_ref", "external_ref": f"in.({chunk})"})
        req = urllib.request.Request(
            f"{URL}/rest/v1/assessment_questions?{query}",
            headers={"apikey": KEY, **({} if KEY.startswith("sb_secret_") else {"Authorization": f"Bearer {KEY}"})},
        )
        with urllib.request.urlopen(req, timeout=60) as res:
            found |= {r["external_ref"] for r in json.loads(res.read() or b"[]")}
    return found


def main() -> None:
    args = sys.argv[1:]
    dry_run = "--dry-run" in args
    sources = [Path(p) for p in args if p != "--dry-run"] or seed.SOURCES
    for src in sources:
        batch, rows = seed.rows(src)
        if not rows:
            print(f"{batch}: nothing importable")
            continue
        if dry_run:
            present = existing([r["external_ref"] for r in rows])
            print(f"{batch}: {len(rows)} questions, {len(rows) - len(present)} would be inserted, {len(present)} already present")
            continue
        inserted = sum(post(rows[i:i + 50]) for i in range(0, len(rows), 50))
        print(f"{batch}: {len(rows)} questions, {inserted} newly inserted, {len(rows) - inserted} already present")


if __name__ == "__main__":
    main()
