# Human template review policy 002

Governance version: `nlu_v1_synthetic_review_policy_002`. This is a prospective,
explicitly owner-requested extension. It changes no historical Plan04 schema,
review evidence, source policy, synthetic source, template freeze or signature.

Human semantic authority may be attached to an exact template rather than a
claim of manual review of every derived row. The authority binds the finite
pattern set, finite fictional binding lists, fixed domain/action/disposition,
allowed slots, family, scenario, conservative semantic parent, generator version
and seed, and exact policy/catalog/renderer/validator hashes. A changed template,
binding list, seed, generator, policy or version requires a new review. Approval
does not extend to future templates or an unbounded paraphrase generator.

The source proposal is `metis_internal_coverage_v1`, catalog version
`nlu_v1_internal_coverage_templates_001`. It is newly project-authored, uses only
fictional fixtures, and has no corpus, connector, user-history or private-data
input. RESTRICTED and publication_allowed=false remain mandatory. This packet
does not create source intake, rights clearance, training or canonical approval.
The old425 synthetic rows remain excluded and are not generation inputs.

## Proposed review scope

There are26 template review targets, with a provisional future budget of165 rows:

| Capability group | Template targets | Future budget hint |
|---|---:|---:|
| MEMORY |4|30|
| PROMISE |4|30|
| GOAL/PROJECT |4|30|
| PLANNING/FOCUS/HABIT |4|30|
| TASK |3|12|
| CALENDAR |1|7|
| PERSON |2|9|
| COMMUNICATION/NAVIGATION |1|5|
| PREFERENCE/KNOWLEDGE/etc. |3|12|

The four missing groups each use one complete, one context, one unsupported and
one near-OOD target. Fixed labels prevent combining those four decisions into
one target. The smaller groups receive only targets needed for their measured
composition deficits. Combined-domain groups include GOAL and PROJECT, and
PLANNING, FOCUS and HABIT. Every pattern alternative is displayed, not hidden
behind three samples. The three renderings per template are illustrations only;
they have no dataset row IDs, row history or canonical provenance. No dataset
rows or JSONL generation output are produced.

Budgets are proposals, not guaranteed coverage passes. Source-skeleton and cosine
checks may reject otherwise valid variants. Family/scenario/seed/parent grouping
can constrain later split sizes. Future generation must not weaken those checks
to satisfy this budget. Related context/unsupported/OOD siblings share a
conservative capability-level parent and cannot cross scientific splits.

## Human decisions and commands

```powershell
python -m ml.nlu_v1.dataset_v0.template_review review
```

The same command resumes its saved cursor. Keys1/2/3 record APPROVE/REJECT/CHANGE;
N/P navigate; S saves; Q saves and quits. CHANGE prompts for requested edits and
does not edit or approve the actual template. All targets start PENDING.

The ignored local review file is
`ml/nlu_v1/datasets/quarantine/plan07-template-review-002/TEMPLATE_DECISIONS.md`.
Exact pending bound decision JSON lives in its `decisions/` folder. Human edits
may also be made to the Decision column, followed by:

```powershell
python -m ml.nlu_v1.dataset_v0.template_review sync
python -m ml.nlu_v1.dataset_v0.template_review check
```

The packet itself includes the exact signing/verification commands. Signing
requires a real enrolled reviewer, their existing external Ed25519 private key,
actual UTC review time and real reason. It reuses the existing
`configs/group_reviewers.yaml`, `semantic_groups.signed_bytes` and Ed25519 trust
store. No keys, identities, signatures, timestamps or decisions are invented.
New signed batches are append-only. REJECT and CHANGE cannot authorize
inheritance, and edits after signing supersede the earlier decision.

## Inheritance gate and limits

`verify_inheritance` is a verification-only gate. An exact human-approved template
must have a valid current signature. A future managed row must validate under
the native schema and reconstruct exactly from the fixed reviewed recipe; its
provenance must bind template and approval hashes, ordinal, source/version,
seed/generator, fixed labels, slots, family/scenario and semantic parent. UTF-16
spans, tokens and BIO labels must match the deterministic renderer.

It also requires current native source/provenance/privacy/family gates, a bound
complete corpus dedupe report with no row quarantine or similarity finding, no
protected evaluation members, and a fresh grouped leakage pass with all four
development partitions represented. It returns truthful template-level semantic
authority only. It creates no HUMAN_REVIEWED/APPROVED events or canonical state
transitions and cannot grant privacy, rights, intake, training or public export.

A later canonical integration must project genuine reviewed-template authority
into a versioned truthful review history; it must not impersonate individual
manual review or patch old Plan04 records. This task prepares only the templates
and review infrastructure. No generation, dataset approval, freeze, training or
Plan08 work occurs here.
