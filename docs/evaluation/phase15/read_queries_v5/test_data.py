import json
import csv
import tempfile
import unittest
from pathlib import Path

from data import HERE, TOPICS, PEOPLE, PARTITIONS, old
from corpus import read_csv, normalized_text
from v5_review import prepare, freeze, FIELDS
from language import guard

class DataTests(unittest.TestCase):
    def test_families_labels_slots_and_exposure(self):
        rows=read_csv(HERE/'intent-v5.csv');old.validate(rows)
        for pool in (TOPICS,PEOPLE):
            for a in PARTITIONS:
                for b in PARTITIONS:
                    if a!=b:self.assertFalse(set(pool[a])&set(pool[b]))
        development={normalized_text(r['utterance']) for r in rows}
        for path in (HERE.parent/'independent_125/queries.csv',old.V3/'user-challenge-2026-10-10.csv'):
            self.assertFalse(development & {normalized_text(r['utterance']) for r in read_csv(path)})
        for part in PARTITIONS:
            self.assertEqual(set(old.LABELS),{r['intent'] for r in rows if r['split']==part and not guard(r['utterance'])})

    def test_annotations_cover_rows_with_valid_spans(self):
        rows=read_csv(HERE/'intent-v5.csv')
        expected=json.loads((HERE/'annotations-v5.json').read_text(encoding='utf-8'))
        self.assertEqual({r['id'] for r in rows},set(expected))
        for row in rows:
            annotation=expected[row['id']]
            self.assertIn(annotation['disposition'],{'read','clarify','reject','unsupported'})
            self.assertEqual(json.loads(row['entities']),annotation['entities'])

    def test_drafts_cannot_approve_or_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'review.csv';prepare(path)
            rows=read_csv(path)
            self.assertEqual(125,len(rows))
            self.assertTrue(all(not r['approved'] and not r['reviewer'] and not r['reviewed_intent'] for r in rows))
            with self.assertRaises(ValueError):prepare(path)
            with self.assertRaises(ValueError):freeze(path,Path(directory)/'frozen.json','reviewer')

    def test_fresh_freeze_binds_candidate_and_refuses_reuse(self):
        # Fictional validator fixtures only; never treated as real human contributions.
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'fresh.csv';output=Path(directory)/'frozen.json'
            with path.open('w',newline='',encoding='utf-8') as stream:
                writer=csv.DictWriter(stream,fieldnames=FIELDS);writer.writeheader()
                for label in old.LABELS:
                    for index in range(25):
                        writer.writerow(dict(utterance=f'Fictional validator fixture {label} {index}',reviewed_intent=label,
                            reviewed_entities='[]',reviewed_filters='{}',reviewed_disposition='clarify',
                            reviewer='test-fixture',approved='yes',human_authored='yes'))
            freeze(path,output,'test-fixture')
            frozen=json.loads(output.read_text())
            self.assertEqual(125,len(frozen['rows']))
            from corpus import sha256
            self.assertEqual(sha256(HERE/'manifest-v5.json'),frozen['candidate_manifest_sha256'])
            self.assertEqual(sha256(output.with_suffix('.tsv')),frozen['input_sha256'])
            with self.assertRaises(ValueError):freeze(path,output,'test-fixture')

if __name__=='__main__':unittest.main()
