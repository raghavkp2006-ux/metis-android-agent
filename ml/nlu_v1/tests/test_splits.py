"""Plan 06 uses invented in-memory records and vectors; no evaluation corpus."""
from contextlib import contextmanager
import copy
import hashlib
import json
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from test_data_management import managed, privacy_review, approve, embeddings, human_evidence
from ml.nlu_v1.data_management.records import append_event, assign_group, load_groups, register_scenario, sha_json, transition, validate_record
from ml.nlu_v1.data_management.storage import json_bytes, jsonl_bytes
from ml.nlu_v1 import independent_registry
from ml.nlu_v1.splitting import split
from ml.nlu_v1.synthetic.generate import generate
from jsonschema import ValidationError


def fixture_rows(count=12, official_last=False, approve_rows=False):
    rows = []
    families, scenarios = load_groups()
    for index in range(count):
        text = f"Set alarm fixture{index} next Friday at 8"
        annotation = f"Set alarm fixture{index} [date : next Friday] at [time : 8]"
        row = managed(str(index + 1), text=text, annotation=annotation, split="test" if official_last and index == count - 1 else "train")
        row["metadata"]["worker_id"] = None
        append_event(row, "PENDING", "PENDING", "fixture", "NONPERSONAL_SPLIT_FIXTURE")
        family = "fixture_family_" + str(index)
        families["families"][family] = copy.deepcopy(families["families"]["alarm_set"])
        scenarios = register_scenario(scenarios, families, "fixture_scenario_" + str(index), family, "fixture_parent_" + str(index), "Nonpersonal split unit-test fixture")
        rows.append(assign_group(row, family, "fixture_scenario_" + str(index), groups=(families, scenarios)))
    groups = families, scenarios
    if approve_rows:
        rows = [approve(privacy_review(row), groups) for row in rows]
    return rows, groups


def edit(row, **changes):
    result = copy.deepcopy(row)
    result.update(changes)
    append_event(result, result["review_status"], result["review_status"], "fixture", "GROUP_TEST_FIXTURE")
    validate_record(result)
    return result


@contextmanager
def independent_fixture():
    """A temporary reviewed registry simulates a future human collection intake."""
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / "independent.yaml"
        source = {"id": "fixture_independent", "version": "fixture_001", "source_class": "INDEPENDENT_EVALUATION",
            "independent_evaluation": True, "metis_status": "REGISTERED_PENDING_INTAKE", "allowed_splits": {"blind": [split.FINAL]},
            "license": {"id": "CC-BY-4.0", "verification": "VERIFIED_AT_INTAKE", "evidence_url": "https://example.invalid/fixture-license"},
            "source_url": "https://example.invalid/fixture-source", "citation": ["Invented unit-test fixture, never a real dataset"],
            "download": {"sha256": "a" * 64, "status": "VERIFIED"}, "identity_verified": True, "privacy_class": "PUBLIC_REVIEW_REQUIRED",
            "collector_id": "fixture-collector", "reviewer_id": "fixture-independent-reviewer", "collection_protocol_sha256": "f" * 64}
        path.write_text(json.dumps({"registry_version": independent_registry.VERSION, "sources": [source]}), encoding="utf-8")
        with patch.object(independent_registry, "PATH", path):
            rows, groups = fixture_rows(13)
            row = copy.deepcopy(rows[-1])
            row["source"].update(dataset=source["id"], version=source["version"], original_split="blind", source_class="INDEPENDENT_EVALUATION")
            row["id"] = hashlib.sha256(json.dumps([row["source"][key] for key in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")).encode()).hexdigest()
            row["metadata"]["independence"] = {"collection_id": "fixture-collection", "collector_id": source["collector_id"], "reviewer_id": source["reviewer_id"],
                "protocol_sha256": source["collection_protocol_sha256"], "registry_version": independent_registry.VERSION,
                "registry_sha256": independent_registry.registry_hash(), "development_exposed": False}
            append_event(row, "PENDING", "PENDING", "fixture", "INDEPENDENT_INTAKE_FIXTURE")
            row = privacy_review(row)
            row = transition(row, "AUTO_VALIDATED", actor="fixture-validator", reason="FIXTURE_ONLY_VALIDATION", groups=groups)
            for state in ("HUMAN_REVIEWED", "APPROVED"):
                proof = human_evidence(row)
                proof["reviewer_id"] = source["reviewer_id"]
                row = transition(row, state, actor=source["reviewer_id"], reason="FIXTURE_ONLY_REVIEW", evidence=proof, groups=groups)
            yield [approve(privacy_review(item), groups) for item in rows[:-1]] + [row], groups


class SplitTests(unittest.TestCase):
    def test_policy_and_schema_checked(self):
        split.check()
        self.assertEqual(sum(split.load_policy()["targets"].values()), 1)
        self.assertFalse(independent_registry.load_sources())

    def test_deterministic_under_input_permutation(self):
        rows, _ = fixture_rows(24)
        assignments, report = split.plan(rows, 17)
        random.Random(4).shuffle(rows)
        reordered, other = split.plan(rows, 17)
        self.assertEqual(assignments, reordered)
        self.assertTrue(report["leakage_passed"])
        self.assertEqual(report["connected_groups"], 24)
        self.assertTrue(all(report["counts"][p] > 0 for p in split.TUNING))
        self.assertEqual(report["counts"][split.FINAL], 0)

    def test_all_required_group_keys_join_transitively(self):
        rows, _ = fixture_rows(4)
        rows[1] = edit(rows[1], family=rows[0]["family"])
        rows[2] = edit(rows[2], scenario_id=rows[1]["scenario_id"])
        rows[3] = edit(rows[3], semantic_parent_id=rows[2]["semantic_parent_id"])
        self.assertEqual(len(split.connected_groups(rows)), 1)
        assignments, report = split.plan(rows)
        self.assertEqual(len(set(assignments.values())), 1)
        self.assertEqual(report["largest_group_rows"], 4)

    def test_source_actors_group_without_exposing_identifiers_in_report(self):
        rows, _ = fixture_rows(2)
        for row in rows:
            row["metadata"]["worker_id"] = "fixture-sensitive-user-id"
            append_event(row, "PENDING", "PENDING", "fixture", "ACTOR_FIXTURE")
        assignments, report = split.plan(rows)
        self.assertEqual(len(set(assignments.values())), 1)
        self.assertEqual(report["actor_metadata_rows"], 2)
        self.assertNotIn("fixture-sensitive-user-id", json.dumps(report))

    def test_generated_seed_and_parent_groups_never_split(self):
        _, rows, _, _, _ = generate(17, 1, "fixture")
        self.assertEqual(len(split.connected_groups(rows)), 1)
        assignments, report = split.plan(rows)
        self.assertEqual(set(assignments.values()), {split.INELIGIBLE})
        self.assertFalse(report["final_ready"])
        with self.assertRaisesRegex(ValueError, "SOURCE_SPLIT_NOT_ELIGIBLE"):
            split.validate_build(rows, assignments)

    def test_missing_generation_parent_rejected(self):
        _, rows, _, _, _ = generate(17, 1, "fixture")
        with self.assertRaisesRegex(ValueError, "PARENT_MISSING"):
            split.connected_groups(rows[1:])

    def test_official_tests_and_connected_family_neighbors_are_held_out(self):
        rows, _ = fixture_rows(3, official_last=True)
        rows[0] = edit(rows[0], family=rows[2]["family"])
        before = copy.deepcopy(rows)
        assignments, report = split.plan(rows)
        self.assertEqual(assignments[rows[0]["id"]], split.PROTECTED)
        self.assertEqual(assignments[rows[2]["id"]], split.PROTECTED)
        self.assertEqual(report["official_rows"], 1)
        self.assertEqual(report["official_test_reassignments"], 0)
        self.assertEqual(rows, before)

    def test_official_test_cannot_be_forced_to_train_or_final(self):
        rows, _ = fixture_rows(1, official_last=True)
        for target in ("TRAIN", split.FINAL):
            forced = {rows[0]["id"]: target}
            with self.assertRaisesRegex(ValueError, "OFFICIAL_TEST_MOVED"):
                split.validate_build(rows, forced)

    def test_leakage_report_catches_family_scenario_and_parent_overlap(self):
        rows, _ = fixture_rows(2)
        for field, check in (("family", "family"), ("scenario_id", "scenario"), ("semantic_parent_id", "semantic_parent")):
            changed = [rows[0], edit(rows[1], **{field: rows[0][field]})]
            forced = {rows[0]["id"]: "TRAIN", rows[1]["id"]: "CALIBRATION"}
            report = split.leakage_report(changed, forced)
            self.assertGreater(report["overlaps"][check], 0)
            with self.assertRaisesRegex(ValueError, "LEAKAGE"):
                split.validate_build(changed, forced)

    def test_normalized_duplicate_and_source_id_overlap_cannot_cross_partitions(self):
        rows, _ = fixture_rows(2)
        row = copy.deepcopy(rows[1])
        row["text"] = rows[0]["text"].upper()
        row["slots"] = [{**slot, "text": slot["text"].upper()} for slot in rows[0]["slots"]]
        row["tokens"], row["bio_tags"] = split.tokenize(row["text"], row["slots"])
        from ml.nlu_v1.data_management.records import text_hash
        row["text_sha256"] = text_hash(row["text"])
        row = edit(row)
        assignments = {row["id"]: "SELECTION", rows[0]["id"]: "TRAIN"}
        self.assertEqual(split.leakage_report([rows[0], row], assignments)["overlaps"]["normalized_text"], 1)
        source_copy = copy.deepcopy(rows[1])
        source_copy["source"]["source_id"] = rows[0]["source"]["source_id"]
        source_copy["source"]["original_split"] = "test"
        source_copy["id"] = hashlib.sha256(json.dumps([source_copy["source"][key] for key in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")).encode()).hexdigest()
        source_copy["partition"] = split.PROTECTED
        source_copy = edit(source_copy)
        assigned = {rows[0]["id"]: "TRAIN", source_copy["id"]: split.PROTECTED}
        self.assertEqual(split.leakage_report([rows[0], source_copy], assigned)["overlaps"]["source_id"], 1)

    def test_conflicting_existing_partition_locks_fail(self):
        rows, _ = fixture_rows(2)
        rows[0] = edit(rows[0], partition="TRAIN")
        rows[1] = edit(rows[1], partition="DEVELOPMENT", family=rows[0]["family"])
        with self.assertRaisesRegex(ValueError, "CONFLICTING_LOCKS"):
            split.plan(rows)

    def test_invalid_slots_labels_dispositions_and_unicode_fail(self):
        rows, _ = fixture_rows(1)
        for field, value in (("slots", [{"type": "DATE", "start": 0, "end": 3, "text": "bad"}]), ("domain", "SAFETY"),
                             ("disposition", "LEGAL"), ("text", "broken\ud800")):
            row = copy.deepcopy(rows[0])
            row[field] = value
            with self.assertRaises((ValueError, ValidationError, UnicodeError)):
                split.plan([row])

    def test_raw_reader_rejects_bad_utf8_surrogates_and_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "bad.jsonl"
            for payload in (b'\xff\n', b'{"x":"\\ud800"}\n', b'{"x":1,"x":2}\n', b'{"x":NaN}\n', b'null\n'):
                path.write_bytes(payload)
                with self.assertRaises((ValueError, ValidationError)):
                    split.read_jsonl(path)

    def test_missing_license_provenance_or_unreviewed_rows_fail(self):
        rows, groups = fixture_rows(2)
        assignments, _ = split.plan(rows)
        with self.assertRaisesRegex(ValueError, "PRIVATE_RESTRICTED_OR_UNKNOWN"):
            split.validate_build(rows, assignments, groups=groups, require_final=False)
        approved, groups = fixture_rows(2, approve_rows=True)
        assignments, _ = split.plan(approved)
        row = copy.deepcopy(approved[0])
        row["privacy_review"]["license_id"] = "Unverified-license"
        with self.assertRaises(ValueError):
            split.validate_build([row, approved[1]], assignments, groups=groups, require_final=False)

    def test_no_embedding_report_and_no_final_cannot_pass_build(self):
        rows, groups = fixture_rows(4, approve_rows=True)
        assignments, _ = split.plan(rows)
        with self.assertRaisesRegex(ValueError, "FRESH_FINAL"):
            split.validate_build(rows, assignments, groups=groups, embeddings=embeddings(rows))
        with self.assertRaisesRegex(ValueError, "INCOMPLETE"):
            split.validate_build(rows, assignments, groups=groups, require_final=False)
        with self.assertRaisesRegex(ValueError, "EMPTY_DATASET"):
            split.validate_build([], {}, require_final=False)

    def test_public_source_cannot_be_labeled_final_independent(self):
        rows, groups = fixture_rows(2, approve_rows=True)
        assignments = {rows[0]["id"]: "TRAIN", rows[1]["id"]: split.FINAL}
        with self.assertRaisesRegex(ValueError, "NOT_INDEPENDENT"):
            split.validate_build(rows, assignments, groups=groups, embeddings=embeddings(rows))

    def test_private_row_never_enters_public_output(self):
        rows, groups = fixture_rows(1)
        row = edit(rows[0], privacy="PRIVATE")
        assignments, _ = split.plan([row])
        with self.assertRaisesRegex(ValueError, "PRIVATE"):
            split.validate_build([row], assignments, groups=groups, require_final=False)

    def test_no_final_build_is_explicitly_incomplete_and_development_blocked(self):
        rows, groups = fixture_rows(5, approve_rows=True)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "bundle"
            manifest = split.write_bundle(rows, root, groups=groups, embeddings=embeddings(rows), require_final=False)
            self.assertEqual(manifest["status"], "NO_FINAL_NOT_COMPLETE")
            self.assertEqual((root / split.FILES[split.FINAL]).read_bytes(), b"")
            split.verify_bundle(root)
            with self.assertRaisesRegex(ValueError, "WITHOUT_FROZEN_FINAL"):
                split.start_development(root, "fixture-developer")
            with self.assertRaises(ValueError):
                split.tuning_rows(root, split.FINAL)

    def test_failed_validation_writes_no_partial_build(self):
        rows, groups = fixture_rows(2)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "not-created"
            with self.assertRaises(ValueError):
                split.write_bundle(rows, root, groups=groups)
            self.assertFalse(root.exists())

    def test_real_final_path_only_with_temporary_independent_intake_and_approval(self):
        with independent_fixture() as (rows, groups), tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "bundle"
            original = copy.deepcopy(rows)
            manifest = split.write_bundle(rows, root, groups=groups, embeddings=embeddings(rows))
            self.assertTrue(manifest["final_ready"])
            self.assertEqual(manifest["row_counts"][split.FINAL], 1)
            self.assertEqual(rows, original)
            split.verify_bundle(root)
            split.start_development(root, "fixture-developer")
            with self.assertRaisesRegex(ValueError, "IMMUTABLE_OUTPUT"):
                split.start_development(root, "fixture-other-developer")
            with self.assertRaises(ValueError):
                split.tuning_rows(root, split.FINAL)
            artifacts = {}
            for name in ("model", "tokenizer", "configuration"):
                artifacts[name] = Path(temp) / (name + ".fixture")
                artifacts[name].write_bytes(b"Invented artifact; never a checkpoint")
            split.seal_model(root, artifacts)
            with self.assertRaises(ValueError):
                split.final_evaluation_rows(root, {}, artifacts)
            approval = {"kind": "FINAL_EVALUATION", "evaluator_id": "fixture-independent-evaluator", "approval_id": "fixture-only-approval",
                "final_freeze_sha256": manifest["files"]["final.freeze.json"], "model_seal_sha256": hashlib.sha256((root / "model.seal.json").read_bytes()).hexdigest()}
            self.assertTrue(split.tuning_rows(root, "TRAIN"))
            original_model = artifacts["model"].read_bytes()
            artifacts["model"].write_bytes(b"Changed after sealing")
            with self.assertRaisesRegex(ValueError, "ARTIFACTS_DO_NOT_MATCH"):
                split.final_evaluation_rows(root, approval, artifacts)
            artifacts["model"].write_bytes(original_model)
            evaluated = split.final_evaluation_rows(root, approval, artifacts)
            self.assertEqual(len(evaluated), 1)
            with self.assertRaisesRegex(ValueError, "IMMUTABLE_OUTPUT"):
                split.final_evaluation_rows(root, approval, artifacts)
            with self.assertRaisesRegex(ValueError, "DEVELOPMENT_CLOSED"):
                split.tuning_rows(root, "TRAIN")
            final_path = root / split.FILES[split.FINAL]
            final_path.write_bytes(final_path.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "FROZEN_SPLIT_FILE_CHANGED"):
                split.verify_bundle(root)

    def test_independent_intake_requires_blind_data_and_separate_review(self):
        with independent_fixture() as (rows, groups):
            row = copy.deepcopy(rows[-1])
            row["metadata"]["independence"]["development_exposed"] = True
            append_event(row, "APPROVED", "APPROVED", "fixture", "EXPOSURE_FIXTURE")
            with self.assertRaises(ValueError):
                split.validate_build(rows[:-1] + [row], split.plan(rows)[0], groups=groups, embeddings=embeddings(rows))

    def test_every_scientific_partition_must_be_nonempty_before_completion(self):
        with independent_fixture() as (rows, groups):
            assignments = {row["id"]: "TRAIN" for row in rows[:-1]}
            assignments[rows[-1]["id"]] = split.FINAL
            with self.assertRaisesRegex(ValueError, "NONEMPTY_TRAIN_DEVELOPMENT"):
                split.validate_build(rows, assignments, groups=groups, embeddings=embeddings(rows))

    def test_official_test_output_preserves_original_text_spans_and_source(self):
        rows, groups = fixture_rows(8, official_last=True, approve_rows=True)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "bundle"
            split.write_bundle(rows, root, groups=groups, embeddings=embeddings(rows), require_final=False)
            official = json.loads((root / "public_benchmarks.jsonl").read_text())
            for key in ("text", "source", "tokens", "slots", "bio_tags"):
                self.assertEqual(official[key], rows[-1][key])
            self.assertNotIn("metadata", official)
            for partition in split.TUNING:
                self.assertNotIn(rows[-1]["id"], (root / split.FILES[partition]).read_text())

    def test_duplicates_between_official_dev_and_test_are_reported(self):
        rows, _ = fixture_rows(1, official_last=True)
        other = copy.deepcopy(rows[0])
        other["source"]["original_split"] = "dev"
        other["id"] = hashlib.sha256(json.dumps([other["source"][key] for key in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")).encode()).hexdigest()
        other = edit(other)
        report = split.leakage_report(rows + [other], {rows[0]["id"]: split.PROTECTED, other["id"]: split.PROTECTED})
        self.assertEqual(report["overlaps"]["normalized_text"], 1)
        self.assertFalse(report["leakage_passed"])

    def test_independent_source_registry_rejects_tuning_permissions_or_missing_license(self):
        with independent_fixture():
            path = independent_registry.PATH
            original = json.loads(path.read_text())
            for change in ("allowed_splits", "license", "reviewer_id"):
                value = copy.deepcopy(original)
                source = value["sources"][0]
                source[change] = {"blind": ["TRAIN"]} if change == "allowed_splits" else {} if change == "license" else source["collector_id"]
                path.write_text(json.dumps(value), encoding="utf-8")
                with self.assertRaises(ValueError):
                    independent_registry.load_sources()

    def test_cli_failed_build_has_no_payload_output(self):
        rows, _ = fixture_rows(2)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "input.jsonl"
            path.write_bytes(jsonl_bytes(rows))
            root = Path(temp) / "output"
            completed = subprocess.run([sys.executable, "-m", "ml.nlu_v1.splitting.split", "--build", str(path), "--output", str(root)], capture_output=True, text=True)
            self.assertNotEqual(completed.returncode, 0)
            self.assertFalse(root.exists())


if __name__ == "__main__":
    unittest.main()
