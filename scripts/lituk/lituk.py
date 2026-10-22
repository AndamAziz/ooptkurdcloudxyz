#!/usr/bin/env python3
"""Life in the UK question bank: validation, duplicate detection, coverage and audit reports.

The question records live in content/questions/ai-batch-*.json (level LIFE_IN_UK) and are imported
by scripts/db/build_ai_question_seed.py (seed SQL) and scripts/release/publish_questions.py (live,
insert-only). This tool never changes those records. Per-question review data (stable id, chapter
and section, verification status, evidence, flags, duplicate decisions) is kept separately in
content/lituk/question-meta.json, keyed by the question's external_ref.

Verification statuses (only "verified" may claim the handbook as evidence):
  pending_handbook       not yet checked against the official handbook (default)
  corroborated_external  answer confirmed by a current authoritative source (URL in evidence);
                         still needs the handbook check before it counts as verified
  needs_review           a concrete doubt is recorded in notes; exclude from verified exports
  verified               confirmed against the handbook; evidence names chapter, section, page
  rejected               wrong or out of scope; to be retired by staff (never deleted here)

Hand-maintained metadata fields: section (+ section_basis "manual"), difficulty, verification,
source {chapter, section, page}, evidence [...], notes, duplicate_of (same fact as another
question; the other one is kept), distinct_from [...] (candidate pair checked: different facts),
flags_cleared [...] (automatic flags checked and found harmless), flags "review:*" (manual).

Usage:
  lituk.py validate                 structural checks; exit 1 on errors (used by CI)
  lituk.py sync-meta                add metadata entries for new questions (keeps existing ones)
  lituk.py audit                    write docs/lituk/audit-report.md and content/lituk/audit.json
  lituk.py live-sql                 print SQL that fingerprints the live LIFE_IN_UK rows
  lituk.py compare-live rows.json   compare a live export (output of live-sql) with the repo
"""
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/db"))
import build_ai_question_seed as seed  # noqa: E402

LEVEL = "LIFE_IN_UK"
OUTLINE = ROOT / "content/lituk/handbook-outline.json"
META = ROOT / "content/lituk/question-meta.json"
AUDIT_JSON = ROOT / "content/lituk/audit.json"
REPORT = ROOT / "docs/lituk/audit-report.md"
COVERAGE = ROOT / "docs/lituk/coverage-checklist.md"

STATUSES = ("pending_handbook", "corroborated_external", "needs_review", "verified", "rejected")
DIFFICULTIES = (None, "easy", "medium", "hard")
STOP = set("""a an the of in on at to for from by with and or is was were are be been it its this that these
which who whom what when where why how did does do has have had not no as into than then there their they
true false statement following uk british britain one two following""".split())


# --------------------------------------------------------------------------- loading
def load_items() -> list[dict]:
    """Every LIFE_IN_UK question with its import row (external_ref, options in stored order, key)."""
    out = []
    for src in sorted((ROOT / "content/questions").glob("ai-batch-*.json")):
        data = json.loads(src.read_text(encoding="utf-8"))
        _, pairs = seed.item_rows(src, include_unimportable=True)
        for item, row in pairs:
            if row["cefr_level"] == LEVEL:
                out.append({"file": src.name, "item": item, "row": row, "ref": row["external_ref"],
                            "importable": seed.is_importable(item, bool(data.get("verified_only")))})
    return out


def load_outline() -> dict:
    return json.loads(OUTLINE.read_text(encoding="utf-8"))


def load_meta() -> dict:
    return json.loads(META.read_text(encoding="utf-8")) if META.exists() else {"questions": {}}


def save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def words(text: str) -> set[str]:
    return {w for w in re.findall(r"[a-z0-9']+", text.lower()) if w not in STOP and len(w) > 2}


def kind(item: dict) -> str:
    if seed.is_true_false(item):
        return "true_false"
    if item["prompt"].startswith("Which TWO"):
        return "which_two"
    return "multiple_choice"


def chapter_tag(item: dict) -> str | None:
    tags = [t for t in item.get("tags", []) if t.startswith("lituk_")]
    return tags[0] if len(tags) == 1 else None


# --------------------------------------------------------------------------- validation
def validate(items: list[dict], outline: dict, meta: dict) -> list[str]:
    errors = []
    chapter_tags = {c["tag"]: c["number"] for c in outline["chapters"]}
    sections = {s["id"]: s for c in outline["chapters"] for s in c["sections"]}
    refs = Counter(q["ref"] for q in items)
    for ref, n in refs.items():
        if n > 1:
            errors.append(f"{ref}: duplicate external_ref ({n} records)")
    prompts = Counter(seed.norm(q["item"]["prompt"]) for q in items)
    for p, n in prompts.items():
        if n > 1:
            errors.append(f"prompt used by {n} Life in the UK questions (across batches): {p!r}")
    for q in items:
        item, row, where = q["item"], q["row"], f"{q['file']} {q['ref']}"
        tag = chapter_tag(item)
        if "life_in_uk" not in item.get("tags", []):
            errors.append(f"{where}: missing tag life_in_uk")
        if tag not in chapter_tags:
            errors.append(f"{where}: needs exactly one chapter tag ({', '.join(chapter_tags)})")
        options, key = row["options"], row["correct_option"]
        if not re.fullmatch(r"[A-F]", key) or ord(key) - 65 >= len(options):
            errors.append(f"{where}: answer key {key!r} outside the {len(options)} options")
        elif options[ord(key) - 65] != item["answer"]:
            errors.append(f"{where}: answer key does not point at the answer")
        if item["answer"] in item["distractors"]:
            errors.append(f"{where}: answer repeated among distractors")
        k = kind(item)
        if k == "true_false" and options != ["True", "False"]:
            errors.append(f"{where}: True/False options must read True, False")
        if k == "which_two" and " and " not in item["answer"]:
            errors.append(f"{where}: 'Which TWO' answer must name two things")
        expl = item["explanation"]
        m = re.match(r"Handbook ch\. (\d)", expl)
        if not m:
            errors.append(f"{where}: explanation must start with 'Handbook ch. N'")
        elif tag in chapter_tags and int(m.group(1)) != chapter_tags[tag]:
            errors.append(f"{where}: explanation cites ch. {m.group(1)} but the question is tagged {tag}")
    for ref, entry in meta.get("questions", {}).items():
        if entry.get("verification") not in STATUSES:
            errors.append(f"meta {ref}: unknown verification status {entry.get('verification')!r}")
        if entry.get("difficulty") not in DIFFICULTIES:
            errors.append(f"meta {ref}: difficulty must be easy, medium, hard or null")
        if entry.get("verification") == "verified":
            src = entry.get("source") or {}
            if not (src.get("section") and src.get("page")):
                errors.append(f"meta {ref}: 'verified' needs a handbook section and page in source")
            elif src["section"] in sections and sections[src["section"]].get("pages"):
                lo, hi = sections[src["section"]]["pages"]
                if not lo <= int(src["page"]) <= hi:
                    errors.append(f"meta {ref}: page {src['page']} is outside section {src['section']} (pages {lo}-{hi})")
            elif src["section"] not in sections:
                errors.append(f"meta {ref}: unknown handbook section {src['section']!r}")
        if entry.get("verification") == "corroborated_external" and not any(
                str(e).startswith("http") for e in entry.get("evidence", [])):
            errors.append(f"meta {ref}: 'corroborated_external' needs a source URL in evidence")
        dup = entry.get("duplicate_of")
        if dup and dup not in meta["questions"]:
            errors.append(f"meta {ref}: duplicate_of points at unknown {dup}")
    return errors


# --------------------------------------------------------------------------- mapping
def map_section(item: dict, chapter: dict) -> tuple[str | None, str]:
    """Provisional section from keywords (and years for history). Returns (section id, basis)."""
    text = " ".join([item["prompt"], item["answer"], item["explanation"]]).lower()
    best, score = None, 0
    for s in chapter["sections"]:
        hits = sum(1 for kw in s["keywords"] if re.search(r"(?<![a-z])" + re.escape(kw), text))
        if hits > score:
            best, score = s["id"], hits
    if best:
        return best, "keywords"
    years = [int(y) for y in re.findall(r"\b(1\d{3}|20[01]\d)\b", text)]
    if years:
        for s in chapter["sections"]:
            lo, hi = s.get("years", (None, None))
            if lo is not None and lo <= min(years) <= hi:
                return s["id"], "year"
    if re.search(r"\bad \d{2,3}\b|\b\d{2,3} bc\b", text):
        return "3.1", "year"
    return None, "unmapped"


# --------------------------------------------------------------------------- duplicates
def fact_signature(item: dict) -> set[str]:
    if seed.is_true_false(item):
        return words(item["prompt"].removeprefix("TRUE or FALSE: "))
    return words(item["prompt"]) | words(item["answer"])


def duplicate_candidates(items: list[dict]) -> list[dict]:
    """Pairs that may test the same fact: same normalised prompt, or high word overlap with a
    shared answer word. Candidates are for human review; decisions go into the metadata."""
    sigs = [(q, fact_signature(q["item"])) for q in items]
    out = []
    for i in range(len(sigs)):
        qa, sa = sigs[i]
        for j in range(i + 1, len(sigs)):
            qb, sb = sigs[j]
            if not sa or not sb:
                continue
            inter = sa & sb
            jacc = len(inter) / len(sa | sb)
            same_prompt = seed.norm(qa["item"]["prompt"]) == seed.norm(qb["item"]["prompt"])
            ans_a, ans_b = words(qa["item"]["answer"]), words(qb["item"]["answer"])
            shared_answer = bool(ans_a & ans_b) or bool(ans_a & sb) or bool(ans_b & sa)
            if same_prompt or (jacc >= 0.34 and shared_answer and len(inter) >= 3):
                out.append({"a": qa["ref"], "b": qb["ref"], "similarity": round(jacc, 2),
                            "shared": sorted(inter)[:12], "exact": same_prompt})
    return sorted(out, key=lambda d: -d["similarity"])


# --------------------------------------------------------------------------- quality checks
def quality_flags(item: dict) -> list[str]:
    flags = []
    k = kind(item)
    expl = item["explanation"].lower()
    if k == "multiple_choice":
        ans = words(item["answer"])
        if ans and not (ans & words(expl)):
            flags.append("explanation_does_not_mention_answer")
    lengths = [len(item["answer"])] + [len(d) for d in item["distractors"]]
    if k != "true_false" and len(lengths) == 4 and lengths[0] > 1.6 * max(lengths[1:]) and lengths[0] > 25:
        flags.append("answer_noticeably_longest")
    text = " ".join([item["prompt"], item["answer"], *item["distractors"], item["explanation"]])
    if re.search(r"\b(EU|European Union|European Economic Community|European Community)\b", text):
        flags.append("exam_scope_eu")
    if re.search(r"\b(current|currently|today|now)\b", item["prompt"], re.I):
        flags.append("time_sensitive_wording")
    return flags


# --------------------------------------------------------------------------- metadata
def sync_meta(items, outline, meta) -> dict:
    chapters = {c["tag"]: c for c in outline["chapters"]}
    qs = meta.setdefault("questions", {})
    for q in items:
        item = q["item"]
        tag = chapter_tag(item)
        entry = qs.get(q["ref"])
        if entry is None:
            section, basis = map_section(item, chapters[tag]) if tag in chapters else (None, "unmapped")
            entry = qs[q["ref"]] = {
                "id": q["ref"],
                "file": q["file"],
                "chapter": chapters[tag]["number"] if tag in chapters else None,
                "section": section,
                "section_basis": basis,
                "format": kind(item),
                "difficulty": item.get("difficulty"),
                "verification": item.get("verification", "pending_handbook"),
                "source": item.get("source") or {"chapter": chapters[tag]["number"] if tag in chapters else None, "section": None, "page": None},
                "evidence": [],
                "flags": [],
                "duplicate_of": None,
                "notes": item.get("notes", ""),
                "explanation_ckb": item.get("explanation_ckb"),
            }
        src = item.get("source") or {}
        if src.get("section") and entry.get("section_basis") != "handbook":
            # Items written from the handbook carry their section; that beats the keyword mapping.
            entry.update(section=src["section"], section_basis="handbook", chapter=src.get("chapter", entry.get("chapter")))
        entry["prompt"] = item["prompt"]  # readability of the meta file; refreshed each sync
    meta["schema"] = 1
    meta["questions"] = dict(sorted(qs.items(), key=lambda kv: (kv[1].get("chapter") or 9, kv[1].get("section") or "", kv[0])))
    return meta


# --------------------------------------------------------------------------- live comparison
def fingerprint(prompt: str, options: list[str], key: str) -> str:
    return hashlib.md5((prompt + "\x1f" + "\x1e".join(options) + "\x1f" + key).encode()).hexdigest()


LIVE_SQL = """select external_ref, status, review_status, md5(prompt || chr(31) || array_to_string(options, chr(30)) || chr(31) || correct_option) as fp
  from public.assessment_questions where cefr_level = 'LIFE_IN_UK' order by external_ref;"""


def compare_live(items, rows: list[dict]) -> dict:
    repo = {q["ref"]: fingerprint(q["row"]["prompt"], q["row"]["options"], q["row"]["correct_option"]) for q in items}
    live = {r["external_ref"]: r for r in rows}
    return {
        "repo": len(repo),
        "live": len(live),
        "missing_live": sorted(set(repo) - set(live)),
        "only_live": sorted(set(live) - set(repo)),
        "changed_live": sorted(r for r in set(repo) & set(live) if live[r]["fp"] != repo[r]),
        "live_status": dict(Counter(f"{r['status']}/{r['review_status']}" for r in rows)),
    }


# --------------------------------------------------------------------------- reports
def audit(items, outline, meta) -> dict:
    errors = validate(items, outline, meta)
    dups = duplicate_candidates(items)
    qs = meta["questions"]
    for q in items:  # automatic flags are recomputed; manual flags (prefixed "review:") are kept
        e = qs[q["ref"]]
        auto = set(quality_flags(q["item"])) - set(e.get("flags_cleared", []))
        e["flags"] = sorted({f for f in e.get("flags", []) if f.startswith("review:")} | auto)
    by_status = Counter(e["verification"] for e in qs.values())
    by_chapter = Counter(e["chapter"] for e in qs.values())
    by_section = Counter(e["section"] for e in qs.values())
    by_format = Counter(e["format"] for e in qs.values())
    decided = {frozenset((r, e["duplicate_of"])) for r, e in qs.items() if e.get("duplicate_of")}
    distinct = {frozenset((r, o)) for r, e in qs.items() for o in e.get("distinct_from", [])}
    open_dups = [d for d in dups if frozenset((d["a"], d["b"])) not in decided | distinct]
    return {
        "total": len(items),
        "errors": errors,
        "by_status": dict(by_status),
        "by_chapter": {str(k): v for k, v in sorted(by_chapter.items(), key=lambda kv: kv[0] or 9)},
        "by_section": {str(k): v for k, v in sorted(by_section.items(), key=lambda kv: str(kv[0]))},
        "by_format": dict(by_format),
        "duplicates_confirmed": sorted([r, e["duplicate_of"]] for r, e in qs.items() if e.get("duplicate_of")),
        "duplicate_candidates_open": open_dups,
        "duplicate_candidates_resolved_distinct": len(dups) - len(open_dups) - len(decided & {frozenset((d["a"], d["b"])) for d in dups}),
        "flags": {f: sorted(r for r, e in qs.items() if f in e["flags"]) for f in sorted({f for e in qs.values() for f in e["flags"]})},
        "unmapped": sorted(r for r, e in qs.items() if not e.get("section")),
    }


def write_reports(result: dict, outline: dict, meta: dict) -> None:
    qs = meta["questions"]
    save_json(AUDIT_JSON, result)
    L = ["# Life in the UK question bank: audit report", "",
         "Generated by `scripts/lituk/lituk.py audit`; do not edit by hand.", "",
         (f"Handbook: {outline['source']} Table of contents {outline['toc_status']}." if outline["toc_status"] == "CONFIRMED"
          else f"Handbook access: **not available** (outline status: {outline['toc_status']}). No question is marked verified "
               "against the handbook; sections are provisional until the table of contents is confirmed."), "",
         "## Totals", "", f"- Questions: **{result['total']}**",
         f"- Structural errors: **{len(result['errors'])}**"]
    for s in STATUSES:
        L.append(f"- {s}: {result['by_status'].get(s, 0)}")
    L += [f"- Confirmed duplicates (kept, marked): {len(result['duplicates_confirmed'])}",
          f"- Duplicate candidates still open: {len(result['duplicate_candidates_open'])}",
          f"- Formats: " + ", ".join(f"{k} {v}" for k, v in sorted(result['by_format'].items())), ""]
    if result["errors"]:
        L += ["## Structural errors", ""] + [f"- {e}" for e in result["errors"]] + [""]
    L += ["## Flags", ""]
    for f, refs in result["flags"].items():
        L.append(f"### {f} ({len(refs)})")
        L += [f"- `{r}` {qs[r]['prompt']}" + (f" — {qs[r]['notes']}" if qs[r].get("notes") else "") for r in refs]
        L.append("")
    L += ["## Duplicates (same fact tested twice)", ""]
    for a, b in result["duplicates_confirmed"]:
        L.append(f"- `{a}` {qs[a]['prompt']}  \n  duplicate of `{b}` {qs[b]['prompt']}")
    if result["duplicate_candidates_open"]:
        L += ["", "### Candidates awaiting a decision", ""]
        for d in result["duplicate_candidates_open"]:
            L.append(f"- {d['similarity']}: `{d['a']}` {qs[d['a']]['prompt']} / `{d['b']}` {qs[d['b']]['prompt']}")
    L += ["", "## Questions needing review", ""]
    for r, e in qs.items():
        if e["verification"] in ("needs_review", "rejected"):
            L.append(f"- **{e['verification']}** `{r}` {e['prompt']} — {e['notes']}")
    L += ["", "## Externally corroborated (current sources, handbook check still pending)", ""]
    for r, e in qs.items():
        if e["verification"] == "corroborated_external":
            L.append(f"- `{r}` {e['prompt']} — {', '.join(e['evidence'])}")
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(L) + "\n", encoding="utf-8")

    C = ["# Handbook coverage checklist", "",
         f"Generated by `scripts/lituk/lituk.py audit`. Table of contents status: **{outline['toc_status']}**.", "",
         "Columns: questions in the section (all statuses) / verified against the handbook / whether the section's pages "
         "have been read in full and its facts inventoried (`reviewed` in handbook-outline.json).", "",
         "| Section | Title | Pages | Questions | Verified | Reviewed in handbook |", "|---|---|---|---|---|---|"]
    verified = Counter(e["section"] for e in qs.values() if e["verification"] == "verified")
    for ch in outline["chapters"]:
        C.append(f"| **{ch['number']}** | **{ch['title']}** | | {result['by_chapter'].get(str(ch['number']), 0)} | | |")
        for s in ch["sections"]:
            pages = "-".join(str(p) for p in s.get("pages", [])) or "?"
            done = f"☑ {s['reviewed']}" if s.get("reviewed") else ("☐ blocked: no handbook" if outline["toc_status"] != "CONFIRMED" else "☐ not yet")
            C.append(f"| {s['id']} | {s['title']} | {pages} | {result['by_section'].get(s['id'], 0)} | {verified.get(s['id'], 0)} | {done} |")
    C += ["", f"Unmapped questions: {len(result['unmapped'])}", "", "## Publisher notes to confirm", ""]
    C += [f"- {n}" for n in outline.get("publisher_notes", [])]
    COVERAGE.write_text("\n".join(C) + "\n", encoding="utf-8")


# --------------------------------------------------------------------------- main
def main(argv: list[str]) -> int:
    cmd = argv[1] if len(argv) > 1 else "validate"
    items, outline, meta = load_items(), load_outline(), load_meta()
    if cmd == "validate":
        errors = validate(items, outline, meta)
        missing = [q["ref"] for q in items if q["ref"] not in meta.get("questions", {})]
        if missing:
            errors.append(f"{len(missing)} question(s) have no metadata entry; run: lituk.py sync-meta")
        for e in errors:
            print("ERROR", e)
        print(f"{len(items)} Life in the UK questions, {len(errors)} error(s)")
        return 1 if errors else 0
    if cmd == "sync-meta":
        save_json(META, sync_meta(items, outline, meta))
        print(f"metadata: {len(meta['questions'])} entries")
        return 0
    if cmd == "audit":
        meta = sync_meta(items, outline, meta)
        result = audit(items, outline, meta)
        save_json(META, meta)
        write_reports(result, outline, meta)
        print(json.dumps({k: v for k, v in result.items() if k in ("total", "by_status", "by_chapter", "by_format")}),
              f"errors={len(result['errors'])} open_duplicates={len(result['duplicate_candidates_open'])} unmapped={len(result['unmapped'])}")
        return 1 if result["errors"] else 0
    if cmd == "live-sql":
        print(LIVE_SQL)
        return 0
    if cmd == "compare-live":
        rows = json.loads(Path(argv[2]).read_text(encoding="utf-8"))
        print(json.dumps(compare_live(items, rows), indent=2))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
