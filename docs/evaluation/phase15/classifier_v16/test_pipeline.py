"""Model preparation tests; no fit or predictions on human calibration requests."""
import copy
import json
import tempfile
import unittest
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch
import pipeline
from prepare_packet import prepare


class PreparationTests(unittest.TestCase):
    def test_incomplete_data_never_loads_or_fits_model(self):
        with patch('pipeline.engine', side_effect=AssertionError('Model must not load')):
            report = pipeline.train(None, None, 'first')
        self.assertEqual('BLOCKED', report['status'])
        self.assertFalse(report['fitting_started'])
        self.assertEqual(2, len(report['blockers']))

    def test_reviewed_training_rows_exclude_exact_duplicates_and_test_families(self):
        rows = pipeline.frozen_development()
        self.assertEqual(364, len(rows))
        parts = {split: [r for r in rows if r['split'] == split]
                 for split in ('train', 'development_validation', 'development_test')}
        self.assertEqual(254, len(parts['train']))
        pipeline.validate_partitions(parts)
        self.assertTrue(all(r['human_approved'] for r in rows))

    def test_text_and_family_leakage_rejected(self):
        rows = pipeline.frozen_development()
        parts = {split: [r for r in rows if r['split'] == split]
                 for split in ('train', 'development_validation', 'development_test')}
        changed = copy.deepcopy(parts)
        changed['development_test'][0]['family_id'] = parts['train'][0]['family_id']
        with self.assertRaisesRegex(ValueError, 'Family leakage'):
            pipeline.validate_partitions(changed)
        changed = copy.deepcopy(parts)
        changed['development_test'][0]['utterance'] = '  ' + parts['train'][0]['utterance'].upper() + ' '
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            pipeline.validate_partitions(changed)

    def test_unapproved_packet_rejected_before_model_import(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'packet.json'
            target.write_text(json.dumps(dict(purpose='CALIBRATION', subset_human_approved=False)), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'approved/frozen'):
                pipeline.load_collection(target, 'CALIBRATION')
            with patch('pipeline.engine', side_effect=AssertionError('Model must not load')):
                self.assertEqual('BLOCKED', pipeline.train(target, None, 'first')['status'])

    def test_approved_pool_cannot_be_used_as_complete_calibration(self):
        source = pipeline.HERE.parent / 'calibration_intake_006/reviewed.json'
        report = prepare([source], 'CALIBRATION', pipeline.OUTPUT / 'not-written-test-proposal.json')
        self.assertEqual('BLOCKED', report['status'])
        self.assertEqual({'CHECK_CALENDAR': 30, 'WHAT_DID_I_PROMISE': 12, 'UNKNOWN': 6}, report['missing'])
        self.assertFalse(report['subset_written'])
        with self.assertRaisesRegex(ValueError, 'Wrong source collection'):
            prepare([source], 'SELECTION', pipeline.OUTPUT / 'not-written-selection.json')

    def test_parser_gain_and_classifier_gain_are_separate(self):
        rows = [dict(utterance='Did I create a task to return the book?', disposition='read')]
        # This is a synthetic guard probe, not a calibration/test score or model prediction.
        result = pipeline.safety_diagnostic(rows, ['CHECK_TASKS'])
        self.assertEqual(1, result['safe_outcome_gain'])
        self.assertEqual(1, result['deterministic_parser_gain'])
        self.assertEqual(0, result['incremental_classifier_read_gain'])
        self.assertEqual(0, result['forbidden_reads'])

    def test_configuration_and_threshold_floor_are_declared(self):
        self.assertEqual(1515, pipeline.PROTOCOL['seed'])
        self.assertTrue(all(t >= .90 for t in pipeline.PROTOCOL['threshold_grid']))
        self.assertEqual(4, len(pipeline.PROTOCOL['feature_grid']))
        self.assertFalse(pipeline.PROTOCOL['production_ml_enabled'])

    def test_failed_score_is_preserved_and_second_attempt_is_rejected(self):
        # Stub-only lifecycle exercise. No numerical model or human-query predictions.
        events = []
        parts = {split: [dict(id=f'{split}-{i}', utterance=f'fixture {split} {i}', intent=label,
                             disposition='clarify_reference', family_id=f'{split}-{i}', split=split)
                         for i, label in enumerate(pipeline.LABELS)]
                 for split in ('train', 'calibration', 'selection', 'development_validation', 'development_test')}
        class Probabilities:
            def __init__(self, size): self.size = size
            def argmax(self, axis): return [0] * self.size
        model = SimpleNamespace(classes_=pipeline.LABELS)
        def fit(rows, configuration):
            events.append(('fit', rows[0]['split']))
            return model
        def calibrate(model, rows):
            events.append(('temperature', rows[0]['split']))
            return 1.0
        def predict(model, rows, settings=None):
            events.append(('predict', rows[0].get('split')))
            return [r['intent'] for r in rows], Probabilities(len(rows))
        def metrics(rows, labels):
            return dict(macro_f1=.1, unknown_false_accepts=0, known_abstentions=0)
        fake = SimpleNamespace(fit=fit, calibrate=calibrate, predict=predict, metrics=metrics,
            export=lambda *args: {'fixture_only': True},
            exported_predict=lambda payload, rows: ([r['intent'] for r in rows], Probabilities(len(rows))),
            calibration_metrics=lambda *args: {'fixture_only': True},
            np=SimpleNamespace(allclose=lambda *args, **kwargs: True))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cal, sel = root / 'cal.json', root / 'sel.json'
            cal.write_text('{}', encoding='utf-8'); sel.write_text('{}', encoding='utf-8')
            with patch('pipeline.OUTPUT', root / 'reports'), patch('pipeline.engine', return_value=fake), \
                 patch('pipeline.preflight', return_value=({'blockers': []}, parts)):
                result = pipeline.train(cal, sel, 'first')
                self.assertEqual('NO_GO', result['status'])
                saved = (root / 'reports/first/first-result.json').read_bytes()
                with self.assertRaisesRegex(ValueError, 'already started'):
                    pipeline.train(cal, sel, 'second')
                self.assertEqual(saved, (root / 'reports/first/first-result.json').read_bytes())
        self.assertEqual(4, sum(kind == 'fit' for kind, split in events))
        self.assertTrue(all(split == 'train' for kind, split in events if kind == 'fit'))
        self.assertTrue(all(split == 'calibration' for kind, split in events if kind == 'temperature'))
        self.assertEqual(1, sum(kind == 'predict' and split == 'development_test' for kind, split in events))


if __name__ == '__main__':
    unittest.main(verbosity=2)
