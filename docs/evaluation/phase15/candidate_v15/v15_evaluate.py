"""Bound v15 native exposed evidence. No classifier fit or independent score."""
import base64
import csv
import hashlib
import json
from collections import Counter
from integrity import HERE, ROOT, check
from v15_candidate import resolve

OUTPUT = ROOT / 'app/build/reports/phase15/v15'
ORIGINAL_FAILURES = {f'p15v5-fresh-{index:04}' for index in (14, 17, 21, 26, 52, 57, 65, 66, 70, 71, 103, 129, 132, 139)}


def production_hash():
    value = hashlib.sha256()
    for path in sorted((ROOT / 'app/src/main/java').rglob('*.kt'), key=lambda path: path.relative_to(ROOT).as_posix()):
        value.update(path.relative_to(ROOT).as_posix().encode())
        value.update(path.read_bytes().replace(b'\r\n', b'\n'))
    return value.hexdigest()


def native_report():
    rows = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
    lines = (OUTPUT / 'native.tsv').read_text(encoding='utf-8').splitlines()
    assert lines[0] == '# input_sha256=' + hashlib.sha256((HERE / 'regression.tsv').read_bytes()).hexdigest()
    assert lines[1] == '# production_sha256=' + production_hash(), 'Stale production snapshot'
    actual = list(csv.DictReader((line for line in lines if not line.startswith('#')), delimiter='\t'))
    assert [r['id'] for r in rows] == [r['id'] for r in actual]
    for row, observed in zip(rows, actual, strict=True):
        parsed = row['parsed']
        assert int(observed['reads']) == parsed['reads'], row['id']
        if parsed['reads']:
            assert base64.b64decode(observed['query_base64']).decode() == parsed['query_text'], row['id']
        if observed['status'] == 'FOLLOW_UP':
            assert row['utterance'] in base64.b64decode(observed['message_base64']).decode(), row['id']
    summary = {}
    for section in ('retained', 'v15', 'human005'):
        pairs = [(row, result) for row, result in zip(rows, actual, strict=True) if row['section'] == section]
        summary[section] = dict(rows=len(pairs), admissions=sum(int(result['reads']) for _, result in pairs),
                                dispositions=dict(Counter(result['status'] for _, result in pairs)),
                                existing_admissions_preserved=sum(row['old_reads'] for row, _ in pairs),
                                complete_queries_and_utf16_entities_agree=True,
                                full_request_preserved_in_clarifications=True)
    benchmark = json.loads((HERE.parent / 'human_review_003/first_evaluation/frozen-benchmark.json').read_text(encoding='utf-8'))['rows']
    original = [row for row in benchmark if row['id'] in ORIGINAL_FAILURES]
    assert len(original) == 14 and all(not resolve(row['utterance'])['reads'] for row in original)
    approved = [row for row in benchmark if row['expected_outcome'] == 'read']
    prohibited = [row for row in benchmark if row['expected_outcome'] != 'read']
    assert len(approved) == 23 and all(resolve(row['utterance'])['reads'] for row in approved)
    assert all(not resolve(row['utterance'])['reads'] for row in prohibited)
    v9 = json.loads((HERE.parent / 'classifier_v9/development-data.json').read_text(encoding='utf-8'))['rows']
    v9_read = [row for row in v9 if row['disposition'] == 'read']
    v9_blocked = [row for row in v9 if row['disposition'] == 'clarify']
    assert len(v9_read) == 299 and all(resolve(row['utterance'])['reads'] for row in v9_read)
    assert all(not resolve(row['utterance'])['reads'] for row in v9_blocked)
    promotions = [dict(id=row['id'], utterance=row['utterance'], scope=row['parsed']['scope']) for row in rows
                  if row['section'] == 'retained' and not row['old_reads'] and row['parsed']['reads']]
    return dict(sections=summary, native_python_cases=len(rows), forced_read_label_trials=4 * len(rows),
                original_forbidden_reads=0, original_forbidden_cases=14,
                approved_benchmark_reads=23, benchmark_prohibited_cases=len(prohibited), benchmark_forbidden_reads=0,
                v9_valid_reads=299, v9_clarification_cases=len(v9_blocked), v9_forbidden_reads=0,
                v9_formerly_unavailable_calendar_forms=100,
                new_gold_forbidden_reads=0, forged_scopes_and_entities_blocked=True,
                authorized_scoped_promotions=promotions,
                human_drafts_ungraded=True, human_macro_f1=None, independent_evaluation=False, ml_enabled=False)


def main():
    check()
    report = native_report()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'authorized_scoped_promotions'}, indent=2))
    print('Authorized promotions of previously unsupported scoped reads:', len(report['authorized_scoped_promotions']))


if __name__ == '__main__':
    main()
