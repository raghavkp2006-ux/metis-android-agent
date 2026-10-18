"""Policy-002 fixtures assess evidence, never issue real source approvals."""
import copy
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import yaml

from ml.nlu_v1.dataset_v0 import source_privacy as policy
from ml.nlu_v1.data_management.records import DataError


class SourcePrivacyTests(unittest.TestCase):
    def matrix(self):
        return {r: {'status': 'VERIFIED', 'evidence': [{'fixture': 'not-real-data'}]} for r in policy.REQUIREMENTS}

    def test_new_policy_exact_allowlist_and_no_added_authority(self):
        p = policy.load_policy()
        self.assertEqual(set(p['scope']), {'massive_1_1_en_us', 'clinc150_full'})
        self.assertFalse(p['application']['publication_allowed'])
        self.assertFalse(p['application']['training_authorized'])
        self.assertFalse(p['application']['canonical_release_authorized'])
        self.assertFalse(p['application']['individual_human_review_claim_allowed'])

    def test_public_requires_every_gate(self):
        self.assertEqual(policy.finding(self.matrix()), 'SOURCE_LEVEL_PUBLIC')
        for key in policy.REQUIREMENTS:
            matrix = self.matrix()
            matrix[key]['status'] = 'MISSING'
            self.assertEqual(policy.finding(matrix), 'INSUFFICIENT_EVIDENCE')

    def test_license_alone_never_clears_source(self):
        matrix = self.matrix()
        for key in matrix:
            if key != 'verified_license_evidence':
                matrix[key]['status'] = 'UNKNOWN'
        self.assertEqual(policy.finding(matrix), 'INSUFFICIENT_EVIDENCE')

    def test_missing_document_body_not_verified_by_hash_only(self):
        matrix = self.matrix()
        matrix['source_collection_privacy_documentation'] = {'status': 'REQUIRES_DOCUMENT_CONTENT_ASSESSMENT', 'evidence': [], 'pinned_readme_sha256': 'a' * 64}
        self.assertEqual(policy.finding(matrix), 'INSUFFICIENT_EVIDENCE')

    def test_unknown_gate_or_missing_gate_rejected(self):
        matrix = self.matrix()
        matrix['invented'] = matrix.pop('exact_source_identity')
        with self.assertRaises(DataError):
            policy.finding(matrix)

    def test_verified_label_needs_actual_evidence_reference(self):
        matrix = self.matrix()
        matrix['source_collection_privacy_documentation']['evidence'] = []
        self.assertEqual(policy.finding(matrix), 'INSUFFICIENT_EVIDENCE')

    def test_known_restriction_is_not_public(self):
        self.assertEqual(policy.finding(self.matrix(), restricted=True), 'SOURCE_LEVEL_RESTRICTED')

    def test_future_or_synthetic_source_not_silently_added(self):
        p = policy.load_policy()
        p['scope']['metis_controlled_synthetic'] = copy.deepcopy(next(iter(p['scope'].values())))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'policy.yaml'
            path.write_text(yaml.safe_dump(p))
            with patch.object(policy, 'POLICY', path):
                with self.assertRaises(DataError):
                    policy.load_policy()

    def test_write_outside_local_quarantine_is_rejected(self):
        with self.assertRaises(DataError):
            policy.write({}, policy.ROOT / 'docs/prohibited-privacy-fixture')

    def test_source_pins_only_allow_training_splits(self):
        p = policy.load_policy()
        for source in p['scope'].values():
            self.assertLessEqual(set(source['original_splits']), {'train', 'oos_train'})
            for key in ('archive_sha256', 'member_sha256'):
                self.assertEqual(len(source[key]), 64)


if __name__ == '__main__':
    unittest.main()
