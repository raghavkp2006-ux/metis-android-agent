"""Bind the new experiment while retaining all historical candidates byte-for-byte."""
import json
import sys

from v11_data import HERE, ROOT, VERSION, digest


def manifest():
    files = [p for p in HERE.iterdir() if (p.suffix in {'.py','.json','.tsv','.md'} or p.name=='.gitattributes') and 'manifest' not in p.name]
    files += [ROOT / p for p in ('docs/evaluation/phase15/requirements.txt',
        'app/src/test/java/dev/metis/agent/ClassifierV11SafetyTest.kt', '.github/workflows/classifier-v11.yml')]
    retained = {}
    for directory in ('read_safety_v6','read_coverage_v7','read_coverage_v8','classifier_v9','read_coverage_v10','human_review_003'):
        for path in (HERE.parent / directory).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts:
                retained[path.relative_to(ROOT).as_posix()] = digest(path,raw=True)
    value=dict(version=VERSION,human_approved=False,ml_enabled=False,ml_packaged=False,
        hashes={p.relative_to(ROOT).as_posix():digest(p) for p in sorted(files)},retained_hashes=retained)
    (HERE / 'development-manifest-v11.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def check_manifest():
    value=json.loads((HERE / 'development-manifest-v11.json').read_text(encoding='utf-8'))
    if value['version']!=VERSION or any(value[k] for k in ('human_approved','ml_enabled','ml_packaged')):
        raise ValueError('Unauthorized experiment state')
    for name,expected in value['hashes'].items():
        if digest(ROOT / name)!=expected: raise ValueError('V11 source/data drift: '+name)
    for name,expected in value['retained_hashes'].items():
        if digest(ROOT / name,raw=True)!=expected: raise ValueError('Historical evidence changed: '+name)
    # The unchanged v10 manifest continues to certify current production sources.
    sys.path.insert(0,str(HERE.parent / 'read_coverage_v10'))
    from v10_evaluate import check_manifest as check_current
    check_current()
    return value


if __name__=='__main__': manifest()
