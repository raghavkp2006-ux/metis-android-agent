import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from ml.data.common import entities, issues, sha, write_csv
from ml.data.import_sheet import audit
from ml.data.similarity import cross_collisions, skeleton
from ml.data.split_tool import check, split
from ml.eval.test_harness import gold
from ml.data.build_public_pool import label


class DataToolTests(unittest.TestCase):
    def test_verbatim_and_labels(self):
        self.assertIn('entity value not verbatim in text: task=Read Book', issues(gold(ent='task=Read Book')))
        self.assertIn('intent absent from code: RENAME_TASK', issues(gold(intent='RENAME_TASK')))
        self.assertTrue(issues(gold('UNKNOWN')))
        with self.assertRaises(ValueError):
            entities('task=x;task=y')

    def test_postpone_target(self):
        self.assertIn('postpone target must use to_time', issues(gold('POSTPONE_TASK', 'tomorrow', 'time=tomorrow')))

    def test_author_split(self):
        with self.assertRaises(ValueError):
            split([gold()], {'a'})
        a, b = split([gold(author='a'), gold(author='b')], {'b'})
        self.assertEqual([r['author'] for r in a], ['a'])
        self.assertEqual([r['author'] for r in b], ['b'])

    def test_exact_normalized_skeleton(self):
        a, b = gold(), gold(text='ADD A TASK TO READ BOOK', ent='task=READ BOOK')
        self.assertIn('normalized', cross_collisions([a], [b], semantic=False)[0]['reasons'])
        b = gold(text='add a task to write notes', ent='task=write notes')
        self.assertEqual(skeleton(a), skeleton(b))
        self.assertTrue(cross_collisions([a], [b], semantic=False))

    def test_appended_evaluation_line_fails_ci(self):
        # Temporary synthetic contract fixtures only. No actual independent test is created.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            train, evaluation, manifest = (root / n for n in ('train.csv', 'synthetic.csv', 'manifest.json'))
            held = gold(text='show tasks', intent='CHECK_TASKS', ent='', author='b')
            write_csv(evaluation, [held])
            manifest.write_text(json.dumps(dict(test_sha256=sha(evaluation))))
            # Different author proves failure from text collision rather than author overlap.
            leaked = dict(held, author='a')
            write_csv(train, [gold(), leaked])
            with self.assertRaisesRegex(ValueError, 'train/test leakage'):
                check(train, evaluation, manifest)
            write_csv(evaluation, [held, held])
            with self.assertRaisesRegex(ValueError, 'test hash changed'):
                check(train, evaluation, manifest)

    def test_semantic_threshold(self):
        a = gold()
        b = gold('CHECK_TASKS', 'which work is outstanding', '')
        with patch('ml.data.similarity.embed', return_value=[[1.,0.],[1.,0.]]):
            self.assertEqual(cross_collisions([a], [b])[0]['reasons'], ['semantic'])
        with patch('ml.data.similarity.embed', side_effect=FileNotFoundError('not found')):
            with self.assertRaises(FileNotFoundError):
                cross_collisions([a], [b])

    def test_public_decisions(self):
        self.assertEqual(label(dict(decision='OOD')), 'UNKNOWN')
        self.assertEqual(label(dict(decision='KNOWN_UNSUPPORTED')), 'UNSUPPORTED')
        self.assertIsNone(label(dict(decision='DROP')))
        self.assertEqual(label(dict(decision='MAP', domain='PERSON', action='CREATE')), 'UNSUPPORTED')


if __name__ == '__main__':
    unittest.main()
