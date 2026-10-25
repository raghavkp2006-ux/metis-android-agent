# Offline Day 1 measurement

Run from repository root with the already provisioned Python 3.12 and JDK 17.
No install, remote dependency resolution, model fitting or tuning is needed.
The public candidate builder requires local PyYAML. Semantic leakage checks require
the preserved MiniLM checkpoint and pinned numpy 2.5.3, onnxruntime 1.30.0 and
tokenizers 0.22.2; missing bytes/runtime fail closed.

```powershell
python -m unittest ml.eval.test_harness ml.data.test_data_tools ml.eval.test_exporter -v
python -m ml.data.import_sheet ml/data/dev_own.csv --audit-only --report ml/data/dev_own_audit.json
python -m ml.data.build_public_pool --output build/ml-day1/public_train_pool.jsonl --report ml/data/public_pool_report.json
python -m ml.eval.export_rules --gold ml/data/dev_own.csv --fixture fixtures/default_state.json --output ml/eval/baseline_rules/predictions.jsonl
python -m ml.eval.harness --gold ml/data/dev_own.csv --predictions ml/eval/baseline_rules/predictions.jsonl --output ml/eval/baseline_rules
python -m ml.eval.report_day1
```

Public pool output is exclusive-create; choose a new output path when reproducing.
It is a local build artifact with a committed hash/count/provenance report.
Re-evaluation of the same predictions is deterministic. Measured latency naturally
varies across independent JVM executions; never replace measured times with constants.

The development sheet is a byte-preserved supplied source. Strict import rejects
its unsupported label names. See the audit and baseline report before fixing labels.
The supplied fixture has no `now`; all fixture rows remain explicitly unevaluable.
Do not invent a clock to make them pass. Update gold/fixture only with reviewed data.

Author splitting of new valid teammate data:

```text
python -m ml.data.import_sheet teammate.csv --output validated.csv --report intake.json --against existing_train.csv
python -m ml.data.split_tool split --input validated.csv --dev-author AUTHOR --output new_split
```

The splitter emits train/development only, with disjoint authors and leakage checks.
It refuses a one-author corpus. The current development sheet is never promoted
to independent test or fed into training by any Day 1 command.

On Day 5 only, the operator can explicitly pass an externally supplied independent
test path to `split_tool seal --train PATH --test EXTERNAL_PATH --manifest PATH`.
Seal refuses overwrite, author overlap and exact/normalized/skeleton/MiniLM
collisions, then records exact raw-byte test SHA-256. `split_tool check` verifies
the hash before any evaluation/leakage processing. Public data never enters test.
The CI entry point is `python -m ml.data.ci_check`, requiring `METIS_TRAIN_CSV`,
`METIS_EXTERNAL_TEST_CSV`, `METIS_TEST_MANIFEST`. Missing paths fail with `not found`.
The manual workflow uses an offline self-hosted runner with the exact checked-out
revision and preprovisioned dependencies. No frozen test is present today.

## Provenance reused read-only

- `ml/nlu_v1/README.md` and legacy v4/v5/v9/v16 READMEs, dataset provenance.
- `.local-evidence/phase0/unrelated-datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/dataset_sources.yaml`.
- Its `ingestion/mappings/{massive,clinc}_intent_mapping.yaml` and `configs/dedupe_policy.yaml`.
- Its `data_management/dedupe.py`, `dataset_v0/similarity_audit.py`, and adjacent Plan07 dedupe report.
- The pinned similarity recipe is adapted into `ml/data/similarity.py` with the
  same checkpoint Git/SHA hashes, runtime versions, .95 threshold, CPU single-thread
  execution, 256-token truncation, masked-mean pooling, L2 normalization and exact
  repeated-inference check. CSV entity values supply typed skeleton spans.
- Raw public archives are bound against their preserved acquisition observations.

No exposed legacy utterance corpus was loaded as evaluation data. The only
evaluated human utterances are this supplied visible development sheet.
