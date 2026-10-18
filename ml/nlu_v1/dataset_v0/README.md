# Dataset v0 build and candidate pool (Plan 07)

The workspace contains a real **2,000-row public human candidate pool**, not a
frozen training dataset. It uses 1,799 MASSIVE en-US rows and 201 CLINC150/OOS
rows from official training partitions. All rows are schema-valid and remain
QUARANTINED / UNKNOWN_PRIVACY with provisional source mappings, unresolved
families/scenarios and no fabricated intake or human-review approvals.

The implemented builder creates a legitimate `dataset_v0_manifest.json` with
FROZEN status only after curation, licensing/privacy, grouped splits, a real
embedding audit and a fresh independent final set pass. Those gates have not
passed for the current pool. Candidate manifests use
QUARANTINED_CANDIDATES_NOT_FROZEN, null split hashes and `training_authorized: false`.
The production release verifier rejects them.

## Run

```powershell
python -m ml.nlu_v1.dataset_v0.build --check
python -m unittest discover -s ml/nlu_v1/tests -v
python -m ml.nlu_v1.dataset_v0.build --verify-candidates ml/nlu_v1/datasets/quarantine/plan07_v0_candidates
python -m ml.nlu_v1.dataset_v0.build --preview ml/nlu_v1/datasets/quarantine/plan07_public_candidates.jsonl
# Only after the existing review/data gates are satisfied:
python -m ml.nlu_v1.dataset_v0.build --build path/to/approved.jsonl --groups path/to/groups.json --embeddings path/to/embeddings.json --output ml/nlu_v1/datasets/frozen/dataset_v0_001
python -m ml.nlu_v1.dataset_v0.build --verify ml/nlu_v1/datasets/frozen/dataset_v0_001
```

Acquisition is reproducible through `dataset_v0.acquire --fetch` for
`massive_1_1_en_us` or `clinc150_full`, then `--prepare`. Existing completed
acquisitions and candidate files are immutable; these commands refuse to replace
them. An incomplete download can resume by freshly verifying bytes against the
same registered HTTPS source. Temporary downloads are owned and cleaned up;
previous artifacts are never deleted. Archive member names are never extracted
as filesystem paths. Downloads are bounded and hashed exactly, and license
evidence is checked against the pinned metadata hashes.

## Actual public acquisition

The [registered MASSIVE 1.1 archive](https://amazon-massive-nlu-dataset.s3.amazonaws.com/amazon-massive-dataset-1.1.tar.gz)
is 40,251,390 bytes in this acquisition; only `1.1/data/en-US.jsonl` was copied
for parsing. The bundled data license and
[pinned Amazon notice](https://github.com/alexa/massive/blob/f966f21846043aabef9b0f974fa7970027f43738/NOTICE.md)
were retained. The notice identifies the dataset's CC BY 4.0 license.

The [pinned CLINC full variant](https://raw.githubusercontent.com/clinc/oos-eval/828f8093932c8fe6ca7936c3d2e52903b1c523de/data/data_full.json)
and its [CC BY 3.0 license](https://github.com/clinc/oos-eval/blob/828f8093932c8fe6ca7936c3d2e52903b1c523de/LICENSE)
were retained. Its other variants were not combined. Raw bytes, license bytes,
locale, source revision, redirects and SHA-256 hashes are recorded in local
observations under `datasets/raw/v0_<source>/`. The original Plan 02 registry is
a historical planning snapshot; these observations are a separate acquisition
record, not permission to train.

Only MASSIVE train and CLINC train/oos_train supplied candidates. Protected
MASSIVE dev/test (2,033/2,974 rows) and CLINC val/test/oos_val/oos_test
(3,000/4,500/100/1,000 rows) stay in the untouched raw payloads, outside the
candidate pool. Their full leakage audit and reviewed benchmark imports remain
pending. Raw parsing for split counts does not make these rows tuning examples.

No eligible legacy rows exist in the immutable retirement catalog. None were
copied, relabeled or trained. Controlled synthetic candidates and hard negatives
remain in their separate Plan 05 quarantine batch; their unapproved rights/privacy
were not bypassed to fill gaps in this public-human pool. Blocked HWU/MTOP sources
and deferred secondary datasets were not used.

## Current distribution and gaps

| Target group | Candidates | v0 target |
| --- | ---: | ---: |
| TASK | 0 | 250 |
| CALENDAR | 745 | 250 |
| MEMORY | 0 | 180 |
| PROMISE | 0 | 120 |
| PERSON | 115 | 100 |
| GOAL / PROJECT | 0 | 180 |
| REMINDER / ALARM / TIMER | 390 | 250 |
| PLANNING / FOCUS / HABIT | 0 | 150 |
| COMMUNICATION / NAVIGATION | 500 | 170 |
| PREFERENCE / KNOWLEDGE / etc. | 0 | 100 |
| OOD / UNSUPPORTED | 250 | 250 |

These counts describe provisional annotations, not reviewed semantic truth. The
acquisition scanned 11,514 MASSIVE and 15,100 CLINC training rows. The adapter
rejected/dropped 391/112 rows; two normalized candidate duplicates were skipped.
No query text is exposed in tracked aggregate reports. Coverage gaps are not
filled by falsely assigning unrelated CLINC or MASSIVE intents to missing domains.
The candidate report includes missing domain/action/disposition/slot labels;
slot counts count annotated spans rather than BIO tokens. Missing context and
confirmation coverage prevents a complete disposition experiment.

## Curation and freeze artifacts

[dataset_v0.yaml](../configs/dataset_v0.yaml) uses the supplied counts with the
lower OOD target of 250, making a 2,000-row total. The release range is 1,500–2,000.
Quotas are soft; every requested group must have nonzero reviewed coverage. A
count alone cannot prove quality or replace missing classes.

The curator validates the whole submitted corpus, then considers connected groups.
Public human rows are preferred for unmet semantic quotas, followed by approved
TRAINABLE_LEGACY, controlled synthetic and hard boundaries. Additional rows can
fill remaining capacity without overriding this order for useful coverage.
Whole groups that exceed the remaining budget are reported rather than split.
Rows are never duplicated or oversampled to satisfy a quota. Missing family,
scenario or parent metadata blocks selection rather than being guessed.

All eligible independent-final groups and external protected groups are preserved
before optional selection. Any unapproved protected group blocks the build. Final
examples are not selected to improve convenient class counts. External public
benchmarks and linked neighbors do not count toward the 1.5k–2k scientific quota.
Canonical approvals remain unchanged through separate partition overlays.

The freeze reruns Plan 06's nonempty five-partition, Unicode/span/BIO, privacy,
provenance/license and leakage gates. Complete text-bound embeddings must cover
the entire input corpus before selecting the release subset; unavailable vectors
cannot be hidden by curation. This is not a downloaded or executed encoder.

A successful new output folder contains:

- `dataset_v0_manifest.json`, written last with FROZEN status;
- `row_hashes.jsonl` binding exact serialized rows, IDs, partitions and canonical
  facts/review hashes;
- `distribution_report.json` with source, family, domain/action/disposition, slot,
  privacy/license counts and missing labels;
- `curation_report.json` with quota deviations, excluded groups and reason codes;
- an exact build configuration snapshot;
- `partitions/` with the five JSONL splits, separate public benchmarks/neighbors,
  leakage/dedupe reports, immutable final freeze and split commit manifest.

The release manifest binds split and artifact hashes, source attribution/license
links, counts, seed, dependency/code hashes and environment. The verifier checks
split integrity, row-hash correspondence, counts and distribution consistency.
Files are append-only; an incomplete folder without the dataset commit manifest
cannot be treated as frozen. No model is trained or automatically started.

## Evidence and remaining work

The subsequent [group review workflow](GROUP_REVIEW.md) adds exact external signed
semantic approvals, exception-only queues, additive coverage templates and a real
pinned offline embedding audit. Semantic approval remains separate from source,
privacy, publication, leakage and dataset-release gates.

The incremental [machine curation pipeline](MACHINE_CURATION.md) now prepares
deterministic metadata, privacy evidence, grouped review queues and candidate
addition plans without bypassing canonical human-review requirements. Its
[aggregate result](../../../../docs/nlu_v1/PLAN_07_MACHINE_CURATION_REPORT.json)
records queue readiness separately from approval and dataset freezing.

The local candidate snapshot contains `dataset_v0_manifest.json`, row hashes,
distribution report and exact candidate JSONL under
`datasets/quarantine/plan07_v0_candidates/`. Its integrity verifier passes. The
[tracked readiness report](../../../../docs/nlu_v1/PLAN_07_DATASET_V0_READINESS.json)
contains aggregate counts and acquisition hashes; raw text and payloads stay ignored.

Current approved curated rows: **0**. Dataset v0 frozen: **false**. Remaining gates
are intake/semantic/privacy review, reviewed family/scenario assignments, missing
group/disposition coverage, complete embedding leakage audit and fresh independent
final collection. A strict attempted build produces no frozen output directory.
Tests prove the writer/verifier path with invented temporary records, approvals,
vectors and a small fixture budget; they are not a 1.5k-row curated dataset or real
independent evaluation.

Domain/action/slot/disposition accuracy, BERT-Tiny failures and label sensibility
need the reviewed dataset and Plans 08–09. No training/model metrics, Android
performance or semantic quality claims are inferred from schema-valid candidates.
