"""Fixture-only human key presses; no real row decisions are generated."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from test_machine_curation import alarm
from ml.nlu_v1.dataset_v0 import privacy_review as privacy, privacy_terminal as terminal
from ml.nlu_v1.data_management.records import DataError


class PrivacyTerminalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.local = patch.object(privacy, 'require_local')
        self.local.start()
        self.addCleanup(self.local.stop)
        self.folder = Path(self.temp.name) / 'review'
        targets = {}
        for key in ('1', '2', '3'):
            row = alarm(key=key)
            item = privacy.target(row, {'license_id': 'fixture-only'})
            targets[item['target_id']] = item
        self.context = dict(targets=dict(sorted(targets.items())), public=[], input_sha256={}, semantic_review={})
        privacy.prepare(self.context, self.folder)
        self.original = (self.folder / 'PRIVACY_DECISIONS.md').read_bytes()

    def session(self, **kwargs):
        return terminal.Session(self.folder, **kwargs)

    def decisions(self):
        return privacy.choices((self.folder / 'PRIVACY_DECISIONS.md').read_bytes().decode(), self.context['targets'])

    def test_one_key_exact_choices_and_no_automatic_classification(self):
        session = self.session()
        ident = session.state['cursor']
        session.handle('1')
        self.assertEqual(self.decisions()[ident], 'PUBLIC')
        self.assertEqual(self.context['targets'][ident]['privacy'], 'UNKNOWN_PRIVACY')
        self.assertEqual(terminal.KEYS, {'1': 'PUBLIC', '2': 'PRIVATE', '3': 'RESTRICTED', '4': 'KEEP_UNKNOWN'})
        self.assertEqual(privacy.CHOICES, {'PENDING', 'PUBLIC', 'PRIVATE', 'RESTRICTED', 'KEEP_UNKNOWN'})

    def test_resume_cursor_filters_and_undo(self):
        session = self.session(pending_only=True, source='massive')
        session.handle('2')
        session.handle('N')
        state = copy.deepcopy(session.state)
        resumed = self.session()
        self.assertEqual(state, resumed.state)
        resumed.handle('U')
        self.assertEqual(set(self.decisions().values()), {'PENDING'})

    def test_invalid_keys_and_classifications_rejected(self):
        session = self.session()
        for key in ('A', 'PUBLIC', '5', ''):
            with self.assertRaises(ValueError):
                session.handle(key)
        with self.assertRaises(DataError):
            session.change(session.ids[0], 'APPROVE')
        self.assertEqual(self.original, session.document.read_bytes())

    def test_only_proposed_cells_changed(self):
        session = self.session()
        target_hash = privacy.packet.file_hash(self.folder / 'privacy_targets.json')
        session.handle('3')
        self.assertEqual(self.original, session.document.read_bytes().replace(b'| RESTRICTED |', b'| PENDING |'))
        self.assertEqual(target_hash, privacy.packet.file_hash(self.folder / 'privacy_targets.json'))

    def test_pending_filter_displays_decided_only_when_requested(self):
        session = self.session(pending_only=True)
        ident = session.state['cursor']
        session.handle('4')
        self.assertNotIn(ident, session.visible())
        session = self.session(pending_only=False)
        session.handle('G', argument=ident)
        self.assertIn('proposed: KEEP_UNKNOWN', session.display())
        before = session.document.read_bytes()
        session.handle('N')
        session.handle('P')
        self.assertEqual(before, session.document.read_bytes())

    def test_pending_completion_does_not_choose_for_other_rows(self):
        session = self.session(pending_only=True)
        for key in ('2', '3', '4'):
            session.handle(key)
        self.assertIsNone(session.state['cursor'])
        self.assertIn('No matching targets', session.display())
        self.assertFalse(session.handle('Q'))

    def test_quit_save_and_loop_compatibility(self):
        keys = iter(('4', 'S', 'Q'))
        terminal.review(self.folder, key_input=lambda: next(keys), output=lambda _: None)
        self.assertEqual(list(self.decisions().values()).count('KEEP_UNKNOWN'), 1)
        self.assertTrue((self.folder / 'review_session.json').exists())

    def test_sync_compatibility_unsigned_only(self):
        session = self.session()
        session.handle('2')
        result = privacy.sync(self.context, self.folder)
        self.assertEqual(result['decisions']['PRIVATE'], 1)
        self.assertEqual(result['privacy_states_changed'], 0)

    def test_crash_between_state_and_document_is_recovered(self):
        session = self.session()
        ident = session.state['cursor']
        replace = terminal.atomic_replace
        def failure(path, data):
            if Path(path) == session.document:
                raise OSError('fixture interrupted write')
            replace(path, data)
        with patch.object(terminal, 'atomic_replace', side_effect=failure):
            with self.assertRaises(OSError):
                session.handle('3')
        self.assertEqual(self.original, session.document.read_bytes())
        resumed = self.session()
        self.assertEqual(self.decisions()[ident], 'RESTRICTED')
        self.assertIsNone(resumed.state['pending_edit'])
        resumed.handle('U')
        self.assertEqual(self.original, resumed.document.read_bytes())

    def test_external_edit_is_never_overwritten(self):
        session = self.session()
        changed = self.original.replace(b'| PENDING |', b'| PRIVATE |', 1)
        session.document.write_bytes(changed)
        with self.assertRaises(DataError):
            session.handle('1')
        self.assertEqual(changed, session.document.read_bytes())

    def test_source_filter_navigation_remaining_and_help(self):
        session = self.session()
        session.handle('G', argument='3')
        self.assertEqual(session.state['cursor'], session.ids[2])
        session.handle('F', argument='clinc')
        self.assertEqual(session.visible(), [])
        session.handle('F', argument='all')
        for key in ('R', 'H', 'S'):
            self.assertTrue(session.handle(key))
        with self.assertRaises(ValueError):
            session.handle('G', argument='9999')
        with self.assertRaises(ValueError):
            session.handle('F', argument='synthetic')

    def test_raw_content_not_in_session_metadata_and_control_codes_escaped(self):
        session = self.session()
        state = (self.folder / 'review_session.json').read_text()
        for target in self.context['targets'].values():
            self.assertNotIn(target['text'], state)
        self.assertNotIn('\x1b', terminal.terminal_text('hello\x1b[31m'))

    def test_original_engine_snapshot_required_and_other_inputs_still_strict(self):
        data = privacy.packet.read_json(self.folder / 'privacy_targets.json')
        data['input_sha256']['ml/nlu_v1/dataset_v0/privacy_review.py'] = '0' * 64
        (self.folder / 'privacy_targets.json').write_bytes(privacy.json_bytes(data))
        (self.folder / 'manifest.json').write_bytes(privacy.json_bytes({'version': privacy.VERSION, 'privacy_targets_sha256': privacy.packet.file_hash(self.folder / 'privacy_targets.json')}))
        with self.assertRaises((DataError, OSError)):
            self.session()

    def test_local_path_check_precedes_any_write(self):
        self.local.stop()
        with self.assertRaises(DataError):
            terminal.review(privacy.packet.ROOT / 'docs', key_input=lambda: 'Q')

    def test_os_lock_prevents_second_reviewer_and_sync(self):
        with terminal.reviewer_lock(self.folder):
            with self.assertRaises(DataError):
                with terminal.reviewer_lock(self.folder):
                    pass
            with self.assertRaises(DataError):
                privacy.sync(self.context, self.folder)

    def test_crash_after_document_write_before_state_clear(self):
        session = self.session()
        ident = session.state['cursor']
        original_write = session.write_state
        def fail_completion():
            if session.state['pending_edit'] is None:
                raise OSError('fixture completion interrupted')
            original_write()
        with patch.object(session, 'write_state', side_effect=fail_completion):
            with self.assertRaises(OSError):
                session.handle('4')
        with self.assertRaises(DataError):
            privacy.sync(self.context, self.folder)
        resumed = self.session()
        self.assertEqual(self.decisions()[ident], 'KEEP_UNKNOWN')
        self.assertEqual(len(resumed.state['undo']), 1)

    def test_existing_decisions_preserved_on_first_open(self):
        path = self.folder / 'PRIVACY_DECISIONS.md'
        changed = path.read_bytes().replace(b'| PENDING |', b'| PRIVATE |', 1)
        path.write_bytes(changed)
        session = self.session()
        session.handle('Q')
        self.assertEqual(changed, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
