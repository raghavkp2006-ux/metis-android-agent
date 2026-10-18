"""Fail-closed execution tests; fixtures never become dataset approvals."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from ml.nlu_v1.dataset_v0 import approved_supplement as supplement
from ml.nlu_v1.data_management.records import DataError, append_event, load_groups, validate_record
from ml.nlu_v1.data_management.storage import atomic_new, json_bytes


class ApprovedSupplementTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ctx = supplement.review.context()
        cls.template = next(iter(cls.ctx['templates'].values()))
        cls.approval = supplement.review.draft(cls.template, cls.ctx)
        # Explicitly an unsigned fixture; the execution entry point separately
        # requires the real batch/signatures before it may call the renderer.
        cls.approval['decision'] = 'APPROVE'
        cls.groups = supplement.extend_groups(cls.ctx, load_groups())

    def row(self, ordinal=0):
        return supplement.make_row(self.ctx, self.template, self.approval, ordinal, self.groups)

    def test_fixed_finite_space_cannot_expand_to_fill_post_dedupe_deficit(self):
        for ordinal in (-1, True, self.template['planned_max_rows']):
            with self.assertRaisesRegex(DataError, 'FINITE_ORDINAL'):
                self.row(ordinal)

    def test_rendering_schema_utf16_bio_and_template_hash_agree(self):
        row = self.row()
        validate_record(row)
        self.assertEqual(row['text'], supplement.review.preview(self.template, 0)['text'])
        self.assertEqual(row['metadata']['template_review']['template_sha256'], supplement.sha_json(self.template))
        self.assertTrue(supplement.validate_exact(row, self.template, self.approval, self.ctx))

    def test_seed_and_parent_and_family_are_not_new_row_approvals(self):
        row = self.row()
        self.assertEqual(row['generation']['seed'], self.template['generation_seed'])
        self.assertEqual(row['semantic_parent_id'], self.template['semantic_parent'])
        self.assertEqual(row['family'], self.template['family'])
        self.assertEqual(row['review_status'], 'QUARANTINED')
        self.assertFalse(row['metadata']['individual_row_human_review_claimed'])
        self.assertFalse(any(e['next'] == 'HUMAN_REVIEWED' for e in row['review_history']))

    def test_native_intake_is_not_fabricated_from_template_approval(self):
        row = self.row()
        self.assertIsNone(row['source']['intake_approval_id'])
        issues = supplement.canonical_issues(row, self.groups)
        self.assertIn('SOURCE_NOT_APPROVED_FOR_CANONICAL_USE', issues)
        self.assertIn('SOURCE_ARTIFACT_UNVERIFIED', issues)

    def test_internal_privacy_has_no_publication_permission(self):
        row = self.row()
        self.assertEqual(row['privacy'], 'RESTRICTED')
        self.assertFalse(row['privacy_review']['publication_allowed'])
        self.assertTrue(row['privacy_review']['reviewer_id'].startswith('machine:'))

    def test_changed_approval_binding_rejected(self):
        bad = copy.deepcopy(self.approval)
        bad['binding']['template_sha256'] = '0' * 64
        with self.assertRaisesRegex(DataError, 'APPROVAL_NOT_BOUND'):
            supplement.make_row(self.ctx, self.template, bad, 0, self.groups)

    def test_unapproved_batch_fails_before_generation(self):
        with patch.object(supplement.review, 'verify_batch', return_value={'all_templates_approved': False}):
            with self.assertRaisesRegex(DataError, 'HUMAN_APPROVAL'):
                supplement.approvals()

    def test_modified_recipe_is_rejected_after_valid_history_rebinding(self):
        row = self.row()
        row['metadata']['template_review']['ordinal'] = 1
        row['privacy_review'] = None
        append_event(row, row['review_status'], row['review_status'], 'fixture:machine', 'FIXTURE_EDIT')
        with self.assertRaisesRegex(DataError, 'EXACT_RECIPE'):
            supplement.validate_exact(row, self.template, self.approval, self.ctx)

    def test_native_schema_detects_wrong_span_text(self):
        row = self.row()
        row['slots'][0]['text'] = 'wrong'
        with self.assertRaises(ValueError):
            supplement.validate_exact(row, self.template, self.approval, self.ctx)

    def test_native_schema_detects_illegal_bio(self):
        row = self.row()
        row['bio_tags'][0] = 'I-SUBJECT'
        with self.assertRaises(ValueError):
            supplement.validate_exact(row, self.template, self.approval, self.ctx)

    def test_governance_cannot_freeze_even_when_candidate_coverage_passes(self):
        report = {'leakage_passed': True, 'counts': {p: 1 for p in supplement.TUNING}}
        result = supplement.refuse_freeze({'missing_candidate_groups': []}, [{'issues': ['SOURCE_NOT_APPROVED']}], report)
        self.assertIn('CANONICAL', result)

    def test_coverage_deficit_prevents_freeze_even_with_other_gates_present(self):
        coverage = {'missing_candidate_groups': ['MEMORY'], 'groups': {
            'MEMORY': {'candidate': {'deficit': {'rows': 5, 'supported_complete': 2}}}}}
        self.assertTrue(supplement.refuse_freeze(coverage, [], {}).startswith('COVERAGE:MEMORY:'))

    def test_missing_partition_prevents_freeze(self):
        result = supplement.refuse_freeze({'missing_candidate_groups': []}, [],
            {'leakage_passed': True, 'counts': {'TRAIN': 1}})
        self.assertIn('FOUR_ELIGIBLE', result)

    def test_preview_is_never_canonical_freeze_authority(self):
        result = supplement.refuse_freeze({'missing_candidate_groups': []}, [],
            {'leakage_passed': True, 'counts': {p: 1 for p in supplement.TUNING}})
        self.assertIn('NATIVE_RESTRICTED_CANONICAL_RELEASE', result)

    def test_conflicts_exclude_all_endpoints_without_deletion(self):
        a, b = self.row(0), self.row(1)
        original = copy.deepcopy([a, b])
        report = {'matches': [{'row_ids': [a['id'], b['id']]}]}
        with patch.object(supplement, 'verify_report') as verify:
            public, generated, excluded = supplement.exclude_conflicts([a], [b], report)
        verify.assert_called_once_with([a, b], report)
        self.assertEqual((public, generated), ([], []))
        self.assertEqual(len(excluded), 2)
        self.assertEqual([a, b], original)

    def test_stale_audit_cannot_authorize_subset(self):
        with self.assertRaisesRegex(DataError, 'STALE'):
            supplement.exclude_conflicts([], [self.row()], {})

    def test_append_only_artifact_hash_check(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            atomic_new(folder / 'summary.json', json_bytes({'dataset_v0_frozen': False}))
            atomic_new(folder / 'manifest.json', json_bytes({'files': {'summary.json': '0' * 64}}))
            with self.assertRaisesRegex(DataError, 'INTEGRITY'):
                supplement.verify_files(folder)

    def test_resume_rechecks_bound_inputs_instead_of_trusting_old_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            atomic_new(folder / 'manifest.json', json_bytes({'files': {},
                'input_sha256': {'ml/nlu_v1/configs/dataset_sources.yaml': '0' * 64}}))
            with self.assertRaisesRegex(DataError, 'BOUND_INPUT_CHANGED'):
                supplement.verify_files(folder, inputs=True)

    def test_registry_and_template_policy_have_separate_truthful_digests(self):
        row = self.row()
        registry = supplement.restricted.ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml'
        self.assertEqual(row['provenance']['source_registry_sha256'], supplement.packet.file_hash(registry))
        self.assertEqual(row['metadata']['synthetic_policy_sha256'], supplement.packet.file_hash(supplement.review.POLICY))

    def test_output_is_local_gitignored_quarantine(self):
        import subprocess
        result = subprocess.run(['git', 'check-ignore', str(supplement.DESTINATION / 'generated.jsonl')],
            cwd=supplement.restricted.ROOT, capture_output=True)
        self.assertEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
