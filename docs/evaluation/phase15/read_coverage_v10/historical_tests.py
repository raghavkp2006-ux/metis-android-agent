"""Retain all historical behavior checks; v10 replaces two obsolete source bindings."""
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
for directory in ('read_coverage_v8', 'classifier_v9'):
    sys.path.insert(0, str(HERE.parent / directory))
import test_v8
import test_v9

replaced = {'test_provenance_data_projections_and_retained_integrity',
            'test_manifest_and_authored_projection_exposure_audit'}
suite = unittest.TestSuite()
for module in (test_v8, test_v9):
    for group in unittest.defaultTestLoader.loadTestsFromModule(module):
        suite.addTests(test for test in group if test._testMethodName not in replaced)
result = unittest.TextTestRunner(verbosity=2).run(suite)
raise SystemExit(not result.wasSuccessful())
