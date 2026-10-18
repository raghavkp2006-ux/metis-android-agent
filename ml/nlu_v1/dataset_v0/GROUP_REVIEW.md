# Exact semantic groups and exception review

This Plan 07 continuation prepares semantic decisions for an external human. It
does not create human approvals, approve source intake/publication rights, train
models, assign scientific partitions or freeze a dataset. The existing Plan 04
state machine and all frozen schema/template history remain unchanged.

## Workflow

`coverage_additions.py` scans the already verified public training acquisitions
first, using the existing frozen mapping decisions. Unsupported or unmapped
annotations produce hashed diagnostics; they are never forced into positive
classes. Registered sources without verified local intake remain unavailable.
Public acquisition and registration are distinct from intake approval.

The additive `coverage_templates_001.yaml` extension uses the existing Plan 05
renderer, canonical envelope, fictional bindings and span/BIO validators. It
provides read/write variants, reference ambiguity, explicit unsupported publication
boundaries and related external-purchase OOD cases for ten domains in six missing
groups. Four structural seeds produce 400 candidates. Source rights remain
internal and pending; every row is quarantined and unassigned. Parent records are
retained. Prior frozen templates and generated batches are unchanged.

`dataset_v0_coverage_policy.yaml` imposes minimum rows, families, scenarios,
complete/context/unsupported cases, actions and slots. Relevant near-OOD cases are
counted by explicit generation origin rather than incorrectly labeling OOD as an
in-domain request. Candidate and APPROVED coverage are reported separately with
targets, deficits and source counts. Passing candidate coverage is insufficient
for freezing.

`semantic_groups.py` verifies source/template identity, schema/span/BIO integrity,
family/scenario bindings and whole-request grammar. Only equivalent requests can
share a group. The group identity includes source/version/split/intent, exact
mapping versions, domain/action/disposition/OOD, ordered slot-type pattern, family,
scenario rule, privacy/license state and hard-negative behavior. Different audit
states are separated. Whole grammars may correct provisional context labels when
arguments are proven complete; references, ambiguous times and vague deletion
targets remain exceptions. Safety policy still determines confirmation.

Every group binds exact row IDs and canonical JSON content hashes, current review
state hashes, family/scenario definitions, per-member privacy/license evidence,
and code/config/mapping hashes. Summary/group artifacts contain no utterances.
Exceptions contain hashed references and reason codes; compact exception tickets
locate affected records but do not authorize a blanket decision. Conservative
exceptions cannot be reduced solely to meet a ticket-count goal.

## External human approval

`group_approval_template.json` contains PENDING unsigned proposals. It is not an
approval. The external reviewer fills decision, reviewer ID, UTC timestamp and
reason, then signs the canonical payload with their own Ed25519 private key.
`signed_bytes()` specifies exact serialization. Private keys stay outside this
workflow. The checked-in reviewer registry is deliberately empty: enroll a real
external human's public key through an authorized registry update before issuing
their signing template. No machine identity or arbitrary reviewer string suffices.

Verification requires the enrolled public key, an exact group/member/content/state
match and unchanged code/config hashes. One signature covers the complete immutable
group. Changed membership, semantics, evidence or state invalidates it. Fixture
keys in unit tests are not enrolled reviewers or dataset approval evidence.

```powershell
python -m ml.nlu_v1.dataset_v0.group_review --verify RUN_DIRECTORY --approval EXTERNAL_SIGNED_APPROVAL.json
```

A verified semantic signature produces a receipt with zero canonical approvals.
The separate `apply_canonical_approval()` adapter can project one real group
approval into the existing per-row Plan 04 transitions only after canonical
source/intake/privacy/rights gates and the exact complete corpus audit pass. It
also requires the human's signed `canonical_release_authorized: true` decision;
that flag defaults to false and cannot be inferred from a semantic signature. It
refuses duplicate/protected members and stale audit content. It calls the existing
QUARANTINED → PENDING → AUTO_VALIDATED → HUMAN_REVIEWED → APPROVED transitions;
it cannot skip HUMAN_REVIEWED or infer intake/publication approvals. No approval
adapter is invoked by default preparation.

## Offline similarity evidence

The audit uses the publisher's
[all-MiniLM-L6-v2 checkpoint](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/tree/10dbd2f06a8baf40ad285b037edda610c1b9a57c)
at immutable revision `10dbd2f06a8baf40ad285b037edda610c1b9a57c`.
The model's LFS SHA-256 and tokenizer/config/README Git blob hashes are pinned in
`similarity_audit.py`. Its model card declares Apache-2.0. Downloading the checkpoint
does not approve any METIS row or dataset license.

```powershell
python -m pip install -r ml/nlu_v1/dataset_v0/requirements-audit.txt
python -m ml.nlu_v1.dataset_v0.prepare_review_stage --output ml/nlu_v1/datasets/quarantine/NEW_STAGE
python -m ml.nlu_v1.dataset_v0.similarity_audit --download
python -m ml.nlu_v1.dataset_v0.similarity_audit --input FULL_CANDIDATES.jsonl --output NEW_EMBEDDING_DIRECTORY
python -m ml.nlu_v1.dataset_v0.group_review --stage STAGE_DIRECTORY --embeddings EMBEDDING_DIRECTORY --output ml/nlu_v1/datasets/quarantine/NEW_GROUP_RUN
python -m ml.nlu_v1.dataset_v0.group_review --verify ml/nlu_v1/datasets/quarantine/NEW_GROUP_RUN
```

Only the explicit download command accesses the network. Inference/verification
use cached hash-verified files. CPU ONNX Runtime runs sequentially with one thread,
fixed package versions, disabled graph optimization and 256-token truncation.
Attention-masked mean pooling and L2 normalization follow the checkpoint's pooling
configuration. Every batch is inferred twice and must match exactly. Each vector
binds row ID and exact text hash; the receipt binds runtime/config/checkpoint hashes
and the complete ordered collection. The receipt is a machine execution record
authorized by the user's Part H request, not a forged human approval sidecar.

The existing Plan 04 cosine audit runs over the entire 2,489-row collection:
2,000 originals, 64 public additions, 25 retained Plan 05 lineage candidates and
400 new controlled candidates. Matches quarantine every implicated row; none are
silently deleted. Plan 06 grouping/lineage checks report unresolved groups and
split readiness independently of embedding execution completeness.

All raw records, vectors, review queues and full manifests remain ignored by Git.
The group-run manifest is written last and binds exact artifacts and code snapshots.
Only aggregate results belong in tracked documentation. No final-independent rows
are generated or treated as available.

## Verified local outcome

Run `plan07-semantic-groups-002` covers 2,489 quarantined candidates. Its 71 semantic
groups cover 460 rows; 2,029 exceptions are indexed by 239 compact tickets. Within
the original 2,000 rows, 21 groups cover 60 rows and 229 exception tickets cover
1,940 rows. The original ticket count was 425. Exception tickets are an index for
individual decisions, not bulk semantic approvals; reducing ticket count does not
erase the unresolved human workload.

All six originally missing capability groups meet candidate minima through public
proposals and controlled additions. CALENDAR, PERSON and COMMUNICATION/NAVIGATION
still fail candidate minima. All required groups lack approved coverage. The real
embedding audit completes with 244 cosine-matching pairs; no matched row is deleted.
Family/scenario grouping, duplicate review, source intake, publication rights and
external human approvals remain unresolved. There are zero approved rows and no
scientific partitions, fabricated independent-final records, dataset freeze or
model training. The aggregate report records 173 passing NLU tests and six passing
retirement tests, unchanged production sources and disabled production ML.
