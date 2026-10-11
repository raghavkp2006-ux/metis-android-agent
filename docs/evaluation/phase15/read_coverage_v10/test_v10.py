"""V10 grammar and provenance regressions, distinct from human approval."""
import base64
import csv
import json
import sys
import unittest
from unittest.mock import patch

from v10_candidate import READ_LABELS, resolve, units
from v10_data import HERE, ROOT, all_rows, authored_rows, canonical, exposed_texts
from v10_grammar import kotlin
from v10_evaluate import check_manifest, evaluate, snapshot


class CoverageV10Test(unittest.TestCase):
    def test_current_manifest_historical_bytes_and_generated_grammar(self):
        check_manifest()
        self.assertEqual(kotlin(), (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV10.kt').read_text(encoding='utf-8'))

    def test_authored_data_projection_and_duplicate_exclusion(self):
        payload = json.loads((HERE / 'development-cases.json').read_text(encoding='utf-8'))
        earlier, seen, expected, excluded = exposed_texts(), set(), [], []
        for row in authored_rows():
            key = canonical(row['utterance'])
            if key in earlier or key in seen: excluded.append(row['id']); continue
            seen.add(key); expected.append(row)
        self.assertEqual(expected, payload['rows'])
        self.assertEqual(excluded, payload['excluded_exact_matches'])
        self.assertTrue(all(not r['human_authored'] and not r['human_approved'] and
            r['provenance'] == 'assistant_authored' for r in expected))
        aggregate = all_rows(expected)
        self.assertEqual(aggregate, json.loads((HERE / 'regression-expectations.json').read_text(encoding='utf-8')))
        inputs = list(csv.DictReader((HERE / 'regression-input.tsv').read_text().splitlines(), delimiter='\t'))
        expectations = list(csv.DictReader((HERE / 'regression-expectations.tsv').read_text().splitlines(), delimiter='\t'))
        for row, native, annotation in zip(aggregate, inputs, expectations, strict=True):
            self.assertEqual(row['utterance'], base64.b64decode(native['text_base64']).decode())
            self.assertEqual(row['entities'], json.loads(base64.b64decode(annotation['entities_base64'])))
            self.assertEqual(row['query_text'], base64.b64decode(annotation['query_base64']).decode())

    def test_all_outcomes_entities_complete_queries_and_forced_labels(self):
        rows = json.loads((HERE / 'regression-expectations.json').read_text(encoding='utf-8'))
        for row in rows:
            with self.subTest(id=row['id']):
                plan = resolve(row['utterance'], source=row['input_source'])
                self.assertEqual(int(row['disposition'] == 'read'), plan['reads'])
                self.assertEqual(row['utterance'], plan['request_text'])
                if plan['reads']:
                    self.assertEqual(row['entities'], plan['entities'])
                    self.assertEqual(row['query_text'], plan['query_text'])
                    self.assertEqual(row['intent'], plan['intent'])
                for label in READ_LABELS:
                    self.assertEqual(int(row['disposition'] == 'read' and row['intent'] == label),
                        resolve(row['utterance'], label, row['input_source'])['reads'])

    def test_unicode_bounds_names_and_content_negation(self):
        for text in ('Show me notes about The Who', "Show me notes about my café 🚀 bowl's glaze",
                     'What did I promise Ari D’Souza', 'What did I promise Zoë'):
            plan = resolve(text)
            self.assertEqual(1, plan['reads'])
            for entity in plan['entities']:
                self.assertEqual(entity['rawValue'], text.encode('utf-16-le')[entity['startOffset']*2:entity['endOffset']*2].decode('utf-16-le'))
        for text in ('What did I promise him', 'What did I promise Tomorrow', 'What did I promise My Sister',
                     'Remind me of ' + 'x'*201, 'Remind me of no stoneware', 'Show my tasks\u200b',
                     'Show my tasks\U000e0020', 'Do not retrieve the tasks I have not completed'):
            for label in READ_LABELS: self.assertEqual(0, resolve(text, label)['reads'])
        for source in ('PROACTIVE','NOTIFICATION','WIDGET'):
            self.assertEqual(0, resolve('Show my tasks', source=source)['reads'])

    def test_output_protection_and_stale_snapshot(self):
        with self.assertRaisesRegex(ValueError, 'ignored'):
            evaluate(HERE / 'regression-input.tsv', HERE / 'report.json')
        with self.assertRaises(ValueError): snapshot(HERE / 'regression-input.tsv', HERE / 'regression-input.tsv', [])

    def test_replaced_v8_projection_and_provenance_audit(self):
        # Historical manifests bind old shared production sources. Preserve the
        # entire old audit except that obsolete assertion; v10 certifies current code.
        from test_v8 import CoverageV8Test
        with patch('test_v8.check_manifest', check_manifest):
            CoverageV8Test('test_provenance_data_projections_and_retained_integrity').test_provenance_data_projections_and_retained_integrity()

    def test_replaced_v9_projection_and_exposure_audit(self):
        sys.path.insert(0, str(HERE.parent / 'classifier_v9'))
        import v9_data
        from test_v9 import ClassifierV9Test
        # Reconstruct exposure as it existed before v10's aggregate replay copies.
        # Current later data must never retroactively erase or relabel v9 rows.
        paths = [p for p in HERE.parent.rglob('*') if p.is_file() and
            HERE not in p.parents and (HERE.parent / 'classifier_v9') not in p.parents]
        earlier = set()
        def visit(value):
            if isinstance(value, dict):
                if isinstance(value.get('utterance'), str): earlier.add(v9_data.canonical(value['utterance']))
                for item in value.values(): visit(item)
            elif isinstance(value, list):
                for item in value: visit(item)
        for path in paths:
            if path.suffix == '.json': visit(json.loads(path.read_text(encoding='utf-8-sig')))
            elif path.suffix == '.csv':
                with path.open(encoding='utf-8-sig', newline='') as stream:
                    for row in csv.DictReader(stream):
                        if row.get('utterance'): earlier.add(v9_data.canonical(row['utterance']))
        test = ClassifierV9Test('test_manifest_and_authored_projection_exposure_audit')
        test.data = json.loads((HERE.parent / 'classifier_v9/development-data.json').read_text(encoding='utf-8'))
        test.rows = test.data['rows']
        with patch('test_v9.check_manifest', check_manifest), patch('v9_data.exposed_texts', return_value=earlier), \
             patch('test_v9.exposed_texts', return_value=earlier):
            test.test_manifest_and_authored_projection_exposure_audit()


if __name__ == '__main__': unittest.main()
