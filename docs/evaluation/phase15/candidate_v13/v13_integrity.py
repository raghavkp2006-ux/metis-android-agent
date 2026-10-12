"""Separate source and frozen-evidence certificate for the local-calendar candidate."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]

CURRENT = [
    '.github/workflows/android.yml', '.github/workflows/classifier-v11.yml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/java/dev/metis/agent/data/storage/LocalAgentReads.kt',
    'app/src/main/java/dev/metis/agent/data/storage/LocalCalendarReads.kt',
    'app/src/main/java/dev/metis/agent/data/storage/LocalPersonalRepository.kt',
    'app/src/main/java/dev/metis/agent/data/storage/RecordDao.kt',
    'app/src/main/java/dev/metis/agent/domain/agent/CalendarReadScope.kt',
    'app/src/main/java/dev/metis/agent/domain/agent/ConfirmedTaskAgent.kt',
    'app/src/main/java/dev/metis/agent/domain/agent/LanguageSpecialist.kt',
    'app/src/main/java/dev/metis/agent/domain/agent/LocalAgentOrchestrator.kt',
    'app/src/main/java/dev/metis/agent/presentation/navigation/AgentApp.kt',
    'app/src/main/java/dev/metis/agent/presentation/navigation/CalendarEditorViewModel.kt',
    'app/src/main/java/dev/metis/agent/presentation/navigation/CalendarWorkspace.kt',
    'app/src/main/java/dev/metis/agent/presentation/navigation/SavedRecords.kt',
    'app/src/test/java/dev/metis/agent/CalendarV13Test.kt',
    'app/src/androidTest/java/dev/metis/agent/CalendarV13StorageTest.kt',
    'app/src/androidTest/java/dev/metis/agent/CalendarV13ScreenTest.kt',
    'docs/PHASE_15_VERIFICATION.md',
    'docs/evaluation/phase15/candidate_v13/README.md',
    'docs/evaluation/phase15/candidate_v13/regression-report.md',
    'docs/evaluation/phase15/candidate_v13/historical_tests.py',
    'docs/evaluation/phase15/candidate_v13/v13_integrity.py',
    'docs/evaluation/phase15/candidate_v13/.gitattributes',
]
CURRENT = sorted(set(CURRENT + [p.relative_to(ROOT).as_posix() for p in
    (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt')]))


def digest(path, raw=False):
    content = path.read_bytes()
    return hashlib.sha256(content if raw else content.replace(b'\r\n', b'\n')).hexdigest()


def frozen_paths():
    return sorted(p.relative_to(ROOT).as_posix() for directory in (
        'read_safety_v6', 'read_coverage_v7', 'read_coverage_v8', 'classifier_v9',
        'read_coverage_v10', 'classifier_v11', 'candidate_v12', 'human_review_003',
    ) for p in (HERE.parent / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)


def create():
    payload = dict(version='phase15-candidate-v13-calendar', ml_enabled=False, ml_packaged=False,
                   human_approved=False, fresh_independent_evaluation=False,
                   hashes={name: digest(ROOT / name) for name in CURRENT},
                   frozen_hashes={name: digest(ROOT / name, raw=True) for name in frozen_paths()})
    (HERE / 'development-manifest-v13.json').write_text(json.dumps(payload, indent=2) + '\n',
        encoding='utf-8', newline='\n')


def check():
    payload = json.loads((HERE / 'development-manifest-v13.json').read_text(encoding='utf-8'))
    if payload['version'] != 'phase15-candidate-v13-calendar' or any(payload[name] for name in (
            'ml_enabled', 'ml_packaged', 'human_approved', 'fresh_independent_evaluation')):
        raise ValueError('Unauthorized candidate state')
    if set(payload['hashes']) != set(CURRENT) or set(payload['frozen_hashes']) != set(frozen_paths()):
        raise ValueError('Manifest inventory drift')
    for group in ('hashes', 'frozen_hashes'):
        for name, expected in payload[group].items():
            if digest(ROOT / name, raw=(group == 'frozen_hashes')) != expected:
                raise ValueError(group + ' drift: ' + name)
    return payload


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == '--write':
        create()
    else:
        result = check()
        print(f"v13 integrity passed: {len(result['hashes'])} current and "
              f"{len(result['frozen_hashes'])} frozen files")
