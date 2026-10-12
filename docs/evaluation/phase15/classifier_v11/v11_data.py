"""New assistant-authored families, specified before training; earlier data is excluded."""
import base64
import hashlib
import json
import random
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
VERSION = 'phase15-classifier-v11'
LABELS = ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR', 'UNKNOWN')
PARTITIONS = ('train', 'calibration', 'selection', 'test')
PRIOR_DIRECTORIES = {'read_queries_v3','read_queries_v4','independent_125','read_queries_v5',
    'read_safety_v6','read_coverage_v7','read_coverage_v8','classifier_v9','read_coverage_v10','human_review_003','gates_v5'}
PREFIXES = ('', 'Please ', 'Could you please ', 'Would you please ', 'Can you ')
SLOTS = {
    'train': ('copper terrarium', 'linen carousel', 'walnut lantern', 'ceramic compass', 'violet mandolin'),
    'calibration': ('silver herbarium', 'cotton windmill', 'willow diorama', 'porcelain sundial', 'azure accordion'),
    'selection': ('bronze vivarium', 'velvet turbine', 'poplar cabinet', 'glass planisphere', 'scarlet harpsichord'),
    'test': ('titanium aquarium', 'silk pinwheel', 'mahogany pavilion', 'stone chronometer', 'sépia harmonium'),
}
PEOPLE = {
    'train': ('Avni Raut', 'Bimal Deol', 'Charu Kapoor', 'Dinesh Vaid', 'Elena Pinto'),
    'calibration': ('Farida Malik', 'Gaurav Hegde', 'Hiral Desai', 'Imran Sood', 'Jyoti Batra'),
    'selection': ('Kabir Grover', 'Lata Shetty', 'Manav Kohli', 'Naina Bhatt', 'Omkar Patil'),
    'test': ('Prisha Jain', 'Qasim Mir', 'Ritvik Bajaj', 'Saira D’Souza', 'Zoë Dutta'),
}

# Each complete stem plus all slots/politeness/unsafe variants is an indivisible
# family. Shared vocabulary and related stems remain a synthetic-data limitation.
TEMPLATES = {
    'CHECK_TASKS': (
        'List the tasks I still need to finish', 'Show me my unfinished tasks',
        'What is left on my task list', 'Which of my tasks are incomplete',
        'Give me my outstanding task list', 'Read my remaining tasks to me',
        'Bring up the tasks still on my list', 'Find my unfinished work items',
        'Retrieve my uncompleted tasks', 'Display my open work items',
        'What tasks have I yet to complete', 'Show me the work I have left to do',
        'Can I review my pending tasks', 'Let me see my open tasks',
        'I need to check my task backlog', 'Help me remember my unfinished work',
        'Tell me what tasks I have pending', 'Which work items remain incomplete',
        'Read back the tasks still awaiting completion', 'What is in my unfinished task queue',
        'Fetch my outstanding work items', 'I would like my remaining task list',
        'What tasks are waiting for me to finish them', 'Show the tasks not completed',
        'Give an overview of my unfinished work items', 'What have I not finished on my task list',
        'List work items that are still open', 'Which entries on my task list remain open',
        'Pull up my incomplete work list', 'I want to inspect my open task list',
    ),
    'CHECK_MEMORY': (
        'Find my notes regarding {slot}', 'Show my saved notes about {slot}',
        'What have I written down about {slot}', 'Do I have any notes on {slot}',
        'Read my stored notes concerning {slot}', 'Help me remember what I noted about {slot}',
        'Pull up my personal records about {slot}', 'Search my saved memories for {slot}',
        'Retrieve what I wrote about {slot}', 'What details did I record about {slot}',
        'Bring back my notes relating to {slot}', 'Let me read my recollections about {slot}',
        'Show me what I had saved about {slot}', 'Have I made any notes about {slot}',
        'Give me the information I stored about {slot}', 'Look through my notes for {slot}',
        'I need to recall my saved details about {slot}', 'What did my notes say about {slot}',
        'Fetch the entries I kept concerning {slot}', 'Can I review my notes about {slot}',
        'Read back my records on {slot}', 'Remind me of my notes about {slot}',
        'Which saved entries refer to {slot}', 'I want to look up what I saved about {slot}',
        'Check whether I noted anything on {slot}', 'Find the information I recorded on {slot}',
        'Display my written notes on {slot}', 'What observations have I saved about {slot}',
        'Help me find my recorded thoughts about {slot}', 'Show me notes concerning {slot}',
    ),
    'WHAT_DID_I_PROMISE': (
        'What have I promised to {slot}', 'Show the promises I gave {slot}',
        'List my commitments involving {slot}', 'What have I agreed to do for {slot}',
        'Remind me of the promises I made to {slot}', 'Read my recorded commitments to {slot}',
        'What did I pledge to do for {slot}', 'Find my outstanding promises to {slot}',
        'Show what I said I would do for {slot}', 'Which obligations do I have to {slot}',
        'Bring up the commitments I gave {slot}', 'What did I undertake for {slot}',
        'Can I review my promises to {slot}', 'Help me recall what I promised {slot}',
        'Retrieve what I agreed with {slot} to do', 'Tell me which commitments I made to {slot}',
        'What have I told {slot} I will do', 'I want to see the promises involving {slot}',
        'Give me my recorded obligations to {slot}', 'Read back my pledges to {slot}',
        'What did I commit to doing for {slot}', 'Do I have any promises to {slot}',
        'Which promises have I recorded for {slot}', 'Fetch the commitments involving {slot}',
        'Let me inspect what I promised {slot}', 'What commitments did I take on for {slot}',
        'Show me my obligations involving {slot}', 'List the things I promised {slot}',
        'I need to recall my commitments toward {slot}', 'Pull up my promises to {slot}',
    ),
    'CHECK_CALENDAR': (
        'Read the appointments on my calendar', 'Show me my scheduled engagements',
        'What does my calendar have on it', 'List the meetings in my schedule',
        'Bring up my booked appointments', 'Which events are in my calendar',
        'Give me my calendar agenda', 'Help me recall my appointments',
        'What have I put on my calendar', 'Display my scheduled meetings',
        'Read back my appointment list', 'Show my diary appointments',
        'Retrieve the events I scheduled', 'Can I review my event calendar',
        'What appointments are in my diary', 'Let me inspect my schedule',
        'List my calendar engagements', 'What meetings have I booked',
        'Fetch the appointments on my schedule', 'I want to check my calendar entries',
        'Tell me what events I have scheduled', 'Which meetings are in my diary',
        'Read my booked engagements', 'Show me my appointment agenda',
        'What is in my event schedule', 'Give an overview of my calendar entries',
        'Pull up the events on my calendar', 'Let me see my booked meetings',
        'I need to recall my scheduled events', 'What does my schedule contain',
    ),
    'UNKNOWN': (
        'Explain the meaning of {slot}', 'Find a website about {slot}',
        'Add a task to buy {slot}', 'Erase everything I saved about {slot}',
        'Email my notes about {slot}', 'Remind me to purchase {slot}',
        'Never retrieve my notes about {slot}', 'Recommend a shop selling {slot}',
        'Change the title of my note on {slot}', 'Translate my description of {slot}',
        'Pretend to show notes about {slot}', 'Write a poem about {slot}',
        'Set a countdown for {slot}', 'Publish my saved details about {slot}',
        'Perhaps I will search for {slot}', 'Show my notes about that thing',
        'What did I promise someone', 'Retrieve and then destroy my notes on {slot}',
        'Arrange an appointment about {slot}', 'Call a friend about {slot}',
        'List tasks and email my notes about {slot}', 'Show my calendar and delete a meeting',
        'If I wanted notes about {slot}, how would I ask', 'I do not want my commitments listed',
        'Tell me which notes I should create about {slot}', 'Buy a {slot} for me',
        'Can you remember this new fact about {slot}', 'Save a new promise about {slot}',
        'Would you cancel my appointments', 'Search the internet instead of my notes for {slot}',
    ),
}


def canonical(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split()).rstrip('?.!')


def digest(path, raw=False):
    value = path.read_bytes()
    return hashlib.sha256(value if raw else value.replace(b'\r\n', b'\n')).hexdigest()


def earlier_texts():
    # Use a fixed inventory of earlier directories, never later experiment data.
    texts = set()
    def visit(value):
        if isinstance(value, dict):
            if isinstance(value.get('utterance'), str): texts.add(canonical(value['utterance']))
            for item in value.values(): visit(item)
        elif isinstance(value, list):
            for item in value: visit(item)
    for path in HERE.parent.rglob('*.json'):
        if '__pycache__' in path.parts or (path.parent!=HERE.parent and path.relative_to(HERE.parent).parts[0] not in PRIOR_DIRECTORIES): continue
        visit(json.loads(path.read_text(encoding='utf-8-sig')))
    import csv
    for path in HERE.parent.rglob('*.csv'):
        if path.parent!=HERE.parent and path.relative_to(HERE.parent).parts[0] not in PRIOR_DIRECTORIES: continue
        with path.open(encoding='utf-8-sig', newline='') as stream:
            for row in csv.DictReader(stream):
                if row.get('utterance'): texts.add(canonical(row['utterance']))
    return texts


def family_partitions():
    generator, result = random.Random(1511), {}
    for label in LABELS:
        order = list(range(30)); generator.shuffle(order)
        for position, index in enumerate(order):
            result[f'{label.lower()}-{index:02}'] = ('train' if position < 18 else
                'calibration' if position < 22 else 'selection' if position < 26 else 'test')
    return result


def authored_rows():
    families = family_partitions()
    for label, templates in TEMPLATES.items():
        for index, template in enumerate(templates):
            family = f'{label.lower()}-{index:02}'
            split = families[family]
            for variant, prefix in enumerate(PREFIXES):
                slot = (PEOPLE if label == 'WHAT_DID_I_PROMISE' else SLOTS)[split][variant]
                body = prefix + template.format(slot=slot)
                variants = [('ordinary', body + '?', label, {})]
                if label != 'UNKNOWN':
                    variants += [
                        ('date_filter', body + ' from last week?', label, {'retrieval_date': 'last week'}),
                        ('count_filter', 'Only the first three results: ' + body + '?', label, {'limit': 'first three'}),
                        ('negation', ("Don't " if variant % 2 else 'Do not ') + body + '?', 'UNKNOWN', {}),
                        ('compound', body + (' and email the results' if variant % 2 else ' then remove them'), 'UNKNOWN', {}),
                        ('action', 'Delete the records after you ' + body, 'UNKNOWN', {}),
                    ]
                for kind, text, intent, constraints in variants:
                    disposition = ('read' if kind == 'ordinary' and label in LABELS[:3] else
                        'unsupported' if kind == 'ordinary' and label == 'CHECK_CALENDAR' else 'clarify')
                    entities = []
                    if kind == 'ordinary' and label in {'CHECK_MEMORY', 'WHAT_DID_I_PROMISE'}:
                        start = text.index(slot)
                        entities = [dict(type='MEMORY' if label == 'CHECK_MEMORY' else 'PERSON', rawValue=slot,
                            startOffset=len(text[:start].encode('utf-16-le'))//2,
                            endOffset=len(text[:start+len(slot)].encode('utf-16-le'))//2)]
                    yield dict(id=f'v11-{family}-{variant}-{kind}', family_id=family, split=split,
                        utterance=text, intent=intent, kind=kind, disposition=disposition, entities=entities,
                        query_text=slot if entities else '', filters=constraints, input_source='TEXT',
                        timestamp='2026-10-12T05:30:00Z', zone='Asia/Kolkata', source='synthetic',
                        provenance='assistant_authored', human_authored=False, human_approved=False)


def validate(rows, earlier=None):
    earlier = earlier_texts() if earlier is None else earlier
    seen_ids, seen_text, families = set(), set(), family_partitions()
    for row in rows:
        text = canonical(row['utterance'])
        if row['id'] in seen_ids or text in seen_text or text in earlier: raise ValueError('Duplicate/exposed row')
        seen_ids.add(row['id']); seen_text.add(text)
        if row['intent'] not in LABELS or row['split'] not in PARTITIONS: raise ValueError('Invalid label/split')
        if families.get(row['family_id']) != row['split']: raise ValueError('Family split drift')
        if row['source'] != 'synthetic' or row['provenance'] != 'assistant_authored' or row['human_authored'] or row['human_approved']:
            raise ValueError('Unauthorized provenance')
        for other in PARTITIONS:
            if other != row['split'] and any(s.casefold() in row['utterance'].casefold() for s in (*SLOTS[other], *PEOPLE[other])):
                raise ValueError('Slot partition leakage')
        for entity in row['entities']:
            start, end = entity['startOffset'], entity['endOffset']
            if not 0 <= start < end or row['utterance'].encode('utf-16-le')[start*2:end*2].decode('utf-16-le') != entity['rawValue']:
                raise ValueError('Invalid UTF-16 span')
    for split in PARTITIONS:
        if {r['intent'] for r in rows if r['split'] == split} != set(LABELS): raise ValueError('Missing partition label')


def build():
    earlier, seen, rows, exclusions = earlier_texts(), set(), [], []
    for row in authored_rows():
        key = canonical(row['utterance'])
        if key in earlier or key in seen:
            exclusions.append(row['id']); continue
        seen.add(key); rows.append(row)
    validate(rows, earlier)
    data = dict(version=VERSION, human_approved=False, provenance='Assistant-authored development only',
        protocol_sha256=digest(HERE / 'protocol.json'), families=family_partitions(), exclusions=exclusions, rows=rows)
    (HERE / 'development-data.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for row in rows:
        lines.append('\t'.join((row['id'], base64.b64encode(row['utterance'].encode()).decode(), row['input_source'], row['timestamp'], row['zone'])))
    (HERE / 'development-input.tsv').write_text('\n'.join(lines)+'\n', encoding='utf-8', newline='\n')
    expectations = ['id\tintent\tdisposition']
    expectations += ['\t'.join((row['id'], row['intent'], row['disposition'])) for row in rows]
    (HERE / 'development-expectations.tsv').write_text('\n'.join(expectations)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(rows=len(rows), exclusions=len(exclusions), families=len(data['families']),
        partitions={s:sum(r['split']==s for r in rows) for s in PARTITIONS})))


if __name__ == '__main__':
    build()
