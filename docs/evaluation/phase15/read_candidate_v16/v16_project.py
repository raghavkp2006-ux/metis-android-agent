"""Retained runtime replay plus independently authored v16 subject/scope gold."""
import base64
import json
from pathlib import Path
from v16_grammar import resolve, PAT, units

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def encoded(text):
    return base64.b64encode(text.encode()).decode()


def scope_text(scope, extended=False):
    value = '|'.join(str(scope[key]).lower() if key == 'negative' else str(scope[key]) for key in
                     ('kind', 'subject', 'person', 'status', 'date_field', 'date', 'requested_field', 'negative'))
    if extended:
        value += '|' + str(scope.get('upcoming', False)).lower() + '|' + str(scope.get('meetings', False)).lower()
        value += '|' + str(scope.get('appointments', False)).lower()
    return value


def gold_entities(row):
    output = []
    for raw, kind in ((row['person'], 'PERSON'), (row['subject'], 'TASK' if row['intent'] == 'CHECK_TASKS' else
                         'MEMORY' if row['intent'] == 'CHECK_MEMORY' else 'TITLE')):
        if not raw:
            continue
        skip = PAT['ARTICLE'].match(raw).end() if kind != 'PERSON' else 0
        # Locate the complete authored subject, then calculate UTF-16 annotation bounds.
        start = row['utterance'].index(raw)
        output.append(dict(type=kind, rawValue=raw[skip:], startOffset=units(row['utterance'][:start + skip]),
                           endOffset=units(row['utterance'][:start + len(raw)])))
    return output


def build():
    old = json.loads((HERE.parent / 'candidate_v15/regression.json').read_text(encoding='utf-8'))
    inputs = [dict(id=r['id'], utterance=r['utterance'], source='TEXT', old_reads=r['parsed']['reads'], section='retained') for r in old]
    # Preserve original non-TEXT input source from the retained TSV.
    sources = {line.split('\t')[0]: line.split('\t')[2] for line in
               (HERE.parent / 'candidate_v15/regression.tsv').read_text(encoding='utf-8').splitlines()[1:]}
    for row in inputs:
        row['source'] = sources[row['id']]
    gold = []
    for name in ('development.json', 'development-supplement-001.json', 'development-supplement-002.json'):
        gold += json.loads((HERE / name).read_text(encoding='utf-8'))['rows']
    inputs += [dict(r, source='TEXT', old_reads=0, section='v16') for r in gold]
    lines = ['id\ttext_base64\tsource\tintent\treads\tquery_base64\tentities_base64\tscope_base64\tsection']
    output = []
    for row in inputs:
        parsed = resolve(row['utterance'], row['source'])
        assert not row['old_reads'] or parsed['reads'], 'Existing read lost: ' + row['id']
        if row['section'] == 'v16':
            assert bool(parsed['reads']) == row['read'], row['id']
            if row['read']:
                assert parsed['entities'] == gold_entities(row), (row['id'], parsed['entities'], gold_entities(row))
                assert parsed['query_text'] == (row['subject'] or row['person']), row['id']
                for key in ('subject', 'person', 'status', 'negative', 'requested_field', 'upcoming'):
                    assert parsed['scope'][key] == row[key], (row['id'], key)
        entity_text = ';'.join(f"{e['type']}:{e['startOffset']}:{e['endOffset']}:{encoded(e['rawValue'])}" for e in parsed['entities'])
        lines.append('\t'.join((row['id'], encoded(row['utterance']), row['source'], parsed['intent'], str(parsed['reads']),
                                encoded(parsed['query_text']), encoded(entity_text),
                                encoded(scope_text(parsed['scope'], row['section'] == 'v16')) if parsed['scope'] else '', row['section'])))
        output.append(dict(id=row['id'], utterance=row['utterance'], section=row['section'], old_reads=row['old_reads'], parsed=parsed))
    (HERE / 'regression.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    (HERE / 'regression.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    assets = []
    for row in gold:
        scope = dict(kind={'CHECK_TASKS': 'TASK', 'CHECK_MEMORY': 'MEMORY', 'WHAT_DID_I_PROMISE': 'PROMISE',
                           'CHECK_CALENDAR': 'CALENDAR'}[row['intent']], subject=row['subject'], person=row['person'],
                     status=row['status'], negative=row['negative'], requested_field=row['requested_field'],
                     date='', date_field='', upcoming=row['upcoming'],
                     appointments=row['intent'] == 'CHECK_CALENDAR' and 'appointment with' in row['utterance'].lower()) if row['read'] else None
        assets.append(dict(row, scope=scope))
    (ROOT / 'app/src/androidTest/assets/phase15-read-v16.json').write_text(json.dumps(dict(rows=assets),
        ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Projected {len(output)} cases; {sum(r["parsed"]["reads"] for r in output)} admissions')


if __name__ == '__main__':
    build()
