"""Join frozen reviewed development outcomes to verified native regression output.

This is exposed deterministic baseline evidence, never independent evaluation or ML F1.
"""
import base64
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from integrity import check
from freeze_review import check as check_freeze
from v15_evaluate import native_report, OUTPUT

HERE = Path(__file__).resolve().parent


def report():
    check()
    check_freeze()
    native_report()  # Reject stale source/input bindings and unexpected native results first.
    data = json.loads((HERE / 'review-projection.json').read_text(encoding='utf-8'))
    regression = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
    native = {r['id']: r for r in csv.DictReader(
        (line for line in (OUTPUT / 'native.tsv').read_text(encoding='utf-8').splitlines() if not line.startswith('#')), delimiter='\t')}
    by_text = defaultdict(list)
    for row in regression:
        by_text[row['utterance']].append(row)
    details = []
    for gold in data['rows']:
        matches = by_text[gold['utterance']]
        assert matches, 'Missing native request: ' + gold['id']
        assert len({(native[row['id']]['reads'], native[row['id']]['contexts']) for row in matches}) == 1
        observed = native[matches[0]['id']]
        permitted = gold['expected_disposition_draft'] == 'read'
        reads, contexts = int(observed['reads']), int(observed['contexts'])
        if not permitted:
            assert reads == contexts == 0, 'Forbidden native access: ' + gold['id']
        if reads:
            assert observed['intent'] == gold['intended_category_draft'], gold['id']
        query = base64.b64decode(observed['query_base64']).decode()
        details.append(dict(id=gold['id'], expected_disposition=gold['expected_disposition_draft'],
                            intended_category=gold['intended_category_draft'], native_disposition=observed['status'],
                            reads=reads, contexts=contexts, complete_subject=gold['complete_subject_draft'],
                            actual_port_query=query, exact_subject_query_agreement=bool(reads) and query == gold['complete_subject_draft'],
                            entity_gold=gold['entities_draft'],
                            entity_scoring='Spans independently validated; no aggregate native gold-entity accuracy claimed',
                            query_scoring='Calendar legacy windows can use empty port query; literal-subject equality is not full-scope correctness',
                            retrieval_gap=permitted and not reads))
    result = dict(version='v15-reviewed-deterministic-baseline-001', exposed_development=True,
                  independent_evaluation=False, model_fitted=False, macro_f1=None, ml_enabled=False,
                  requests=len(details), permitted_read_requests=sum(r['expected_disposition'] == 'read' for r in details),
                  actual_read_calls=sum(r['reads'] for r in details),
                  retrieval_gaps=sum(r['retrieval_gap'] for r in details),
                  prohibited_requests=sum(r['expected_disposition'] != 'read' for r in details), forbidden_reads=0,
                  expected_dispositions=dict(Counter(r['expected_disposition'] for r in details)),
                  native_dispositions=dict(Counter(r['native_disposition'] for r in details)),
                  exact_subject_port_query_matches=sum(r['exact_subject_query_agreement'] for r in details),
                  full_scope_constraint_accuracy=None,
                  limitation='A port call alone is not a correctly scoped retrieval; entities, fields, dates and result accuracy remain separate gates. Same-user development author/reviewer; only two calendar development-test examples.',
                  rows=details)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / 'reviewed-development-baseline.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key != 'rows'}, indent=2))
    return result


if __name__ == '__main__':
    report()
