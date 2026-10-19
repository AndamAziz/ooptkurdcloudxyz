#!/usr/bin/env python3
"""Build backend/supabase/seed/assessment_questions.sql from public/data/questions.csv.

Only rows whose source is 'generated' are included. Rows taken from OOPT
(Oxford Online Placement Test) documents are excluded because their licence for
redistribution has not been established.

The generated rows are template variants of a small set of sentences (the same
prompt with shuffled or swapped distractors), so items are de-duplicated per
(level, prompt). For each prompt the first variant without filler options such as
"am (alt)" is kept. Prompts whose keyed answer differs between variants are
ambiguous and are dropped. Included rows are marked review_status='unreviewed'
because no teacher has checked them yet.

The seed is idempotent: it upserts on external_ref.

Usage: python3 scripts/db/build_question_seed.py [csv_path] [out_path]
"""
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "public/data/questions.csv"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "backend/supabase/seed/assessment_questions.sql"

SKILLS = {"grammar_mcq": "grammar", "vocab_mcq": "vocabulary"}
LEVELS = {"A1", "A2", "B1", "B2", "C1", "C2"}
VARIANT_SUFFIX = re.compile(r"\s*\(v\d+\)\s*$")

# Explanations in the CSV that are factually wrong, keyed by CSV id. The CSV itself is
# left unchanged; corrections are applied here so they stay reviewable.
EXPLANATION_FIXES = {
    "2479": "'carry out' means 'conduct' or 'perform' (e.g. carry out a survey).",
}

# Items whose key, options or prompt are wrong or ambiguous in the CSV (found in a manual review of
# every seeded item). Options keep their order, so the keyed letter does not change and answers given
# from an older downloaded copy are still scored against the same letter. Fields not listed are kept.
ITEM_FIXES = {
    # Keyed verb did not fit the sentence ("works/plays/teaches to school").
    "627": {"options": ["goes", "went", "go", "going"]},
    "640": {"options": ["went", "go", "goes", "going"]},
    "642": {"options": ["go", "went", "going", "goes"]},
    # Filler options such as "am (alt)" replaced by a real distractor.
    "621": {"options": ["are", "is", "am", "be"]},
    "628": {"options": ["be", "am", "is", "are"]},
    "629": {"options": ["is", "are", "be", "am"]},
    "637": {"options": ["is", "am", "be", "are"]},
    "638": {"options": ["are", "be", "am", "is"]},
    # "in the office/shop/school" is also correct: replace the second correct option.
    "633": {"options": ["at", "on", "to", "into"]},
    "639": {"options": ["at", "to", "on", "into"]},
    "644": {"options": ["into", "to", "at", "on"]},
    # "It will rain" is also acceptable.
    "1123": {"options": ["is going to", "goes to", "is", "going to"]},
    # "take a decision" is also correct (British English).
    "1622": {"prompt": "Choose the correct verb: I need to ___ a decision by Friday.", "options": ["do", "make", "have", "give"]},
    # Zero conditional "we stay" and "we're staying" are also possible.
    "1623": {"options": ["stayed", "had stayed", "will stay", "would stay"]},
    # Unnatural template sentences ("Please ___ X and tell me tomorrow").
    "1625": {"prompt": "Choose the correct verb: Can you ___ me some advice about my essay?"},
    "1627": {"prompt": "Choose the correct verb: Let's ___ in touch after you move to London."},
    "1628": {"prompt": "Choose the correct verb: You look tired. Why don't you ___ a break?"},
    "1629": {"prompt": "Choose the correct verb: Please ___ your homework before you watch TV."},
    "1630": {"prompt": "Choose the correct verb: Please ___ attention to the teacher."},
    # Past simple "saw" was also correct without a time marker that needs the present perfect.
    "1626": {"prompt": "Choose the correct option: I ___ this film three times so far."},
    # "take a role", "make a deadline": second correct option; prompts were not meaningful sentences.
    "2478": {"prompt": "Choose the correct verb: Parents ___ an important role in a child's education.", "options": ["make", "play", "pose", "raise"]},
    "2484": {"prompt": "Choose the correct verb: We worked all night to ___ the deadline.", "options": ["meet", "do", "pose", "take"]},
    "2485": {"prompt": "Choose the correct verb: We must ___ the costs into account before making a decision."},
    "2486": {"prompt": "Choose the correct verb: After a long discussion, the committee finally ___ a conclusion.", "options": ["took", "raised", "met", "reached"], "explanation": "Natural collocation: 'reach a conclusion'."},
    # Keyed "pose" in "We must pose a risk" was wrong ("take a risk" fits that sentence).
    "2487": {"prompt": "Choose the correct verb: Smoking ___ a serious risk to your health.", "options": ["does", "takes", "poses", "makes"], "explanation": "Natural collocation: 'pose a risk (to)'."},
    "2488": {"prompt": "Choose the correct verb: I'd like to ___ an issue at the next meeting."},
    # "did I hear" is also correct after "Seldom".
    "2482": {"options": ["I did hear", "have I heard", "I have heard", "I heard"]},
    # Incomplete template sentences ("likely to ___ light on.").
    "3204": {"prompt": "Choose the correct verb: The new study is likely to ___ light on the causes of the disease."},
    "3210": {"prompt": "Choose the correct verb: It is important to ___ a distinction between facts and opinions."},
    "3214": {"prompt": "Choose the correct verb: The new policy will ___ greater emphasis on early education."},
    # "raise a dilemma" is also used.
    "3215": {"options": ["make", "take", "shed", "pose"]},
    # "decline/refuse a proposal" are also acceptable.
    "3802": {"options": ["reject", "repel", "deprive", "deny"]},
}


def q(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def arr(values) -> str:
    return "array[" + ",".join(q(v) for v in values) + "]::text[]"


def norm(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


def main() -> None:
    rows = list(csv.DictReader(SRC.open(encoding="utf-8-sig")))
    skipped = {"not_generated": 0, "invalid": 0, "duplicate_variant": 0, "ambiguous_key": 0}
    groups: dict[tuple[str, str], list[dict]] = {}
    for r in rows:
        if r["source"] != "generated":
            skipped["not_generated"] += 1
            continue
        options = [r[f"options_{k}"].strip() for k in "ABCD"]
        answer = r["correct_answer"].strip().upper()
        if (r["q_type"] not in SKILLS or r["cefr"] not in LEVELS or answer not in "ABCD"
                or not answer or not all(options) or not r["prompt"].strip()):
            skipped["invalid"] += 1
            continue
        prompt = VARIANT_SUFFIX.sub("", r["prompt"].strip())
        groups.setdefault((r["cefr"], norm(prompt)), []).append(dict(r, prompt=prompt))

    values = []
    for variants in groups.values():
        keyed = {norm(v[f"options_{v['correct_answer'].strip().upper()}"]) for v in variants}
        if len(keyed) > 1:
            skipped["ambiguous_key"] += len(variants)
            continue
        clean = [v for v in variants if not any("(alt" in v[f"options_{k}"].lower() for k in "ABCD")]
        r = (clean or variants)[0]
        skipped["duplicate_variant"] += len(variants) - 1
        fix = ITEM_FIXES.get(r["id"], {})
        options = fix.get("options") or [r[f"options_{k}"].strip() for k in "ABCD"]
        prompt = fix.get("prompt", r["prompt"])
        tags = [t.strip() for t in r["tags"].split(";") if t.strip() and t.strip() not in LEVELS]
        explanation = fix.get("explanation") or EXPLANATION_FIXES.get(r["id"], r["explanation"].strip())
        values.append(
            "(" + ",".join([
                q(f"csv:{r['id']}"), q(r["cefr"]) + "::public.cefr_level", q(SKILLS[r["q_type"]]),
                q(prompt), arr(options), q(r["correct_answer"].strip().upper()),
                q(explanation) if explanation else "null", arr(tags),
                "'published'", "'unreviewed'", "'generated'",
            ]) + ")"
        )

    header = (
        "-- GENERATED by scripts/db/build_question_seed.py — do not edit by hand.\n"
        f"-- {len(values)} questions; skipped: {skipped}\n"
        "-- One item per (level, prompt); published for practice but flagged review_status='unreviewed'.\n"
    )
    body = (
        "insert into public.assessment_questions\n"
        "  (external_ref, cefr_level, skill, prompt, options, correct_option, explanation, tags, status, review_status, source)\n"
        "values\n  " + ",\n  ".join(values) + "\n"
        "on conflict (external_ref) do update set\n"
        "  cefr_level = excluded.cefr_level, skill = excluded.skill, prompt = excluded.prompt,\n"
        "  options = excluded.options, correct_option = excluded.correct_option,\n"
        "  explanation = excluded.explanation, tags = excluded.tags,\n"
        "  version = public.assessment_questions.version + case when\n"
        "    (public.assessment_questions.prompt, public.assessment_questions.options, public.assessment_questions.correct_option)\n"
        "    is distinct from (excluded.prompt, excluded.options, excluded.correct_option) then 1 else 0 end;\n"
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(header + body, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(values)} questions, skipped {skipped}")


if __name__ == "__main__":
    main()
