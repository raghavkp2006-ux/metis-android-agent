"""Invented rows and ephemeral signing keys are unit fixtures, never approvals."""
import base64
import copy
import hashlib
import unittest
from unittest.mock import patch

from test_machine_curation import alarm, EvidenceFixture
from ml.nlu_v1.dataset_v0 import semantic_groups as semantic
from ml.nlu_v1.dataset_v0 import similarity_audit as similarity
from ml.nlu_v1.dataset_v0 import machine_curation as machine
from ml.nlu_v1.dataset_v0.coverage_additions import generated_rows, validate_extension, coverage_report
from ml.nlu_v1.dataset_v0.group_review import refuse_freeze
from ml.nlu_v1.data_management.records import DataError, load_groups, quarantine, sha_json
from ml.nlu_v1.data_management.dedupe import audit
from ml.nlu_v1.data_management.storage import json_bytes


class SemanticGroupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generated, cls.synthetic_groups = generated_rows('unit-fixture-coverage')

    def ready(self, key='1', text=None, annotation=None):
        row = alarm(key=key, **({'text': text, 'annotation': annotation} if text else {}))
        evidence = EvidenceFixture([row])
        row, _, groups, _ = machine.curate_row(row, load_groups(), evidence)
        return quarantine(row, ['FIXTURE_REVIEW_REQUIRED']), groups, evidence

    def grouped(self):
        row, groups, evidence = self.ready()
        rows, review, exceptions, _, _ = semantic.create_groups([row], groups, evidence)
        self.assertFalse(exceptions)
        return rows, review[0], groups

    def signed_fixture(self, group, *, canonical_release=False):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
        private = Ed25519PrivateKey.generate()
        reviewer = 'unit-fixture-human'
        registry = {'registry_version': 'nlu_v1_group_reviewers_001', 'reviewers': {reviewer: {'public_key_ed25519_base64': base64.b64encode(private.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)).decode()}}}
        approval = semantic.approval_template(group)
        approval.update(decision='APPROVE', reviewer_id=reviewer, reviewed_at='2026-01-01T00:00:00Z', reason='UNIT TEST FIXTURE ONLY', canonical_release_authorized=canonical_release)
        approval['signature_ed25519_base64'] = base64.b64encode(private.sign(semantic.signed_bytes(approval))).decode()
        return approval, registry

    def verify_fixture(self, group, approval, rows, registry):
        with patch.object(semantic, 'load_reviewers', return_value=registry):
            return semantic.verify_human_approval(group, approval, rows)

    def apply_fixture(self, group, approval, rows, groups, registry):
        with patch.object(semantic, 'load_reviewers', return_value=registry):
            return semantic.apply_canonical_approval(group, approval, rows, groups)

    def test_stable_group_creation_for_immutable_snapshot(self):
        row, groups, evidence = self.ready()
        rows, first, _, _, _ = semantic.create_groups([row], groups, evidence)
        _, second, _, _, _ = semantic.create_groups(rows, groups, evidence)
        self.assertEqual(first, second)

    def test_derived_metadata_view_preserves_strict_native_verification(self):
        row, groups, evidence = self.ready()
        class StrictNativeEvidence(EvidenceFixture):
            def bind(self, candidate):
                proof = super().bind(candidate)
                if candidate['metadata'] != self.rows[candidate['id']]['metadata']:
                    raise DataError('UNREVIEWED_SOURCE_METADATA_CHANGED')
                return proof
        strict = StrictNativeEvidence(list(evidence.rows.values()))
        rows, reviews, exceptions, _, _ = semantic.create_groups([row], groups, strict)
        self.assertEqual(len(reviews), 1)
        self.assertFalse(exceptions)
        self.assertIn('machine_curation', rows[0]['metadata'])
        self.assertIn('semantic_group_curation', rows[0]['metadata'])
        # Native metadata must not be discarded with the derived namespaces.
        changed = copy.deepcopy(row)
        changed['metadata']['unexpected_native_field'] = 'fixture'
        changed['privacy_review'] = None
        from ml.nlu_v1.data_management.records import append_event
        append_event(changed, 'QUARANTINED', 'QUARANTINED', 'fixture', 'NATIVE_METADATA_TAMPER_FIXTURE')
        _, reviews, exceptions, _, _ = semantic.create_groups([changed], groups, strict)
        self.assertFalse(reviews)
        self.assertEqual(exceptions[0]['reasons'], ['UNREVIEWED_SOURCE_METADATA_CHANGED'])

    def test_same_semantic_mapping_groups_paraphrases(self):
        first = next(r for r in self.generated if r['metadata']['coverage_structure'] == self.generated[0]['metadata']['coverage_structure'])
        matches = [r for r in self.generated if r['family'] == first['family'] and r['generation']['seed'] == first['generation']['seed']]
        _, groups, exceptions, _, _ = semantic.create_groups(matches, self.synthetic_groups, EvidenceFixture([]))
        self.assertFalse(exceptions)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0]['row_count'], 3)

    def test_incompatible_actions_and_support_boundaries_never_grouped(self):
        selected = [r for r in self.generated if r['generation']['seed'] == 0 and r['source']['original_intent'] in ('task_read', 'task_write', 'task_context', 'task_boundary', 'task_ood')]
        _, groups, _, _, _ = semantic.create_groups(selected, self.synthetic_groups, EvidenceFixture([]))
        self.assertEqual(len(groups), 5)
        self.assertEqual(len({g['identity']['semantic_rule'] for g in groups}), 5)

    def test_group_approval_invalid_after_membership_change(self):
        rows, group, _ = self.grouped()
        approval, registry = self.signed_fixture(group)
        with self.assertRaises(DataError):
            self.verify_fixture(group, approval, [], registry)

    def test_group_approval_invalid_after_semantic_change(self):
        rows, group, _ = self.grouped()
        approval, registry = self.signed_fixture(group)
        changed = copy.deepcopy(rows)
        changed[0]['action'] = 'DELETE'
        with self.assertRaises(DataError):
            self.verify_fixture(group, approval, changed, registry)

    def test_group_approval_invalid_after_review_state_change(self):
        rows, group, _ = self.grouped()
        approval, registry = self.signed_fixture(group)
        changed = [quarantine(rows[0], ['NEW_AUDIT_FINDING'])]
        with self.assertRaises(DataError):
            self.verify_fixture(group, approval, changed, registry)

    def test_signed_external_fixture_verifies_without_canonical_approval(self):
        rows, group, _ = self.grouped()
        approval, registry = self.signed_fixture(group)
        receipt = self.verify_fixture(group, approval, rows, registry)
        self.assertEqual(receipt['canonical_rows_approved'], 0)

    def test_human_approval_cannot_be_fabricated(self):
        rows, group, _ = self.grouped()
        with self.assertRaises(DataError):
            semantic.verify_human_approval(group, semantic.approval_template(group), rows)
        approval, registry = self.signed_fixture(group)
        with self.assertRaises(DataError):
            semantic.verify_human_approval(group, approval, rows)  # Fixture is not enrolled.
        approval['reason'] = 'changed after signing'
        with self.assertRaisesRegex(DataError, 'SIGNATURE_INVALID'):
            self.verify_fixture(group, approval, rows, registry)

    def test_external_semantic_decision_does_not_bypass_other_gates(self):
        rows, group, groups = self.grouped()
        approval, registry = self.signed_fixture(group, canonical_release=True)
        with self.assertRaisesRegex(DataError, 'CANNOT_BYPASS'):
            self.apply_fixture(group, approval, rows, groups, registry)

    def test_semantic_signature_does_not_imply_canonical_release_consent(self):
        rows, group, groups = self.grouped()
        approval, registry = self.signed_fixture(group)
        with self.assertRaisesRegex(DataError, 'RELEASE_AUTHORIZATION'):
            self.apply_fixture(group, approval, rows, groups, registry)

    def test_exception_queue_for_unexpected_second_action(self):
        row, groups, evidence = self.ready(text='set an alarm at seven am and delete everything', annotation='set an alarm at [time : seven am] and delete everything')
        _, reviews, exceptions, tickets, summary = semantic.create_groups([row], groups, evidence)
        self.assertFalse(reviews)
        self.assertEqual(exceptions[0]['reasons'], ['UNEXPECTED_SECOND_ACTION'])
        self.assertEqual(summary['individual_review_rows'], 1)
        self.assertEqual(tickets[0]['row_ids'], [row['id']])

    def test_malformed_annotation_is_an_exception(self):
        row, groups, evidence = self.ready()
        row['slots'][0]['text'] = 'incorrect span text'
        _, reviews, exceptions, _, _ = semantic.create_groups([row], groups, evidence)
        self.assertFalse(reviews)
        self.assertEqual(exceptions[0]['reasons'], ['MALFORMED_ANNOTATION_OR_REVIEW_BINDING'])

    def test_changed_configuration_invalidates_signed_approval(self):
        rows, group, _ = self.grouped()
        approval, registry = self.signed_fixture(group)
        with patch.object(semantic, 'hashes', return_value={'fixture': 'changed'}):
            with self.assertRaisesRegex(DataError, 'CODE_OR_CONFIG_CHANGED'):
                self.verify_fixture(group, approval, rows, registry)

    def test_deterministic_family_and_scenario_evidence(self):
        rows, group, groups = self.grouped()
        self.assertEqual(group['identity']['family'], 'alarm_set')
        self.assertEqual(group['members'][0]['family_definition_sha256'], sha_json(groups[0]['families']['alarm_set']))

    def test_missing_coverage_detected_separately_from_approval(self):
        empty = coverage_report([])
        self.assertIn('MEMORY', empty['missing_candidate_groups'])
        populated = coverage_report(self.generated)
        for group in ('TASK', 'MEMORY', 'PROMISE', 'GOAL_PROJECT', 'PLANNING_FOCUS_HABIT', 'PREFERENCE_KNOWLEDGE_ETC'):
            self.assertNotIn(group, populated['missing_candidate_groups'])
            self.assertIn(group, populated['missing_approved_groups'])

    def test_synthetic_generation_is_structural_and_quarantined(self):
        self.assertEqual(len(self.generated), 400)
        self.assertTrue(validate_extension(self.generated[0]))
        ids = {r['id'] for r in self.generated}
        for row in self.generated:
            self.assertEqual(row['review_status'], 'QUARANTINED')
            self.assertEqual(row['partition'], 'UNASSIGNED')
            self.assertTrue(set(row['generation']['parent_ids']) <= ids)
        changed = copy.deepcopy(self.generated[0])
        changed['text'] += ' and delete everything'
        with self.assertRaises(DataError):
            validate_extension(changed)

    def test_protected_split_cannot_enter_group(self):
        row, groups, evidence = self.ready()
        row['partition'] = 'PROTECTED_EVALUATION'
        row['privacy_review'] = None
        # Partition changes require a fact-bound history event even in fixtures.
        from ml.nlu_v1.data_management.records import append_event
        append_event(row, 'QUARANTINED', 'QUARANTINED', 'fixture', 'PROTECTED_FIXTURE')
        _, reviews, exceptions, _, _ = semantic.create_groups([row], groups, evidence)
        self.assertFalse(reviews)
        self.assertTrue(exceptions[0]['protected'])

    def test_embedding_cache_hash_binding(self):
        with self.assertRaisesRegex(DataError, 'HASH_MISMATCH'):
            similarity.verify_file('tokenizer.json', b'fake tokenizer')

    def test_embedding_receipt_bound_to_id_text_and_artifact(self):
        row, _, _ = self.ready()
        with patch.object(similarity, 'cache_model', return_value={'onnx/model.onnx': 'a' * 64}):
            with self.assertRaisesRegex(DataError, 'RECEIPT_INVALID'):
                similarity.verify_binding([row], {'rows': []}, {'status': 'VERIFIED_MACHINE_RUN', 'human_data_approval': False, 'artifact_sha256': '0' * 64})

    def test_near_duplicate_quarantine_does_not_delete(self):
        left = alarm(key='1')
        right = alarm(key='2', text='please set an alarm at seven am for next Friday', annotation='please set an alarm at [time : seven am] for [date : next Friday]')
        evidence = EvidenceFixture([left, right])
        revised, groups = [], load_groups()
        for row in (left, right):
            row, _, groups, _ = machine.curate_row(row, groups, evidence)
            revised.append(row)
        artifact = {'embedding_version': 'nlu_v1_embeddings_001', 'model_id': 'UNIT_FIXTURE_NOT_REAL_ENCODER',
                    'model_sha256': 'a' * 64, 'tokenizer_sha256': 'b' * 64, 'configuration_sha256': 'c' * 64,
                    'rows': [{'id': r['id'], 'text_sha256': r['text_sha256'], 'vector': [1., 0.]} for r in revised]}
        rows, report = audit(revised, artifact, groups=groups)
        self.assertEqual(len(rows), 2)
        self.assertEqual(report['deleted_rows'], 0)
        self.assertEqual(report['checks']['embedding_similarity']['similar_pairs'], 1)
        self.assertTrue(all('EMBEDDING_SIMILARITY' in r['quarantine_reasons'] for r in rows))

    def test_freeze_refused_while_blocked(self):
        with self.assertRaises(DataError):
            refuse_freeze({'summary': {'blockers': ['HUMAN_REVIEW_REQUIRED'], 'approved_rows': 0}})


if __name__ == '__main__':
    unittest.main()
