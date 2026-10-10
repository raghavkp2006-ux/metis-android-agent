import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import audit
import collect


class CollectionTests(unittest.TestCase):
    def test_template_is_blank_and_balanced(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'template.csv'
            collect.prepare(path)
            rows = collect.read_csv(path)
            self.assertEqual(len(rows), 125)
            for label in collect.v5_review.LABELS:
                self.assertEqual(sum(r['intent'] == label for r in rows), 25)
            self.assertTrue(all(not r['utterance'] and not r['approved'] and
                                not r['human_authored'] and not r['reviewer'] for r in rows))
            with self.assertRaises(ValueError):
                collect.prepare(path)

    def test_utf16_span(self):
        span = collect.entities('🙂 notes about optics', 'CHECK_MEMORY', 'optics', '')[0]
        self.assertEqual(span['startOffset'], 15)
        self.assertEqual(span['endOffset'], 21)

    def test_repeated_entity_requires_selection(self):
        with self.assertRaises(ValueError):
            collect.entities('Asha or Asha', 'WHAT_DID_I_PROMISE', 'Asha', '')
        span = collect.entities('Asha or Asha', 'WHAT_DID_I_PROMISE', 'Asha', '8')[0]
        self.assertEqual(span['startOffset'], 8)

    def test_invalid_span(self):
        for raw, offset in [('absent', ''), ('optics', '1'), ('', '0')]:
            with self.assertRaises(ValueError):
                collect.entities('optics', 'CHECK_MEMORY', raw, offset)

    def test_conversion_requires_human_truth(self):
        with tempfile.TemporaryDirectory() as folder:
            source, output = Path(folder) / 'input.csv', Path(folder) / 'review.csv'
            row = dict.fromkeys(collect.FIELDS, '')
            row.update(id='fixture', intent='CHECK_MEMORY', utterance='Notes on optics',
                       entity_text='optics', filters_json='{}', disposition='read',
                       expected_response='Retrieve optics notes', reviewer='Fixture reviewer',
                       human_authored='yes')
            collect.write_csv(source, collect.FIELDS, [row])
            with self.assertRaises(ValueError):
                collect.convert(source, output, 'Fixture reviewer')
            self.assertFalse(output.exists())
            source.unlink()
            row['approved'] = 'yes'
            collect.write_csv(source, collect.FIELDS, [row])
            collect.convert(source, output, 'Fixture reviewer')
            converted = collect.read_csv(output)[0]
            self.assertEqual(converted['reviewed_entities'],
                             '[{"type": "MEMORY", "rawValue": "optics", "startOffset": 9, "endOffset": 15}]')
            self.assertEqual(converted['draft_intent'], '')
            with self.assertRaises(ValueError):
                collect.convert(source, output, 'Fixture reviewer')


class AuditTests(unittest.TestCase):
    def test_never_promotes_recorded_python_parity(self):
        result = audit.audit()
        self.assertEqual(result['decision'], 'NO_GO')
        self.assertEqual(result['checks']['frozen_evidence_integrity']['status'], 'passed')
        self.assertEqual(result['checks']['python_export_parity']['status'], 'recorded')
        self.assertEqual(result['checks']['android_parity_and_devices']['status'], 'pending')

    def test_hash_mismatch_fails_integrity(self):
        original = audit.sha
        with patch.object(audit, 'sha', side_effect=lambda p: '0'*64 if p.name == 'language.py' else original(p)):
            result = audit.audit()
        self.assertEqual(result['checks']['frozen_evidence_integrity']['status'], 'failed')

    def test_missing_export_fails_integrity(self):
        original = Path.is_file
        with patch.object(Path, 'is_file', lambda p: False if p.name == 'read-query-v5-development-model.json' else original(p)):
            result = audit.audit()
        self.assertEqual(result['checks']['development_export_integrity']['status'], 'failed')


if __name__ == '__main__':
    unittest.main()
