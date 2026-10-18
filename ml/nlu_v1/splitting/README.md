# Grouped partitions and leakage gates (Plan 06)

The splitter is implemented; a production split is not yet built. The Plan 06
audit covered 41 quarantined synthetic candidates and no approved corpus or fresh independent
evaluation intake. The [readiness report](../../../../docs/nlu_v1/PLAN_06_SPLIT_READINESS.json)
must not be interpreted as a passed dataset validation. No five-file dataset or
final data freeze is emitted from ineligible inputs or an empty collection.
Plan 07 subsequently added a [2,000-row public candidate pool](../dataset_v0/README.md),
also unapproved and quarantined; it does not change the earlier audit evidence.

```powershell
python -m ml.nlu_v1.splitting.split --check
python -m unittest discover -s ml/nlu_v1/tests -v
python -m ml.nlu_v1.splitting.split --preview ml/nlu_v1/datasets/quarantine/plan05-seed17.jsonl
# After reviewed eligible corpus and real embedding artifacts exist:
python -m ml.nlu_v1.splitting.split --build path/to/approved.jsonl --groups path/to/groups.json --embeddings path/to/embeddings.json --output ml/nlu_v1/datasets/frozen/new_split_id
python -m ml.nlu_v1.splitting.split --verify ml/nlu_v1/datasets/frozen/new_split_id
```

The strict CLI creates `train.jsonl`, `development.jsonl`, `calibration.jsonl`,
`selection.jsonl` and `final_independent.jsonl` only after all build gates pass.
Every scientific partition must be nonempty before completion. Local raw and
partition payloads stay ignored by Git. No public upload is performed.

## Group first

Union-find builds connected components from semantic family, scenario, scoped
source ID, semantic parent, generation-parent edges, available source speaker/user
identifiers, generator/seed group, normalized text and source skeleton. Any
transitive connection stays together. Family membership uses the semantic family
ID, not the broad domain/action label. This deliberately tests generalization to
held-out semantic families; per-label coverage may be limited and must be assessed
in later dataset/evaluation work.

Speaker/user fields include the mappers' `worker_id` and `userid`. Aliases are
conservatively grouped within the source registry's shared leakage namespace,
where present, otherwise dataset/version. Missing actor metadata is reported,
never invented. Actor values are hashed in findings and excluded from partition
projections. CLINC array indices are split-local under the existing source-ID
policy; row zero in two distinct source splits is not one source record. Other
source IDs use their registered global scope.

Deterministic largest-group-first allocation minimizes squared row-count error
against approximately 70/10/7.5/7.5/5, with hash-based seeded tie breaking. Input
order does not affect assignments. Final data is independently collected; it is
never a random 5% slice of public train or synthetic rows. The remaining four
targets are renormalized over the eligible tuning pool. Large components may
prevent target ratios; reports show component sizes and actual counts instead of
breaking groups. The current 41 candidates form one component because the same
generation seed links all their families.

Already assigned canonical partitions are locks. Conflicting connected locks fail.
Partition membership is a separately hashed overlay: reviewed canonical records,
privacy evidence and review chains are never mutated to assign a split.
[Partition row schema](partition_row.schema.json) preserves exact text, UTF-16
tokens/slots/BIO, labels, source identity and license, plus canonical facts/review
hashes. Worker IDs, free-form source annotations and human review identities are
not exported in these public-safe projections.

## Official public tests

Official protected source splits are recognized from the source registry, including
MASSIVE test, MTOP test, CLINC OOS test and deferred SGD-X test. These rows keep
their original source IDs, splits, text and annotations. They go to the separate
`public_benchmarks.jsonl`, never the five METIS tuning/final files. Connected
training-source neighbors are excluded from tuning in `heldout_neighbors.jsonl`.
If an old partition lock conflicts, the build fails instead of moving tests.

Exact/normalized duplicates across official dev/test buckets are reported even
though both are external protected material. The code preserves their rows and
fails validation; it never silently edits an official benchmark. Family overlap
between official dev/test collections is inherent to their external design; the
whole connected family is excluded from METIS tuning. Those official collections
remain separate in source provenance and cannot become fresh independent data.

The current workspace has no downloaded official test rows. Preservation is
verified with invented fixtures and registry policy, not a claim that a real
benchmark snapshot has already been imported or audited.

## Fail-closed validation

All rows must pass the managed canonical schema, exact UTF-16 span/BIO checks,
review chain and provenance/license gates. Unicode is checked recursively for
unpaired surrogates before hashing; invalid UTF-8, duplicate JSON keys and nonfinite
JSON numbers fail. Unknown labels/dispositions and malformed rows fail.

Builds reject every family/scenario/source-ID/parent/actor/seed/normalized/skeleton
edge crossing a partition. PUBLIC privacy, bound publication rights and APPROVED
review are required for these outputs. Private/restricted/unknown rows are not
silently filtered. Plan 04 dedupe is rerun with complete text-bound embedding
artifacts; missing/stale embeddings and any unresolved similarity quarantine fail.
This exact reference similarity scan is quadratic; optimization must preserve
whole-collection coverage, not substitute an unchecked sample.

Validation finishes before filesystem writes. Output directories are new,
append-only locations; protected archive/app/schema paths are rejected. A commit
manifest is written last and hashes all seven partition/held-out files, policy,
leakage/dedupe reports and final freeze metadata. Verification checks hashes,
row counts, projection schema, spans and final freeze bindings. A partial build
without its commit marker is unusable.

## Independent final intake and lifecycle

[independent_sources.yaml](../configs/independent_sources.yaml) is a separate
fresh-evaluation registry, currently empty. Its strict loader requires verified
source identity, actual artifact SHA-256, explicit license evidence and citation,
final-only source split permissions, distinct collector/reviewer and a hashed
collection protocol. Registered public datasets and synthetic sources cannot be
reclassified as independent by changing a row's source-class flag.

Fresh managed rows use INDEPENDENT_EVALUATION, ordinary privacy/review gates, and
`metadata.independence` binding collection ID, collector/reviewer, protocol and
independent registry hashes, with `development_exposed: false`. The existing
primary source registry remains unchanged. The managed record's original source
registry provenance remains intact; the independent namespace is an explicit,
separately bound supplement. Registry admission alone does not approve rows.

A nonempty eligible final set receives `final.freeze.json` with immutable content,
policy and input hashes. `start_development` verifies that freeze before creating
an append-only development lock. `tuning_rows` accepts only TRAIN, DEVELOPMENT,
CALIBRATION or SELECTION, requires that lock and never returns final or official
benchmark rows. After final access it rejects further tuning reads.

`seal_model` binds model/tokenizer/configuration hashes after development.
`final_evaluation_rows` requires a separate bound FINAL_EVALUATION approval,
verifies the actual artifact bytes against that seal, and consumes one final
access marker before returning rows. Repeated final access and replacement
seals/locks fail. These are repository workflow and integrity controls, not OS
access isolation; ordinary filesystem access cannot be prevented by Python APIs.
Actual model training/evaluation are outside this plan and were not run.

The internal writer's `require_final=False` option exists for nonfinal diagnostics.
It emits `NO_FINAL_NOT_COMPLETE` with an empty reserved final file and cannot
start development or tuning access. The CLI does not expose that option. Empty
reservations are not independent evaluation evidence or completion of Plan 06.

## Current evidence

The local readiness audit found all 41 candidates in one ineligible component,
zero assigned rows in every scientific partition, and no official or independent
evaluation rows. Zero protected overlap is **not evaluable**, rather than a pass
inferred from empty files. A strict attempted build fails without creating an
output directory. No final dataset freeze, model development lock, model seal or
final evaluation was created in the repository. Tests exercise successful freezes
only with invented temporary intake/approvals/vectors, never real human data.
