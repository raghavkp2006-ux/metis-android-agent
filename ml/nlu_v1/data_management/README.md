# Plan 04 — canonical data, provenance, privacy and families

Version: `nlu_v1_data_001`. This data-management layer wraps Plan 03 ingestion
records; the frozen `nlu_v1_schema_001` model contract is unchanged. It stores all
required metadata, enforces privacy/review states, registers semantic families and
scenarios, audits duplicates/lineage and quarantines suspects without deletion.
No real corpus has been collected or approved by this implementation.

## Local directories

```text
ml/nlu_v1/datasets/
├── raw/
├── canonical/
├── quarantine/
└── frozen/
```

Only README files are tracked. Payloads, nonempty reports and manifests are ignored
by Git. Raw imports are immutable byte copies; canonical/quarantine batches are
new snapshots. A batch's hash manifest is its commit marker: readers must use
`verify_batch` and reject missing, incomplete or modified files. Failed batches
without a marker are not usable. Existing batches are never overwritten, moved or
deleted. `frozen/` is reserved for later reviewed dataset snapshots; this step
does not build or freeze training data. The immutable V4–V16 archive stays untouched.

## Record and provenance contract

`canonical_data.schema.json` requires row ID, exact text/hash, source dataset,
version, record ID, original split/intent, domain/action/slots/disposition, BIO
tokens, semantic family, scenario ID, semantic parent, privacy, review status,
partition, generation metadata, provenance, review history and quarantine reasons.
Original annotation/composition metadata is retained locally. Model semantics
and UTF-16 spans are checked against the frozen output schema.

Migration preserves exact text, slots, source ID/split/intent and source version.
Provenance includes the complete ingestion-row hash, import file hash/batch/ordinal,
mapping configuration hash, source-registry version/hash, upstream privacy and
family/scenario definition hashes. Source artifact hash and intake approval remain
separate from the import-file hash. Null source evidence stays unresolved and
quarantined; no hash, license, consent, author or scenario is invented.

For CONTROLLED_SYNTHETIC sources, generation is mandatory and contains parent row
IDs, integer seed, generator ID and template ID. Root generation explicitly has an
empty parent list. Non-synthetic source classes cannot carry generation metadata.
Missing parents, self-parenting, cycles, inconsistent parent groups and unsafe
privacy ancestry are rejected/quarantined. This defines storage for Plan 05; no
synthetic source or generation run is approved or built here.

## Privacy and review states

Privacy labels are exactly PUBLIC, PRIVATE, RESTRICTED, UNKNOWN_PRIVACY.
PUBLIC_LICENSED from the ingestion layer becomes UNKNOWN_PRIVACY: a dataset
license is not privacy clearance. CONSENTED_REDACTED becomes RESTRICTED rather
than assuming consent to publish. New classifications need evidence bound to the
proposed record facts, reviewer/record IDs, UTC time, license and explicit publication
permission. PRIVATE/RESTRICTED records may be retained locally with reviewed rights;
they never enter a public export. UNKNOWN_PRIVACY always blocks public export and
canonical approval. Hashes are provenance references, not anonymization.

Review states are exactly PENDING, AUTO_VALIDATED, HUMAN_REVIEWED, APPROVED,
REJECTED and QUARANTINED. Normal progress is:

```text
PENDING → AUTO_VALIDATED → HUMAN_REVIEWED → APPROVED
          ↘ REJECTED / QUARANTINED
```

Approval cannot skip human review. Each transition appends a UTC, actor/reason,
content-binding and evidence-digest record chained to the previous event. Changed
text, semantics, source, privacy, groups or generation invalidate existing evidence.
Review evidence also binds the current state event, preventing replay after a new
quarantine finding. Hash chains detect edits; they do not authenticate people or
turn model-generated review into human review. Operator review sidecars are the
trusted input boundary; source rows cannot supply their own approvals.

Quarantine retains original records and every reason. It never picks a duplicate
winner. Release requires an explicit external human-review record bound to the
current facts, state event and full reason list; it returns only to PENDING.
Re-audit is required and can quarantine the same unresolved suspect again. No
automatic re-audit, privacy edit or embedding change silently releases a row.

## Families, scenarios and semantic parents

`configs/families.yaml` registers the requested families, including task_read_all,
task_read_date, task_read_status, task_create, task_delete, calendar_read_date,
calendar_read_person, calendar_create, memory_read_subject, promise_read_person
and unknown_external_action. Definitions constrain domain/action and relevant
slots/dispositions. “Read all” families cannot conceal an explicit scope filter.
Assignment requires a compatible registered definition, not a keyword guess.

`configs/scenarios.yaml` starts empty because no dataset scenarios exist yet.
`register_scenario` creates an opaque scenario ID linked to a family, semantic
parent ID and nonpersonal description. It rejects duplicate IDs and invalid
families. Assignment records definition hashes; changing a definition requires
revalidation. Unresolved grouping remains null and quarantined.

A semantic family is a capability category; a semantic parent identifies an
actual origin scenario/template/paraphrase group. All paraphrases of one parent
must share that parent, even across source files or datasets. Assigned members
cannot straddle training, tuning or protected partitions. Generated descendants
inherit their parent group. Official dev/test remains PROTECTED_EVALUATION during
migration; it cannot be reassigned to TRAIN or tuning pools. Full split allocation
and exposure audits remain Plan 06; declaring a parent is not proof of independent
authorship or absence of leakage.

## Dedupe and similarity quarantine

`configs/dedupe_policy.yaml` pins the five required checks:

1. Exact original text.
2. NFKC, casefold and whitespace-normalized text; original text is unchanged.
3. Source identity. MASSIVE/HWU/MTOP/legacy IDs are global within source/version;
   CLINC ordinal IDs are scoped to their original split.
4. Source skeleton: replace annotated slots with typed placeholders and normalize,
   grouping within source and original intent. Unknown slot values are not guessed.
5. Cosine embedding similarity above the declared threshold.

All implicated rows are quarantined, including exact duplicates and label conflicts.
The report contains row IDs/ordinals, checks, reasons, policy/model hashes and counts,
not utterance text. It also audits duplicate row IDs, generation graphs and semantic
parent partition conflicts. Suspects and original snapshots remain available for
review; `deleted_rows` is always zero.

The embedding interface accepts precomputed, text-bound vectors plus exact model,
tokenizer and configuration hashes. It validates one model/dimension, finite nonzero
vectors and exact row-ID/text hashes, then performs a deterministic reference cosine
scan. It downloads no model and performs no network inference. Missing/stale vectors
quarantine affected rows and make the audit incomplete. Invalid vectors reject the
artifact. The 0.95 threshold is a conservative quarantine candidate, not measured
semantic quality. Reference comparison is quadratic and intended for bounded offline
batches; large-scale indexing/performance is not claimed here.

Reports bind the collection's exact facts, dedupe policy and their own integrity.
Any changed text/labels/provenance/group invalidates the report. Human review-state
changes alone do not change vector input, but public/reuse approvals additionally
bind current review state. Embeddings and hash metadata do not prove model quality
or genuine independent authorship.

## Public export and future reuse

`storage.export_public` requires a complete, current dedupe report, APPROVED rows,
PUBLIC classification, matching publication/license evidence and a separate
operator PUBLIC_EXPORT approval bound to the collection, report, every selected
record and review-state hash. Failure writes no partial export. No unknown/private/
restricted data or unapproved ancestors can be exported. Worker IDs, original
annotation metadata, parse/audit text and internal review evidence stay local.
The public projection retains exact selected text, labels, source provenance,
families/scenarios/lineage, license evidence, citation and change records. It is a
local artifact; the tool does not upload or publish to any external service.

The older Plan 03 `to_frozen_record` now also requires this layer's governed,
APPROVED record, matching ingestion origin, complete audit, assigned target
partition and parent/scenario. It cannot bypass new privacy or quarantine gates.
New PUBLIC maps explicitly to the frozen record's PUBLIC_LICENSED metadata;
PRIVATE/RESTRICTED remain PRIVATE_DO_NOT_PUBLISH and require additional explicit
private-training consent. Public export permission is not training permission.
No real rows have been converted to training records; schema-bridge tests use
isolated synthetic fixtures only. Plans 06–07 still own actual split/dataset builds.

## Commands and verification

```powershell
python ml/nlu_v1/data_management/manage.py --check
python -m unittest discover -s ml/nlu_v1/tests -v
python ml/nlu_v1/data_management/manage.py --import-jsonl path/to/intake.jsonl --batch-id intake_001 --embeddings path/to/embeddings.json
```

Import without embeddings is allowed only into quarantine with an incomplete
report. Classification, scenario registration, evidence-bound review and controlled
export are explicit Python APIs in `records.py`/`storage.py`; they do not synthesize
human approvals. Existing source-license/intake blocks continue to apply. No manual
user query batches are needed for implementation progress.

Local verification on 2026-10-16: 74 NLU tests pass (21 new governance tests and
one new old-adapter bypass regression), plus six legacy-retirement tests. Frozen
schema, registry, mapping inventories, management schema, retirement integrity,
payload Git-ignore checks and whitespace checks pass. Production sources are
unchanged and ML remains disabled. CI includes the management check; remote CI,
Android/device checks and real semantic-model dedupe were not run.

The checked-in [empty-workspace report](../../../docs/nlu_v1/PLAN_04_EMPTY_WORKSPACE_DEDUPE.json)
records zero real rows and NOT_APPLICABLE_EMPTY_INPUT for embeddings. Numeric
similarity behavior was exercised with synthetic test vectors in temporary test
directories, not a real semantic encoder or corpus. No training data, checkpoint,
independent evaluation or public dataset export was created.
