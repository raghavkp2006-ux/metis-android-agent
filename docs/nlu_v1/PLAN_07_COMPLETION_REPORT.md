# Plan 07 completion report — policy 002

Plan 07 is INCOMPLETE. No Dataset v0 exists, no manifest is frozen, and no training or Plan08 work was started. New prospective governance: `nlu_v1_privacy_policy_002`. Previous Plan04/Plan07 rules, source observations, row histories and signed review decisions remain unchanged.

## Source assessment

| Source | Candidates | Version / revision | Archive SHA-256 | Member SHA-256 | Covered splits | Finding |
|---|---:|---|---|---|---|---|
| massive_1_1_en_us | 1799 | 1.1 / f966f21846043aabef9b0f974fa7970027f43738 | 4cba5faa11c71437928e17cb1b9b3d8b8e727e7ea363a3a9a8045e19c0491577 | c70f75c6a543a26e249ec383df67733ad9b1066f6c0406c2e04a3f03356e407e | train | INSUFFICIENT_EVIDENCE |
| clinc150_full | 265 | data_full.json-at-pinned-revision / 828f8093932c8fe6ca7936c3d2e52903b1c523de | 36923c3705a59e08fe9c3883d8bc2dd966ef93e22cb78ac41171782a698d56e0 | 36923c3705a59e08fe9c3883d8bc2dd966ef93e22cb78ac41171782a698d56e0 | oos_train, train | INSUFFICIENT_EVIDENCE |

Each source assessment binds all fourteen rule checks, exact evidence, source/config hashes and member row IDs/content hashes. Existing PublicEvidence verifies source identity, archive/member/license hashes and canonical/native-row provenance. Native equality proves this pool was acquired from the public datasets rather than METIS accounts/connectors; it does not establish upstream confidentiality practices.

## One smallest next blocker

Missing locally available, hash-verified source collection/privacy evidence establishing policy v002 rule 11 for the exact scoped MASSIVE and CLINC artifacts. The metadata registry contains pinned README hashes without their actual document bytes. Local license text alone is insufficient. Obtaining those pinned bytes is an evidence-acquisition step; their content must then establish the requirement or further authoritative evidence is needed. No privacy clearance or consent is invented.

The MASSIVE archive contains NOTICE and LICENSE, with no README or collection/privacy document. Its notice establishes SLURP lineage, not confidentiality-free collection. CLINC acquisition contains license text and payload, without local collection/privacy documentation. Neither source qualifies, so no source approval or native intake authorization is issued. The original operator intake reason is preserved in the conversation and remains conditional on actual native prerequisites.

## Maximum compliant selection and backlog

| Measure | Result |
|---|---|
| Eligible after source002 privacy and intake | 0 |
| Public candidates excluded from prospective v0 selection | 2064 |
| Synthetic rows excluded under owner decision | 425 |
| Rows preserved in quarantine/backlog | 2489 |
| Existing PUBLIC classifications preserved | 60 |
| Family/scenario backlog | 59; zero included |
| Similarity backlog | 648 distinct rows; zero included |
| Similarity findings | 143 skeleton groups; 244 cosine pairs |

All original rows are retained with their histories and findings. The assessment exclusion list binds every row ID/content hash and reasons; it does not mark rows REJECTED or delete them. The 60 earlier PUBLIC classifications and evidence remain intact, but source002 does not provide the missing native intake, canonical/publication/release gates for those rows. They also cannot satisfy the existing 1500–2000-row Dataset v0 requirement.

Similarity selection exclusion honors the existing quarantine-all-matches rule. It does not choose a duplicate winner, infer equivalent semantics, invent human adjudication, or require review merely because a similarity score exists. All 425 synthetic rows remain in backlog with generation evidence and no intake/privacy authority.

## Required checks

| Check | Result |
|---|---|
| Source/schema/provenance | Existing signed 2489-row run verified, public native rows reverified; no facts changed |
| Source-level privacy | FAIL for both sources: insufficient rule-11 evidence |
| Native intake | Not attempted because actual privacy prerequisite fails |
| Curation/distribution | Existing curate([]): BLOCKED_EMPTY_CORPUS; zero selected rows |
| Coverage | FAIL: coverage_report([]) misses all ten approved capability groups |
| Dedupe/embedding | Existing complete, hash-bound full-corpus audit verified; no new inference/comparison run claimed |
| Family/scenario/lineage | Unresolved candidates excluded; histories and parent bindings retained |
| Grouped leakage | FAIL: leakage_report([], {}) is not scientific validation |
| Partition generation | Not run without an eligible corpus |
| Training authorization | Not granted; no included rows |
| Dataset manifest/freeze | Not created |
| Independent-final evaluation | Reserved and untouched; never fabricated |

Split counts: TRAIN=0, DEVELOPMENT=0, CALIBRATION=0, SELECTION=0. These represent absent partitions; no empty split is advertised as a valid generated dataset. No eligible approved public source exists for coverage additions in this run.

Fixing rule 11 does not promise a completed dataset: the actual freeze implementation still has separate canonical/publication, coverage and scientific partition gates, including its final-independent condition. No downstream validator is weakened or silently rewritten by the new privacy policy.

## Artifacts and verification

- `ml/nlu_v1/configs/privacy_policy_002.yaml`
- `docs/nlu_v1/PRIVACY_POLICY_002.md`
- `ml/nlu_v1/dataset_v0/source_privacy.py`
- `ml/nlu_v1/datasets/quarantine/plan07-privacy-policy-002-assessment-001/assessment.json`
- `ml/nlu_v1/datasets/quarantine/plan07-privacy-policy-002-assessment-001/manifest.json`

Assessment SHA-256: `65ef5820ba7869593350ff6e888075688e7b2b0c81a8873751c847256ca0d1ef`. The local manifest binds all assessment inputs, source/config evidence and exact member hashes. No raw utterances are copied to this report.

All 238 NLU tests passed, including ten policy002 tests. Frozen schema verification and legacy-retirement checks passed; production sources remain unchanged and production ML remains disabled. Existing input hashes were checked again after assessment. No privacy states, approvals, source observations, reviewer decisions, legacy V4–V16 artifacts or dataset rows were modified.
