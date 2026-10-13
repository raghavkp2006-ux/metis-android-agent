"""Data-only calibration review, preservation and eligibility checks."""
import hashlib
import json
import unittest
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


class Intake006(unittest.TestCase):
    def test_manifest_approval_and_source_bindings(self):
        manifest = json.loads((HERE / 'intake-manifest.json').read_text(encoding='utf-8'))
        for name, expected in manifest['hashes'].items():
            self.assertEqual(expected, digest(HERE / name), name)
        approval = json.loads((HERE / 'review-approval.json').read_text(encoding='utf-8'))
        self.assertEqual(approval['intake_sha256'], digest(HERE / 'intake.json'))
        self.assertEqual(approval['review_sha256'], digest(HERE / 'review.md'))
        intake = json.loads((HERE / 'intake.json').read_text(encoding='utf-8'))
        self.assertEqual(intake['source_sha256'], digest(HERE / 'requests.txt'))
        self.assertEqual(intake['earlier_projection_sha256'], digest(HERE.parent / 'candidate_v15/review-projection.json'))

    def test_original_numbering_deduplication_and_reviewed_quotas(self):
        data = json.loads((HERE / 'reviewed.json').read_text(encoding='utf-8'))
        self.assertEqual(list(range(51, 152)), [r['submitted_ordinal'] for r in data['rows']])
        unique = [r for r in data['rows'] if not r['duplicate_of']]
        self.assertEqual(99, len(unique))
        self.assertEqual({'CHECK_MEMORY': 73, 'CHECK_TASKS': 19, 'WHAT_DID_I_PROMISE': 3, 'UNKNOWN': 4},
                         dict(Counter(r['intended_category_draft'] for r in unique)))
        self.assertEqual(0, sum(bool(r['earlier_exact_matches']) for r in unique))
        self.assertFalse(data['calibration_collection_complete'])

    def test_review_never_grants_fitting_or_changes_context(self):
        data = json.loads((HERE / 'reviewed.json').read_text(encoding='utf-8'))
        self.assertFalse(data['predictions_run'])
        for row in data['rows']:
            self.assertTrue(row['human_approved'])
            self.assertTrue(row['family_human_reviewed'])
            self.assertEqual('independent_no_history', row['conversation_context'])
            self.assertFalse(row['fit_eligible'])
            self.assertFalse(row['calibration_eligible'])
            self.assertFalse(row['selection_eligible'])
            self.assertFalse(row['independent_evaluation_eligible'])
            self.assertEqual(row['utterance'], row['constraints_draft']['verbatim_request'])

    def test_utf16_annotations_are_literal_and_drafts_cannot_overwrite_approval(self):
        from draft import build
        with self.assertRaisesRegex(ValueError, 'immutable'):
            build()
        data = json.loads((HERE / 'reviewed.json').read_text(encoding='utf-8'))
        for row in data['rows']:
            raw = row['utterance'].encode('utf-16-le')
            for entity in row['entities_draft']:
                self.assertEqual(entity['rawValue'], raw[2 * entity['startOffset']:2 * entity['endOffset']].decode('utf-16-le'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
