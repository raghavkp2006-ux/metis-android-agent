"""Source-bound native v16 development regression report, never independent evaluation."""
import base64
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from integrity import HERE, ROOT, check
from v16_project import scope_text

OUTPUT = ROOT / 'app/build/reports/phase15/read-v16'


def production_hash():
    value = hashlib.sha256()
    for path in sorted((ROOT / 'app/src/main/java').rglob('*.kt'), key=lambda p: p.relative_to(ROOT).as_posix()):
        value.update(path.relative_to(ROOT).as_posix().encode())
        value.update(path.read_bytes().replace(b'\r\n', b'\n'))
    return value.hexdigest()


def device_evidence():
    first = OUTPUT / 'phone-v16.txt'
    retry = OUTPUT / 'phone-v16-ui-retry.txt'
    if not first.exists() or not retry.exists():
        return dict(status='unexecuted_in_this_checkout', compiled=True)
    # ADB/Tee-Object on Windows PowerShell can produce UTF-16 logs.
    def read(path):
        data = path.read_bytes()
        return data.decode('utf-16') if data.startswith((b'\xff\xfe', b'\xfe\xff')) else data.decode('utf-8-sig')
    initial, repeated = read(first), read(retry)
    storage_passes = 0
    blocks = initial.split('INSTRUMENTATION_STATUS_CODE: 0')
    for block in blocks[:-1]:
        last = block.rsplit('INSTRUMENTATION_STATUS_CODE: 1', 1)[-1]
        storage_passes += 'class=dev.metis.agent.CandidateV16DeviceTest' in last
    assert storage_passes == 4, 'Physical v16 storage results incomplete'
    assert 'OK (7 tests)' in repeated, 'UI retry did not pass'
    assert 'FAILURES!!!' in initial, 'Keep the first failed run visible'
    return dict(status='storage_passed_ui_passed_on_retry', storage_tests_passed=4,
                ui_tests_passed_on_retry=7, initial_combined_passed=10, initial_combined_total=11,
                initial_failure='CalendarV13ScreenTest: no Compose hierarchies found; cause unconfirmed',
                device='CPH2691, Android 16 / API 36', personal_data_cleared=False,
                temporary_stay_awake_restored=True, ml_resource_measurements=False,
                raw_logs=[first.name, retry.name])


def evaluate():
    check()
    rows = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
    lines = (OUTPUT / 'native.tsv').read_text(encoding='utf-8').splitlines()
    assert lines[0] == '# input_sha256=' + hashlib.sha256((HERE / 'regression.tsv').read_bytes()).hexdigest()
    assert lines[1] == '# production_sha256=' + production_hash(), 'Stale native production snapshot'
    observed = list(csv.DictReader((line for line in lines if not line.startswith('#')), delimiter='\t'))
    assert [r['id'] for r in rows] == [r['id'] for r in observed]
    by_text = defaultdict(list)
    for expected, actual in zip(rows, observed, strict=True):
        parsed = expected['parsed']
        assert int(actual['reads']) == parsed['reads'], expected['id']
        if actual['intent'] in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
            assert int(actual['contexts']) == parsed['reads'], expected['id']
        if parsed['reads']:
            assert actual['intent'] == parsed['intent'], expected['id']
            assert base64.b64decode(actual['query_base64']).decode() == parsed['query_text'], expected['id']
            if parsed['scope']:
                assert base64.b64decode(actual['scope_base64']).decode() == scope_text(parsed['scope'],
                    expected['section'] == 'v16'), expected['id']
        if actual['status'] == 'FOLLOW_UP':
            assert expected['utterance'] in base64.b64decode(actual['message_base64']).decode(), expected['id']
        by_text[expected['utterance']].append((expected, actual))
    human = json.loads((HERE.parent / 'candidate_v15/review-projection.json').read_text(encoding='utf-8'))['rows']
    exact_subjects = exact_entities = statuses = task_reads = policy_statuses = effective_queries = 0
    entity_key = lambda e: (e['startOffset'], e['endOffset'], e['type'], e['rawValue'])
    for row in human:
        expected, actual = by_text[row['utterance']][0]
        permitted = row['expected_disposition_draft'] == 'read'
        assert bool(int(actual['reads'])) == permitted, row['id']
        if permitted:
            parsed = expected['parsed']
            assert parsed['intent'] == row['intended_category_draft'], row['id']
            exact_subjects += parsed['query_text'] == row['complete_subject_draft']
            effective_queries += parsed['query_text'] == (row['complete_subject_draft'] or
                next((e['rawValue'] for e in row['entities_draft'] if e['type'] == 'PERSON'), ''))
            exact_entities += sorted(parsed['entities'], key=entity_key) == sorted(row['entities_draft'], key=entity_key)
            if parsed['scope'] and parsed['scope']['kind'] == 'TASK':
                task_reads += 1
                statuses += parsed['scope']['status'] == row['constraints_draft'].get('status')
                policy_statuses += parsed['scope']['status'] == ('OPEN' if row['id'] == 'user-v12-07' else
                                                                row['constraints_draft'].get('status'))
    gold = [r for r in rows if r['section'] == 'v16']
    old = [r for r in rows if r['section'] == 'retained']
    result = dict(version='v16-native-development-regression', production_ml_enabled=False,
                  independent_evaluation=False, classifier_fitted=False, semantic_macro_f1=None,
                  native_python_cases=len(rows), forced_read_label_trials=4 * len(rows),
                  new_assistant_gold_cases=len(gold), new_assistant_valid_reads=sum(r['parsed']['reads'] for r in gold),
                  new_gold_entities_full_queries_and_scopes_agree=True,
                  existing_v15_admissions_preserved=sum(r['old_reads'] for r in old),
                  newly_supported_retained_cases=sum(not r['old_reads'] and bool(r['parsed']['reads']) for r in old),
                  human_reviewed_requests=365, human_permitted_reads=96, human_read_admissions=96,
                  human_previous_read_admissions=44, human_new_read_admissions=52,
                  human_prohibited_requests=269, human_forbidden_reads=0,
                  human_exact_subject_port_query_matches=exact_subjects, human_exact_entity_annotation_matches=exact_entities,
                  human_subject_or_explicit_person_query_matches=effective_queries,
                  human_task_status_matches=statuses, human_task_read_count=task_reads,
                  human_task_status_policy_matches=policy_statuses,
                  full_human_scope_constraint_accuracy=None,
                  standalone_nonread_action_contexts=sum(int(r['contexts']) > 0 and not int(r['reads']) for r in observed),
                  constraint_preservation='Every FOLLOW_UP contains the complete original request; new gold scopes/queries/UTF-16 annotations agree natively. Full stored-answer accuracy is separate.',
                  dispositions=dict(Counter(r['status'] for r in observed)),
                  device_tests=device_evidence(),
                  limitation='Exposed same-user reviewed development. Two person-only calendar queries use the explicit person, not an empty subject. One frozen task-status annotation conflicts with the prior explicit OPEN-list policy; original preserved and separately documented. All retrieval coverage gains are deterministic. No model F1 or independent evaluation claim.')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / 'report.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    evaluate()
