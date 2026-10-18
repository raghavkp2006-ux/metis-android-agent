# Plan 07 approved-template supplement audit

Plan 07 remains incomplete. Dataset v0 is not frozen, and no model training or
Plan 08 work was performed.

The smallest remaining capability blocker is **two SUPPORTED_COMPLETE CALENDAR
examples**: six survive against the required eight. All permitted ordinals of
the approved recipes have been used. Additional patterns, binding schedules or
template versions require their own bound template review; the generator does
not extend the reviewed space to manufacture coverage.

## Execution and evidence

Run locally with:

```powershell
python -m ml.nlu_v1.dataset_v0.approved_supplement
```

The append-only, gitignored evidence directory is
`ml/nlu_v1/datasets/quarantine/plan07-approved-template-generation-002/`.
Its `manifest.json` binds the input hashes and every output artifact, including
the generated and retained candidates, exclusions, exact coverage, native
canonical findings, complete duplicate report, real embeddings/receipt, group
registry and planning-only split assignments. Reopening a completed run verifies
its bound inputs and output hashes rather than approving it again.

The 26 Ed25519 decisions in `template-human-001` were verified against the
unchanged policy002, template versions, fictional bindings, fixed seeds and
renderer/validator code. Exactly 165 unique row IDs were rendered within the
reviewed finite ordinal limits. Every generated prediction passed native schema,
UTF-16 span, BIO, label, disposition/OOD and exact recipe checks. All generated
rows remain RESTRICTED with publication disallowed. The authority is explicitly
template-level; no individual-row human review or canonical approval was invented.

The first audit execution, `plan07-approved-template-generation-001`, is retained
as evidence. Execution002 separates the actual canonical-registry digest from
the synthetic-policy digest in the candidate provenance. It creates no new
utterances or row IDs. Its embeddings are reused only after checking the real
execution receipt, pinned offline model/configuration and complete ordered
ID/text membership. The native duplicate audit is rerun against the corrected
candidate facts. Raw utterances and vectors remain in the ignored local area.

## Results

| Check | Result |
|---|---|
| Signed templates verified | 26/26 |
| Public-source candidates submitted / retained | 1,762 / 1,762 |
| Unique synthetic candidates rendered / schema-recipe valid | 165 / 165 |
| Synthetic candidates excluded by similarity | 26 |
| Synthetic candidates retained | 139 |
| Combined retained candidates | 1,901 |
| Embedding-similarity pairs in the submitted pool | 18 |
| Exact/normalized/source-ID/skeleton/row-ID collisions | 0 |
| Conflicting endpoints retained | 0 |
| Rows approved or released | 0 |
| Dataset v0 rows / materialized partition rows | 0 / 0 |

The real offline all-MiniLM checkpoint was used for similarity auditing only.
Deterministic repeat inference matched exactly. The existing cosine threshold
and all-endpoints quarantine rule were retained; no artificial vectors or
similarity pass were substituted. All rejected rows remain in the evidence.

| Capability | Retained rows / minimum | Other deficient minimum |
|---|---|---|
| TASK | 46 / 24 | None |
| CALENDAR | 648 / 24 | Complete examples: 6 / 8 |
| MEMORY | 28 / 24 | None |
| PROMISE | 28 / 24 | None |
| PERSON | 116 / 24 | None |
| GOAL/PROJECT | 14 / 24 | Complete examples: 7 / 8 |
| REMINDER/ALARM/TIMER | 276 / 24 | None |
| PLANNING/FOCUS/HABIT | 26 / 24 | None |
| COMMUNICATION/NAVIGATION | 440 / 24 | None |
| PREFERENCE/KNOWLEDGE/etc. | 289 / 24 | None |

These are candidate coverage counts, not approved or frozen dataset counts.
Origin-bound OOD examples can contribute to more than one policy grouping;
capability totals are consequently not an additive partition of the corpus.

## Gates still closed

All retained public candidates retain their verified source-level privacy,
intake and conditional training evidence. The 139 retained internal candidates
still have native `SOURCE_NOT_APPROVED_FOR_CANONICAL_USE` and
`SOURCE_ARTIFACT_UNVERIFIED` findings. The new source is absent from the old
canonical source registry, and the implementation does not fabricate its native
intake approval ID. Semantic template approval is not intake or release authority.
All native canonical findings remain in `canonical_findings.json`.

The grouped preview has zero overlap findings across every required key,
including family, scenario, source ID, source actor, seed and parent lineage.
It assigns 1,724 public rows to TRAIN and 38 to DEVELOPMENT; the 139 internal rows
remain INELIGIBLE under the native source permission gate. CALIBRATION and
SELECTION are empty. This is a planning artifact, not four valid dataset splits
or a completed dataset leakage gate. No partition files were materialized.

The 425 historical synthetic rows remain excluded. No protected source test,
final-independent data, archived ML evidence or production Android source was
consumed or modified. All original review decisions and signed template versions
remain unchanged.

Verification: **299 NLU tests pass**, including 20 supplement execution tests.
Frozen schema001, schema002 extension, managed-data contract, source-registry and
legacy retirement checks pass, with six additional retirement tests passing.
