#!/usr/bin/env python3
"""Builds SQL that stores the Kurdish (Sorani) translations in content/translations/ckb/*.json.

Each file maps a question's external_ref to {prompt, options, explanation, passage}. The SQL only
updates the *_ckb columns of questions that exist, so it is safe to run again. Questions keep
their version (translations do not change how answers are marked); updated_at moves, which makes
the app download the level pack again.

Usage:
  python3 scripts/db/build_translation_sql.py [LEVEL ...] > translations.sql
  python3 scripts/db/build_translation_sql.py --chunk 150 --out DIR   (one file per chunk)
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "content/translations/ckb"


def lit(value):
    if value is None:
        return "null"
    return "'" + str(value).replace("'", "''") + "'"


def array(values):
    if values is None:
        return "null"
    return "array[" + ", ".join(lit(v) for v in values) + "]::text[]"


def rows(levels):
    for path in sorted(SRC.glob("*.json")):
        if levels and path.stem not in levels:
            continue
        for ref, t in sorted(json.loads(path.read_text(encoding="utf-8")).items()):
            yield ref, t


def statement(batch):
    values = ",\n  ".join(
        f"({lit(ref)}, {lit(t['prompt'])}, {array(t['options'])}, {lit(t['explanation'])}, {lit(t['passage'])})"
        for ref, t in batch
    )
    return (
        "update public.assessment_questions q set\n"
        "  prompt_ckb = v.prompt, options_ckb = case when cardinality(v.options) = cardinality(q.options) then v.options end,\n"
        "  explanation_ckb = v.explanation, passage_ckb = case when q.passage is not null then v.passage end\n"
        "from (values\n  " + values + "\n) as v(ref, prompt, options, explanation, passage)\n"
        "where q.external_ref = v.ref\n"
        "  and (q.prompt_ckb, q.options_ckb, q.explanation_ckb, q.passage_ckb)\n"
        "      is distinct from (v.prompt, v.options, v.explanation, v.passage);\n"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("levels", nargs="*")
    parser.add_argument("--chunk", type=int, default=200)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    items = list(rows(set(args.levels)))
    batches = [items[i:i + args.chunk] for i in range(0, len(items), args.chunk)]
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        for i, batch in enumerate(batches, 1):
            (args.out / f"{i:03d}.sql").write_text(statement(batch), encoding="utf-8")
        print(f"{len(items)} translations in {len(batches)} files")
    else:
        print("\n".join(statement(b) for b in batches))


if __name__ == "__main__":
    main()
