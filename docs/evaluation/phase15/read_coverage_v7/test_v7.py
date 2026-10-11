"""Authored development and previously exposed regression checks; no new reviewed score."""
import json
import base64
import unittest

from v7_candidate import READ_LABELS, resolve
from v7_data import HERE, ROOT, cases
from regressions import FROZEN, load
from v7_evaluate import check_manifest, evaluate, snapshot


class CoverageV7Test(unittest.TestCase):
    def test_development_cases_have_new_provenance_and_no_exposed_duplicates(self):
        check_manifest()
        rows = json.loads((HERE / 'development-cases.json').read_text(encoding='utf-8'))
        self.assertEqual(cases(), rows)
        self.assertEqual(60, len(rows))
        self.assertEqual(10, len({r['family_id'] for r in rows}))
        self.assertEqual(len(rows), len({r['id'] for r in rows}))
        frozen, _ = load()
        exposed = {' '.join(r['utterance'].lower().split()) for r in frozen['rows']}
        for row in rows:
            self.assertFalse(row['human_authored'])
            self.assertEqual('development', row['split'])
            self.assertNotIn(' '.join(row['utterance'].lower().split()), exposed)

    def test_native_inputs_and_expectations_are_exact_development_projections(self):
        inputs = [line.split('\t') for line in (HERE / 'development-input.tsv').read_text().splitlines()[1:]]
        expectations = [line.split('\t') for line in (HERE / 'development-expectations.tsv').read_text().splitlines()[1:]]
        for row, actual, expected in zip(cases(), inputs, expectations, strict=True):
            self.assertEqual(row['id'], actual[0])
            self.assertEqual(row['utterance'], base64.b64decode(actual[1]).decode())
            raw = row['entities'][0]['rawValue'] if row['entities'] else ''
            self.assertEqual([row['id'], row['intent'], row['disposition'], raw],
                             expected[:3] + [base64.b64decode(expected[3]).decode()])

    def test_every_development_outcome_and_positive_entity(self):
        for row in cases():
            with self.subTest(id=row['id']):
                plan = resolve(row['utterance'])
                self.assertEqual(row['disposition'], plan['disposition'])
                self.assertEqual(row['utterance'], plan['request_text'])
                self.assertEqual(int(row['disposition'] == 'read'), plan['reads'])
                if plan['reads']:
                    self.assertEqual(row['intent'], plan['intent'])
                    self.assertEqual(row['entities'], plan['entities'])
                for label in READ_LABELS:
                    self.assertEqual(int(row['disposition'] == 'read' and row['intent'] == label),
                                     resolve(row['utterance'], label)['reads'])

    def test_all_exposed_prohibited_outcomes_stay_blocked_for_every_label(self):
        frozen, _ = load()
        for row in frozen['rows']:
            if row['intent'] == 'UNKNOWN' or frozen['annotations'][row['id']]['disposition'] != 'read':
                for label in READ_LABELS:
                    with self.subTest(id=row['id'], label=label):
                        plan = resolve(row['utterance'], label)
                        self.assertEqual(0, plan['reads'])
                        self.assertEqual((0, 0), (plan['proposals'], plan['completed']))

    def test_prepositions_do_not_erase_trailing_constraints(self):
        for suffix in [' last year', ' in May', ' on Monday', ' at 5', ' at 5 pm', ' with tag exam',
                       ' older than 3 days', ' sorted by date', ' from 2024', ' containing equations']:
            text = 'Find my notes about friction in pulleys' + suffix
            self.assertEqual(0, resolve(text)['reads'], text)
            self.assertEqual(text, resolve(text)['request_text'])

    def test_request_source_spans_quotes_and_forged_labels(self):
        for source in ['NOTIFICATION', 'WIDGET', 'PROACTIVE']:
            self.assertEqual(0, resolve('Find my notes about moss on limestone', source=source)['reads'])
        for text in ['Find my notes about friction in pulleys\n', 'Find my notes about o\u200bld optics',
                     'Find my notes about "friction in pulleys"', 'Find my notes about ' + 'x' * 513]:
            self.assertEqual(0, resolve(text)['reads'])
        plan = resolve('Search memory for resonators 🚀')
        span = plan['entities'][0]
        self.assertEqual('resonators 🚀', plan['request_text'].encode('utf-16-le')[span['startOffset'] * 2:
                                                                              span['endOffset'] * 2].decode('utf-16-le'))
        self.assertEqual(0, resolve('Find my notes about moss on limestone', 'CHECK_TASKS')['reads'])

    def test_reports_cannot_target_frozen_results_or_accept_stale_snapshots(self):
        with self.assertRaisesRegex(ValueError, 'ignored'):
            evaluate(FROZEN / 'native-rules.tsv', FROZEN / 'native-rules.tsv', FROZEN / 'first-reviewed-result.json')
        frozen, _ = load()
        with self.assertRaisesRegex(ValueError, 'Stale native'):
            snapshot(FROZEN / 'native-rules.tsv', FROZEN / 'frozen-benchmark.tsv', [r['id'] for r in frozen['rows']])


if __name__ == '__main__':
    unittest.main()
