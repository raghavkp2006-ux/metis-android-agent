"""Offline terminal frontend for the existing local privacy Markdown choices."""
from contextlib import contextmanager
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unicodedata

from . import privacy_review as privacy
from ml.nlu_v1.data_management.records import DataError, sha_json

KEYS = {'1': 'PUBLIC', '2': 'PRIVATE', '3': 'RESTRICTED', '4': 'KEEP_UNKNOWN'}
SOURCES = {'all': None, 'massive': 'massive_1_1_en_us', 'clinc': 'clinc150_full'}
HELP = ('1 PUBLIC: human determines suitable for public privacy classification\n'
        '2 PRIVATE: human determines private/personal content should remain private\n'
        '3 RESTRICTED: human determines restricted use is required\n'
        '4 KEEP_UNKNOWN: insufficient evidence for privacy clearance\n'
        'N next; P previous; U undo; S save; Q save and quit; F source filter;\n'
        'G global row number or exact target ID; R remaining; H help.\n'
        'Only proposed choices are recorded; no classifications are applied.')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def atomic_replace(path, data):
    """Local sibling temporary + fsync + replace; never leave a partial file."""
    path = Path(path)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.privacy-terminal-', delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()


@contextmanager
def reviewer_lock(folder):
    """OS-owned lock is released even after a crash; no stale PID guessing."""
    path = Path(folder) / 'review_session.lock'
    with path.open('a+b') as stream:
        stream.seek(0, 2)
        if stream.tell() == 0:
            stream.write(b'0')
            stream.flush()
        stream.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise DataError('PRIVACY_REVIEW_ALREADY_OPEN') from None
        try:
            yield
        finally:
            stream.seek(0)
            if os.name == 'nt':
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def terminal_text(value):
    # Source text cannot issue terminal control/escape sequences.
    return ''.join(c if c in '\n\t' or not unicodedata.category(c).startswith('C')
                   else '\\u%04x' % ord(c) for c in str(value))


def packet_context(folder):
    privacy.require_local(folder)
    manifest = privacy.packet.read_json(folder / 'manifest.json')
    if manifest['version'] != privacy.VERSION or privacy.packet.file_hash(folder / 'privacy_targets.json') != manifest['privacy_targets_sha256']:
        raise DataError('PRIVACY_PACKET_CHANGED')
    context = privacy.packet.read_json(folder / 'privacy_targets.json')
    for name, expected in context['input_sha256'].items():
        path = privacy.packet.ROOT / name
        if name == 'ml/nlu_v1/dataset_v0/privacy_review.py' and privacy.packet.file_hash(path) != expected:
            path = folder / 'prepared_privacy_review.py'
        if privacy.packet.file_hash(path) != expected:
            raise DataError('PRIVACY_PACKET_INPUT_BINDINGS_STALE')
    for ident, target in context['targets'].items():
        if ident != 'privacy-row:' + target['row_id'] or target['privacy'] != 'UNKNOWN_PRIVACY' or target['source']['dataset'] not in privacy.SOURCES or target['source']['source_class'] != 'PUBLIC_DATASET':
            raise DataError('PRIVACY_TARGET_OUT_OF_SCOPE')
    return context, manifest['privacy_targets_sha256']


class Session:
    def __init__(self, folder, *, pending_only=None, source=None):
        self.folder = Path(folder)
        self.context, binding = packet_context(self.folder)
        self.targets = self.context['targets']
        self.ids = list(self.targets)
        self.document = self.folder / 'PRIVACY_DECISIONS.md'
        self.state_path = self.folder / 'review_session.json'
        self.state = {'version': 'nlu_v1_local_privacy_terminal_001', 'packet_sha256': binding,
                      'cursor': self.ids[0] if self.ids else None, 'source': 'all',
                      'pending_only': False, 'undo': [], 'pending_edit': None}
        if self.state_path.exists():
            state = privacy.packet.read_json(self.state_path)
            if set(state) != set(self.state) or state['version'] != self.state['version'] or state['packet_sha256'] != binding or state['source'] not in SOURCES or type(state['pending_only']) is not bool or (state['cursor'] is not None and state['cursor'] not in self.targets):
                raise DataError('PRIVACY_SESSION_INVALID')
            self.state = state
        self.recover()
        self.raw = self.document.read_bytes()
        self.decisions = privacy.choices(self.raw.decode('utf-8'), self.targets)
        if source is not None:
            if source not in SOURCES:
                raise DataError('INVALID_SOURCE_FILTER')
            self.state['source'] = source
        if pending_only is not None:
            self.state['pending_only'] = pending_only
        self.normalize_cursor()
        self.save()

    def write_state(self):
        atomic_replace(self.state_path, privacy.json_bytes(self.state))

    def recover(self):
        edit = self.state['pending_edit']
        if not edit:
            return
        raw = self.document.read_bytes()
        if digest(raw) == edit['before_sha256']:
            found = privacy.choices(raw.decode(), self.targets)
            if found.get(edit['target']) != edit['before']:
                raise DataError('PRIVACY_RECOVERY_CONFLICT')
            after = self.edited(raw, edit['target'], edit['after'])
            if digest(after) != edit['after_sha256']:
                raise DataError('PRIVACY_RECOVERY_BINDING_INVALID')
            atomic_replace(self.document, after)
        elif digest(raw) != edit['after_sha256']:
            raise DataError('PRIVACY_RECOVERY_EXTERNAL_EDIT_CONFLICT')
        self.state['pending_edit'] = None
        self.write_state()

    def visible(self):
        return [i for i in self.ids if (SOURCES[self.state['source']] is None or self.targets[i]['source']['dataset'] == SOURCES[self.state['source']])
                and (not self.state['pending_only'] or self.decisions[i] == 'PENDING')]

    def normalize_cursor(self):
        visible = self.visible()
        if self.state['cursor'] not in visible:
            previous = self.ids.index(self.state['cursor']) if self.state['cursor'] in self.ids else 0
            self.state['cursor'] = next((i for i in visible if self.ids.index(i) >= previous), visible[0] if visible else None)

    def save(self):
        if self.document.read_bytes() != self.raw:
            raise DataError('PRIVACY_DOCUMENT_CHANGED_IN_ANOTHER_EDITOR')
        self.write_state()

    def edited(self, raw, ident, choice):
        if ident not in self.targets or choice not in privacy.CHOICES:
            raise DataError('PRIVACY_TARGET_OR_DECISION_INVALID')
        lines = raw.decode('utf-8').splitlines(keepends=True)
        hits = 0
        for index, line in enumerate(lines):
            if line.startswith('| ' + ident + ' |'):
                parts = line.split('|')
                parts[-2] = ' ' + choice + ' '
                lines[index] = '|'.join(parts)
                hits += 1
        if hits != 1:
            raise DataError('PRIVACY_REVIEW_MEMBERSHIP_CHANGED')
        data = ''.join(lines).encode('utf-8')
        privacy.choices(data.decode(), self.targets)
        return data

    def change(self, ident, choice, *, undo=False):
        if choice not in privacy.CHOICES:
            raise DataError('PRIVACY_TARGET_OR_DECISION_INVALID')
        if self.document.read_bytes() != self.raw:
            raise DataError('PRIVACY_DOCUMENT_CHANGED_IN_ANOTHER_EDITOR')
        old = self.decisions[ident]
        if old == choice:
            self.save()
            return
        after = self.edited(self.raw, ident, choice)
        visible = self.visible()
        if not undo:
            self.state['undo'].append({'target': ident, 'before': old, 'after': choice})
        else:
            self.state['undo'].pop()
        self.decisions[ident] = choice
        if undo:
            self.state['cursor'] = ident
        elif ident in visible:
            self.state['cursor'] = visible[min(visible.index(ident) + 1, len(visible) - 1)]
        self.normalize_cursor()
        self.state['pending_edit'] = {'target': ident, 'before': old, 'after': choice,
                                      'before_sha256': digest(self.raw), 'after_sha256': digest(after)}
        # Write-ahead state makes a crash between the two replaces recoverable.
        self.write_state()
        atomic_replace(self.document, after)
        self.raw = after
        self.state['pending_edit'] = None
        self.write_state()

    def handle(self, command, *, argument=None):
        key = command.strip().upper()
        if key in KEYS:
            if self.state['cursor'] is not None:
                self.change(self.state['cursor'], KEYS[key])
        elif key in ('N', 'P'):
            visible = self.visible()
            if visible:
                at = visible.index(self.state['cursor'])
                self.state['cursor'] = visible[max(0, min(len(visible) - 1, at + (1 if key == 'N' else -1)))]
        elif key == 'U':
            if self.state['undo']:
                last = self.state['undo'][-1]
                if self.decisions.get(last['target']) != last['after']:
                    raise DataError('PRIVACY_UNDO_CONFLICT')
                self.change(last['target'], last['before'], undo=True)
        elif key == 'F':
            value = (argument or '').lower().strip()
            if value not in SOURCES:
                raise ValueError('Choose all, massive or clinc.')
            self.state['source'] = value
            self.normalize_cursor()
        elif key == 'G':
            value = (argument or '').strip()
            if value.isdigit() and 1 <= int(value) <= len(self.ids):
                value = self.ids[int(value) - 1]
            if value not in self.visible():
                raise ValueError('Target is absent or excluded by the current filters.')
            self.state['cursor'] = value
        elif key not in ('S', 'Q', 'R', 'H'):
            raise ValueError('Invalid key. H shows controls.')
        self.save()
        return key != 'Q'

    def display(self):
        ident = self.state['cursor']
        remaining = sum(c == 'PENDING' for c in self.decisions.values())
        visible = self.visible()
        if ident is None:
            return f'No matching targets. Remaining PENDING: {remaining}/{len(self.ids)}. F changes source; restart --all-targets to include decided rows.'
        target = self.targets[ident]
        src = target['source']
        return terminal_text('\n'.join([
            f"Progress: {self.ids.index(ident)+1}/{len(self.ids)}; filtered {visible.index(ident)+1}/{len(visible)}; remaining PENDING {remaining}",
            f"Filter: {self.state['source']}; PENDING only: {self.state['pending_only']}",
            f"Source: {'MASSIVE' if src['dataset'].startswith('massive') else 'CLINC150'}",
            f'Review target: {ident}', f"Source ID: {src['source_id']}",
            f"Current privacy: {target['privacy']}; proposed: {self.decisions[ident]}",
            'Full utterance: ' + target['text'],
            'Slots/entities: ' + json.dumps(target['slots'], ensure_ascii=False),
            'License: ' + target['source_proof']['license_id'],
            f"Version: {src['version']}; original split: {src['original_split']}",
            'Artifact SHA-256: ' + str(src['artifact_sha256']),
            'Row SHA-256: ' + target['row_content_sha256'],
            'Provenance: ' + json.dumps(target['provenance'], ensure_ascii=False),
            'Source evidence: ' + json.dumps(target['source_proof'], ensure_ascii=False),
            '1 PUBLIC | 2 PRIVATE | 3 RESTRICTED | 4 KEEP_UNKNOWN | H help']))


def read_key():
    if os.name == 'nt':
        import msvcrt
        key = msvcrt.getwch()
        if key in ('\x00', '\xe0'):
            msvcrt.getwch()
            return ''
        if key == '\x03':
            raise KeyboardInterrupt
        print(key)
        return key
    import sys
    if not sys.stdin.isatty():
        return input('Choice: ')
    import termios
    import tty
    previous = termios.tcgetattr(sys.stdin)
    try:
        tty.setraw(sys.stdin.fileno())
        key = sys.stdin.read(1)
    finally:
        termios.tcsetattr(sys.stdin, termios.TCSADRAIN, previous)
    if key == '\x03':
        raise KeyboardInterrupt
    print(key)
    return key


def review(folder=privacy.FOLDER, *, pending_only=None, source=None, key_input=None, prompt=input, output=print):
    privacy.require_local(folder)
    key_input = key_input or read_key
    with reviewer_lock(folder):
        session = Session(folder, pending_only=pending_only, source=source)
        output(HELP)
        while True:
            output(session.display())
            try:
                key = key_input().strip().upper()
                argument = prompt('Source (all/massive/clinc): ' if key == 'F' else 'Global row number / target ID: ') if key in ('F', 'G') else None
                if key == 'H':
                    output(HELP)
                if key == 'R':
                    output('Remaining PENDING: ' + str(sum(v == 'PENDING' for v in session.decisions.values())))
                if not session.handle(key, argument=argument):
                    return
            except (EOFError, KeyboardInterrupt):
                session.save()
                return
            except ValueError as error:
                if isinstance(error, DataError):
                    raise
                output(str(error))
