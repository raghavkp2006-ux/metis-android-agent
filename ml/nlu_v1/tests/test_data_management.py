"""Plan 04 tests use synthetic in-memory fixtures, never training/evaluation data."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from ml.nlu_v1.ingestion.massive_mapper import map_record
from ml.nlu_v1.ingestion.clinc_mapper import map_record as map_clinc
from ml.nlu_v1.data_management.records import (
    HERE, PRIVACY, STATES, DataError, append_event, assign_group, canonical_issues, classify_privacy,
    facts_hash, load_groups, migrate_ingestion, quarantine, register_scenario, schema, sha_json, text_hash, transition, validate_record,
)
from ml.nlu_v1.data_management.dedupe import audit, collection_hash, verify_report
from ml.nlu_v1.data_management.storage import export_public, process, verify_batch
from jsonschema import ValidationError


def raw(key="1", text="Set alarm next Friday at 8", annotation="Set alarm [date : next Friday] at [time : 8]", split="train"):
    return {"id": key, "utt": text, "annot_utt": annotation, "intent": "alarm_set", "scenario": "alarm", "locale": "en-US", "partition": split, "worker_id": "fixture-worker"}


def managed(key="1", **kwargs):
    mapped = map_record(raw(key, **kwargs))
    mapped["source"].update(artifact_sha256="a" * 64, intake_approval_id="fixture-intake")
    return migrate_ingestion(mapped, batch_id="fixture", row_number=int(key) if key.isdigit() else 1, import_file_sha256="b" * 64)


def groups_for(rows, parent_ids=None):
    families, scenarios = load_groups()
    for index, row in enumerate(rows):
        scenarios = register_scenario(scenarios, families, "fixture-scenario-" + str(index), "alarm_set",
                                      parent_ids[index] if parent_ids else "fixture-parent-" + str(index), "Nonpersonal unit-test scenario")
    groups = families, scenarios
    return [assign_group(row, "alarm_set", "fixture-scenario-" + str(index), groups=groups) for index, row in enumerate(rows)], groups


def privacy_review(row, privacy="PUBLIC", publication=True):
    proposal = {**row, "privacy": privacy}
    evidence = {"binding_sha256": facts_hash(proposal), "review_id": "fixture-privacy", "reviewer_id": "fixture-human",
                "reviewed_at": "2026-10-16T00:00:00Z", "publication_allowed": publication, "license_id": "CC-BY-4.0"}
    return classify_privacy(row, privacy, evidence)


def human_evidence(row):
    return {"kind": "HUMAN_REVIEW", "binding_sha256": facts_hash(row), "reviewer_id": "fixture-human", "review_id": "fixture-review",
            "state_event_sha256": row["review_history"][-1]["event_sha256"], "quarantine_sha256": sha_json(row["quarantine_reasons"])}


def approve(row, groups):
    row = transition(row, "AUTO_VALIDATED", actor="fixture-validator", reason="VALIDATED", groups=groups)
    for state in ("HUMAN_REVIEWED", "APPROVED"):
        row = transition(row, state, actor="fixture-human", reason="FIXTURE_ONLY_REVIEW", evidence=human_evidence(row), groups=groups)
    return row


def embeddings(rows, vectors=None):
    vectors = vectors or [[1.0 if i == j else 0.0 for j in range(len(rows))] for i in range(len(rows))]
    return {"embedding_version": "nlu_v1_embeddings_001", "model_id": "fixture-vectors-no-real-model",
            "model_sha256": "c" * 64, "tokenizer_sha256": "d" * 64, "configuration_sha256": "e" * 64,
            "rows": [{"id": r["id"], "text_sha256": text_hash(r["text"]), "vector": vector} for r, vector in zip(rows, vectors)]}


def public_approval(rows, report):
    return {"kind": "PUBLIC_EXPORT", "input_sha256": collection_hash(rows), "report_sha256": report["report_sha256"],
            "reviewer_id": "fixture-human", "approval_id": "fixture-export", "row_bindings": {r["id"]: facts_hash(r) for r in rows},
            "row_review_bindings": {r["id"]: r["review_history"][-1]["event_sha256"] for r in rows}}


def edit_fixture(row, **changes):
    """Represent a new unreviewed revision; never retain stale privacy approval."""
    result = copy.deepcopy(row)
    result.update(changes)
    result["privacy_review"] = None
    append_event(result, result["review_status"], result["review_status"], "fixture-author", "FIXTURE_REVISION")
    validate_record(result)
    return result


def synthetic_fixture(key="1"):
    result = managed(key)
    result["source"].update(dataset="metis_controlled_synthetic", version="unapproved-test-contract", source_class="CONTROLLED_SYNTHETIC")
    result["id"] = hashlib.sha256(json.dumps([result["source"][k] for k in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")).encode()).hexdigest()
    result["generation"] = {"parent_ids": [], "seed": 7, "generator_id": "fixture-generator", "template_id": "fixture-template"}
    append_event(result, "PENDING", "PENDING", "fixture-author", "FIXTURE_SYNTHETIC_CONTRACT")
    validate_record(result)
    return result


class DataManagementTests(unittest.TestCase):
    def test_exact_privacy_and_review_catalogs_and_schema(self):
        self.assertEqual(["PUBLIC", "PRIVATE", "RESTRICTED", "UNKNOWN_PRIVACY"], PRIVACY)
        self.assertEqual(["PENDING", "AUTO_VALIDATED", "HUMAN_REVIEWED", "APPROVED", "REJECTED", "QUARANTINED"], STATES)
        self.assertEqual(schema(), json.loads((HERE / "canonical_data.schema.json").read_text(encoding="utf-8")))

    def test_migration_preserves_text_source_slots_and_provenance(self):
        mapped = map_record(raw())
        row = migrate_ingestion(mapped, batch_id="fixture", row_number=1, import_file_sha256="b" * 64)
        for key in ("id", "text", "domain", "action", "slots", "disposition", "tokens", "bio_tags"):
            self.assertEqual(mapped[key], row[key])
        for key, value in mapped["source"].items():
            self.assertEqual(value, row["source"][key])
        self.assertEqual("UNKNOWN_PRIVACY", row["privacy"])
        self.assertEqual("PENDING", row["review_status"])
        self.assertEqual(sha_json(mapped), row["provenance"]["ingestion_row_sha256"])
        self.assertEqual(text_hash(mapped["text"]), row["text_sha256"])

    def test_families_scenarios_and_semantic_parent_constraints(self):
        row = managed()
        [row], groups = groups_for([row])
        self.assertEqual("alarm_set", row["family"])
        self.assertEqual("fixture-parent-0", row["semantic_parent_id"])
        with self.assertRaises(DataError):
            assign_group(row, "task_delete", row["scenario_id"], groups=groups)
        with self.assertRaises(DataError):
            register_scenario(groups[1], groups[0], row["scenario_id"], "alarm_set", "different", "duplicate")
        changed = copy.deepcopy(groups)
        changed[1]["scenarios"][row["scenario_id"]]["description"] = "Changed definition"
        self.assertIn("SCENARIO_DEFINITION_CHANGED", canonical_issues(row, changed))

    def test_privacy_and_review_require_bound_evidence_and_no_automatic_approval(self):
        [row], groups = groups_for([managed()])
        with self.assertRaises(DataError):
            transition(row, "AUTO_VALIDATED", actor="fixture", reason="AUTO", groups=groups)
        row = privacy_review(row)
        row = transition(row, "AUTO_VALIDATED", actor="fixture", reason="AUTO", groups=groups)
        with self.assertRaises(DataError):
            transition(row, "APPROVED", actor="fixture-human", reason="SKIP", evidence=human_evidence(row), groups=groups)
        with self.assertRaises(DataError):
            transition(row, "HUMAN_REVIEWED", actor="fixture-human", reason="REVIEW", groups=groups)
        stale = human_evidence(row)
        row = transition(row, "HUMAN_REVIEWED", actor="fixture-human", reason="REVIEW", evidence=stale, groups=groups)
        with self.assertRaises(DataError):
            transition(row, "APPROVED", actor="fixture-human", reason="APPROVE", evidence=stale, groups=groups)

    def test_changed_text_labels_or_history_invalidates_review(self):
        [row], groups = groups_for([managed()])
        row = approve(privacy_review(row), groups)
        for patch in ({"text": "Changed"}, {"action": "DELETE"}, {"family": "task_delete"}):
            with self.assertRaises((DataError, ValidationError, ValueError)):
                validate_record({**row, **patch})
        invalid = copy.deepcopy(row)
        invalid["review_history"][0]["actor"] = "changed"
        with self.assertRaises(DataError):
            validate_record(invalid)
        invalid = copy.deepcopy(row)
        invalid["privacy_review"]["publication_allowed"] = False
        with self.assertRaises(DataError):
            validate_record(invalid)

    def test_all_required_provenance_and_new_enums_are_strict(self):
        row = managed()
        for patch in ({"privacy": "PUBLIC_LICENSED"}, {"review_status": "PENDING_REVIEW"}, {"execute": True}):
            with self.assertRaises(ValidationError):
                validate_record({**row, **patch})
        invalid = copy.deepcopy(row)
        del invalid["provenance"]["import_row_number"]
        with self.assertRaises(ValidationError):
            validate_record(invalid)

    def test_synthetic_parent_seed_metadata_required_and_self_parent_rejected(self):
        row = synthetic_fixture()
        self.assertEqual(7, row["generation"]["seed"])
        invalid = copy.deepcopy(row)
        del invalid["generation"]["seed"]
        with self.assertRaises(ValidationError):
            validate_record(invalid)
        invalid = copy.deepcopy(row)
        invalid["generation"]["parent_ids"] = [row["id"]]
        with self.assertRaises(DataError):
            validate_record(invalid)
        self.assertIn("SOURCE_NOT_APPROVED_FOR_CANONICAL_USE", canonical_issues(row))

    def test_exact_normalized_source_and_skeleton_checks_quarantine_both(self):
        cases = [
            (managed("1"), managed("2"), "exact_text"),
            (managed("1"), managed("2", text="ＳET  alarm next Friday at 8", annotation="ＳET  alarm [date : next Friday] at [time : 8]"), "normalized_text"),
            (managed("1"), managed("1", text="Set alarm Monday at 9", annotation="Set alarm [date : Monday] at [time : 9]"), "source_id"),
            (managed("1"), managed("2", text="Set alarm Monday at 9", annotation="Set alarm [date : Monday] at [time : 9]"), "source_skeleton"),
        ]
        for left, right, check in cases:
            with self.subTest(check=check):
                original = copy.deepcopy([left, right])
                result, report = audit([left, right])
                self.assertEqual(original, [left, right])
                self.assertEqual(2, len(result))
                self.assertEqual(0, report["deleted_rows"])
                self.assertGreater(report["checks"][check]["collision_groups"], 0)
                self.assertEqual(["QUARANTINED"] * 2, [r["review_status"] for r in result])
                self.assertEqual([left["text"], right["text"]], [r["text"] for r in result])

    def test_source_id_scope_avoids_false_clinc_split_ordinal_collisions(self):
        rows = []
        for split in ("train", "test"):
            mapped = map_clinc(["Bank account fixture", "freeze_account"], split, "0")
            rows.append(migrate_ingestion(mapped, batch_id="fixture", row_number=1))
        _, report = audit(rows)
        self.assertEqual(0, report["checks"]["source_id"]["collision_groups"])
        self.assertEqual("PROTECTED_EVALUATION", rows[1]["partition"])

    def test_embedding_similarity_quarantines_without_deleting_or_changing_text(self):
        rows = [managed("1"), managed("2", text="Wake me on Monday by 9", annotation="Wake me on [date : Monday] by [time : 9]")]
        result, report = audit(rows, embeddings(rows, [[1, 0], [0.99, 0.01]]))
        self.assertEqual("COMPLETE", report["checks"]["embedding_similarity"]["status"])
        self.assertEqual(1, report["checks"]["embedding_similarity"]["similar_pairs"])
        self.assertTrue(all("EMBEDDING_SIMILARITY" in r["quarantine_reasons"] for r in result))
        self.assertEqual([r["text"] for r in rows], [r["text"] for r in result])
        self.assertEqual(0, report["deleted_rows"])

    def test_missing_stale_and_invalid_embeddings_cannot_pass_audit(self):
        rows = [managed()]
        result, report = audit(rows)
        self.assertFalse(report["audit_complete"])
        self.assertIn("EMBEDDINGS_MISSING", result[0]["quarantine_reasons"])
        stale = embeddings(rows)
        stale["rows"][0]["text_sha256"] = "0" * 64
        result, report = audit(rows, stale)
        self.assertIn("EMBEDDING_TEXT_BINDING_STALE", result[0]["quarantine_reasons"])
        self.assertFalse(report["audit_complete"])
        for vector in ([float("nan")], [0], [True]):
            invalid = embeddings(rows, [vector])
            with self.assertRaises(DataError):
                audit(rows, invalid)

    def test_unknown_privacy_and_missing_provenance_quarantine(self):
        row = migrate_ingestion(map_record(raw()), batch_id="fixture", row_number=1)
        result, report = audit([row], embeddings([row]))
        for reason in ("SOURCE_ARTIFACT_UNVERIFIED", "PRIVACY_UNRESOLVED", "FAMILY_UNRESOLVED_OR_INCOMPATIBLE", "SCENARIO_PARENT_UNRESOLVED_OR_INCOMPATIBLE"):
            self.assertIn(reason, result[0]["quarantine_reasons"])
        self.assertTrue(report["audit_complete"])

    def test_quarantine_release_is_human_bound_never_automatic(self):
        [row], groups = groups_for([managed()])
        row = privacy_review(row)
        row = quarantine(row, ["MANUAL_REVIEW_REQUIRED"])
        result, _ = audit([row], embeddings([row]), groups=groups)
        self.assertEqual("QUARANTINED", result[0]["review_status"])
        with self.assertRaises(DataError):
            transition(row, "PENDING", actor="fixture-human", reason="RELEASE", groups=groups)
        evidence = human_evidence(row)
        released = transition(row, "PENDING", actor="fixture-human", reason="REVIEWED_RELEASE", evidence=evidence, groups=groups)
        self.assertEqual([], released["quarantine_reasons"])
        self.assertEqual("PENDING", released["review_status"])
        self.assertEqual("QUARANTINED", released["review_history"][-1]["previous"])
        newer = quarantine(row, ["NEW_REASON"])
        with self.assertRaises(DataError):
            transition(newer, "PENDING", actor="fixture-human", reason="RELEASE", evidence=evidence, groups=groups)

    def test_same_semantic_parent_cannot_cross_train_and_protected_partitions(self):
        rows, groups = groups_for([managed("1"), managed("2", split="test")], ["shared-parent", "shared-parent"])
        rows[0] = edit_fixture(rows[0], partition="TRAIN")
        result, _ = audit(rows, embeddings(rows), groups=groups)
        self.assertTrue(all("SEMANTIC_PARENT_PARTITION_CONFLICT" in r["quarantine_reasons"] for r in result))
        with self.assertRaises(DataError):
            edit_fixture(rows[1], partition="TRAIN")

    def test_generation_missing_parents_cycles_and_privacy_inheritance_quarantine(self):
        first, second = synthetic_fixture("1"), synthetic_fixture("2")
        first = edit_fixture(first, generation={**first["generation"], "parent_ids": ["f" * 64]})
        result, _ = audit([first], embeddings([first]))
        self.assertIn("GENERATION_PARENT_MISSING", result[0]["quarantine_reasons"])
        first, second = synthetic_fixture("1"), synthetic_fixture("2")
        first = edit_fixture(first, generation={**first["generation"], "parent_ids": [second["id"]]})
        second = edit_fixture(second, generation={**second["generation"], "parent_ids": [first["id"]]})
        result, _ = audit([first, second], embeddings([first, second]))
        self.assertTrue(all("GENERATION_LINEAGE_CYCLE_OR_DEPENDENT" in r["quarantine_reasons"] for r in result))
        first, second = synthetic_fixture("1"), synthetic_fixture("2")
        first = edit_fixture(first, privacy="PRIVATE")
        second = edit_fixture(second, privacy="PUBLIC", generation={**second["generation"], "parent_ids": [first["id"]]})
        result, _ = audit([first, second], embeddings([first, second]))
        self.assertIn("ANCESTOR_PRIVACY_REVIEW_REQUIRED", result[1]["quarantine_reasons"])

    def test_reviewed_public_export_strips_private_audit_metadata(self):
        [row], groups = groups_for([managed()])
        row = approve(privacy_review(row), groups)
        rows, report = audit([row], embeddings([row]), groups=groups)
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "public.jsonl"
            result = export_public(rows, report, target, approval=public_approval(rows, report), groups=groups)
            self.assertEqual(1, result["public_rows"])
            exported = json.loads(target.read_text(encoding="utf-8"))
            self.assertEqual(row["text"], exported["text"])
            self.assertEqual("PUBLIC", exported["privacy"])
            self.assertNotIn("metadata", exported)
            self.assertNotIn("composition", exported)
            self.assertNotIn("fixture-worker", target.read_text(encoding="utf-8"))
            with self.assertRaises(DataError):
                export_public(rows, report, target, approval=public_approval(rows, report), groups=groups)

    def test_nonpublic_and_unapproved_rows_never_export_and_fail_without_partial_file(self):
        for privacy in ("PRIVATE", "RESTRICTED", "UNKNOWN_PRIVACY"):
            [row], groups = groups_for([managed()])
            row = privacy_review(row, privacy=privacy, publication=False)
            if privacy != "UNKNOWN_PRIVACY":
                row = approve(row, groups)
            rows, report = audit([row], embeddings([row]), groups=groups)
            with tempfile.TemporaryDirectory() as directory:
                target = Path(directory) / "public.jsonl"
                with self.assertRaises(DataError):
                    export_public(rows, report, target, approval=public_approval(rows, report), groups=groups)
                self.assertFalse(target.exists())

    def test_export_requires_fresh_report_and_separate_operator_approval(self):
        [row], groups = groups_for([managed()])
        row = approve(privacy_review(row), groups)
        rows, report = audit([row], embeddings([row]), groups=groups)
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "public.jsonl"
            with self.assertRaises(DataError):
                export_public(rows, report, target, approval=None, groups=groups)
            changed = copy.deepcopy(report)
            changed["deleted_rows"] = 1
            with self.assertRaises(DataError):
                verify_report(rows, changed)
            with self.assertRaises(DataError):
                verify_report([], report)
            _, incomplete = audit(rows, groups=groups)
            with self.assertRaises(DataError):
                export_public(rows, incomplete, target, approval=public_approval(rows, incomplete), groups=groups)

    def test_local_batch_preserves_input_and_quarantines_without_deleting(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "ingestion.jsonl"
            raw_bytes = (json.dumps(map_record(raw())) + "\r\n").encode()
            source.write_bytes(raw_bytes)
            destination = root / "datasets"
            manifest = process(source, batch_id="fixture", root=destination)
            self.assertEqual(1, manifest["quarantine_rows"])
            self.assertEqual(0, manifest["canonical_rows"])
            self.assertEqual(raw_bytes, (destination / "raw/fixture.input.jsonl").read_bytes())
            self.assertEqual(manifest, verify_batch(destination, "fixture"))
            self.assertTrue((destination / "frozen").is_dir())
            with self.assertRaises(DataError):
                process(source, batch_id="fixture", root=destination)
            with (destination / "quarantine/fixture.jsonl").open("ab") as stream:
                stream.write(b"tamper")
            with self.assertRaises(DataError):
                verify_batch(destination, "fixture")

    def test_empty_audit_is_honest_and_does_not_claim_semantic_model_run(self):
        rows, report = audit([])
        self.assertEqual([], rows)
        self.assertEqual(0, report["row_count"])
        self.assertEqual("NOT_APPLICABLE_EMPTY_INPUT", report["checks"]["embedding_similarity"]["status"])
        self.assertIsNone(report["checks"]["embedding_similarity"]["model"])
        verify_report([], report)
        actual = json.loads((Path(__file__).resolve().parents[3] / "docs/nlu_v1/PLAN_04_EMPTY_WORKSPACE_DEDUPE.json").read_text(encoding="utf-8"))
        verify_report([], actual)
        self.assertEqual(report, actual)

    def test_cli_errors_do_not_echo_private_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "invalid.jsonl"
            source.write_text('{"text":"PRIVATE_MARKER"}', encoding="utf-8")
            result = subprocess.run([sys.executable, str(HERE / "manage.py"), "--import-jsonl", str(source), "--batch-id", "fixture", "--root", str(root / "datasets")], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("PRIVATE_MARKER", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
