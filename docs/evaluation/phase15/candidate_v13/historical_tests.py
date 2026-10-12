"""Replay historical Python behavior without recertifying frozen candidates against v13 sources."""
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
V12 = HERE.parent / 'candidate_v12'
sys.path.insert(0, str(V12))
from v12_data import digest  # noqa: E402


def check_historical():
    manifest = json.loads((V12 / 'development-manifest-v12.json').read_text(encoding='utf-8'))
    for name, expected in manifest['hashes'].items():
        if name.startswith('docs/evaluation/phase15/candidate_v12/') and digest(ROOT / name) != expected:
            raise ValueError('Frozen v12 artifact changed: ' + name)
    for name, expected in manifest['retained_hashes'].items():
        if digest(ROOT / name, raw=True) != expected:
            raise ValueError('Historical artifact changed: ' + name)
    return manifest


for directory in ('read_coverage_v8', 'classifier_v9', 'read_coverage_v10', 'classifier_v11'):
    sys.path.insert(0, str(HERE.parent / directory))
import test_v8, test_v9, test_v10, test_v11, test_v12  # noqa: E402

original_rglob = Path.rglob


def historical_paths(path, pattern):
    return (p for p in original_rglob(path, pattern) if V12 not in p.parents and HERE not in p.parents
            and p not in (V12, HERE))


replaced = {'test_provenance_data_projections_and_retained_integrity',
            'test_manifest_and_authored_projection_exposure_audit'}
suite = unittest.TestSuite()
for module in (test_v8, test_v9, test_v10, test_v11, test_v12):
    for group in unittest.defaultTestLoader.loadTestsFromModule(module):
        suite.addTests(test for test in group if module not in (test_v8, test_v9)
                       or test._testMethodName not in replaced)

with patch('pathlib.Path.rglob', historical_paths), \
        patch('test_v10.check_manifest', check_historical), \
        patch('test_v11.check_manifest', check_historical), \
        patch('test_v12.check_manifest', check_historical):
    result = unittest.TextTestRunner(verbosity=2).run(suite)
raise SystemExit(not result.wasSuccessful())
