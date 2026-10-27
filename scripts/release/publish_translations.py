#!/usr/bin/env python3
"""Apply the Kurdish translations in content/translations/ckb/*.json to Supabase.

Only the *_ckb columns are written (see migration 20261027120000_translation_content): the English
question, its options and its answer key are never touched, so a re-run is safe and changes
nothing a learner was scored against. Rows that already hold the same translation are skipped
server-side, so a second run reports 0 updated.

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
Usage: publish_translations.py [--dry-run] [LEVEL ...]
  --dry-run  only reads the files and reports what would be sent.
  LEVEL      limits it to those files (A1, A2, B1, B2, C1, C2, LIFE_IN_UK).
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "content" / "translations" / "ckb"
BATCH = 200

URL = os.environ["SUPABASE_URL"].rstrip("/")
KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]


def send(rows: list[dict]) -> int:
    """Applies one batch; returns how many question rows it changed."""
    req = urllib.request.Request(
        f"{URL}/rest/v1/rpc/content_set_question_translations",
        data=json.dumps({"p_rows": rows}).encode(),
        method="POST",
        headers={
            "apikey": KEY,
            "Content-Type": "application/json",
            **({} if KEY.startswith("sb_secret_") else {"Authorization": f"Bearer {KEY}"}),
        },
    )
    with urllib.request.urlopen(req, timeout=120) as res:
        return int(json.loads(res.read() or b"0") or 0)


def main() -> None:
    args = sys.argv[1:]
    dry_run = "--dry-run" in args
    levels = {a for a in args if a != "--dry-run"}
    for path in sorted(SRC.glob("*.json")):
        if levels and path.stem not in levels:
            continue
        rows = [
            {
                "external_ref": ref,
                "prompt_ckb": t.get("prompt"),
                "options_ckb": t.get("options"),
                "explanation_ckb": t.get("explanation"),
                "passage_ckb": t.get("passage"),
            }
            for ref, t in sorted(json.loads(path.read_text(encoding="utf-8")).items())
        ]
        if dry_run:
            print(f"{path.stem}: {len(rows)} translations would be sent")
            continue
        changed = sum(send(rows[i:i + BATCH]) for i in range(0, len(rows), BATCH))
        print(f"{path.stem}: {len(rows)} translations sent, {changed} questions updated")


if __name__ == "__main__":
    main()
