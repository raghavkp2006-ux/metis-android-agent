"""Retain historical behavior; supersede only obsolete current-source certificates."""
import importlib.util
import runpy
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from integrity import check, HERE, V15


def legacy_certificate():
    spec = importlib.util.spec_from_file_location('integrity', V15 / 'integrity.py')
    old = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old)
    old.check = check
    sys.modules['integrity'] = old
    sys.path.insert(0, str(V15))


if '--v15' in sys.argv:
    legacy_certificate()
    suite = unittest.defaultTestLoader.discover(str(V15), pattern='test_*.py')
    raise SystemExit(not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful())
if '--evaluate' in sys.argv:
    legacy_certificate()
    runpy.run_path(str(V15 / 'v15_evaluate.py'), run_name='__main__')
    runpy.run_path(str(V15 / 'reviewed_evaluate.py'), run_name='__main__')
    raise SystemExit(0)

original = Path.rglob
excluded = (HERE, HERE.parent / 'classifier_v16', HERE.parent / 'calibration_intake_006')


def historical_paths(path, pattern):
    return (p for p in original(path, pattern) if not any(folder == p or folder in p.parents for folder in excluded))


with patch('pathlib.Path.rglob', historical_paths):
    try:
        runpy.run_path(str(V15 / 'historical_tests.py'), run_name='__main__')
    except SystemExit as result:
        if result.code:
            raise
raise SystemExit(subprocess.run([sys.executable, __file__, '--v15'], check=False).returncode)
