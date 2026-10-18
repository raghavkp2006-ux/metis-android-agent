# Plan 01 — Schema and Label Freeze

DONE: `nlu_v1_schema_001` is frozen per the user's explicit Plan 01 catalogs.

The authoritative [schema README](../../ml/nlu_v1/schema/README.md) specifies
all labels, BIO encoding, UTF-16 spans, dispositions and ownership boundaries.
The [freeze manifest](../../ml/nlu_v1/schema/freeze.json) records the version,
approval basis and hashes. The earlier `metis-nlu-v1.0` candidate is superseded;
it was never used for training or production.

- Domains approved: exact 19-label Plan 01 catalog.
- Actions approved: exact 16-label Plan 01 catalog.
- Slots approved: exact 20 types, represented by 41 BIO tags including O.
- Dispositions approved: exact five-label Plan 01 catalog.
- Strict JSONL row schema and semantic validator implemented.
- Validator tests pass; freeze integrity checked.

ML extracts raw dates and references; deterministic date code and Context Agent
resolve them. Safety policy independently determines confirmation requirements.
No NLU disposition authorizes execution. Internal agents are not language domains.

No training data was built. Dataset registry and subsequent plans remain pending.
See [verification](STEP_01_VERIFICATION.md).
