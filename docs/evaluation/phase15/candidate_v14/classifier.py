"""Separately versioned disabled experiment using the retained training engine in isolation."""
import importlib.util
import json
import sys
from data import HERE

sys.path.insert(0, str(HERE.parent / 'candidate_v12'))
path = HERE.parent / 'candidate_v12/v12_model.py'
spec = importlib.util.spec_from_file_location('metis_v14_training_engine', path)
engine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(engine)
engine.PROTOCOL = json.loads((HERE / 'protocol.json').read_text(encoding='utf-8'))
engine.MODEL_VERSION = 'phase15-classifier-v14'


def partitions():
    earlier = []
    for directory, filename in (('classifier_v11', 'development-data.json'),
                                ('candidate_v12', 'classifier-development.json')):
        earlier += [row for row in json.loads((HERE.parent / directory / filename).read_text(encoding='utf-8'))['rows']
                    if row['split'] != 'test']
    new = json.loads((HERE / 'classifier-development.json').read_text(encoding='utf-8'))['rows']
    parts = {split: [row for row in earlier + new if row['split'] == split]
             for split in ('train', 'calibration', 'selection', 'test')}
    if any(row.get('provenance') != 'assistant_authored' for rows in parts.values() for row in rows):
        raise ValueError('Human drafts or unknown provenance cannot enter synthetic fitting/scoring')
    families = [set(row['family_id'] for row in rows) for rows in parts.values()]
    if any(families[i] & families[j] for i in range(4) for j in range(i)):
        raise ValueError('Family leakage')
    texts = [{engine.preprocess(row['utterance']) for row in rows} for rows in parts.values()]
    if any(texts[i] & texts[j] for i in range(4) for j in range(i)):
        raise ValueError('Text leakage')
    return parts
