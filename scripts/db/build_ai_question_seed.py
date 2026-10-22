#!/usr/bin/env python3
"""Build backend/supabase/seed/assessment_questions_<batch>.sql from content/questions/<batch>.json.

AI-drafted items are imported as status='draft', review_status='unreviewed', so students never
see them until a teacher approves them in Staff tools (approving a draft also publishes it).

Each item gives the correct answer and three distractors; option order is shuffled
deterministically from the prompt so the key is not always in the same position. True/False
items (Life in the UK) give "True" or "False" as the answer and the other as the only
distractor; their prompt starts "TRUE or FALSE: " and their options always read True, False. Items may
reference a shared reading passage ("passage": id from the batch's "passages") and/or media
({"type": "image"|"video", "path": "samples/x.png", "caption": ...}); media files must exist in
content/media/ (they are uploaded to the question-media bucket by the englishmaster-media workflow).

Re-running the seed only updates items that are still unreviewed drafts: a teacher's
approval, retirement or later edit is never overwritten.

Source-verified batches (Life in the UK) set "verified_only": true; then only items with
"verification": "verified" and a "source" page are imported, and items may carry a stable "ref".
Exam-only batches set "pool": "exam": their questions are only ever used in level exams (never
downloaded for practice), so a learner meets them for the first time in the exam.
Extra item fields (source, verification, difficulty, explanation_ckb, notes) are not imported;
they are review data checked by scripts/lituk/lituk.py.

Usage: python3 scripts/db/build_ai_question_seed.py [batch_json ...]
"""
import hashlib
import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [Path(p) for p in sys.argv[1:]] or sorted((ROOT / "content/questions").glob("ai-batch-*.json"))
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2", "LIFE_IN_UK"]
TRUE_FALSE = ["True", "False"]
SKILLS = {"grammar", "vocabulary", "reading"}
MEDIA_ROOT = ROOT / "content/media"


def q(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def arr(values) -> str:
    return "array[" + ",".join(q(v) for v in values) + "]::text[]"


def norm(text: str) -> str:
    return " ".join(text.lower().split())


def is_true_false(item: dict) -> bool:
    return sorted([item["answer"], *item["distractors"]]) == sorted(TRUE_FALSE)


def validate(item: dict, where: str, passages: dict) -> None:
    problems = []
    if item["level"] not in LEVELS:
        problems.append("unknown level")
    if item["skill"] not in SKILLS:
        problems.append("unknown skill")
    prompt = item["prompt"].rstrip()
    gaps = prompt.count("___")
    true_false = is_true_false(item)
    if not true_false and (gaps > 1 or (gaps == 0 and not prompt.endswith(("?", ":")))):
        problems.append("prompt needs one ___ gap, or must be a question ending in '?' or ':'")
    if "passage" in item and item["passage"] not in passages:
        problems.append(f"unknown passage {item['passage']!r}")
    media = item.get("media")
    if media:
        if media.get("type") not in ("image", "video"):
            problems.append("media type must be image or video")
        if not (MEDIA_ROOT / media.get("path", "")).is_file():
            problems.append(f"media file content/media/{media.get('path')} does not exist")
    options = [item["answer"], *item["distractors"]]
    if true_false:
        if not prompt.startswith("TRUE or FALSE: "):
            problems.append("a True/False item's prompt starts with 'TRUE or FALSE: '")
    elif len(item["distractors"]) != 3:
        problems.append("need exactly 3 distractors (or a True/False pair)")
    if len({norm(o) for o in options}) != len(options) or not all(o.strip() for o in options):
        problems.append("options must be non-empty and distinct")
    if not item.get("explanation", "").strip():
        problems.append("explanation required")
    if problems:
        raise SystemExit(f"{where}: {item['prompt']!r}: {', '.join(problems)}")


def is_importable(item: dict, verified_only: bool) -> bool:
    """In a "verified_only" batch, only items verified against their source (with a page) are imported."""
    if not verified_only:
        return True
    return item.get("verification") == "verified" and bool((item.get("source") or {}).get("page"))


def rows(src: Path) -> tuple[str, list[dict]]:
    """Validated rows of one batch, as column -> value (also used by publish_questions.py)."""
    batch, pairs = item_rows(src)
    return batch, [row for _, row in pairs]


def item_rows(src: Path, include_unimportable: bool = False) -> tuple[str, list[tuple[dict, dict]]]:
    """(item, row) pairs of one batch. Items held back by "verified_only" are skipped unless asked for."""
    data = json.loads(src.read_text(encoding="utf-8"))
    batch = data["batch"]
    passages = data.get("passages", {})
    verified_only = bool(data.get("verified_only"))
    pool = data.get("pool", "practice")
    if pool not in ("practice", "exam"):
        raise SystemExit(f"{src.name}: pool must be practice or exam")
    seen = set()
    refs = set()
    result = []
    for item in data["items"]:
        validate(item, src.name, passages)
        if not include_unimportable and not is_importable(item, verified_only):
            continue
        # A prompt may repeat only with a different passage or picture (e.g. "Which statement is true?").
        context = item.get("passage") or (item.get("media") or {}).get("path") or ""
        key = (item["level"], norm(item["prompt"]) + "|" + context)
        if key in seen:
            raise SystemExit(f"{src.name}: duplicate prompt in {item['level']}: {item['prompt']!r}")
        seen.add(key)
        # Batch 01 ids are derived from level + prompt only; keep that so existing rows keep their refs.
        digest = hashlib.sha256((f"{item['level']}|{norm(item['prompt'])}" + (f"|{context}" if context else "")).encode()).hexdigest()
        # A stable explicit id ("ref", e.g. "lituk:C3-0042") keeps the record identity when the
        # wording is corrected later; without one the id is derived from the prompt as before.
        ref = item.get("ref")
        if ref is not None:
            if not re.fullmatch(r"[a-z]+:[A-Za-z0-9._-]{1,60}", ref):
                raise SystemExit(f"{src.name}: invalid ref {ref!r} (expected e.g. lituk:C3-0042)")
            if ref in refs:
                raise SystemExit(f"{src.name}: duplicate ref {ref!r}")
            refs.add(ref)
            digest = hashlib.sha256(ref.encode()).hexdigest()
        options = [item["answer"], *item["distractors"]]
        if is_true_false(item):
            options = list(TRUE_FALSE)  # always "True", "False" in that order
        else:
            random.Random(digest).shuffle(options)
        media = item.get("media") or {}
        result.append((item, {
            "external_ref": ref or f"ai:{batch}:{digest[:12]}",
            "cefr_level": item["level"],
            "skill": item["skill"],
            "prompt": item["prompt"],
            "options": options,
            "correct_option": "ABCD"[options.index(item["answer"])],
            "explanation": item["explanation"].strip(),
            "tags": item.get("tags", []),
            "status": "draft",
            "review_status": "unreviewed",
            "source": f"ai-draft:{batch}",
            "passage": passages.get(item.get("passage", "")),
            "media_type": media.get("type"),
            "media_path": media.get("path"),
            "media_caption": media.get("caption"),
            "pool": pool,
        }))
        if pool == "exam" and item["level"] not in LEVELS[:6]:
            raise SystemExit(f"{src.name}: exam-only questions must be English levels, not {item['level']}")
    return batch, result


def build(src: Path) -> None:
    batch, items = rows(src)
    counts = dict.fromkeys(LEVELS, 0)
    values = []
    for r in items:
        counts[r["cefr_level"]] += 1
        opt = lambda v: q(v) if v else "null"
        values.append("(" + ",".join([
            q(r["external_ref"]), q(r["cefr_level"]) + "::public.cefr_level", q(r["skill"]),
            q(r["prompt"]), arr(r["options"]), q(r["correct_option"]), q(r["explanation"]),
            arr(r["tags"]), "'draft'", "'unreviewed'", q(r["source"]),
            opt(r["passage"]), opt(r["media_type"]), opt(r["media_path"]), opt(r["media_caption"]), q(r["pool"]),
        ]) + ")")

    out = ROOT / f"backend/supabase/seed/assessment_questions_{batch.replace('-', '_')}.sql"
    header = (
        f"-- GENERATED by scripts/db/build_ai_question_seed.py from content/questions/{src.name} — do not edit by hand.\n"
        f"-- {len(values)} AI-drafted questions: { {k: v for k, v in counts.items() if v} }\n"
        "-- Imported as draft + unreviewed: invisible to students until a teacher approves them.\n"
    )
    t = "public.assessment_questions"
    body = (
        f"insert into {t}\n"
        "  (external_ref, cefr_level, skill, prompt, options, correct_option, explanation, tags, status, review_status, source,\n"
        "   passage, media_type, media_path, media_caption, pool)\n"
        "values\n  " + ",\n  ".join(values) + "\n"
        "on conflict (external_ref) do update set\n"
        "  cefr_level = excluded.cefr_level, skill = excluded.skill, prompt = excluded.prompt,\n"
        "  options = excluded.options, correct_option = excluded.correct_option,\n"
        "  explanation = excluded.explanation, tags = excluded.tags, passage = excluded.passage,\n"
        "  media_type = excluded.media_type, media_path = excluded.media_path, media_caption = excluded.media_caption,\n"
        "  pool = excluded.pool\n"
        f"where {t}.status = 'draft' and {t}.review_status = 'unreviewed';\n"
    )
    out.write_text(header + body, encoding="utf-8")
    print(f"wrote {out.relative_to(ROOT)}: {len(values)} questions { {k: v for k, v in counts.items() if v} }")


if __name__ == "__main__":
    for source in SOURCES:
        build(source)
