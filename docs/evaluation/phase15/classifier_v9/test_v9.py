"""Development integrity and classifier boundary regressions; no independent evaluation."""
import copy
import csv
import json
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np

from v9_data import HERE, ROOT, LABELS, PARTITIONS, SLOTS, authored_rows, canonical, exposed_texts, validate
from v9_model import fit, calibrate, select, predict, export, exported_predict
from v9_evaluate import check_manifest, output_path, resolve, complete_outcomes


class ClassifierV9Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((HERE / 'development-data.json').read_text(encoding='utf-8'))
        cls.rows = cls.data['rows']
        cls.train = [r for r in cls.rows if r['split'] == 'train']
        # Small but real word+character model: export tests exercise both feature blocks.
        cls.sample = [r for label in LABELS for r in [r for r in cls.train if r['intent'] == label][:5]]
        cls.model = fit(cls.sample, 1.0, True)

    def test_manifest_and_authored_projection_exposure_audit(self):
        check_manifest()
        exposed, seen, expected, exclusions = exposed_texts(), set(), [], []
        for row in authored_rows():
            key = canonical(row['utterance'])
            if key in exposed or key in seen:
                exclusions.append(row['id'])
                continue
            seen.add(key); expected.append(row)
        self.assertEqual(expected, self.rows)
        with (HERE / 'development-data.csv').open(encoding='utf-8', newline='') as stream:
            projection = list(csv.DictReader(stream))
        self.assertEqual(len(self.rows), len(projection))
        for row, projected in zip(self.rows, projection):
            self.assertEqual(row['entities'], json.loads(projected.pop('entities')))
            self.assertTrue(all(row[key] == value for key, value in projected.items()))
        self.assertEqual(exclusions, [r['id'] for r in self.data['exclusions']])
        validate(self.rows, exposed)
        self.assertEqual(100, len(self.data['families']))
        self.assertEqual(dict(train=70, calibration=5, selection=10, test=15),
                         {s: list(self.data['families'].values()).count(s) for s in PARTITIONS})
        for split in PARTITIONS:
            self.assertEqual(set(LABELS), {r['intent'] for r in self.rows if r['split'] == split})

    def test_family_duplicate_offset_fixture_and_provenance_corruption_fail(self):
        for change in ('family', 'duplicate', 'offset', 'fixture', 'provenance'):
            rows = copy.deepcopy(self.rows)
            row = next(r for r in rows if r['split'] == 'train' and r['entities'])
            if change == 'family': row['split'] = 'test'
            elif change == 'duplicate': rows.append(copy.deepcopy(row))
            elif change == 'offset': row['entities'][0]['endOffset'] -= 1
            elif change == 'fixture': row['utterance'] += ' ' + SLOTS['test'][0]
            else: row['human_approved'] = True
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(rows, set())

    def test_wrong_partitions_never_fit_calibrate_or_select(self):
        held = [r for r in self.rows if r['split'] == 'test']
        with self.assertRaisesRegex(ValueError, 'only train'):
            fit(held, 1.0, False)
        with self.assertRaisesRegex(ValueError, 'only calibration'):
            calibrate(self.model, self.train)
        calibration = [r for r in self.rows if r['split'] == 'calibration']
        with patch('v9_model.fit') as trainer:
            with self.assertRaisesRegex(ValueError, 'only selection'):
                select(self.train, calibration, held)
            trainer.assert_not_called()
        vocabulary = self.model.named_steps['features'].transformer_list[0][1].vocabulary_
        self.assertNotIn('lapidary', vocabulary)

    def test_numeric_export_parity_unicode_and_tampering(self):
        selected = dict(temperature=.8, threshold=.9, abstain=False)
        payload = export(self.model, selected, 'development-fixture')
        probes = [dict(utterance=text) for text in (
            'Show notes about The Who', 'What did I promise Zoë O’Neil?', 'Recall café 🚀 tuning',
            'Ｓｈｏｗ my tasks', 'Do not delete my notes', 'Can you read my calendar?', 'unknown 🧩')]
        labels, values = predict(self.model, probes, .8, .9)
        restored, probabilities = exported_predict(json.loads(json.dumps(payload)), probes)
        self.assertEqual(labels, restored)
        np.testing.assert_allclose(values, probabilities, rtol=1e-12, atol=1e-12)
        payload['intercept'][0] += 1
        with self.assertRaisesRegex(ValueError, 'drift'):
            exported_predict(payload, probes)

    def test_threshold_abstention_and_export_output_protection(self):
        labels, _ = predict(self.model, self.sample, abstain=True)
        self.assertEqual({'UNKNOWN'}, set(labels))
        payload = export(self.model, dict(temperature=1.0, threshold=None, abstain=True), 'fixture')
        restored, _ = exported_predict(payload, self.sample)
        self.assertEqual(labels, restored)
        with self.assertRaises(ValueError): predict(self.model, self.sample, threshold=.5)
        with self.assertRaises(ValueError): output_path(HERE / 'development-report.json')
        with self.assertRaises(ValueError): output_path(ROOT / 'docs/evaluation/phase15/human_review_003/first_evaluation/result.json')

    def test_forced_labels_cannot_bypass_fixed_gate_or_drop_constraints(self):
        prohibited = [r for r in self.rows if r['disposition'] != 'read']
        for row in prohibited:
            for label in LABELS:
                plan = resolve(row['utterance'], label)
                self.assertEqual(0, plan['reads'], row['id'])
                self.assertEqual(row['utterance'], plan['request_text'])
        row = next(r for r in self.rows if r['kind'] == 'compound')
        native = {row['id']: dict(intent='UNKNOWN', reads='0', status='FOLLOW_UP', entities='',
                                 proposals='0', completed='0', query_base64='')}
        # Even a forged confident read label remains only a suggestion.
        with patch('v9_evaluate.predict', return_value=(['CHECK_MEMORY'], np.array([[1., 0., 0., 0., 0.]]))), \
             patch('v9_evaluate.calibration_metrics', return_value={}):
            result = complete_outcomes([row], native, self.model, dict(temperature=1., threshold=.9, abstain=False))
        self.assertEqual(0, result['counts']['simulated_hybrid_reads'])
        self.assertEqual(row['utterance'], result['details'][0]['request_text'])


if __name__ == '__main__':
    unittest.main()
