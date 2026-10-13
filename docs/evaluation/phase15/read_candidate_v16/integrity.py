"""Current v16 certificate; historical v15 artifacts remain byte-bound evidence."""
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
MANIFEST = HERE / 'development-manifest-v16.json'
V15 = HERE.parent / 'candidate_v15'


def digest(path, raw=False):
    value = path.read_bytes()
    return hashlib.sha256(value if raw else value.replace(b'\r\n', b'\n')).hexdigest()


def prior():
    spec = importlib.util.spec_from_file_location('metis_v15_preservation', V15 / 'integrity.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.check_prior()
    return module


def payload():
    old = prior()
    retained = json.loads((V15 / 'development-manifest-v15.json').read_text(encoding='utf-8'))
    for name, expected in retained['frozen_hashes'].items():
        if old.frozen_digest(ROOT / name) != expected:
            raise ValueError('Historical evidence drift: ' + name)
    current = {p.relative_to(ROOT).as_posix() for p in (ROOT / 'app/src/main/java').rglob('*.kt')}
    for folder in (HERE, HERE.parent / 'classifier_v16'):
        current.update(p.relative_to(ROOT).as_posix() for p in folder.iterdir() if p.is_file() and p != MANIFEST)
    current.update(('app/build.gradle.kts', 'app/src/test/java/dev/metis/agent/CandidateV16Test.kt',
                    'app/src/androidTest/java/dev/metis/agent/CandidateV16DeviceTest.kt',
                    'app/src/androidTest/assets/phase15-read-v16.json', '.github/workflows/android.yml',
                    '.github/workflows/classifier-v11.yml', 'docs/evaluation/phase15/requirements.txt',
                    'MODEL_CARD.md', 'docs/ROADMAP.md', 'docs/AGENT_TRAINING_PLAN.md'))
    frozen = dict(retained['frozen_hashes'])
    frozen.update({p.relative_to(ROOT).as_posix(): digest(p, raw=True)
                   for p in V15.iterdir() if p.is_file()})
    # Intake 006 explicitly declares LF-normalized text; its semantic bytes are otherwise exact.
    intake = {p.relative_to(ROOT).as_posix(): digest(p)
              for p in (HERE.parent / 'calibration_intake_006').iterdir() if p.is_file()}
    return dict(version='phase15-read-and-classifier-v16-preparation', ml_enabled=False, ml_packaged=False,
                fresh_independent_evaluation=False, hashes={n: digest(ROOT / n) for n in sorted(current)},
                frozen_hashes=frozen, frozen_intake006_lf_hashes=intake,
                historical_policy='Original v6–v15 evidence bytes unchanged; v15 shared-source certificate superseded only for current sources, never rewritten')


def check():
    actual = payload()
    expected = json.loads(MANIFEST.read_text(encoding='utf-8'))
    if expected != actual:
        drift = [f'{group}: {n}' for group in ('hashes', 'frozen_hashes', 'frozen_intake006_lf_hashes')
                 for n in sorted(set(expected[group]) | set(actual[group])) if expected[group].get(n) != actual[group].get(n)]
        raise ValueError('V16 current/frozen certificate drift: ' + ', '.join(drift))
    return expected


if __name__ == '__main__':
    if sys.argv[1:] == ['--write']:
        if list((ROOT / 'app/build/reports/phase15/v16').glob('*/first-result.json')):
            raise ValueError('A scored classifier cannot be silently recertified')
        MANIFEST.write_text(json.dumps(payload(), indent=2) + '\n', encoding='utf-8', newline='\n')
    else:
        result = check()
        print(f'V16 integrity: {len(result["hashes"])} current; {len(result["frozen_hashes"])} frozen historical artifacts')
