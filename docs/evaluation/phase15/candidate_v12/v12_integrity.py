"""Current candidate integrity; historical manifests continue to certify historical sources."""
import json
from v12_data import HERE,ROOT,VERSION,digest


def manifest():
    files=[p for p in HERE.iterdir() if p.is_file() and 'manifest' not in p.name and p.suffix in {'.py','.json','.md','.tsv'}]
    files += [HERE / '.gitattributes']
    files += list((ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt'))
    files += [ROOT / p for p in ('docs/evaluation/phase15/requirements.txt',
        'app/src/main/java/dev/metis/agent/domain/storage/MemorySearch.kt',
        'app/src/test/java/dev/metis/agent/CandidateV12ReadTest.kt',
        'app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt',
        'app/src/androidTest/java/dev/metis/agent/CandidateV12DeviceTest.kt',
        'app/src/androidTest/assets/phase15-v12.tsv','app/src/androidTest/assets/.gitattributes',
        'docs/PHASE_15_VERIFICATION.md','.github/workflows/android.yml','.github/workflows/classifier-v11.yml')]
    retained={}
    for directory in ('read_safety_v6','read_coverage_v7','read_coverage_v8','classifier_v9','read_coverage_v10','classifier_v11','human_review_003'):
        for p in (HERE.parent / directory).rglob('*'):
            if p.is_file() and '__pycache__' not in p.parts: retained[p.relative_to(ROOT).as_posix()]=digest(p,raw=True)
    payload=dict(version=VERSION,human_approved=False,ml_enabled=False,ml_packaged=False,
        hashes={p.relative_to(ROOT).as_posix():digest(p) for p in sorted(files)},retained_hashes=retained)
    (HERE / 'development-manifest-v12.json').write_text(json.dumps(payload,indent=2)+'\n',encoding='utf-8',newline='\n')


def check_manifest():
    m=json.loads((HERE / 'development-manifest-v12.json').read_text(encoding='utf-8'))
    if m['version']!=VERSION or any(m[k] for k in ('human_approved','ml_enabled','ml_packaged')): raise ValueError('Unauthorized v12 state')
    for p,expected in m['hashes'].items():
        if digest(ROOT / p)!=expected: raise ValueError('Current source/data drift: '+p)
    for p,expected in m['retained_hashes'].items():
        if digest(ROOT / p,raw=True)!=expected: raise ValueError('Historical artifact changed: '+p)
    return m


if __name__=='__main__': manifest()
