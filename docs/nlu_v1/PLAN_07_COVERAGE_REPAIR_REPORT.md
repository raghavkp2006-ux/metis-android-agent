# Plan07 coverage-repair search outcome

This is a read-only, source-training-only coverage assessment. No mapping, row, privacy state, authority record, signature, existing exclusion or historical evidence was changed. No model, protected split, synthetic row or Dataset v0 build was used.

## Current coverage

Every capability requires at least24 rows,3 families,4 scenarios,8 supported-complete examples,2 needs-context examples,2 known-unsupported examples,2 actions and1 slot type. Six capability groups also require2 origin-bound near-OOD examples. These are separate requirements: a large total row count alone is not coverage. All canonical APPROVED counts are0; eligibility here means the1762 retained curation candidates, not approved training-ready records.

| Capability | Eligible rows | APPROVED rows | Families | Scenarios | Complete | Context | Unsupported | Actions | Slot types |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| CALENDAR | 643 | 0 | 9 | 643 | 1 | 636 | 6 | 4 | 9 |
| COMMUNICATION_NAVIGATION | 435 | 0 | 3 | 435 | 3 | 430 | 2 | 3 | 9 |
| GOAL_PROJECT | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| MEMORY | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| PERSON | 107 | 0 | 2 | 107 | 1 | 106 | 0 | 2 | 7 |
| PLANNING_FOCUS_HABIT | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| PREFERENCE_KNOWLEDGE_ETC | 267 | 0 | 2 | 267 | 0 | 0 | 24 | 1 | 7 |
| PROMISE | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| REMINDER_ALARM_TIMER | 276 | 0 | 4 | 276 | 21 | 252 | 3 | 4 | 7 |
| TASK | 34 | 0 | 2 | 34 | 0 | 0 | 34 | 2 | 0 |

## Exact nonzero deficits

| Capability | Deficit quantities |
|---|---|
| CALENDAR | supported_complete: 7 |
| COMMUNICATION_NAVIGATION | supported_complete: 5 |
| GOAL_PROJECT | actions: 2; families: 3; known_unsupported: 2; near_ood: 2; needs_context: 2; rows: 24; scenarios: 4; slot_types: 1; supported_complete: 8 |
| MEMORY | actions: 2; families: 3; known_unsupported: 2; near_ood: 2; needs_context: 2; rows: 24; scenarios: 4; slot_types: 1; supported_complete: 8 |
| PERSON | families: 1; known_unsupported: 2; supported_complete: 7 |
| PLANNING_FOCUS_HABIT | actions: 2; families: 3; known_unsupported: 2; near_ood: 2; needs_context: 2; rows: 24; scenarios: 4; slot_types: 1; supported_complete: 8 |
| PREFERENCE_KNOWLEDGE_ETC | actions: 1; families: 1; near_ood: 2; needs_context: 2; supported_complete: 8 |
| PROMISE | actions: 2; families: 3; known_unsupported: 2; near_ood: 2; needs_context: 2; rows: 24; scenarios: 4; slot_types: 1; supported_complete: 8 |
| REMINDER_ALARM_TIMER | None |
| TASK | families: 1; near_ood: 2; needs_context: 2; slot_types: 1; supported_complete: 8 |

## Source search and meaning boundaries

All11514 MASSIVE train and15100 CLINC train/oos_train entries in the verified local source lookups were searched (26614 total). Protected validation/test/final rows were not searched. The complete approved source-intent mapping inventories were also inspected. Search-only access to the broader source corpus does not silently extend exact-member policy003 privacy/intake/training authority.

Broad lexical/phrase retrieval found514 MEMORY cues and38 PROMISE cues. Cues are search aids, not classifiers, schema annotations, admissions, consent or authority records. The retrieved PROMISE cases were screened as32 financial/statutory obligation requests,2 fraud-committed requests,1 social idiom,1 dialogue agreement,1 reservation confirmation and1 prefix false positive. None supplied a validated personal-promise request. Explicit promise/pledge/vow/commitment and reported-would/agreed-to patterns supplied no positive request in this scan. The full raw retrieval evidence is gitignored and local; no utterance is copied into this report.

Some MEMORY-like note/recall wording exists, including broad/ambiguous source-intent cases. We do not claim the corpus contains no conceivable MEMORY expression. No new fully governed MEMORY row was validated/admitted, and the existing mappings do not supply MEMORY/PROMISE labels. Potential note, reminder, list, dialogue-repeat, preference and factoid boundaries must not be relabeled wholesale to satisfy quotas. Keywords alone are insufficient.

This search is not a semantic proof that every imaginable paraphrase is absent. It establishes the current usable/validated counts and records the inspected retrieval evidence. Since the required PROMISE minimum cannot be met by any validated example identified in this allowed-material search, the task stop condition is applied. Other potential additions are not forced or staged as if they could produce a complete dataset.

## Decisive capability deficit

| Capability | Required minimum | Available validated eligible rows | Deficit |
|---|---:|---:|---:|
| PROMISE |24|0|24|
| MEMORY |24|0|24|

The single smallest next blocker is PROMISE:24 additional legitimate, governed examples are required, and0 were identified as usable. Counts here refer to final eligible/validated rows, not to the absence of raw language cues. Existing sources and mapping evidence do not justify fabricating mappings or restoring excluded synthetic data.

## Outcome and checks

New rows added=0. Newly excluded during this repair=0. Earlier727 backlog exclusions remain unchanged. The eligible pool remains1762; it remains a curation pool, not a frozen Dataset v0. Coverage before and after is byte-equivalent. Grouped planning was rerun and again has no overlap findings, but only TRAIN1724 and DEVELOPMENT38 are nonempty in the preview; CALIBRATION and SELECTION are empty. It is not a valid four-partition dataset.

Final Dataset v0 rows=0. Final TRAIN=0, DEVELOPMENT=0, CALIBRATION=0, SELECTION=0; no scientific partition files were generated. Coverage=false, dataset-level leakage=false, frozen=false, Plan07complete=false. There was no new-row schema/similarity pipeline to run because no additions were admitted. Existing rows were schema-validated, the existing complete full-public cosine report was verified against unchanged input facts, and current source/hash/provenance evidence was reverified. No new inference or redundant cosine run is claimed.

Local evidence: `ml/nlu_v1/datasets/quarantine/plan07-coverage-repair-001/`. It contains before/after coverage, full local training-source cue records with exact native identities/content hashes, source mapping inventory, grouping preview and a hash-bound manifest. Existing source authorities are referenced rather than changed.

Run manifest SHA-256: `c9d55302aff86e33a939f6f20313e3dcbd6d3dd555105883a3b236d3f26b4b14`. The complete NLU test suite is run for this change; the final response reports the result.
