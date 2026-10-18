"""Machine curation tests use invented records, never human approval claims."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from test_data_management import managed, raw
from ml.nlu_v1.ingestion.massive_mapper import map_record
from ml.nlu_v1.data_management.records import append_event, load_groups, migrate_ingestion, text_hash, facts_hash, validate_record
from ml.nlu_v1.data_management.storage import json_bytes
from ml.nlu_v1.dataset_v0 import machine_curation as machine


def alarm(text="set an alarm at seven am for next Friday", annotation="set an alarm at [time : seven am] for [date : next Friday]", key="1"):
    return managed(key, text=text, annotation=annotation)


class EvidenceFixture:
    """Trusted fixture native bindings; explicitly no network or real approval."""
    root = Path("__nonexistent_machine_test_data__")
    def __init__(self, rows):
        self.rows = {row["id"]: copy.deepcopy(row) for row in rows}
        self.sources = {"massive_1_1_en_us": {"observation": {"intake_approval_id": None}, "lookup": {}}}

    def bind(self, row):
        original = self.rows[row["id"]]
        if row["source"]["original_split"] != "train":
            raise machine.DataError("SOURCE_ROW_NOT_VERIFIED_TRAINING_SPLIT")
        if any(row[field] != original[field] for field in ("text", "domain", "action", "slots", "ood", "tokens", "bio_tags")):
            raise machine.DataError("SOURCE_SEMANTIC_OR_SPAN_CONTRADICTION")
        return {"member_sha256": row["source"]["artifact_sha256"], "license_sha256": "f" * 64,
            "license_id": "CC-BY-4.0", "text_sha256": text_hash(row["text"]), "expected_disposition": "SUPPORTED_COMPLETE"}


class MachineCurationTests(unittest.TestCase):
    def test_family_assignment_is_deterministic(self):
        row = alarm()
        family = machine.family_for(row, load_groups()[0])
        self.assertEqual(family, "alarm_set")
        evidence = EvidenceFixture([row])
        first = machine.curate_row(row, load_groups(), evidence)[0]
        second = machine.curate_row(row, load_groups(), evidence)[0]
        self.assertEqual((first["family"], first["scenario_id"], first["semantic_parent_id"]), (second["family"], second["scenario_id"], second["semantic_parent_id"]))

    def test_ambiguous_family_is_quarantined(self):
        native = raw(text="show calendar next Friday with Alex", annotation="show calendar [date : next Friday] with [person : Alex]")
        native.update(intent="calendar_query", scenario="calendar")
        mapped = map_record(native)
        mapped["source"].update(artifact_sha256="a" * 64, intake_approval_id="fixture-only")
        row = migrate_ingestion(mapped, batch_id="fixture", row_number=1, import_file_sha256="a" * 64)
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertFalse(result["auto_curated"])
        self.assertIn("AMBIGUOUS_FAMILY", result["manual_review_queue"][0]["reasons"])
        self.assertIsNone(result["rows"][0]["family"])

    def test_explicit_composite_family_uses_all_date_and_event_filters(self):
        native = raw(text="show meetings for next Friday", annotation="show [event_name : meetings] for [date : next Friday]")
        native.update(intent="calendar_query", scenario="calendar")
        mapped = map_record(native)
        mapped["source"].update(artifact_sha256="a" * 64, intake_approval_id="fixture-only")
        row = migrate_ingestion(mapped, batch_id="fixture", row_number=1, import_file_sha256="a" * 64)
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertEqual(result["auto_curated"][0]["family"], "calendar_read_date_event")
        self.assertEqual(result["curation_report"]["manual_edge_case_rows"], 0)

    def test_complete_disposition_corrected_only_with_closed_proof(self):
        original = alarm()
        evidence = EvidenceFixture([original])
        row = copy.deepcopy(original)
        row["disposition"] = "SUPPORTED_NEEDS_CONTEXT"
        append_event(row, "PENDING", "PENDING", "fixture", "PROVISIONAL_DISPOSITION_FIXTURE")
        output, reasons, _, _ = machine.curate_row(row, load_groups(), evidence)
        self.assertEqual(output["disposition"], "SUPPORTED_COMPLETE")
        self.assertTrue(output["metadata"]["machine_curation"]["disposition_corrected"])
        self.assertFalse(reasons)

    def test_ambiguous_time_and_injected_clause_do_not_get_corrected(self):
        for row in (alarm("set an alarm at seven", "set an alarm at [time : seven]"),
                    alarm("set an alarm at seven am and delete everything", "set an alarm at [time : seven am] and delete everything")):
            row["disposition"] = "SUPPORTED_NEEDS_CONTEXT"
            append_event(row, "PENDING", "PENDING", "fixture", "AMBIGUITY_FIXTURE")
            with self.assertRaises(ValueError):
                machine.correct_disposition(row, {"expected_disposition": "SUPPORTED_COMPLETE"}, "CLOSED_REQUEST_GRAMMAR")

    def test_privacy_requires_bound_license_source_and_closed_values(self):
        row = alarm()
        proof = EvidenceFixture([row]).bind(row)
        with self.assertRaises(ValueError):
            machine.bind_privacy(row, {**proof, "license_sha256": ""}, "CLOSED_REQUEST_GRAMMAR")
        with self.assertRaises(ValueError):
            machine.bind_privacy(row, {**proof, "text_sha256": "0" * 64}, "CLOSED_REQUEST_GRAMMAR")
        result, reasons, _, _ = machine.curate_row(row, load_groups(), EvidenceFixture([row]))
        self.assertFalse(reasons)
        self.assertEqual(result["privacy"], "PUBLIC")
        self.assertEqual(result["privacy_review"]["binding_sha256"], facts_hash(result))
        self.assertTrue(result["privacy_review"]["reviewer_id"].startswith("machine:"))
        self.assertFalse(result["privacy_review"]["publication_allowed"])
        validate_record(result)

    def test_no_fake_human_review_or_quarantine_release(self):
        from ml.nlu_v1.data_management.records import quarantine
        row = quarantine(alarm(), ["INTAKE_REVIEW_PENDING"])
        before = copy.deepcopy(row)
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertEqual(len(result["auto_curated"]), 1)
        self.assertEqual(result["readiness"][0]["queue_state"], "AUTO_CURATED_READY_FOR_REVIEW")
        output = result["auto_curated"][0]
        self.assertEqual(output["review_status"], "QUARANTINED")
        self.assertTrue(all(event["next"] not in ("HUMAN_REVIEWED", "APPROVED") for event in output["review_history"]))
        self.assertEqual(output["source"]["intake_approval_id"], before["source"]["intake_approval_id"])
        self.assertEqual(row, before)
        self.assertEqual(result["curation_report"]["approved_rows"], 0)

    def test_protected_split_is_excluded(self):
        row = managed("1", split="test")
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertFalse(result["auto_curated"])
        self.assertFalse(result["manual_review_queue"])
        self.assertEqual(result["excluded"][0]["reason"], "PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED")
        self.assertNotIn("text", result["excluded"][0])
        self.assertIsNone(result["rows"][0]["family"])

    def test_approved_protected_rows_are_excluded_before_workflow_checks(self):
        from test_data_management import groups_for, approve, privacy_review
        rows, groups = groups_for([managed("1", split="test")])
        row = approve(privacy_review(rows[0]), groups)
        original = copy.deepcopy(row)
        _, reasons, _, _ = machine.curate_row(row, load_groups(), EvidenceFixture([row]))
        self.assertEqual(reasons, ["PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED"])
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertFalse(result["manual_review_queue"])
        self.assertEqual(result["curation_report"]["protected_or_nonpublic_excluded_rows"], 1)
        self.assertEqual(row, original)
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "protected-exclusion"
            machine.write(result, destination)
            self.assertEqual((destination / "quarantined.jsonl").read_bytes(), b"")
            machine.verify_run(destination)

    def test_missing_capabilities_and_full_label_counts(self):
        row = alarm()
        result = machine.run([row], evidence=EvidenceFixture([row]))
        self.assertIn("MEMORY", result["missing_coverage_report"]["missing_input_groups"])
        self.assertEqual(len(result["coverage_report"]["machine_ready"]["all_labels"]["domains"]), 19)
        self.assertEqual(len(result["coverage_report"]["machine_ready"]["all_labels"]["slots"]), 20)
        self.assertEqual(result["coverage_report"]["machine_ready"]["all_labels"]["domains"]["ALARM"], 1)

    def test_duplicate_and_source_skeleton_rows_are_rejected_from_auto_queue(self):
        first = alarm(key="1")
        second = alarm("set an alarm at eight am for next Friday", "set an alarm at [time : eight am] for [date : next Friday]", key="2")
        result = machine.run([first, second], evidence=EvidenceFixture([first, second]))
        self.assertFalse(result["auto_curated"])
        self.assertEqual(result["curation_report"]["manual_edge_case_rows"], 2)
        self.assertTrue(any(match["check"] == "source_skeleton" for match in result["dedupe_report"]["matches"]))

    def test_cross_partition_family_leakage_is_rejected(self):
        first = alarm(key="1")
        second = alarm("wake me up at eight am", "wake me up at [time : eight am]", key="2")
        first["partition"], second["partition"] = "TRAIN", "DEVELOPMENT"
        for row in (first, second):
            append_event(row, "PENDING", "PENDING", "fixture", "PARTITION_FIXTURE")
        result = machine.run([first, second], evidence=EvidenceFixture([first, second]))
        self.assertFalse(result["auto_curated"])
        self.assertTrue(result["leakage_report"]["findings"])

    def test_manual_queue_contains_only_local_edge_cases_not_global_embedding_gate(self):
        clear = alarm()
        edge = alarm("i need an alarm", "i need an alarm", key="2")
        result = machine.run([clear, edge], evidence=EvidenceFixture([clear, edge]))
        self.assertEqual(len(result["auto_curated"]), 1)
        self.assertEqual(result["curation_report"]["manual_edge_case_rows"], 1)
        self.assertEqual(result["manual_review_queue"][0]["row_ids"], [edge["id"]])
        self.assertIn("APPROVED_EMBEDDING_ARTIFACT_MISSING", result["curation_report"]["blockers"])

    def test_freeze_refused_and_artifacts_are_hashed(self):
        row = alarm()
        result = machine.run([row], evidence=EvidenceFixture([row]))
        with self.assertRaisesRegex(ValueError, "NOT_AN_APPROVED_DATASET"):
            machine.refuse_freeze(result)
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "curation"
            manifest = machine.write(result, destination)
            for name, digest in manifest["files"].items():
                self.assertEqual(hashlib.sha256((destination / name).read_bytes()).hexdigest(), digest)
            self.assertFalse(manifest["training_authorized"])
            self.assertFalse((destination / "train.jsonl").exists())
            self.assertFalse((destination / "final_independent.jsonl").exists())
            machine.verify_run(destination)
            (destination / "coverage_report.json").write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "ARTIFACT_CHANGED"):
                machine.verify_run(destination)

    def test_embeddings_require_separate_hash_bound_approval(self):
        self.assertEqual(machine.approved_embeddings()[1], "APPROVED_EMBEDDING_ARTIFACT_MISSING")
        with tempfile.TemporaryDirectory() as temp:
            path, approval = Path(temp) / "vectors.json", Path(temp) / "approval.json"
            path.write_bytes(json_bytes({"model_sha256": "a" * 64, "tokenizer_sha256": "b" * 64, "configuration_sha256": "c" * 64}))
            approval.write_bytes(json_bytes({"kind": "EMBEDDING_ARTIFACT", "status": "APPROVED", "approval_id": "fixture", "reviewer_id": "fixture", "artifact_sha256": "0" * 64}))
            with self.assertRaises(ValueError):
                machine.approved_embeddings(path, approval)

    def test_privacy_rejects_free_text_and_long_number_values(self):
        self.assertFalse(machine.safe_slot({"type": "PERSON", "text": "Alex"}))
        self.assertFalse(machine.safe_slot({"type": "DATE", "text": "1234567890"}))
        self.assertFalse(machine.safe_slot({"type": "EVENT", "text": "Alex's appointment"}))

    def test_external_finance_rule_does_not_guess_abstract_knowledge_question(self):
        row = {"text": "what is apr", "slots": [], "domain": "UNKNOWN", "action": "NONE", "disposition": "OUT_OF_DOMAIN", "source": {"original_intent": "apr"}}
        self.assertIsNone(machine.semantics(row)[0])
        row["text"] = "what is my apr"
        self.assertEqual(machine.semantics(row)[0], "CLOSED_EXTERNAL_FINANCE_GRAMMAR")

    def test_existing_restricted_privacy_is_never_automatically_public(self):
        row = alarm()
        evidence = EvidenceFixture([row])
        row["privacy"] = "PRIVATE"
        append_event(row, "PENDING", "PENDING", "fixture", "PRIVATE_FIXTURE")
        output, reasons, _, _ = machine.curate_row(row, load_groups(), evidence)
        self.assertEqual(output["privacy"], "PRIVATE")
        self.assertIn("PRIVACY_ALREADY_RESTRICTED_REQUIRES_HUMAN_REVIEW", reasons)
        with self.assertRaises(ValueError):
            machine.bind_privacy(row, evidence.bind(row), "CLOSED_REQUEST_GRAMMAR")

    def test_changed_machine_facts_rebind_existing_privacy_proof(self):
        original = alarm()
        evidence = EvidenceFixture([original])
        first, reasons, _, _ = machine.curate_row(original, load_groups(), evidence)
        self.assertFalse(reasons)
        second, reasons, _, _ = machine.curate_row(first, load_groups(), evidence)
        self.assertFalse(reasons)
        self.assertEqual(second["privacy_review"]["binding_sha256"], facts_hash(second))
        self.assertNotEqual(second["privacy_review"]["binding_sha256"], first["privacy_review"]["binding_sha256"])
        validate_record(second)


if __name__ == "__main__":
    unittest.main()
