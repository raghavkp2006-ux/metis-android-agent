# METIS NLU v1 frozen schema

Version: `nlu_v1_schema_001`. Status: FROZEN. Approval basis: the user's Plan 01
specifies the exact domain, action, slot and disposition catalogs and requests
their freeze. Freeze date: 2026-10-16. `freeze.json` records SHA-256 hashes of the
catalogs, schemas and these annotation rules, with CRLF normalized to LF.
`python ml/nlu_v1/contract.py --check` verifies synchronization and freeze integrity.
Never edit this version in place after freeze; introduce a new schema version
and an explicit migration. Numeric model label IDs will be separately recorded
in model manifests; YAML catalog order is stable.

## Meaning

The four YAML catalogs contain exactly the approved labels: 19 domains, 16
actions, 20 slot types and five dispositions. BIO tags comprise `O` plus `B-` and
`I-` for each slot type (41 tags). Internal Safety, Verification, Decision and
Context agents are not domains. Domain and action are independent semantic
dimensions, not a renamed AgentIntent enum. No capability or legal domain/action
matrix is inferred from the label catalog; deterministic validation checks
supported operations and current capabilities later.

Disposition describes the annotated semantic request:

| Label | Meaning |
| --- | --- |
| SUPPORTED_COMPLETE | Supported semantic request with explicit required arguments; no execution authority |
| SUPPORTED_NEEDS_CONTEXT | Missing or ambiguous arguments or references require context/clarification |
| SUPPORTED_NEEDS_CONFIRMATION | Annotation or prediction anticipates confirmation; Safety independently decides |
| KNOWN_UNSUPPORTED | Recognized but unsupported operation, or a non-actionable negated, quoted, hypothetical or informational command |
| OUT_OF_DOMAIN | Input outside the METIS semantic scope |

Use the outer request: reminder content mentioning a call is REMINDER/CREATE,
not COMMUNICATION/CALL. Never infer an active command from quoted/negated text.
Use KNOWN_UNSUPPORTED for non-actionable commands rather than any SUPPORTED
disposition; retain named domain/action if unambiguous, otherwise UNKNOWN/NONE.
Multiple unresolved active requests use KNOWN_UNSUPPORTED until a multi-request
contract exists. Missing arguments for a known single request use
SUPPORTED_NEEDS_CONTEXT and remain absent; do not invent values.

OUT_OF_DOMAIN requires UNKNOWN/NONE and `ood: true`. All other dispositions
require `ood: false`. UNKNOWN requires NONE and either KNOWN_UNSUPPORTED or
OUT_OF_DOMAIN. A SUPPORTED disposition requires a named domain and non-NONE
action. OOD is a boolean, not a new label catalog. Confidence, abstention thresholds
and execution eligibility are not encoded as new dispositions.

ML extracts “next Friday” as DATE; deterministic date code resolves it using
the request clock/zone. ML extracts “him” and “that meeting” as REFERENCE;
Context Agent resolves them. NLU never returns normalized dates, contact/entity
IDs, permissions, acceptance tokens or executable payloads. SUPPORTED_COMPLETE
does not waive confirmation. Safety policy is the source of confirmation
requirements, even when NLU predicts a different disposition.

## JSONL representation

`row.schema.json` defines one strict UTF-8 JSON object per line. Required fields:
schema_version, id, text, language, tokens, bio_tags, meaning, split, family_id,
scenario_id and provenance. `output.schema.json` is the meaning-only envelope:
schema_version, text, tokens, bio_tags and meaning. Additional fields are rejected
at every object boundary. `meaning` contains domain, action, slots, disposition
and boolean ood. No training rows are created by this schema implementation.

Each token has start, end and text. Each slot has type, start, end and text.
Offsets are UTF-16 code units `[start, end)`, matching Android Kotlin String.
Both must match original text exactly and avoid surrogate interiors. Tokens
are ordered, nonoverlapping, cover all nonwhitespace input, and may leave only
whitespace gaps. One BIO tag is required per token. An I-X tag must follow B-X
or I-X; B-X starts a new span, including adjacent spans of the same type. Slot
annotations must exactly equal BIO-decoded spans in order, including interior
spaces/punctuation. Slots are flat, nonoverlapping and limited to 64 per input.
SUBJECT covers content as a whole; nested entities inside it are not also tagged.
DATE_RANGE takes precedence over nested dates for an explicit range. REFERENCE
takes precedence over guessing a PERSON or EVENT for unresolved mentions.

Text is nonblank, limited to 4,000 UTF-16 units; original case and punctuation
are preserved. Schema tokens are annotation tokens, independent of model subwords.
Future mappers must record reproducible tokenization; training must align
subwords to these offsets and reject truncation that loses annotated slots.

Provenance requires source ID/version/record/split/hash, mapper version,
license/approval IDs, source class, privacy, author/reviewer IDs, nullable generator
ID and parent IDs. IDs must be nonpersonal opaque references. These are evidence
references, not verified permissions. Registry approval, privacy screening,
source-byte verification and cross-row duplicate/family/scenario/lineage audits
are later gates. Passing this validator never establishes training eligibility.

TRAIN fits gradients; DEVELOPMENT supports error analysis; CALIBRATION fits score
calibration; SELECTION selects models/thresholds; FINAL_INDEPENDENT_TEST is frozen
independent assessment only. Final rows require independent-evaluation provenance,
no generator, different author/reviewer IDs and publishable privacy classification.
Protected evaluation never becomes training data. Historical V4–V16 evidence
remains immutable and no old model is reused.

## Validation

```powershell
python -m pip install -r ml/nlu_v1/requirements.txt
python ml/nlu_v1/contract.py --check
python -m unittest discover -s ml/nlu_v1/tests -v
python ml/nlu_v1/contract.py --validate-jsonl path/to/candidate.jsonl
```

JSON Schema checks structure and enumerated labels. The Python validator also
checks BIO transitions, UTF-16 bounds, text/span agreement, token coverage,
decoded-slot agreement and row-level provenance constraints. JSONL validation
rejects empty files, blank rows, duplicate JSON keys and malformed records;
errors report line numbers/types without echoing private content.

Plan 01 stops at schema freeze. No dataset ingestion, generation or training is
part of this change. Frozen labels do not establish model quality or Android
performance and do not enable any production action.
