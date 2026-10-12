"""Independent new gold and exposed runtime diagnostics, explicitly separated."""
import base64
import json
from pathlib import Path
from v15_candidate import resolve

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def encoded(value):
    return base64.b64encode(value.encode()).decode()


def scope_text(scope):
    return '|'.join(str(scope[key]).lower() if key == 'negative' else str(scope[key])
                    for key in ('kind', 'subject', 'person', 'status', 'date_field', 'date', 'requested_field', 'negative'))


def build():
    inputs = []
    old = json.loads((HERE.parent / 'candidate_v14/regression.json').read_text(encoding='utf-8'))
    for row in old:
        inputs.append(dict(id=row['id'], utterance=row['utterance'], source=row['source'], section='retained',
                           old_read=row['parsed']['reads'], expected_scope=None, gold=False))
    for row in json.loads((HERE / 'development.json').read_text(encoding='utf-8'))['rows']:
        actual = resolve(row['utterance'])
        assert actual['reads'] == row['read'], (row['id'], actual)
        if row['read']:
            assert actual['scope'] == row['scope'], row['id']
            gold_entities = [{k: v for k, v in entity.items() if k != 'query'} for entity in row['entities']]
            assert actual['entities'] == gold_entities, (row['id'], actual['entities'], gold_entities)
        inputs.append(dict(row, source='TEXT', section='v15', old_read=0, expected_scope=row['scope'], gold=True))
    for row in json.loads((HERE.parent / 'human_intake_005/intake.json').read_text(encoding='utf-8'))['rows']:
        inputs.append(dict(row, source='TEXT', section='human005', old_read=0, expected_scope=None, gold=False))
    lines = ['id\ttext_base64\tsource\tintent\treads\tquery_base64\tentities_base64\tscope_base64\tsection']
    output = []
    for row in inputs:
        parsed = resolve(row['utterance'], row['source'])
        assert not row['old_read'] or parsed['reads'], 'Existing admission lost: ' + row['id']
        expected_scope = row['expected_scope']
        entity_text = ';'.join(f"{e['type']}:{e['startOffset']}:{e['endOffset']}:{encoded(e['rawValue'])}" for e in parsed['entities'])
        lines.append('\t'.join((row['id'], encoded(row['utterance']), row['source'], parsed['intent'], str(parsed['reads']),
                                encoded(parsed['query_text']), encoded(entity_text),
                                encoded(scope_text(expected_scope)) if expected_scope else '', row['section'])))
        output.append(dict(id=row['id'], utterance=row['utterance'], section=row['section'], independently_authored_gold=row['gold'],
                           old_reads=row['old_read'], parsed=parsed))
    (HERE / 'regression.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    (HERE / 'regression.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Projected {len(output)} cases; {sum(r["parsed"]["reads"] for r in output)} admissions')


if __name__ == '__main__':
    build()
