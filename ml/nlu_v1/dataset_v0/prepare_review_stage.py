"""Prepare an immutable public-first candidate stage for grouped review."""
import argparse
import hashlib
from pathlib import Path

from ml.nlu_v1.dataset_v0.machine_curation import PublicEvidence, file_hash
from ml.nlu_v1.dataset_v0.coverage_additions import generated_rows, public_candidates, TEMPLATES, COVERAGE
from ml.nlu_v1.data_management.records import DataError
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import read_jsonl, official
from ml.nlu_v1.synthetic.generate import verify_generation_batch


def prepare(destination):
    destination = Path(destination)
    if destination.exists() or (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('NEW_QUARANTINE_STAGE_DIRECTORY_REQUIRED')
    originals = read_jsonl(DATASETS / 'quarantine/plan07_public_candidates.jsonl')
    if any(official(r) or r['partition'] != 'UNASSIGNED' or r['source']['source_class'] != 'PUBLIC_DATASET' for r in originals):
        raise DataError('PROTECTED_OR_NONPUBLIC_INPUT_FORBIDDEN')
    evidence = PublicEvidence()
    public, counts = public_candidates(evidence, {r['id'] for r in originals})
    synthetic, groups = generated_rows()
    verify_generation_batch(DATASETS, 'plan05-seed17')
    origins = {'task_read_subject_date_status', 'memory_read_subject', 'promise_read_person'}
    retained = [r for r in read_jsonl(DATASETS / 'quarantine/plan05-seed17.jsonl')
                if r['metadata']['structure']['origin_family'] in origins]
    files = {'public_additions.jsonl': jsonl_bytes(public), 'synthetic_additions.jsonl': jsonl_bytes(synthetic),
             'full_embedding_input.jsonl': jsonl_bytes(originals + public + retained + synthetic),
             'synthetic_groups.json': json_bytes({'families': groups[0], 'scenarios': groups[1]}),
             'public_source_search_report.json': json_bytes({'public_additions': len(public), 'counts': counts,
                 'excluded_annotations': evidence.public_scan_exceptions, 'approved_intakes': 0,
                 'verified_available_sources': sorted(evidence.sources), 'unavailable_or_invalid_sources': evidence.errors,
                 'other_registered_sources': 'NO_VERIFIED_LOCAL_ACQUISITION_USED',
                 'source_priority': 'PUBLIC_TRAINING_ACQUISITIONS_BEFORE_CLOSED_SYNTHETIC',
                 'positive_public_coverage': 'EXISTING_PROVISIONAL_MAPPINGS_PRESERVED; SUPPORTED_DEFICITS_FILLED_BY_SYNTHETIC_CANDIDATES'}),
             'coverage_templates.yaml': TEMPLATES.read_bytes(), 'coverage_policy.yaml': COVERAGE.read_bytes(),
             'stage_engine.py': Path(__file__).read_bytes()}
    manifest = {'stage_version': 'nlu_v1_review_stage_001', 'status': 'QUARANTINED_CANDIDATES_ONLY',
                'files': {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
                'original_rows': len(originals), 'public_additions': len(public), 'retained_lineage_rows': len(retained),
                'new_synthetic_rows': len(synthetic), 'approved_rows': 0, 'training_authorized': False,
                'generator_sha256': file_hash(Path(__file__).with_name('coverage_additions.py')),
                'final_independent': 'RESERVED_NEVER_GENERATED'}
    destination.mkdir(parents=True, exist_ok=False)
    for name, content in files.items():
        atomic_new(destination / name, content)
    atomic_new(destination / 'stage_manifest.json', json_bytes(manifest))
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = prepare(args.output)
    print({key: result[key] for key in ('original_rows', 'public_additions', 'retained_lineage_rows', 'new_synthetic_rows', 'approved_rows')})
