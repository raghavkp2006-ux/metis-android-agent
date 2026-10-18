# Plan 02 — Dataset Source Registry

[dataset_sources.yaml](../../ml/nlu_v1/configs/dataset_sources.yaml) is version
`nlu_v1_sources_001`, reviewed 2026-10-16. It registers future purposes, not
permission to use a dataset. Prediction schema `nlu_v1_schema_001` is unchanged.

| Source | Data license evidence | METIS status |
| --- | --- | --- |
| MASSIVE 1.1 en-US | CC-BY-4.0; code license differs | Registered; intake/hash/mapping pending |
| Original HWU / NLU Evaluation Data | CC-BY-4.0 | Blocked pending corpus split provenance |
| MTOP English | Official archive identified; authoritative license unverified | Blocked license and archive/split verification |
| CLINC150 full / OOS | CC-BY-3.0 | Registered; intake/hash/scope mapping pending |
| Legacy METIS | Per-row rights/consent/approval required | Quarantined; no eligible current catalog records |
| SGD | CC-BY-SA-4.0 | Deferred context work |
| SGD-X | CC-BY-SA-4.0 | Deferred context work; inherits SGD lineage |
| SLURP text | CC-BY-4.0 | Deferred; shared-lineage audit required |
| SLURP audio | CC-BY-NC-4.0 | Blocked product use; exact artifact/purpose not approved |
| SNIPS custom-intent benchmark | CC0-1.0; upstream requests citation | Deferred smoke tests only |
| Banking77 | CC-BY-4.0 | Optional OOD only; deferred |

All identity/license URLs and citations appear in the registry. Seven repository
heads were resolved using `git ls-remote`; README/license/notice files were fetched
at those exact revisions. [Metadata evidence](../../ml/nlu_v1/configs/dataset_source_evidence.json)
records their URLs and raw-byte SHA-256. These are not dataset payload hashes.
Offline checking verifies references/hash syntax, not remote availability or
publisher authenticity. Preserve fetched license bytes with eventual intake.

The publisher's [MASSIVE notice](https://github.com/alexa/massive/blob/f966f21846043aabef9b0f974fa7970027f43738/NOTICE.md)
specifies the data license separately from code. The original
[HWU README](https://github.com/xliuhw/NLU-Evaluation-Data/blob/f6071b496b17d71e6eb43f543af0707f4ff30557/README.md)
describes user IDs, annotated corpus and cross-validation derivatives. A packaged
HWU64 benchmark is not silently substituted for that original corpus.

Meta's [official announcement](https://ai.meta.com/blog/democratizing-conversational-ai-systems-through-new-data-sets-and-research/)
links `fb.me/mtop_dataset`, resolving to the registered archive. Its authoritative
dataset license was not available in inspected metadata. No mirror tag or paper
copyright was accepted as dataset permission. MTOP remains blocked; proposed
split names require archive-member verification before use.

[SLURP's publisher](https://github.com/pswietojanski/slurp/blob/8eb16545762be97ace75334109d73824217311f1/README.md#license)
distinguishes text from noncommercial audio. Separate entries prevent text rights
from approving audio. Training eligibility never automatically grants publication
rights for adapted datasets, models or APKs; each artifact needs license/privacy
review and the applicable attribution, license, change and ShareAlike notices.

## Split and mapping policy

Allowlisted purposes are conditional, never present intake approval. Only official
training material may feed TRAIN or be grouped into DEVELOPMENT, CALIBRATION and
SELECTION. Original dev/val/eval/test material stays protected; it never enters
fitting, augmentation, distillation or tuning pools. CLINC oos_test is preserved
for OOD evaluation. Known public tests cannot become a fresh METIS final test.

BENCHMARK_DEVELOPMENT, BENCHMARK_TEST, OOD_BENCHMARK_TEST and SMOKE_TEST are
registry purposes, not new frozen row-schema split labels. Later benchmark
manifests must preserve original partitions outside training pools; there is no
automatic coercion to TRAIN or FINAL_INDEPENDENT_TEST.

HWU corpus is not assumed to be a single official training partition. Its generated
cross-validation folds overlap: concatenating fold training files risks exposing
held-out records. Resolve corpus/fold identity and stable row IDs, then review a
grouped split manifest before training. Preserve available user/speaker IDs in
access-controlled lineage sidecars using opaque identifiers; the frozen row schema
has no arbitrary metadata field. Do not invent missing speaker IDs. Unnecessary
demographics stay outside model input and public artifacts.

MASSIVE retains SLURP IDs/partitions, and SLURP includes links to NLU benchmark
material. All three therefore share a conservative leakage group. SGD-X variants
inherit their SGD parent dialogue and split. Source variants, related paraphrases
and parent records require joint audits; declaring lineage is not a passed audit.

Legacy reuse requires explicit TRAINABLE_LEGACY classification plus verified
license, consent, nonprivate classification, matching source hash and row-level
purpose approval. Any EXPOSED, PROTECTED_EVALUATION or PRIVATE_DO_NOT_PUBLISH
classification denies automatic reuse even if another flag says trainable.
The current catalog remains untouched and quarantined. Raw archive contents
were not inspected, republished, evaluated or reclassified during this step.

Every source intent must receive MAP / KNOWN_UNSUPPORTED / OOD / DROP before
ingestion; unmapped intents block intake. Actual mappings belong to Plan 03.
CLINC's own scope labels do not define METIS scope. Banking77 is OOD-only;
SNIPS is smoke-test-only. Neither implies supported banking or other actions.

## Hashing strategy

Payload download SHA-256 fields are null because no dataset payload was downloaded.
Null blocks use. Metadata hashes and Git commits cannot substitute for payload
hashes. First resolve authoritative identity, license and split provenance.
At intake stream exact bytes to quarantine, hash before extraction, and record
requested/resolved URL, immutable revision/release, UTC time, length and digest.
For mutable archives, freeze the first reviewed hash and reject later mismatches;
this is a local integrity pin, not independent publisher authentication.
Never trust ETags or filenames as SHA-256.

Record extracted member paths/hashes, bundled license hash, original split/locale,
mapper version and approval reference. Reject unsafe archive paths, unknown
partitions, wrong locales and mixed variants. Rehash before mapping; quarantine
mismatches without silently replacing accepted digests. Hash canonical output
and partition manifests separately. Dataset hashing uses raw bytes with no
line-ending/Unicode normalization, unlike portable schema-freeze text hashes.
Legacy uses existing per-file hashes plus new approved row lineage; its evidence
archive stays immutable.

## Verification and limits

Run `python ml/nlu_v1/source_registry.py` for offline registry checks. All actual
use is denied in this registry-only stage. The check is not a downloader or an
ingestion/training engine. Registry policy tests also run in the NLU CI job.

Local checks on 2026-10-16: 28 NLU tests (19 frozen-contract plus nine registry),
six retirement tests, freeze integrity, retirement integrity and `git diff --check`
passed. No dataset payload, training data or model was created; production sources
are unchanged. Remote CI, APK asset inspection and Android/device tests were not
run for this offline configuration change.

Registry artifacts, split policy and hashing strategy are implemented. MTOP
license/archive verification, HWU partition provenance, legacy approvals and all
payload hashes remain explicit use blockers. Registration does not mean approval
for ingestion. Plan 03 can implement mappers and quarantine gates while respecting
these blocks; no manual user query batches are required.
