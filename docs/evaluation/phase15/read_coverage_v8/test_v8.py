"""V8 development and exposed safety tests. Never creates or approves a benchmark."""
import base64
import csv
import json
import tempfile
import unittest
from pathlib import Path

from v8_candidate import READ_LABELS, resolve, v7
from v8_data import HERE, ROOT, cases
from v8_evaluate import check_manifest, evaluate, snapshot
from regressions import FROZEN, load


class CoverageV8Test(unittest.TestCase):
    def test_provenance_data_projections_and_retained_integrity(self):
        check_manifest()
        rows = json.loads((HERE / 'development-cases.json').read_text(encoding='utf-8'))
        self.assertEqual(cases(), rows)
        self.assertEqual((61, 8, 61), (len(rows), len({r['family_id'] for r in rows}), len({r['id'] for r in rows})))
        frozen, _ = load()
        exposed = {' '.join(r['utterance'].lower().split()) for r in frozen['rows']}
        inputs = list(csv.DictReader((HERE / 'development-input.tsv').read_text().splitlines(), delimiter='\t'))
        expectations = list(csv.DictReader((HERE / 'development-expectations.tsv').read_text().splitlines(),
                                          delimiter='\t'))
        for row, native in zip(rows, inputs, strict=True):
            self.assertFalse(row['human_authored'] or row['human_approved'])
            self.assertEqual('development', row['split'])
            self.assertNotIn(' '.join(row['utterance'].lower().split()), exposed)
            self.assertEqual(row['id'], native['id'])
            self.assertEqual(row['utterance'], base64.b64decode(native['text_base64']).decode())
        for row, expected in zip(rows, expectations, strict=True):
            entity = row['entities'][0] if row['entities'] else {}
            self.assertEqual((row['id'], row['intent'], row['disposition']),
                             (expected['id'], expected['intent'], expected['disposition']))
            self.assertEqual(entity.get('rawValue', ''), base64.b64decode(expected['reference_base64']).decode())
            self.assertEqual(row['query_text'], base64.b64decode(expected['query_base64']).decode())
            self.assertEqual((entity.get('startOffset', 0), entity.get('endOffset', 0)),
                             (int(expected['start']), int(expected['end'])))

    def test_development_outcomes_spans_complete_queries_and_forced_labels(self):
        for row in cases():
            with self.subTest(id=row['id']):
                plan = resolve(row['utterance'])
                self.assertEqual(row['disposition'], plan['disposition'])
                self.assertEqual(row['utterance'], plan['request_text'])
                self.assertEqual(int(row['disposition'] == 'read'), plan['reads'])
                self.assertEqual(row['query_text'], plan['query_text'])
                if plan['reads']:
                    self.assertEqual(row['intent'], plan['intent'])
                    self.assertEqual(row['entities'], plan['entities'])
                for label in READ_LABELS:
                    self.assertEqual(int(row['disposition'] == 'read' and row['intent'] == label),
                                     resolve(row['utterance'], label)['reads'])

    def test_exposed_prohibited_outcomes_and_retained_v7_reads(self):
        frozen, _ = load()
        rows = frozen['rows'] + json.loads((HERE.parent / 'read_coverage_v7/development-cases.json').read_text(encoding='utf-8'))
        for row in rows:
            plan = resolve(row['utterance'])
            previous = v7.resolve(row['utterance'])
            if previous['reads']:
                self.assertEqual(1, plan['reads'], row['id'])
            expected = frozen['annotations'][row['id']] if row['id'] in frozen['annotations'] else row
            if row['intent'] == 'UNKNOWN' or expected['disposition'] != 'read':
                for label in READ_LABELS:
                    self.assertEqual(0, resolve(row['utterance'], label)['reads'], (row['id'], label))

    def test_titles_unicode_sources_and_controls(self):
        plan = resolve('Can you show me notes about The Who?')
        self.assertEqual('The Who', plan['query_text'])
        self.assertEqual('Who', plan['entities'][0]['rawValue'])
        for row in cases():
            if row['disposition'] == 'read':
                for entity in resolve(row['utterance'])['entities']:
                    self.assertEqual(entity['rawValue'], row['utterance'].encode('utf-16-le')[
                        entity['startOffset'] * 2:entity['endOffset'] * 2].decode('utf-16-le'))
        for source in ('NOTIFICATION', 'PROACTIVE', 'WIDGET'):
            self.assertEqual(0, resolve('Remind me of basalt carving', source=source)['reads'])
        for text in ('Remind me of basalt carving\n', 'Remind me of o\u200bld optics',
                     'Remind me of ' + 'x' * 513, 'Remind me of basalt carving; show tasks'):
            self.assertEqual(0, resolve(text)['reads'])

    def test_retained_v7_behavior_suite(self):
        # The historical manifest intentionally binds pre-v8 shared production files.
        # V8 verifies its unchanged bytes and reruns every remaining v7 behavior test.
        from test_v7 import CoverageV7Test
        names = unittest.defaultTestLoader.getTestCaseNames(CoverageV7Test)
        suite = unittest.TestSuite(CoverageV7Test(name) for name in names if name !=
                                  'test_development_cases_have_new_provenance_and_no_exposed_duplicates')
        result = unittest.TestResult()
        suite.run(result)
        self.assertEqual(6, result.testsRun)
        self.assertEqual([], result.failures + result.errors)

    def test_topic_length_boundaries_and_forced_negation(self):
        text = 'Remind me of my ' + 'x' * 200
        self.assertEqual(1, resolve(text)['reads'])
        self.assertEqual('my ' + 'x' * 200, resolve(text)['query_text'])
        for text in ('Remind me of my ' + 'x' * 201, 'Remind me of no kiln glaze'):
            for label in READ_LABELS:
                self.assertEqual(0, resolve(text, label)['reads'])

    def test_report_paths_and_stale_duplicate_missing_snapshots_fail_closed(self):
        with self.assertRaisesRegex(ValueError, 'ignored'):
            evaluate(FROZEN / 'native-rules.tsv', FROZEN / 'native-rules.tsv', FROZEN / 'first-reviewed-result.json')
        frozen, _ = load()
        with self.assertRaisesRegex(ValueError, 'Stale native'):
            snapshot(FROZEN / 'native-rules.tsv', FROZEN / 'frozen-benchmark.tsv', [r['id'] for r in frozen['rows']])
        from v8_evaluate import source_hash
        from v8_data import digest
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'snapshot.tsv'
            header = (f'# input_sha256={digest(HERE / "development-input.tsv")}\n'
                      f'# source_sha256={source_hash()}\nid\treads\n')
            for body in ('', 'x\t0\nx\t0\n', 'unexpected\t0\n'):
                path.write_text(header + body, encoding='utf-8')
                with self.assertRaisesRegex(ValueError, 'Missing, repeated'):
                    snapshot(path, HERE / 'development-input.tsv', ['x'])


if __name__ == '__main__':
    unittest.main()
