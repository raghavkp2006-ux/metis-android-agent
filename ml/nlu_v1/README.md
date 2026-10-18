# METIS NLU v1

Status: NOT_TRAINED. Plan 01 complete; `nlu_v1_schema_001` is FROZEN.
Legacy classifiers through V16 are retired historical evidence.

Target: tokenizer → semantic transformer encoder → shared representation with
domain classification, action classification, slot/entity extraction and OOD/UNKNOWN
heads. Structured meaning flows through Context Agent, deterministic validators,
Safety Agent, the specific agent and Verification Agent.

The model must never write storage, decide authorization, invent missing entities,
send messages, create tasks/events, or bypass safety and confirmation. Confidence
alone never authorizes execution. Existing deterministic app behavior remains active.

Follow the [master implementation index](../../docs/nlu_v1/MASTER_IMPLEMENTATION_INDEX.md)
in its 14-step order. The [schema and annotation contract](../../docs/nlu_v1/SCHEMA_AND_LABELS.md)
defines step 1. The [approved YAML catalogs](schema/README.md) are the source of
truth; `contract.py` validates strict output and canonical-row schemas. Single-row
validation does not establish provenance approval or cross-split isolation.

```powershell
python -m pip install -r ml/nlu_v1/requirements.txt
python ml/nlu_v1/contract.py --check
python ml/nlu_v1/source_registry.py
python -m unittest discover -s ml/nlu_v1/tests -v
```

Student: BERT-Tiny-class joint multi-task Transformer for Android. Teacher:
all-MiniLM-L6-v2-class training/distillation model; never required in the APK.
Step 2's [dataset source registry](../../docs/nlu_v1/DATASET_SOURCE_REGISTRY.md)
registers primary/secondary sources, license evidence, planned splits and hashing
rules. Unclear license/identity/splits block use; payload hashes await intake.
No training data is built. Collection/fitting require later data gates. No manual
user query batches are required.

Step 3's [five source mappers](ingestion/README.md) convert isolated/approved source
records to a consistent quarantine envelope. Original text, UTF-16 slots, source
IDs/splits and MTOP composition are preserved. Real intake requires external
artifact approval; blocked sources stay blocked. No real corpus has been built.

Step 4's [canonical governance layer](data_management/README.md) adds traceable
source/import hashes, PUBLIC/PRIVATE/RESTRICTED/UNKNOWN_PRIVACY, six review states,
family/scenario/parent registries, generation lineage, five dedupe checks and
quarantine/export gates. Dataset working directories are ignored by Git. The
Plan 04 dedupe report is a historical empty-workspace snapshot; no real embedding model was run.

Step 5's [controlled generators](synthetic/README.md) create structured positive
forms, nearby action boundaries, unresolved references and preserved compound
requests. The 41-row local batch remains quarantined. The explicit
[002 context extension](schema_versions/nlu_v1_schema_002/README.md) can represent
UNKNOWN/NONE needing context while keeping frozen 001 unchanged. Rights/privacy,
embedding similarity and full split validation remain pending; no training data
or model was built.

Step 6's [grouped splitter and leakage gates](splitting/README.md) hold connected
families, scenarios, speakers/users, source IDs, parents and generation seeds in
one partition. Official public tests stay external; fresh independent final data
needs its own verified intake. Strict builds, immutable manifests and final/tuning
access gates are implemented. The current candidates remain ineligible, so no
five-file dataset or final data freeze has been created.

Step 7's [v0 curator and freeze builder](dataset_v0/README.md) now has a 2,000-row
public-human review pool from MASSIVE train and CLINC train/oos_train, with exact
source/license hashes, candidate row hashes and distribution reports. Six target
groups remain uncovered. These rows are quarantined and unapproved; no Dataset v0
freeze or model training has occurred. Official tests remain in raw protected data.

`schema` and `tests` contain the offline contract implementation. Model training,
evaluation and public export remain reserved. No model or Android ML runtime
has been introduced. `datasets/nlu_v1` holds the future data policy.

Every future run must preserve classification micro/macro/per-class metrics and
confusion matrices; UNKNOWN false accepts, known false rejects, OOD precision/recall/F1
and abstention; slot precision/recall/F1, exact spans and exact structured requests;
forbidden executions, valid requests lost, clarification accuracy and unsupported
operation rejection; model/APK sizes, p50/p95 latency, peak RAM, battery and cold-start
costs; seed, model/dataset/tokenizer/configuration hashes, Git commit and environment.
