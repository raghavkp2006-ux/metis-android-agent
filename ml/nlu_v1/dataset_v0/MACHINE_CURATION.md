# Plan 07 machine curation

The machine policy prepares review evidence; it does not grant human approval.
Frozen schema versions, canonical review transitions and original candidate/source
snapshots remain unchanged. Policy and engine snapshots are bound by SHA-256 in
each append-only run manifest.

Run from the repository root:

```powershell
python -m ml.nlu_v1.dataset_v0.machine_curation --check
python -m ml.nlu_v1.dataset_v0.machine_curation --output ml/nlu_v1/datasets/quarantine/NEW_RUN_NAME
python -m ml.nlu_v1.dataset_v0.machine_curation --verify ml/nlu_v1/datasets/quarantine/NEW_RUN_NAME
```

Use a fresh output directory. The default input is the immutable 2,000-row public
candidate pool. Derived machine annotations are not a replacement for native
source verification. Raw text, queues and source payloads remain ignored by Git.

## Evidence and review

Family assignment requires one deterministic rule; competing matches quarantine
the row. Scenario and parent groups derive from source identity, source intent and
typed source skeletons. Supplemental rules are separately versioned. Whole-request
grammars bind semantic checks to verified native intents. Context dispositions
change only when completeness is proved; unresolved references and ambiguous
times remain unresolved. Original text and slot spans are preserved.

MASSIVE and CLINC rows are matched against native training records with verified
artifact and license hashes. Public privacy binding additionally requires a
closed, nonpersonal utterance proof. Source identity alone never makes a row
PUBLIC. Binding privacy does not grant publication rights or intake approval.
Private/restricted classifications are preserved.

Canonical rows stay QUARANTINED. A separate AUTO_CURATED_READY_FOR_REVIEW overlay
records machine readiness, with explicit machine actors and no HUMAN_REVIEWED or
APPROVED events. Plan 04 still requires human quarantine release and approval.
The edge-case queue groups reasons, intents, families and slot signatures, with
sample text and every affected row ID. Reviewers must expand the affected rows;
samples do not authorize blanket approval of unresolved semantics.

## Verified local result

Run `plan07_machine_curation_005` assigns families/scenarios to 1,970 of 2,000
rows and binds privacy evidence for 55. It proves 32 rows ready for the required
human step; 1,968 edge rows form 425 review tickets. There are zero disposition
corrections, zero approved rows and zero machine-created human review events.
All 2,000 originals remain quarantined, with zero rejected rows.

The run audits another 25 quarantined candidates from the immutable Plan 05
generation snapshot, including parent lineage. It prepares 32 native public
training references for potential additions and seven structure-first template
proposals. These are not approved training data. Public additions are considered
before synthetic proposals; new generation requires versioned templates and
source/rights approval rather than editing prior generation history.

Missing groups remain TASK, MEMORY, PROMISE, GOAL/PROJECT,
PLANNING/FOCUS/HABIT and PREFERENCE/KNOWLEDGE. Coverage artifacts enumerate every
domain, action, slot, disposition, family and scenario, including zero counts.

Plan 04 duplicate and Plan 06 grouping/leakage checks run over originals and
controlled additions. Missing approved embeddings and incomplete grouping are
reported as blockers. An embedding artifact requires a separate approved,
hash-bound model/tokenizer/config evidence sidecar; synthetic vectors cannot
satisfy this audit. No training/development/calibration/selection partitions are
prepared while approval, coverage, privacy/rights and leakage gates remain open.
Final-independent data stays reserved and uncollected. Dataset v0 is not frozen,
Plan 07 is incomplete and Plan 08 cannot start. No model is trained.

The manifest binds all requested curation, queue, coverage and quarantine
artifacts, the policy and exact engine snapshot. Verification checks the artifact
set, hashes, canonical schemas, counts and absence of invented human approvals.
The tracked aggregate report contains no utterance samples.
