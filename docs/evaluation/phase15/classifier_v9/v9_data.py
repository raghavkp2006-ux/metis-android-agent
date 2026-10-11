"""Assistant-authored v9 families, defined before fitting. No human approval."""
import argparse
import base64
import csv
import hashlib
import io
import json
import random
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
VERSION = 'phase15-classifier-v9'
SEED = 1509
LABELS = ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR', 'UNKNOWN')
PARTITIONS = ('train', 'calibration', 'selection', 'test')

# One template and its five lexical/entity variants form one indivisible family.
# These are development approximations of families, not independent human scenarios.
TEMPLATES = {
    'CHECK_TASKS': [
        'List my open tasks', 'What tasks are still open', 'Show my outstanding tasks',
        'Which tasks have I left unfinished', 'Retrieve the tasks I have not completed',
        'Give me an overview of my task list', 'What remains on my to do list',
        'Display my pending work items', 'Can I see the tasks awaiting completion',
        'Help me recall my remaining tasks', 'Read out my unfinished task list',
        'What work have I still got to do', 'Bring up my open task inventory',
        'I would like to view my incomplete tasks', 'Tell me which tasks remain outstanding',
        'Fetch my current task backlog', 'Show the work items I still owe myself',
        'Summarize the tasks that remain open', 'Let me inspect my pending task list',
        'May I see my unfinished work list',
    ],
    'CHECK_MEMORY': [
        'Show me notes about {slot}', 'Do you have notes about {slot}', 'Remind me of {slot}',
        'Search memory for {slot}', 'What do you know about {slot}',
        'Retrieve my records regarding {slot}', 'Look up my notes on {slot}',
        'Have I recorded anything concerning {slot}', 'Bring up what I saved on {slot}',
        'Can I read my stored details for {slot}', 'Help me recall my notes concerning {slot}',
        'Fetch my written recollections of {slot}', 'Find my personal entries concerning {slot}',
        'What information did I keep on {slot}', 'I want to see what I noted on {slot}',
        'Read back my saved observations about {slot}', 'Do my records mention {slot}',
        'Show the material I stored concerning {slot}', 'Which of my notes discuss {slot}',
        'Let me review my recorded thoughts on {slot}',
    ],
    'WHAT_DID_I_PROMISE': [
        'Show my promises to {slot}', 'List promises for {slot}', 'What did I promise {slot}',
        'What promises did I make to {slot}', 'Show commitments to {slot}',
        'What did I tell {slot} I would do', 'Retrieve the commitments I made to {slot}',
        'Remind me what I owe {slot}', 'Which commitments involve {slot}',
        'Have I agreed to do anything for {slot}', 'Bring up my promises involving {slot}',
        'What obligations did I take on for {slot}', 'Can I see what I pledged to {slot}',
        'Find the commitments I recorded for {slot}', 'Read back what I promised to {slot}',
        'I want to review my commitments toward {slot}', 'Tell me about my promises for {slot}',
        'What have I undertaken to do for {slot}', 'Fetch the promises I made involving {slot}',
        'Let me inspect my recorded obligations to {slot}',
    ],
    'CHECK_CALENDAR': [
        'Show my calendar', 'What is on my schedule', 'Read my calendar entries',
        'List my scheduled events', 'Bring up my appointments', 'What events have I scheduled',
        'Can I see my calendar agenda', 'Retrieve my planned appointments',
        'What meetings are on my calendar', 'Show the events in my diary',
        'Give me a view of my schedule', 'Which appointments have I booked',
        'Help me recall my scheduled meetings', 'Read back my calendar agenda',
        'I want to inspect my event schedule', 'What does my appointment diary contain',
        'Fetch my calendar commitments', 'Let me review my scheduled engagements',
        'Display the meetings I have scheduled', 'May I view my calendar entries',
    ],
    'UNKNOWN': [
        'Explain how {slot} works', 'Search the web for {slot}', 'Create a task about {slot}',
        'Delete notes mentioning {slot}', 'Send my notes about {slot}', 'Schedule a reminder for {slot}',
        'Do not search for {slot}', 'What should I buy for {slot}', 'Rename the record for {slot}',
        'Translate the phrase {slot}', 'Imagine I had notes about {slot}', 'Write an essay on {slot}',
        'Set an alarm for {slot}', 'Upload the records on {slot}', 'I might ask about {slot} later',
        'Show the notes about it', 'What did I promise them', 'Search memory then erase {slot}',
        'Book a meeting about {slot}', 'Tell someone about {slot}',
    ],
}
PREFIXES = ('', 'Please ', 'Can you ', 'Could you please ', 'Please can you ')
SLOTS = {
    'train': ('amber loom', 'birch mosaic', 'cedar flute', 'dahlia kiln', 'elm tapestry'),
    'calibration': ('fuchsia prism', 'garnet spindle', 'hazel easel', 'indigo trellis', 'juniper brooch'),
    'selection': ('kelp mandolin', 'lilac pendulum', 'maple astrolabe', 'nectarine bezel', 'ochre zither'),
    'test': ('pearl sundial', 'quartz marquetry', 'rosewood atelier', 'saffron dulcimer', 'tulip lapidary'),
}
PEOPLE = {
    'train': ('Aditi Vora', 'Biren Shah', 'Chitra Das', 'Dev Menon', 'Esha Suri'),
    'calibration': ('Fariha Khan', 'Girish Sen', 'Hema Rao', 'Ishan Pai', 'Jaya Kale'),
    'selection': ('Kiran Vyas', 'Leela Nair', 'Mihir Bose', 'Nila Dutt', 'Omar Paul'),
    'test': ('Pia Mehta', 'Qadir Roy', 'Rhea Sethi', 'Sana Kapur', 'Tara Lal'),
}


def canonical(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split()).rstrip('?.!')


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def exposed_texts():
    """All checked-in earlier CSV/JSON utterances are exclusions, never fitting data."""
    result = set()
    def visit(value):
        if isinstance(value, dict):
            if isinstance(value.get('utterance'), str):
                result.add(canonical(value['utterance']))
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    for path in HERE.parent.rglob('*'):
        if not path.is_file() or HERE in path.parents or '__pycache__' in path.parts:
            continue
        if path.suffix == '.csv':
            with path.open(encoding='utf-8-sig', newline='') as stream:
                for row in csv.DictReader(stream):
                    if row.get('utterance'):
                        result.add(canonical(row['utterance']))
        elif path.suffix == '.json':
            visit(json.loads(path.read_text(encoding='utf-8-sig')))
    return result


def family_partitions():
    result = {}
    randomizer = random.Random(SEED)
    for label in LABELS:
        indices = list(range(20))
        randomizer.shuffle(indices)
        for position, index in enumerate(indices):
            result[f'{label.lower()}-{index:02}'] = (
                'train' if position < 14 else 'calibration' if position < 15
                else 'selection' if position < 17 else 'test')
    return result


def authored_rows():
    rows = []
    partitions = family_partitions()
    for label, templates in TEMPLATES.items():
        for index, template in enumerate(templates):
            family = f'{label.lower()}-{index:02}'
            split = partitions[family]
            for variant, prefix in enumerate(PREFIXES):
                slot = (PEOPLE if label == 'WHAT_DID_I_PROMISE' else SLOTS)[split][variant]
                text = prefix + template.format(slot=slot) + '?'
                entities = []
                if '{slot}' in template and label in {'CHECK_MEMORY', 'WHAT_DID_I_PROMISE'}:
                    start = text.index(slot)
                    entities = [dict(type='MEMORY' if label == 'CHECK_MEMORY' else 'PERSON', rawValue=slot,
                        startOffset=len(text[:start].encode('utf-16-le')) // 2,
                        endOffset=len(text[:start + len(slot)].encode('utf-16-le')) // 2)]
                variants = [('ordinary', text, label,
                    'read' if label in LABELS[:3] else 'unsupported' if label == 'CHECK_CALENDAR' else 'clarify', {})]
                if label != 'UNKNOWN':
                    suffix = ' due tomorrow' if label == 'CHECK_TASKS' else ' from yesterday'
                    variants.extend([
                        ('filter', text[:-1] + suffix + '?', label, 'clarify', {'requested_suffix': suffix.strip()}),
                        ('negation', 'Do not ' + text, 'UNKNOWN', 'clarify', {}),
                        ('compound', text[:-1] + ' and delete the results', 'UNKNOWN', 'clarify', {}),
                    ])
                for kind, utterance, intent, disposition, filters in variants:
                    rows.append(dict(id=f'v9-{family}-{variant}-{kind}', family_id=family, split=split,
                        utterance=utterance, intent=intent, disposition=disposition, kind=kind,
                        entities=entities if kind in {'ordinary', 'filter'} else [], filters=filters,
                        query_text=slot if label in {'CHECK_MEMORY', 'WHAT_DID_I_PROMISE'} and kind == 'ordinary' else '',
                        language='en-IN', style='normal', noise_type='none', difficulty='medium' if kind == 'ordinary' else 'hard',
                        expected_outcome='execute_eligible' if disposition == 'read' else 'reject' if disposition == 'unsupported' else 'clarify',
                        input_source='TEXT', source='synthetic', provenance='assistant_authored',
                        human_authored=False, human_approved=False, timestamp='2026-10-09T05:30:00Z', zone='Asia/Kolkata'))
    return rows


def validate(rows, exposed=None):
    excluded = exposed_texts() if exposed is None else exposed
    identifiers, texts, families = set(), set(), {}
    for row in rows:
        key = canonical(row['utterance'])
        if row['id'] in identifiers or key in texts or key in excluded:
            raise ValueError('Repeated or previously exposed utterance: ' + row['id'])
        identifiers.add(row['id']); texts.add(key)
        if row['split'] not in PARTITIONS or row['intent'] not in LABELS:
            raise ValueError('Invalid label/partition')
        if row['split'] != family_partitions()[row['family_id']]:
            raise ValueError('Family partition drift')
        if (row['human_authored'] or row['human_approved'] or row['source'] != 'synthetic'
                or row['provenance'] != 'assistant_authored'):
            raise ValueError('Invalid development provenance')
        for partition in PARTITIONS:
            if partition != row['split'] and any(slot.casefold() in row['utterance'].casefold()
                    for slot in (*SLOTS[partition], *PEOPLE[partition])):
                raise ValueError('Entity fixture leakage')
        families.setdefault(row['family_id'], set()).add(row['split'])
        encoded = row['utterance'].encode('utf-16-le')
        for entity in row['entities']:
            if encoded[entity['startOffset'] * 2:entity['endOffset'] * 2].decode('utf-16-le') != entity['rawValue']:
                raise ValueError('Entity offset mismatch')
    if any(len(parts) != 1 for parts in families.values()):
        raise ValueError('Family leakage')
    for split in PARTITIONS:
        if {r['intent'] for r in rows if r['split'] == split} != set(LABELS):
            raise ValueError('Partition lacks a label')


def build():
    exposed = exposed_texts()
    rows, exclusions, seen = [], [], set()
    for row in authored_rows():
        key = canonical(row['utterance'])
        if key in exposed or key in seen:
            exclusions.append(dict(id=row['id'], reason='earlier_exposed' if key in exposed else 'duplicate_authored'))
            continue
        seen.add(key); rows.append(row)
    validate(rows, exposed)
    payload = dict(version=VERSION, provenance='Assistant-authored development only; not independent',
                   license='Assistant-authored project development data; no third-party corpus/license asserted',
                   seed=SEED, human_approved=False, families=family_partitions(), exclusions=exclusions, rows=rows)
    (HERE / 'development-data.json').write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    fields = ('id', 'intent', 'utterance', 'language', 'style', 'source', 'noise_type', 'entities',
              'difficulty', 'split', 'family_id', 'expected_outcome')
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fields, lineterminator='\n')
    writer.writeheader()
    writer.writerows({key: json.dumps(row[key], ensure_ascii=False) if key == 'entities' else row[key]
                     for key in fields} for row in rows)
    (HERE / 'development-data.csv').write_text(stream.getvalue(), encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for row in rows:
        lines.append('\t'.join((row['id'], base64.b64encode(row['utterance'].encode()).decode(),
                                row['input_source'], row['timestamp'], row['zone'])))
    (HERE / 'development-input.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(f'Authored {len(rows)} rows / {len(payload["families"])} families; quarantined {len(exclusions)} exact matches.')


def manifest():
    files = sorted(p for p in HERE.iterdir() if p.suffix in {'.py', '.json', '.csv', '.tsv', '.txt'} and 'manifest' not in p.name)
    files += [HERE.parent / 'requirements.txt', HERE.parent / 'read_coverage_v8/development-manifest-v8.json']
    payload = dict(version=VERSION, seed=SEED, human_approved=False, ml_enabled=False, ml_packaged=False,
                   hashes={p.relative_to(ROOT).as_posix(): digest(p) for p in files})
    (HERE / 'development-manifest-v9.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', action='store_true')
    manifest() if parser.parse_args().manifest else build()
