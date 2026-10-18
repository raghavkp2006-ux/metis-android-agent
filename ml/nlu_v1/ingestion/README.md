# Plan 03 — source ingestion and mappers

Plan 04 adds the [managed data contract](../data_management/README.md). New managed
records use its privacy/review catalogs; this ingestion format remains a source
adapter boundary. `to_frozen_record` now requires governed approval and a complete
dedupe audit, so the older adapter cannot bypass new privacy/quarantine gates.

Implemented adapters: `massive_mapper.py`, `hwu_mapper.py`, `mtop_mapper.py`,
`clinc_mapper.py`, `legacy_metis_mapper.py`. `map_record` functions are pure
conversion APIs; they do not download data, approve sources, write files, fit
models, resolve dates/references or dispatch actions.

## Canonical JSONL

`canonical.schema.json`, version `nlu_v1_ingest_001`, defines a strict ingestion
envelope containing the requested id, text, domain, action, slots, disposition,
family, source, privacy and review_status. Source includes dataset registry ID,
version, original record ID/split/intent, mapper/mapping versions and decision,
nullable artifact SHA-256 and intake approval reference. Additional top-level
fields are frozen NLU schema version, UTF-16 annotation tokens/BIO tags, boolean
OOD, purpose, metadata and composition.

The frozen `nlu_v1_schema_001` files are unchanged. Canonical semantic fields and
BIO spans validate against its output contract; the ingestion envelope preserves
source/parse metadata without adding fields to that frozen model interface.
`common.to_frozen_record` is an explicit adapter to the frozen training-row schema,
not an implicit rename or automatic partition assignment.

All mapper output is QUARANTINE/PENDING_REVIEW. Pure adapters leave intake hash
and approval reference null. The intake runner fills them only after verifying
the input bytes against trusted external artifact approval. IDs hash dataset,
version, original split and source record ID; they never use text as a source ID.
Family defaults to an unresolved provisional identifier, not a claim of leakage
isolation. Later grouping review must bind real family/scenario IDs.

## Source rules

| Mapper | Input and behavior |
| --- | --- |
| MASSIVE | Original en-US JSONL: id/utt/annot_utt/partition/scenario/intent. Exact 60-intent table; no wildcard fallback. Bracket markup must reconstruct utt exactly. Locale and scenario/intent must agree. |
| HWU | Original semicolon CSV: answerid/userid/scenario/intent/status/answer/answer_annotation. Combine scenario and short intent for lookup; preserve original intent and user ID. Never replace answer with answer_normalised. IRR_ rows are explicit drops. |
| MTOP | English structured records or eight-column TSV adapter. Preserve original parse and a tree with UTF-16 spans. Only outermost slots flatten to BIO; nested intents become KNOWN_UNSUPPORTED while retaining the tree. |
| CLINC | Full JSON split arrays of text/label pairs. Preserve split key and source row ordinal. 150 explicit intents plus oos; unrelated labels remain OOD candidates, not forced positive METIS actions. |
| Legacy | Only explicit TRAINABLE_LEGACY rows with external text/ID-bound rights/privacy approval. Relabel CHECK_TASKS/CHECK_CALENDAR/CHECK_MEMORY/WHAT_DID_I_PROMISE to TASK/CALENDAR/MEMORY/PROMISE + READ. UNKNOWN requires bound reviewed reannotation. |

Every slot must match original text with UTF-16 `[start,end)` offsets, including
astral characters and repeated entity strings. Source annotations align in order;
there is no substring search, fuzzy repair, casing/whitespace normalization or
invented slot value. Unknown labels, invalid spans and inconsistent annotations
reject the row. Metadata keeps the original bracket annotation; MTOP additionally
keeps the full parse/tree and native slot/token metadata. Audit metadata is not
model input, publication permission, or an executable payload.

MTOP DATE_TIME/START_TIME/END_TIME cannot be blindly relabeled as DATE or TIME.
They require external text-bound temporal-review spans. These select raw DATE,
TIME or DATE_RANGE substrings; they do not resolve dates. REVIEW_TEMPORAL is an
adapter instruction, never a new frozen slot label. Native archive layout remains
unverified while its registry license/split gates are blocked.

All five mapping tables have explicit versions in `mappings/`. MASSIVE covers
exactly the publisher's 60-intent metadata inventory. CLINC covers 150 labels plus
oos from the official label metadata. HWU lists explicit scenario:intent keys;
MTOP lists the supported parser subset. Complete native inventories must be checked
at approved intake; unlisted roots/entities quarantine instead of being guessed.
Slot mapping coverage is explicit, not a claim that every native entity type has
already been reviewed. DROP decisions have reasons and are counted by ordinal.

These tables are deterministic candidate mappings, marked PENDING_DATASET_REVIEW.
MAP initially uses SUPPORTED_NEEDS_CONTEXT so source intent labels cannot assert
argument completeness or waive Safety confirmation. CLINC OOS is also a provisional
METIS scope decision, not proof that every source-OOS row is outside METIS. All
mapped rows require semantic review before training. Legacy UNKNOWN is never
automatically OOD: absent reannotation raises LEGACY_UNKNOWN_REANNOTATION_REQUIRED.

## Intake gates and use

`ingest.py` accepts an already downloaded local member plus an external artifact
approval JSON. It is a quarantine converter, not a downloader or dataset builder.
Only sources REGISTERED_PENDING_INTAKE with verified license metadata can reach
file parsing. MTOP/HWU/legacy remain blocked by the current registry even though
their pure adapters are testable with isolated fixtures. Rows cannot approve
themselves; no approval manifest is generated here.

The trusted approval must contain source/version/revision/license/mapper identity,
APPROVED status, approval ID and true license/identity/split/privacy gates; all
Plan 02 intake-manifest fields are mandatory. The input member hash is checked
before and after conversion. Source URL/revision/locale, timestamp, archive/license
digests and safe member path must be explicit. Approval is operator configuration,
not cryptographic publisher authentication. Retain the actual archive/license
evidence with the approval; this converter does not re-download or inspect them.

MASSIVE/CLINC files containing several official partitions use MIXED_OFFICIAL in
the artifact manifest; per-row original partitions remain intact. Split overrides
must agree with both embedded data and approved file partition. Protected source
dev/test/OOS test rows can be quarantined for benchmarks but cannot be promoted to
TRAIN/DEVELOPMENT/CALIBRATION/SELECTION, even with a per-row approval. Public/legacy
rows can never become a fresh FINAL_INDEPENDENT_TEST through this adapter.

Promotion requires verified intake, an external approval bound to the entire
canonical JSON hash, artifact digest, target split, family/scenario IDs and explicit
license/identity/split/privacy/mapping/isolation gates. It also checks registry
source status, allowed original split and frozen provenance. Changing text,
semantic fields, source metadata or groups invalidates approval. No real row has
been promoted in this implementation; successful promotion tests use synthetic
in-memory fixtures only. Global privacy, consent and leakage auditing remain
Plans 04–06, not capabilities inferred from boolean fields supplied by source data.

Output publication refuses existing files, writes through a temporary sibling,
validates every row, rejects duplicate source IDs and publishes atomically. Failure
removes temporary output; no partial JSONL is exposed. Output into immutable legacy,
frozen schema or Android source locations is prohibited. Keep any future private
intake under ignored `.local-evidence/nlu_v1/`; no personal payload belongs in Git.
Errors expose fixed codes, not utterance text or JSON Schema instance dumps.

```powershell
python ml/nlu_v1/ingestion/common.py --check
python ml/nlu_v1/ingestion/check_mappings.py
python -m unittest discover -s ml/nlu_v1/tests -v
python ml/nlu_v1/ingestion/ingest.py --help
```

For a separately approved local artifact, supply `--source`, `--input`, a new
`--output` and `--approval`; HWU/MTOP partitioned members also need
`--original-split`. Temporal and legacy per-row reviews belong to the trusted
approval sidecar. Validate quarantine output with
`python ml/nlu_v1/ingestion/common.py --validate-jsonl path/to/quarantine.jsonl`.
Frozen training rows use the separate `contract.py --validate-jsonl` command.

## Verification

All five mapper fixtures preserve text, source ID/split, reconstruct slot spans and
pass canonical semantic validation. Each mapper has a protected-partition test
that denies training even with an approval fixture. Additional tests cover nested
MTOP/temporal spans, CLINC scope, legacy reinterpretation/quarantine, changed
approvals, Unicode, hashes, malformed input, native reader layouts, private-error
redaction, atomic output and duplicate records.

52 NLU tests pass locally (24 new mapper/intake tests, 28 earlier tests). Frozen
schema, registry, mapping inventories, retirement integrity, six retirement tests
and whitespace checks pass. CI is wired to run ingestion schema and mapping checks.
No dataset utterance payloads, private legacy records, training corpus or model
were loaded/generated. Only public code/label metadata was fetched for inventories;
its URLs, immutable revisions and raw-byte hashes are recorded. Remote CI,
real-source intake parity and Android/device tests were not run. Native HWU/MTOP
coverage and source approval remain explicit blockers to full real-data intake.
