"""Summarize native observations without treating drafts as gold labels."""
import base64
import csv
import hashlib
import json
from collections import Counter

from prepare import HERE, ROOT
from validate import check_projection


def main():
    data = check_projection()
    directory = ROOT / 'app/build/reports/phase15/human-development-004'
    path = directory / 'native.tsv'
    lines = path.read_text(encoding='utf-8').splitlines()
    assert lines[0] == '# input_sha256=' + hashlib.sha256((HERE / 'input.tsv').read_bytes()).hexdigest()
    runner = ROOT / 'app/src/test/java/dev/metis/agent/HumanDevelopment004Test.kt'
    assert lines[1] == '# runner_sha256=' + hashlib.sha256(runner.read_bytes()).hexdigest()
    certificate = HERE.parent / 'candidate_v13/development-manifest-v13.json'
    assert lines[2] == '# v13_manifest_sha256=' + hashlib.sha256(certificate.read_bytes()).hexdigest()
    predictions = list(csv.DictReader((line for line in lines if not line.startswith('#')), delimiter='\t'))
    assert [row['id'] for row in predictions] == [row['id'] for row in data['rows']]
    decoded = []
    for prediction, original in zip(predictions, data['rows'], strict=True):
        row = dict(prediction)
        for key in ('query', 'calendar', 'message'):
            row[key] = base64.b64decode(row.pop(key + '_base64')).decode()
        row['utterance'] = original['utterance']
        row['contexts'], row['reads'] = int(row['contexts']), int(row['reads'])
        assert row['reads'] <= 1 and row['contexts'] <= 1
        if row['status'] in ('FOLLOW_UP', 'DENIED'):
            assert row['reads'] == row['contexts'] == 0
        if row['status'] == 'FOLLOW_UP':
            assert original['utterance'] in row['message']
        if original['standalone_clarification_draft']:
            assert row['reads'] == row['contexts'] == 0
        decoded.append(row)
    report = dict(ungraded=True, human_approved=False, fresh_independent_evaluation=False,
                  macro_f1=None, entity_accuracy=None, forbidden_read_rate=None,
                  statuses=dict(Counter(row['status'] for row in decoded)),
                  intents=dict(Counter(row['intent'] for row in decoded)),
                  contexts=sum(row['contexts'] for row in decoded),
                  reads=sum(row['reads'] for row in decoded), rows=decoded)
    (directory / 'diagnostics.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n',
                                               encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'rows'}, indent=2))


if __name__ == '__main__':
    main()
