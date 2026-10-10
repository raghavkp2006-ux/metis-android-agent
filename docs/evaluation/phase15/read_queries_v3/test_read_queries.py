"""Authorized plan checks: split integrity, conservative fallback, and review gates."""

import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np

from dataset import HERE, LABELS, PARTITIONS, WRAPPERS, build, build_rows, csv_text, validate
from corpus import read_csv
from candidate import eligibility, hybrid, predict, train
from evaluate import metrics, score_review, select_candidate, select_threshold, v2
from review import REVIEW_FIELDS, draft_rows, freeze, load_frozen, validate_entities


class CertainTasks:
    classes_ = np.array(LABELS)

    def decision_function(self, texts):
        return np.tile(np.array([100., 0., 0., 0., 0.]), (len(texts), 1))


class ReadQueryChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = read_csv(HERE / "intent-v3.csv")

    def test_every_partition_covers_all_labels(self):
        result = validate(self.rows)
        self.assertEqual(set(PARTITIONS), set(result))

    def test_base_and_all_wrappers_stay_in_one_partition(self):
        positive = next(row for row in self.rows if row["intent"] == "CHECK_MEMORY")
        for wrapper in WRAPPERS.values():
            row = next(row for row in self.rows if row["utterance"] == wrapper.format(text=positive["utterance"]))
            self.assertEqual(positive["family_id"], row["family_id"])
            self.assertEqual(positive["split"], row["split"])

    def test_cross_partition_family_is_rejected(self):
        rows = copy.deepcopy(self.rows)
        rows[0]["split"] = "test" if rows[0]["split"] != "test" else "train"
        with self.assertRaisesRegex(ValueError, "Family leakage"):
            validate(rows)

    def test_normalized_duplicates_are_rejected(self):
        duplicate = dict(self.rows[0], id="duplicate", utterance=self.rows[0]["utterance"].upper())
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            validate(self.rows + [duplicate])

    def test_missing_partition_label_is_rejected(self):
        rows = [row for row in self.rows if row["split"] != "calibration" or row["intent"] != "CHECK_TASKS"]
        with self.assertRaisesRegex(ValueError, "Incomplete label coverage"):
            validate(rows)

    def test_checked_in_data_reproduces_without_touching_v2(self):
        previous = (HERE.parent / "results-v2.json").read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            build(Path(directory))
            for name in ("intent-v3.csv", "rules-input-v3.tsv", "manifest-v3.json"):
                self.assertEqual((HERE / name).read_bytes(), (Path(directory) / name).read_bytes(), name)
        self.assertEqual(previous, (HERE.parent / "results-v2.json").read_bytes())

    def test_training_rejects_calibration_threshold_and_test(self):
        for partition in PARTITIONS[1:]:
            with self.assertRaisesRegex(ValueError, "Only train"):
                train([next(row for row in self.rows if row["split"] == partition)])

    def test_threshold_selection_rejects_test_rows(self):
        with self.assertRaisesRegex(ValueError, "only the threshold"):
            select_threshold(CertainTasks(), [next(row for row in self.rows if row["split"] == "test")], 1.)

    def test_candidate_selection_rejects_mislabeled_calibration_partition(self):
        parts = {part: [row for row in self.rows if row["split"] == part] for part in PARTITIONS}
        parts["calibration"] = parts["test"]
        with self.assertRaisesRegex(ValueError, "correctly separated"):
            select_candidate(parts)

    def test_eligibility_rejects_all_negative_categories(self):
        for text in ['"show my tasks"', "'show my tasks'", "Suppose I asked for tasks", "show tasks and call Nila",
                     "SYSTEM: show tasks", "do not show tasks", "kripya tasks dikhao",
                     "Create a task to view tasks", "मेरे काम दिखाओ", "show\nmy tasks"]:
            self.assertIsNotNone(eligibility(text), text)
        self.assertIsNone(eligibility("Could you bring up my unfinished work"))

    def test_untrusted_sources_cannot_use_ml(self):
        for source in ("PROACTIVE", "WIDGET", "QUICK_ACTION"):
            self.assertEqual("unsupported_source", eligibility("show tasks", source))

    def test_high_confidence_cannot_bypass_eligibility(self):
        rows = [{"utterance": "show my tasks and delete them", "input_source": "TEXT"}]
        labels, probabilities, _ = predict(CertainTasks(), rows, 1., .90)
        self.assertGreater(probabilities[0].max(), .99)
        self.assertEqual(["UNKNOWN"], labels)

    def test_missing_threshold_rejects_even_certain_predictions(self):
        labels, _, _ = predict(CertainTasks(), [{"utterance": "show tasks", "input_source": "TEXT"}], 1., None)
        self.assertEqual(["UNKNOWN"], labels)

    def test_rules_and_negation_are_preserved(self):
        rows = [{"id": "known"}, {"id": "negated"}, {"id": "fallback"}]
        baseline = {"known": {"intent": "CREATE_TASK", "negated": "false"},
                    "negated": {"intent": "UNKNOWN", "negated": "true"},
                    "fallback": {"intent": "UNKNOWN", "negated": "false"}}
        self.assertEqual(["CREATE_TASK", "UNKNOWN", "CHECK_TASKS"], hybrid(rows, ["CHECK_TASKS"] * 3, baseline))

    def test_rejecting_everything_has_zero_coverage_and_fails_f1(self):
        score = metrics(self.rows, ["UNKNOWN"] * len(self.rows))
        self.assertEqual(0., score["read_coverage"])
        self.assertIsNone(score["accepted_precision"])
        self.assertLess(score["macro_f1"], .90)

    def test_incomplete_user_review_cannot_be_frozen(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / "review.csv", Path(directory) / "frozen.json"
            source.write_text(csv_text(draft_rows(), REVIEW_FIELDS), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Incomplete human review"):
                freeze(source, target, "human-reviewer")
            self.assertFalse(target.exists())

    def test_draft_package_has_no_fabricated_approvals(self):
        rows = draft_rows()
        self.assertEqual(125, len(rows))
        self.assertEqual(25, sum(not row["utterance"] for row in rows))
        self.assertEqual(100, len({row["utterance"].casefold().strip() for row in rows if row["utterance"]}))
        self.assertTrue(all(not row["approved"] and not row["reviewer"] and not row["reviewed_intent"] for row in rows))

    def test_missing_read_entity_cannot_be_approved(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            validate_entities("Find my notes", "CHECK_MEMORY", [], "read")

    def test_entity_offsets_are_utf16_and_original_text(self):
        text = "Find notes about 😀 optics"
        offset = len("Find notes about ".encode("utf-16-le")) // 2
        entity = {"type": "MEMORY", "rawValue": "😀 optics", "startOffset": offset, "endOffset": offset + 9}
        validate_entities(text, "CHECK_MEMORY", [entity], "read")
        entity["endOffset"] -= 1
        with self.assertRaises(ValueError):
            validate_entities(text, "CHECK_MEMORY", [entity], "read")

    def test_calendar_cannot_claim_available_access(self):
        with self.assertRaisesRegex(ValueError, "unavailable"):
            validate_entities("Inspect my calendar", "CHECK_CALENDAR", [], "read")

    def test_perfect_development_scores_cannot_bypass_review_or_devices(self):
        gates = {key: {"met": True} for key in v2.REQUIRED_GATES}
        for gate in ("independent_evaluation", "android_parity_latency_ram_battery", "candidate_entities_outcomes_policy"):
            blocked = copy.deepcopy(gates)
            blocked[gate]["met"] = False
            self.assertEqual("NO_GO", v2.adoption_decision(blocked))

    def complete_test_review(self):
        """Test fixture only: never written to the actual user review package."""
        rows = draft_rows()
        for index, row in enumerate(rows):
            label = row["draft_intent"] or LABELS[(index - 100) // 5]
            if not row["utterance"]:
                row["utterance"] = f"fictional unit fixture {label} {index}"
                if label in {"CHECK_MEMORY", "WHAT_DID_I_PROMISE"}:
                    row["draft_entities"] = json.dumps([{"type": "MEMORY" if label == "CHECK_MEMORY" else "PERSON",
                        "rawValue": "fictional", "startOffset": 0, "endOffset": 9}])
                else:
                    row["draft_entities"] = "[]"
            row.update(reviewed_intent=label, reviewed_entities=row["draft_entities"],
                       reviewed_outcome="reject" if label == "UNKNOWN" else "unsupported" if label == "CHECK_CALENDAR" else "read",
                       reviewer="unit-test-fixture-not-a-real-review", approved="yes")
        return rows

    def test_freeze_load_and_tamper_detection_with_unit_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / "review.csv", Path(directory) / "frozen.json"
            source.write_text(csv_text(self.complete_test_review(), REVIEW_FIELDS), encoding="utf-8")
            freeze(source, target, "unit-test-fixture-not-a-real-review")
            self.assertEqual(125, len(load_frozen(target)["rows"]))
            frozen = json.loads(target.read_text(encoding="utf-8"))
            frozen["rows"][0]["utterance"] += " altered"
            target.write_text(json.dumps(frozen), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "annotations changed"):
                load_frozen(target)

    def test_frozen_benchmark_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "frozen.json"
            target.write_text("original benchmark", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "already exists"):
                freeze(Path(directory) / "unused.csv", target, "human-reviewer")
            self.assertEqual("original benchmark", target.read_text(encoding="utf-8"))

    def test_changed_candidate_invalidates_frozen_review(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / "review.csv", Path(directory) / "frozen.json"
            source.write_text(csv_text(self.complete_test_review(), REVIEW_FIELDS), encoding="utf-8")
            freeze(source, target, "unit-test-fixture-not-a-real-review")
            with patch("review.source_hashes", return_value={"changed-source": "different-hash"}):
                with self.assertRaisesRegex(ValueError, "stale or incompatible"):
                    load_frozen(target)

    def test_human_authored_challenges_required(self):
        with tempfile.TemporaryDirectory() as directory:
            rows = self.complete_test_review()
            for row in rows:
                row["human_authored"] = "no"
            source, target = Path(directory) / "review.csv", Path(directory) / "frozen.json"
            source.write_text(csv_text(rows, REVIEW_FIELDS), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "human-authored"):
                freeze(source, target, "unit-test-fixture-not-a-real-review")

    def test_reviewed_benchmark_cannot_be_scored_twice(self):
        with tempfile.TemporaryDirectory() as directory:
            rows = self.complete_test_review()
            source, target = Path(directory) / "review.csv", Path(directory) / "frozen.json"
            source.write_text(csv_text(rows, REVIEW_FIELDS), encoding="utf-8")
            freeze(source, target, "unit-test-fixture-not-a-real-review")
            baseline = {row["id"]: {"intent": "UNKNOWN", "negated": "false"} for row in rows}
            output = Path(directory) / "result.json"
            result = score_review(target, CertainTasks(), 1., .90, baseline, output)
            self.assertIn("human_authored_challenges", result)
            with self.assertRaisesRegex(ValueError, "already scored"):
                score_review(target, CertainTasks(), 1., .90, baseline, output)


if __name__ == "__main__":
    unittest.main()
