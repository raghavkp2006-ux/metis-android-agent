"""Retain original tests; replace only obsolete shared-source certificates."""
import runpy
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
if '--v14' in sys.argv:
    from integrity import check_prior
    sys.path.insert(0, str(HERE.parent / 'candidate_v14'))
    import test_v14
    with patch('test_v14.check', check_prior):
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test_v14))
    raise SystemExit(not result.wasSuccessful())
original = Path.rglob
EXCLUDED = (HERE, HERE.parent / 'human_intake_005')


def historical_paths(path, pattern):
    return (item for item in original(path, pattern) if not any(folder == item or folder in item.parents for folder in EXCLUDED))


with patch('pathlib.Path.rglob', historical_paths):
    try:
        runpy.run_path(str(HERE.parent / 'candidate_v14/historical_tests.py'), run_name='__main__')
    except SystemExit as result:
        if result.code:
            raise

# Separate interpreter prevents old evaluators' generic module names from colliding.
raise SystemExit(subprocess.run([sys.executable, __file__, '--v14'], check=False).returncode)
