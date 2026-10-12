"""Preserve historical behavior; v12 replaces only obsolete current-source certification."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from v12_data import HERE
from v12_integrity import check_manifest

for directory in ('read_coverage_v8','classifier_v9','read_coverage_v10','classifier_v11'):
    sys.path.insert(0,str(HERE.parent / directory))
import test_v8,test_v9,test_v10,test_v11

# Later aggregate replay copies must not retroactively mark old training rows
# as previously exposed. Only restore the earlier file inventory during audits.
original_rglob=Path.rglob
def historical_paths(path,pattern):
    return (p for p in original_rglob(path,pattern) if HERE not in p.parents and p!=HERE)

replaced={'test_provenance_data_projections_and_retained_integrity','test_manifest_and_authored_projection_exposure_audit'}
suite=unittest.TestSuite()
for module in (test_v8,test_v9,test_v10,test_v11):
    for group in unittest.defaultTestLoader.loadTestsFromModule(module):
        suite.addTests(test for test in group if module not in (test_v8,test_v9) or test._testMethodName not in replaced)
with patch('pathlib.Path.rglob',historical_paths),patch('test_v10.check_manifest',check_manifest),patch('test_v11.check_manifest',check_manifest):
    result=unittest.TextTestRunner(verbosity=2).run(suite)
raise SystemExit(not result.wasSuccessful())
