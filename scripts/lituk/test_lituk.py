#!/usr/bin/env python3
"""Tests for the question import gate and the Life in the UK audit tool (python3 -m unittest)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parents[1] / "scripts/db"))
import build_ai_question_seed as seed  # noqa: E402
import lituk  # noqa: E402


def fixture(prompt: str, **extra) -> dict:
    """Test fixture only: not app content."""
    return {"level": "LIFE_IN_UK", "skill": "reading", "prompt": prompt, "answer": "Alpha",
            "distractors": ["Beta", "Gamma", "Delta"], "explanation": "Handbook ch. 5 (fixture): Alpha.",
            "tags": ["life_in_uk", "lituk_government"], **extra}


def batch_file(items: list[dict], **top) -> Path:
    path = Path(tempfile.mkdtemp()) / "fixture.json"
    path.write_text(json.dumps({"batch": "fixture", "items": items, **top}), encoding="utf-8")
    return path


class ImportGateTest(unittest.TestCase):
    def test_verified_only_batches_import_verified_items_with_a_page(self):
        src = batch_file([
            fixture("Fixture one?", verification="verified", source={"chapter": 5, "section": "5.2", "page": 120}),
            fixture("Fixture two?", verification="verified", source={"chapter": 5, "section": "5.2", "page": None}),
            fixture("Fixture three?", verification="pending_handbook"),
        ], verified_only=True)
        _, rows = seed.rows(src)
        self.assertEqual(["Fixture one?"], [r["prompt"] for r in rows])
        _, everything = seed.item_rows(src, include_unimportable=True)
        self.assertEqual(3, len(everything))

    def test_ordinary_batches_are_unchanged(self):
        _, rows = seed.rows(batch_file([fixture("Fixture one?"), fixture("Fixture two?")]))
        self.assertEqual(2, len(rows))
        self.assertTrue(all(r["external_ref"].startswith("ai:fixture:") for r in rows))

    def test_a_stable_ref_survives_a_wording_fix_and_keeps_the_option_order(self):
        _, a = seed.rows(batch_file([fixture("Fixture wording?", ref="lituk:C5-0001")]))
        _, b = seed.rows(batch_file([fixture("Fixture wording, corrected?", ref="lituk:C5-0001")]))
        self.assertEqual("lituk:C5-0001", a[0]["external_ref"])
        self.assertEqual(a[0]["external_ref"], b[0]["external_ref"])
        self.assertEqual(a[0]["options"], b[0]["options"])

    def test_bad_or_repeated_refs_are_refused(self):
        with self.assertRaises(SystemExit):
            seed.rows(batch_file([fixture("Fixture?", ref="no spaces allowed")]))
        with self.assertRaises(SystemExit):
            seed.rows(batch_file([fixture("Fixture a?", ref="lituk:X-1"), fixture("Fixture b?", ref="lituk:X-1")]))

    def test_building_twice_gives_identical_rows(self):
        src = batch_file([fixture("Fixture one?"), fixture("TRUE or FALSE: Fixture.", answer="True", distractors=["False"])])
        self.assertEqual(seed.rows(src), seed.rows(src))
        self.assertEqual(["True", "False"], seed.rows(src)[1][1]["options"])


class AuditToolTest(unittest.TestCase):
    def setUp(self):
        self.items = lituk.load_items()
        self.outline = lituk.load_outline()
        self.meta = lituk.load_meta()

    def test_the_bank_validates(self):
        self.assertEqual([], lituk.validate(self.items, self.outline, self.meta))

    def test_verified_needs_a_page_and_corroborated_needs_a_url(self):
        ref = self.items[0]["ref"]
        meta = json.loads(json.dumps(self.meta))
        meta["questions"][ref].update(verification="verified", source={"chapter": 1, "section": "1.1", "page": None})
        self.assertTrue(any("needs a handbook section and page" in e for e in lituk.validate(self.items, self.outline, meta)))
        meta["questions"][ref].update(verification="corroborated_external", evidence=["no url"])
        self.assertTrue(any("needs a source URL" in e for e in lituk.validate(self.items, self.outline, meta)))

    def test_a_wrong_answer_key_is_caught(self):
        broken = json.loads(json.dumps(self.items[:1]))
        broken[0]["row"]["correct_option"] = "D" if broken[0]["row"]["correct_option"] != "D" else "A"
        self.assertTrue(any("answer key" in e for e in lituk.validate(broken, self.outline, {"questions": {}})))

    def test_duplicate_detection_finds_the_same_fact_asked_twice(self):
        pairs = lituk.duplicate_candidates(self.items)
        prompts = {(self.meta["questions"][p["a"]]["prompt"], self.meta["questions"][p["b"]]["prompt"]) for p in pairs}
        self.assertTrue(any("St Paul" in a and "St Paul" in b for a, b in prompts))

    def test_live_comparison_reports_changes_and_gaps(self):
        rows = [{"external_ref": q["ref"], "status": "published", "review_status": "approved",
                 "fp": lituk.fingerprint(q["row"]["prompt"], q["row"]["options"], q["row"]["correct_option"])}
                for q in self.items]
        self.assertEqual([], lituk.compare_live(self.items, rows)["changed_live"])
        rows[0]["fp"] = "0" * 32
        del rows[1]
        result = lituk.compare_live(self.items, rows)
        self.assertEqual([self.items[0]["ref"]], result["changed_live"])
        self.assertEqual([self.items[1]["ref"]], result["missing_live"])


if __name__ == "__main__":
    unittest.main()
