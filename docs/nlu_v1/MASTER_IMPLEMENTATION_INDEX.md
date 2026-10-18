# METIS NLU v1 — Master Implementation Index

Recorded 2026-10-16. Active NLU sequence; historical V4–V16 evidence stays
archived and immutable. NLU v1 is NOT_TRAINED and not enabled.

Build a fresh on-device NLU system while preserving the modular 24-agent
architecture and deterministic authority. Domains describe meaning; coordination
and safety agents do not need their own language labels.

```text
User Input → METIS NLU v1
→ Domain + Action + Slots + Disposition + OOD
→ Context Agent → Deterministic Validator → Safety Agent
→ Orchestrator / Specific Agent → Action or Retrieval → Verification Agent
```

| Order | Plan | Required evidence | Status |
| --- | --- | --- | --- |
| 1 | Schema and labels | Catalog, strict schemas, annotation rules, contract tests, review/freeze | DONE; nlu_v1_schema_001 frozen per Plan 01 |
| 2 | Dataset source registry | Exact versions, primary URLs, licenses, permitted purposes, approvals, hashes | Registry implemented; unresolved sources blocked, payload hashes pending intake |
| 3 | Dataset ingestion and mappers | Reproducible intake, explicit label/slot mappings and rejection tests | Five adapters implemented/tested; real-source intake remains approval/coverage gated |
| 4 | Canonical data/provenance/privacy | Lineage, source hashes, approvals, quarantine, deny-by-default legacy eligibility | Implemented/tested; empty-workspace report; real corpus/embedding audit pending approved data |
| 5 | Synthetic data and hard negatives | Controlled generators, family/scenario lineage, adversarial coverage, review | Implemented/tested; 41 local candidates quarantined, rights/privacy/embedding review pending |
| 6 | Splits/leakage/validation | Five purposes; duplicate, near-duplicate, family, scenario and parent-lineage isolation; reserve independent evaluation | Implemented/tested; actual five-partition validation and final freeze pending approved corpus/fresh independent intake |
| 7 | Build Dataset v0 | Frozen manifest, hashes, distributions, coverage, rejected-row audit and prior data gates | Builder tested; 2,000 public-human candidates quarantined; six group gaps/review/leakage/final intake block an actual freeze |
| 8 | Train BERT-Tiny baseline | Joint multi-task model, reproducible config/seeds, deterministic baseline comparison | Not started |
| 9 | Evaluate OOD and errors | Per-class/confusion/span/structured-request metrics, false accepts/rejects, calibration, error taxonomy | Not started |
| 10 | Expand Dataset v1 | Development-driven additions; preserved protected partitions; updated leakage audit | Not started |
| 11 | Train MiniLM teacher and distill | Multi-task teacher, supervised distillation, student/teacher comparisons | Not started |
| 12 | Export ONNX, quantize and deploy Android | Tokenizer/offset/numerical parity; APK/model size, cold start, RAM, battery, device p50/p95 | Not started |
| 13 | Integrate agents/context/safety | Routing, ambiguity, capability/permission/revision gates, no bypass, verified effects | Not started |
| 14 | Final validation and public benchmark | Frozen fresh independent evaluation, publication license/privacy review and all completion gates | Not started |

Student: BERT-Tiny-class joint multi-task Transformer for Android. Teacher:
all-MiniLM-L6-v2-class model for training/distillation only; never required in APK.
These are architecture targets, not downloaded checkpoints or performance claims.

Primary source candidates: MASSIVE 1.1 en-US; HWU / NLU Evaluation Data; MTOP
English; CLINC150 / OOS; explicitly approved TRAINABLE_LEGACY METIS data;
controlled METIS-specific synthetic data. Step 2 must verify versions/licenses
before ingestion. Listing a source is not approval or a direct METIS label mapping.

Secondary candidates: SGD / SGD-X for later context/multi-turn work; SLURP for
speech/ASR robustness; SNIPS for slot/intent sanity; Banking77 optionally for OOD.
Public source test partitions do not silently enter training. Known public tests
and generated fixtures cannot substitute for fresh independent evaluation.

Rules that never change:

- ML never directly authorizes or executes actions.
- Deterministic validators and Safety Agent remain final authority.
- Historical V4–V16 ML evidence remains archived and immutable.
- Protected evaluation data never becomes training data.
- No manual query batches from the user are required for progress.

Completion requires dataset isolation, semantic-model evaluation, Android
parity/performance, agent-routing tests, safety gates and a final independent
evaluation all to pass. Missing evidence is not a pass. Every run records dataset,
model, tokenizer and configuration hashes, Git revision, seed and environment.
Freeze measurable acceptance criteria before tuning or opening final evaluation.

Step 1: [contract and annotation rules](SCHEMA_AND_LABELS.md),
[catalogs and freeze rules](../../ml/nlu_v1/schema/README.md),
[output schema](../../ml/nlu_v1/schema/output.schema.json),
[record schema](../../ml/nlu_v1/schema/row.schema.json),
[validator](../../ml/nlu_v1/contract.py).

Step 2: [registry policy and verification](DATASET_SOURCE_REGISTRY.md),
[dataset source registry](../../ml/nlu_v1/configs/dataset_sources.yaml),
[pinned metadata evidence](../../ml/nlu_v1/configs/dataset_source_evidence.json).

Step 3: [source mappers, canonical envelope and verification](../../ml/nlu_v1/ingestion/README.md),
[canonical schema](../../ml/nlu_v1/ingestion/canonical.schema.json),
[MASSIVE mapping](../../ml/nlu_v1/ingestion/mappings/massive_intent_mapping.yaml).

Step 4: [canonical governance and verification](../../ml/nlu_v1/data_management/README.md),
[managed record schema](../../ml/nlu_v1/data_management/canonical_data.schema.json),
[empty-workspace dedupe report](PLAN_04_EMPTY_WORKSPACE_DEDUPE.json).

Step 5: [controlled generation, context extension and verification](../../ml/nlu_v1/synthetic/README.md),
[family templates](../../ml/nlu_v1/configs/synthetic_families.yaml),
[aggregate candidate report](PLAN_05_GENERATION_REPORT.json).

Step 6: [grouped partitioning, leakage gates and final lifecycle](../../ml/nlu_v1/splitting/README.md),
[split policy](../../ml/nlu_v1/configs/split_policy.yaml),
[workspace readiness report](PLAN_06_SPLIT_READINESS.json).

Step 7: [v0 curator, public acquisition and freeze artifacts](../../ml/nlu_v1/dataset_v0/README.md),
[v0 build targets](../../ml/nlu_v1/configs/dataset_v0.yaml),
[candidate/readiness evidence](PLAN_07_DATASET_V0_READINESS.json).

Step 7 continuation: [machine curation and review workflow](../../ml/nlu_v1/dataset_v0/MACHINE_CURATION.md),
[aggregate machine curation evidence](PLAN_07_MACHINE_CURATION_REPORT.json).

Step 7 grouped review continuation: [immutable group approvals, coverage gates and offline similarity audit](../../ml/nlu_v1/dataset_v0/GROUP_REVIEW.md).

The [aggregate grouped-review result](PLAN_07_GROUP_REVIEW_REPORT.json) distinguishes
semantic groups, individual exceptions, candidate coverage, approved coverage and
completed embedding execution from unresolved release/leakage gates.
