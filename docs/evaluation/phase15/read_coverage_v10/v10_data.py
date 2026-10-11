"""Assistant-authored v10 development families and immutable exposed projections."""
import argparse
import base64
import csv
import hashlib
import io
import json
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
VERSION = 'phase15-read-coverage-v10'

# Defined before the v10 grammar. These are development examples, never human approval.
# Reference and literal-query annotations are intentionally independent of the parser.
FAMILIES = {
    'saved-recall': [
        ('I saved something about the alabaster lamp. can you pull it up', 'alabaster lamp', 'the alabaster lamp'),
        ('I noted down something about my obsidian chessboard. can i see it', 'obsidian chessboard', 'my obsidian chessboard'),
        ('I saved something about the botanical conservatory hours', 'botanical conservatory hours', 'the botanical conservatory hours'),
    ],
    'remembered-content': [
        ('I wrote something about not swimming after 6pm. why did i write that', 'not swimming after 6pm', 'not swimming after 6pm'),
        ('What did I note about the weather in Udaipur in September?', 'weather in Udaipur in September', 'the weather in Udaipur in September'),
    ],
    'attributed-content': [
        ('Did I write down what my cousin said about his pottery?', 'my cousin said about his pottery', 'what my cousin said about his pottery'),
        ('Did I write down what the dentist said about the crown fitting?', 'crown fitting', 'what the dentist said about the crown fitting'),
    ],
    'descriptive-identity': [
        ('Who was the woman at the exhibition who worked at that textile studio?',
         'exhibition who worked at that textile studio', 'woman at the exhibition who worked at that textile studio'),
        ('What is the name of the street where my cousin lives?', 'my cousin', 'name of the street where my cousin lives'),
    ],
    'requested-field': [
        ('The number for the electrician. do you have it?', 'electrician', 'number for the electrician'),
        ('The name of the restaurant we liked in Mysuru', 'restaurant we liked in Mysuru', 'restaurant we liked in Mysuru'),
        ("The name of the teacher at my cousin's school", "teacher at my cousin's school", "teacher at my cousin's school"),
        ('The password for the copper archive', 'password for the copper archive', 'password for the copper archive'),
        ('The name of the hotel in Ooty where we stayed', 'hotel in Ooty', 'hotel in Ooty where we stayed'),
    ],
    'full-query-unicode': [
        ('Would you please show me notes about The Kinks?', 'Kinks', 'The Kinks'),
        ("Would you please retrieve notes regarding my café 🚀 bowl's glaze?", "café 🚀 bowl's glaze", "my café 🚀 bowl's glaze"),
    ],
}

# Literal subjects for the approved exposed reads. This is a development query
# projection, not a revision of any frozen entity/disposition/filter annotation.
EXPOSED_QUERIES = {
    '0009': 'the car service', '0027': 'the wifi password for the cafe',
    '0028': 'the recipe for kadhi',
    '0029': 'guy at the conference who worked at that AI startup',
    '0030': 'the house i looked at on fridays road',
    '0032': 'what my friend said about her divorce',
    '0033': 'name of the street where my aunty lives',
    '0035': 'not eating after 9pm', '0036': "the dog's vet",
    '0037': 'number for the mechanic', '0038': 'the exam syllabus',
    '0039': 'the argument with rohan', '0040': 'restaurant we liked in bangalore',
    '0042': 'the weather in shillong in october', '0043': "teacher at my niece's school",
    '0044': 'what the doctor said about the scan results', '0045': 'the concert setlist',
    '0046': 'password for the cloud drive', '0047': 'why i quit the running club',
    '0048': 'the land registry office hours', '0049': 'hotel in manali where we stayed',
    '0050': 'the warranty on the washing machine', '0134': '',
}


def canonical(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split()).rstrip('?.!')


def digest(path, normalized=True):
    value = path.read_bytes()
    return hashlib.sha256(value.replace(b'\r\n', b'\n') if normalized else value).hexdigest()


def exposed_texts():
    result = set()
    def visit(value):
        if isinstance(value, dict):
            if isinstance(value.get('utterance'), str): result.add(canonical(value['utterance']))
            for item in value.values(): visit(item)
        elif isinstance(value, list):
            for item in value: visit(item)
    for path in HERE.parent.rglob('*'):
        if not path.is_file() or HERE in path.parents or '__pycache__' in path.parts: continue
        if path.suffix == '.json': visit(json.loads(path.read_text(encoding='utf-8-sig')))
        elif path.suffix == '.csv':
            with path.open(encoding='utf-8-sig', newline='') as stream:
                for row in csv.DictReader(stream):
                    if row.get('utterance'): result.add(canonical(row['utterance']))
    return result


def row(identifier, family, text, intent, disposition, reference='', query='', section='development', filters=None):
    entities = []
    if reference:
        start = text.index(reference)
        entities = [dict(type='PERSON' if intent == 'WHAT_DID_I_PROMISE' else 'MEMORY', rawValue=reference,
            startOffset=len(text[:start].encode('utf-16-le')) // 2,
            endOffset=len(text[:start + len(reference)].encode('utf-16-le')) // 2)]
    return dict(id=identifier, family_id=family, utterance=text, intent=intent, disposition=disposition,
        entities=entities, query_text=query, filters=filters or {}, section=section, source='synthetic',
        provenance='assistant_authored', human_authored=False, human_approved=False, split='development',
        input_source='TEXT', timestamp='2026-10-09T05:30:00Z', zone='Asia/Kolkata')


def authored_rows():
    rows = []
    for family, items in FAMILIES.items():
        for index, (text, reference, query) in enumerate(items):
            rows.append(row(f'v10-{family}-{index}-ordinary', family, text, 'CHECK_MEMORY', 'read', reference, query))
            variants = [('negation', 'Do not ' + text), ('filter', text.rstrip('?.!') + ' from yesterday'),
                        ('compound', text.rstrip('?.!') + ' and delete the results'),
                        ('second', text.rstrip('?.!') + '. Show my tasks'),
                        ('control', text + '\u200b'),
                        ('unresolved', text.replace(reference, 'it')),
                        ('malformed', text.rstrip('?.!') + ':')]
            for kind, unsafe in variants:
                rows.append(row(f'v10-{family}-{index}-{kind}', family, unsafe, 'UNKNOWN', 'clarify'))
    # New prefixes/slots cover each exposed template family without fitting or
    # relabelling v9. Exact earlier matches are quarantined, not called new data.
    v9 = json.loads((HERE.parent / 'classifier_v9/development-data.json').read_text(encoding='utf-8'))['rows']
    seen = set()
    for item in v9:
        family = item['family_id']
        if item['kind'] != 'ordinary' or item['intent'] == 'UNKNOWN' or family in seen: continue
        seen.add(family)
        text = 'Would you please ' + item['utterance'].rstrip('?.!')
        reference = item['entities'][0]['rawValue'] if item['entities'] else ''
        query = item['query_text']
        if reference:
            replacement = 'Ari D’Souza' if item['intent'] == 'WHAT_DID_I_PROMISE' else 'platinum origami'
            text = text.replace(reference, replacement)
            query = query.replace(reference, replacement)
            reference = replacement
        disposition = 'unsupported' if item['intent'] == 'CHECK_CALENDAR' else 'read'
        rows.append(row(f'v10-wording-{family}', 'wording-' + family, text, item['intent'], disposition, reference, query))
        for kind, unsafe in [('negation', 'Do not ' + text), ('compound', text + ' then send the results'),
                             ('filter', text + ' due tomorrow'),
                             ('unresolved', text.replace(reference, 'him' if item['intent'] == 'WHAT_DID_I_PROMISE' else 'it')
                              if reference else text + ' about it'), ('malformed', text + ':')]:
            rows.append(row(f'v10-wording-{family}-{kind}', 'wording-' + family, unsafe, 'UNKNOWN', 'clarify'))
    # A descriptive attribution has a short annotation but a much longer complete
    # subject. Author the limit family before adding its grammar production.
    for length in (255, 256, 257):
        prefix, suffix = 'what the doctor at the ', ' said about the crown fitting'
        query = prefix + 'a' * (length - len(prefix + suffix)) + suffix
        text = 'Did I write down ' + query + '?'
        positive = length <= 256
        rows.append(row(f'v10-query-limit-{length}', 'query-length', text, 'CHECK_MEMORY' if positive else 'UNKNOWN',
            'read' if positive else 'clarify', 'crown fitting' if positive else '', query if positive else ''))
    # Pronouns and scheduling never acquire recall authority through a new form.
    for index, text in enumerate(('I saved something about it. can you pull it up',
        'Did I write down what she said about it', 'The number for someone. do you have it',
        'Remind me to review platinum origami', 'The name of the hotel where we stayed',
        'What did I note about the trip to Udaipur last year',
        'What did I note about the weather in Udaipur in September from yesterday',
        'I wrote something about not swimming after 6pm. remind me tomorrow',
        'Who was he at that place', 'The name of the street where she lives')):
        rows.append(row(f'v10-boundary-{index}', 'unresolved-boundary', text, 'UNKNOWN', 'clarify'))
    for index, text in enumerate(('Show me notes about he', 'Show me notes about she',
        'What did I save about they', 'The number for somebody. do you have it')):
        rows.append(row(f'v10-pronoun-{index}', 'unresolved-pronouns', text, 'UNKNOWN', 'clarify'))
    return rows


def all_rows(development):
    rows = development.copy()
    frozen = json.loads((HERE.parent / 'human_review_003/first_evaluation/frozen-benchmark.json').read_text(encoding='utf-8'))
    for item in frozen['rows']:
        annotation = frozen['annotations'][item['id']]
        rows.append(dict(item, **annotation, query_text=EXPOSED_QUERIES.get(item['id'].rsplit('-', 1)[1], ''), section='exposed'))
    for directory, section in (('classifier_v9', 'v9'), ('read_coverage_v8', 'v8'), ('read_coverage_v7', 'v7')):
        data = json.loads((HERE.parent / directory / ('development-data.json' if section == 'v9' else 'development-cases.json')).read_text(encoding='utf-8'))
        for item in data['rows'] if section == 'v9' else data:
            query = item.get('query_text', item['entities'][0]['rawValue'] if item['entities'] else '')
            rows.append(dict(item, section=section, query_text=query if item['disposition'] == 'read' else '', filters=item.get('filters', {})))
    return rows


def build():
    earlier, seen, development, excluded = exposed_texts(), set(), [], []
    for item in authored_rows():
        key = canonical(item['utterance'])
        if key in earlier or key in seen:
            excluded.append(item['id']); continue
        seen.add(key); development.append(item)
    payload = dict(version=VERSION, human_approved=False, provenance='Assistant-authored development; no independence',
                   excluded_exact_matches=excluded, rows=development)
    (HERE / 'development-cases.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    rows = all_rows(development)
    (HERE / 'regression-expectations.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for item in rows:
        lines.append('\t'.join((item['id'], base64.b64encode(item['utterance'].encode()).decode(), item['input_source'], item['timestamp'], item['zone'])))
    (HERE / 'regression-input.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    stream = io.StringIO(newline='')
    writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
    writer.writerow(('id', 'section', 'intent', 'disposition', 'entities_base64', 'query_base64', 'native_entities'))
    for item in rows:
        writer.writerow((item['id'], item['section'], item['intent'], item['disposition'],
            base64.b64encode(json.dumps(item['entities'], ensure_ascii=False).encode()).decode(),
            base64.b64encode(item['query_text'].encode()).decode(),
            ';'.join(':'.join((e['type'], str(e['startOffset']), str(e['endOffset']),
                base64.b64encode(e['rawValue'].encode()).decode(), '')) for e in item['entities'])))
    (HERE / 'regression-expectations.tsv').write_text(stream.getvalue(), encoding='utf-8', newline='\n')
    print(f'Defined {len(development)} development cases before grammar; {len(rows)} aggregate regression rows.')


def manifest():
    files = [p for p in HERE.iterdir() if p.suffix in {'.py', '.json', '.tsv', '.md'} and 'manifest' not in p.name]
    files += list((ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt'))
    files += [ROOT / p for p in ('app/src/main/java/dev/metis/agent/domain/storage/MemorySearch.kt',
        'app/src/test/java/dev/metis/agent/ReadCoverageV10Test.kt',
        'app/src/test/java/dev/metis/agent/ReadSafetyV6Test.kt',
        'app/src/test/java/dev/metis/agent/MemorySearchQueryTest.kt',
        'app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt',
        'app/src/androidTest/java/dev/metis/agent/LanguageReadTest.kt', '.github/workflows/android.yml', '.gitattributes')]
    retained = {}
    for directory in ('read_safety_v6', 'read_coverage_v7', 'read_coverage_v8', 'classifier_v9', 'human_review_003'):
        for path in (HERE.parent / directory).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts:
                retained[path.relative_to(ROOT).as_posix()] = digest(path, False)
    payload = dict(version=VERSION, human_approved=False, ml_enabled=False, ml_packaged=False,
        hashes={p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(files)}, retained_hashes=retained)
    (HERE / 'development-manifest-v10.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', action='store_true')
    manifest() if parser.parse_args().manifest else build()
