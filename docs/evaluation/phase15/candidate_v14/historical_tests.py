"""Run retained Python behavior tests with their original historical inventory horizon."""
import runpy
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
original = Path.rglob
later_intake = HERE.parent / 'human_development_004'


def historical_paths(path, pattern):
    return (item for item in original(path, pattern) if HERE not in item.parents and
            later_intake not in item.parents and item not in (HERE, later_intake))


with patch('pathlib.Path.rglob', historical_paths):
    runpy.run_path(str(HERE.parent / 'candidate_v13/historical_tests.py'), run_name='__main__')
