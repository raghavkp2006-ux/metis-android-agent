# METIS NLU v1 data workspace

Status: EMPTY; [schema nlu_v1_schema_001](../../ml/nlu_v1/schema/README.md)
is frozen per Plan 01. No training data is built. Follow the
[master index](../../docs/nlu_v1/MASTER_IMPLEMENTATION_INDEX.md) in order.
All row formats use strict UTF-8 JSONL. Frozen training rows validate with
`python ml/nlu_v1/contract.py --validate-jsonl path/to/candidate.jsonl`;
managed records use the separate Plan 04 schema and tooling below.
Passing row validation does not approve provenance or establish split isolation.

Keep TRAIN, DEVELOPMENT, CALIBRATION, SELECTION and FINAL INDEPENDENT TEST
separate. Freeze purpose, IDs, text, scenario/family provenance, authorship and
reviewer records before use. Enforce text/family/scenario separation and independent
authors/reviewers where practical. Reserve the final independent test before tuning.

Legacy material is indexed separately in `../legacy/catalog.json`; none is
automatically approved for training, publication or fresh independent evaluation.
Schema approval does not approve dataset sources, personal-record collection or
training. The [source registry](../../docs/nlu_v1/DATASET_SOURCE_REGISTRY.md)
is implemented with blocked/pending sources. Intake hashes, provenance/privacy,
leakage and dataset-build gates remain outstanding. Plan 02 downloads no payloads.

Plan 03 [source adapters](../../ml/nlu_v1/ingestion/README.md) are implemented and
fixture-tested. Their canonical envelope is quarantine-only and validated separately
from frozen training rows. The workspace stays empty until approved intake; private
quarantine material belongs under ignored `.local-evidence/nlu_v1/`.

Plan 04's [managed workspace](../../ml/nlu_v1/data_management/README.md) is under
`ml/nlu_v1/datasets/{raw,canonical,quarantine,frozen}` with ignored local payloads.
It preserves ingestion provenance, enforces explicit privacy/review states and
family/scenario lineage, and quarantines duplicates/similarity suspects. This
earlier directory remains the policy index; no data is duplicated into it.
