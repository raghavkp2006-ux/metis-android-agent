"""Integrity of exposed intake; no scoring or model selection."""
import base64
import hashlib
import json
import sys
from pathlib import Path

from prepare import HERE, ROOT, make_rows

sys.path.insert(0, str(HERE.parent / 'candidate_v13'))
from v13_integrity import check as check_v13  # noqa: E402

FILES = ['.gitattributes', 'attachment.txt', 'message.txt', 'prepare.py', 'development.json',
         'input.tsv', 'user-review.md', 'README.md', 'validate.py', 'diagnostics.py']
RUNNER = 'app/src/test/java/dev/metis/agent/HumanDevelopment004Test.kt'


def digest(path):
    content = path.read_bytes()
    if path.name != 'attachment.txt':
        content = content.replace(b'\r\n', b'\n')
    return hashlib.sha256(content).hexdigest()


def check_projection():
    data = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))
    assert data['rows'] == make_rows(), 'Source or draft annotation projection drift'
    assert not any(data[key] for key in ('human_approved', 'fresh_independent_evaluation', 'ml_enabled'))
    for name, value in data['source_sha256'].items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == value
    lines = (HERE / 'input.tsv').read_text(encoding='utf-8').splitlines()
    assert lines[0] == 'id\ttext_base64\tsource\ttimestamp\tzone'
    assert len(lines) == 215
    for line, row in zip(lines[1:], data['rows'], strict=True):
        fields = line.split('\t')
        assert fields[0] == row['id'] and base64.b64decode(fields[1]).decode() == row['utterance']
        assert fields[2:] == ['TEXT', '2026-10-12T05:30:00Z', 'Asia/Kolkata']
        assert row['constraints_to_preserve'] == row['utterance']
        assert not any(row[key] for key in ('human_approved', 'fit_eligible',
                                          'calibration_eligible', 'holdout_eligible'))
        assert all(row[key] is None for key in ('entities_draft', 'query_text_draft', 'disposition_draft'))
    return data


def main():
    data = check_projection()
    check_v13()
    hashes = {path.relative_to(ROOT).as_posix(): digest(path)
              for path in [*(HERE / name for name in FILES), ROOT / RUNNER,
                           HERE.parent / 'candidate_v13/development-manifest-v13.json']}
    manifest = dict(version='phase15-human-development-004-intake', human_approved=False,
                    ml_enabled=False, fresh_independent_evaluation=False, hashes=hashes)
    target = HERE / 'intake-manifest.json'
    if sys.argv[1:] == ['--write-manifest']:
        target.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    else:
        assert not sys.argv[1:]
        assert json.loads(target.read_text(encoding='utf-8')) == manifest, 'Intake certificate drift'
    print(f"Intake integrity passed: {len(data['rows'])} unapproved cases; v13 sources and frozen evidence unchanged")


if __name__ == '__main__':
    main()
