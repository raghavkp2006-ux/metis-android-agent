# Plan 07 release-gate resolution map

Read-only classification. No gate is resolved, row released, dataset changed, decision modified, or approval created. No source utterances are published here. All 2489 analyzed rows remain quarantined.

## Categories

| Code | Meaning |
|---|---|
| A | AUTO_RESOLVABLE_FROM_EXISTING_VERIFIED_EVIDENCE |
| B | REQUIRES_EXPLICIT_HUMAN_OR_OPERATOR_APPROVAL |
| C | REQUIRES_NEW_EXTERNAL_EVIDENCE |
| D | CANNOT_BE_RESOLVED_FOR_DATASET_V0 |
| E | NOT_APPLICABLE |

A includes existing passes and mechanical reverification, not authority to mutate signed data. B requires an explicit factual operator assessment/annotation/authorization, never a waiver of scientific checks. C requires actual new evidence; approval cannot manufacture it. D denotes proven permanent v0 exclusion. E is not applicable.

## Source and gate status

| Source | Rows | Privacy | Intake | Provenance | Canonical use | Publication | Training |
|---|---:|---|---|---|---|---|---|
| clinc150_full | 265 | UNKNOWN_PRIVACY: 263, PUBLIC: 2 | Missing all (B) | Hashes present; intake-bound gate blocked (B) | REGISTERED_PENDING_INTAKE (B) | 0 approved (B) | 0 authorized (B) |
| massive_1_1_en_us | 1799 | UNKNOWN_PRIVACY: 1741, PUBLIC: 58 | Missing all (B) | Hashes present; intake-bound gate blocked (B) | REGISTERED_PENDING_INTAKE (B) | 0 approved (B) | 0 authorized (B) |
| metis_controlled_synthetic | 425 | UNKNOWN_PRIVACY: 425 | Missing all (B) | Hashes present; intake-bound gate blocked (B) | GENERATION_ONLY_PENDING_REVIEW (B) | 0 approved (B); rights evidence C | 0 authorized (B) |

| Source | Human semantic APPROVE rows | Group / exception rows | Bound / unresolved families/scenarios | Skeleton / embedding affected rows | Signed canonical release |
|---|---:|---|---|---|---|
| clinc150_full | 265 | 2 / 263 | 264 / 1 | 0 / 0 | 0 authorized; 265 require B |
| massive_1_1_en_us | 1799 | 58 / 1741 | 1741 / 58 | 112 / 149 | 0 authorized; 1799 require B |
| metis_controlled_synthetic | 425 | 400 / 25 | 425 / 0 | 400 / 182 | 0 authorized; 425 require B |

No other source class is present. Duplicate/similarity counts overlap. No source-ID, row-ID, exact-text or normalized-text collision groups were found. The machine-readable map partitions every row exactly once by source, privacy, review scope and gate/blocker vector; each member has an immutable content hash.

## Each gate by source

Cells contain resolution category and row count.

| Gate | clinc150_full | massive_1_1_en_us | metis_controlled_synthetic |
|---|---|---|---|
| canonical_provenance_gate | B: 265 | B: 1799 | B: 425 |
| canonical_release_authorization | B: 265 | B: 1799 | B: 425 |
| canonical_use_approval | B: 265 | B: 1799 | B: 425 |
| downloaded_public_source_license | A: 265 | A: 1799 | E: 425 |
| duplicate_similarity_disposition | A: 265 | A: 1556; B: 243 | A: 20; B: 405 |
| embedding_execution_and_binding | A: 265 | A: 1799 | A: 425 |
| family_scenario | A: 264; B: 1 | A: 1741; B: 58 | A: 425 |
| grouped_leakage_readiness | B: 265 | B: 1799 | B: 425 |
| human_semantic_review | A: 265 | A: 1799 | A: 425 |
| intake_authorization | B: 265 | B: 1799 | B: 425 |
| privacy_clearance | A: 2; B: 263 | A: 58; B: 1741 | B: 425 |
| protected_evaluation_exclusion | A: 265 | A: 1799 | A: 425 |
| publication_approval | B: 265 | B: 1799 | B: 425 |
| publication_supporting_rights_evidence | A: 265 | A: 1799 | C: 425 |
| schema | A: 265 | A: 1799 | A: 425 |
| source_identity_and_artifact_integrity | A: 265 | A: 1799 | A: 425 |
| synthetic_generation_binding | E: 265 | E: 1799 | A: 425 |
| training_authorization | B: 265 | B: 1799 | B: 425 |

## Gate prerequisites and repository authority

### source_identity_and_artifact_integrity

A verifies existing bytes and identities; it grants no intake permission. PublicEvidence verified pinned local archive/member/license evidence. Plan05 generation commit and all 400 additive coverage renderings were verified.

### canonical_provenance_gate

B for all rows: artifact and import hashes exist. SOURCE_ARTIFACT_UNVERIFIED is an OR predicate covering missing artifact hash, intake_approval_id or import hash; here every intake ID is missing. Mechanical hash verification is A; actual intake authorization/IDs are B. Redownloading every source is not justified.

### intake_authorization

B: operator must authorize exact sources/artifacts/splits and issue genuine bound approval IDs. No approval ID is fabricated. First recommended single gate: public-source intake authorization for 2064 rows, using already verified acquisition/license/split evidence; not synthetic canonical intake.

### canonical_use_approval

B: REGISTERED_PENDING_INTAKE identifies public sources, not completed intake. All 425 synthetic rows fail accepted canonical source status. synthetic_registry.load_source hard-codes GENERATION_ONLY_PENDING_REVIEW, QUARANTINE-only splits and UNKNOWN_PRIVACY. A status edit is invalid. Synthetic canonical use needs expressly authorized versioned governance support before existing release transitions can accept it; no new path is invented here.

### privacy_clearance

A only for 60 existing PUBLIC public-source rows with fact-bound constrained-grammar evidence. Remaining 2429 require B: a factual privacy assessment and bound record. Licensing or fictional names do not confer privacy clearance. If assessment exposes a need for missing ownership/consent evidence, escalate those exact rows to C; do not fabricate consent or assume all unknown rows need external consent.

### publication_supporting_rights_evidence

Existing MASSIVE CC-BY-4.0 and CLINC CC-BY-3.0 identity/license evidence is A, not per-artifact publication approval. Synthetic rights basis is C: the current internal pending-rights license provides no verified publication grant. Responsible owners/operators must supply an actual rights/ownership/licensing basis; then B approves rights/privacy/attribution/change records. This does not assert a specific external third-party license is legally required.

### publication_approval

B for all 2489. Separate per-artifact license/privacy review is mandatory. Public registry publication policy and redistribution rules require attribution/notices/change records. Existing PUBLIC machine evidence explicitly sets publication_allowed=false. No additional interpretation of source licenses is made.

### training_authorization

B for all. Public official train/oos_train splits permit future TRAIN/DEVELOPMENT/CALIBRATION/SELECTION only after all gates. Synthetic registry permits generated QUARANTINE only: generation provenance is not training permission. Global source registry training_authorized=false is preserved.

### human_semantic_review

A: all 2100 signed APPROVE targets valid; 460 group rows plus 2029 exception rows. No REJECT or CHANGE. Semantic review is not privacy, intake, publication, duplicate disposition, canonical approval or release.

### family_scenario

A for 2430 bound rows. B for 59: explicit compatible family/scenario/parent annotations using existing registries; no guessed assignment. Human APPROVE with no corrections supplies no missing family definition.

### duplicate_similarity_disposition

Audit execution is A and COMPLETE. B for affected rows requires explicit adjudication/exclusion/retention evidence; semantic APPROVE is not duplicate clearance. 143 skeleton groups and 244 embedding pairs affect 648 distinct rows. Similarity never deletes a row or proves identical semantics.

### grouped_leakage_readiness

B prerequisite resolution: missing group annotations and eligible approved corpus. Rerunning deterministic grouped leakage/lineage after prerequisites is A, not a discretionary waiver. Zero observed overlaps with no scientific partitions is not a pass. Source actors, family/scenario, source IDs, parent lineage and seed grouping remain enforced.

### protected_evaluation_exclusion

A: none of the 2489 rows is from protected official benchmark/test/final inputs. Public original train/oos_train identity verified; synthetic rows remain quarantined. This does not approve training or make public test data available.

### canonical_release_authorization

B: all 2100 signed canonical_release_authorized flags are false. semantic_groups.apply_canonical_approval requires signed true plus exact full-corpus audit and nonhuman gates. Exception envelopes explicitly bind row_promotion_authorized=false; no standalone exception-record promotion adapter exists. Do not toggle flags or reinterpret semantic review as release. Existing Plan04 requires fact/state/reason-bound human release evidence and re-audit; authorized implementation work for a supported exception path remains separate.

### synthetic_privacy_and_canonical_authority

No existing rule authorizes automatic PUBLIC or canonical classification for the 425 synthetic rows. configs/synthetic_sources.yaml says UNKNOWN_PRIVACY, INTERNAL_CANDIDATES_ONLY and no public export until explicit rights/privacy review. configs/coverage_templates_001.yaml says QUARANTINE_ONLY and INTERNAL_CANDIDATES_ONLY_PENDING_REVIEW. synthetic/generate.py:make_rows and coverage_additions.py:generated_rows initialize unknown privacy/no review. records.py:migrate_ingestion explicitly states Licensing or a synthetic claim alone never establishes publication privacy. records.py:classify_privacy requires bound evidence. Fictional templates and verified deterministic generation establish provenance only. Public-source constrained grammar paths in machine_curation.bind_privacy and semantic_groups.prepare_row do not authorize synthetic clearance.

### new_external_evidence

C currently applies to the 425 synthetic rows supporting publication-rights basis, followed by explicit B approval. Dataset-level C: separately reserved independent-final evidence is absent, and cannot be generated from this pool. Any future failed privacy assessment may identify additional row-specific C needs; none is presumed resolved here.

### permanent_v0_exclusion

D=0 established rows. No protected source rows, license denial or irreparable provenance defect is proven in this pool. Current synthetic ineligibility and duplicate suspicion are not permanent rejection; future v0 eligibility is not promised.

### coverage

Candidate minima missing CALENDAR, PERSON, COMMUNICATION_NAVIGATION; all ten required capability groups lack approved coverage. Scan already verified public training acquisitions first (A preparation). New row review is B; truly unavailable source evidence is C. Final independent evidence remains separate.

### historical_findings

Historical EMBEDDINGS_MISSING and earlier privacy/semantic flags remain preserved evidence. They are not fresh missing-embedding claims: current bound embedding audit is COMPLETE. No old reason/history is silently cleared.

## Exact bound rules and evidence

The Plan05 generation commit and every additive coverage rendering were verified. The preceding full-corpus audit artifacts and signed batch were hash-verified; this map does not claim another embedding inference or rerun of the cosine audit. Existing source license evidence only was used; no online sources or new license interpretations were introduced. All inputs below were byte-hashed before and after report construction.

The 425 synthetic rows comprise 400 additive coverage records and 25 retained Plan05 lineage records. Neither generation proof nor fictional bindings supplies the missing privacy/rights approvals.

First single recommended gate: explicit public-source intake authorization for 2064 MASSIVE/CLINC rows, using verified existing acquisition/license/split evidence. This recommendation grants no permission and leaves every other gate independent.

Machine map: `ml/nlu_v1/datasets/quarantine/plan07-release-gate-map-001.json`.

- `ml/nlu_v1/configs/coverage_templates_001.yaml` — SHA-256 `19e7176039ae2c55bfb968020cbb3bebc860958b76698a562b956d6072856d65`
- `ml/nlu_v1/configs/dataset_source_evidence.json` — SHA-256 `e3b008c5a5251ceddad5e1479918237963b36a196bd0119f347ff1bf125370d4`
- `ml/nlu_v1/configs/dataset_sources.yaml` — SHA-256 `4fa2c0e1f873e84070de04191f93dd8a7ab278dcbe747a723e2af51d5e810c10`
- `ml/nlu_v1/configs/machine_curation_002.yaml` — SHA-256 `ac091732a2e5c4df2d5580c324eb68443b00eaec93b9bcccf0dd052ac872a7ad`
- `ml/nlu_v1/configs/split_policy.yaml` — SHA-256 `7ca1a0531528e81fea39ed177c2305f5186d06dcb651e6a8a5ee462729b7b0ee`
- `ml/nlu_v1/configs/synthetic_families.yaml` — SHA-256 `6142268d2d2606be026ff0bb2b2e9896ec874708be5c0b061fb6573ed3d6299a`
- `ml/nlu_v1/configs/synthetic_sources.yaml` — SHA-256 `5c803e688287c6eae5dd7c2f20ea5a012d7b393ddd9e458e129109b92955c79c`
- `ml/nlu_v1/data_management/records.py` — SHA-256 `8a497341b84b67c5336b12fa74b52149f5c41e5969cc4aaf4023419dcc1ffc95`
- `ml/nlu_v1/dataset_v0/coverage_additions.py` — SHA-256 `688692b2cac0d107eefc2d5e62ddbc3b56c8b62664abf721dc12da3616948907`
- `ml/nlu_v1/dataset_v0/machine_curation.py` — SHA-256 `757bb87696166da25bb96e22dbbad03fd690a7ff83168edabc0d39a1fce93949`
- `ml/nlu_v1/dataset_v0/semantic_groups.py` — SHA-256 `451999b6930455761b5ed24c23a1d022d66d029835190ff71b331f13b209e827`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/coverage_report.json` — SHA-256 `54696f28bfd02a9a902c71107927425f9bac5d8bcff297ff9add2b06074483c1`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/dedupe_report.json` — SHA-256 `3545fdad8c8ef5769c56495bc856229752161cb8d9cc098d8e933f5a207b4f98`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/distribution_report.json` — SHA-256 `ae6ccdb1edd86924fea96b7a00b1b9c1ec907615ba955fec89c30847ca964c0d`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/leakage_report.json` — SHA-256 `bb6f24108d53c12de21f74cb2857ee575f1fb4bc84741cf3b1eeec9dee806c9f`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/post_review_manifest.json` — SHA-256 `45d1169b2cc8d20f923b4d263bff44544c587f45593acc82208ad9857c74dd0d`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/post_review_report.json` — SHA-256 `f9ce3b69e92a30d47ba766d4d046c1c2403459f13fa45774a05acd32f96038f7`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/quarantined.jsonl` — SHA-256 `b630457b393ed7b1273eab2ee3681e33f7e479969e5dd6d989c3a0756c1815bf`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/release_attempts.json` — SHA-256 `7f3b74202e3eabaf1cb951e7dabe0b76f8a94beeecab78d955d82367419ea778`
- `ml/nlu_v1/datasets/quarantine/plan07-post-review-001/row_release_blockers.json` — SHA-256 `9c1e5184fcd9a3c14427cc225fc152f88b4fb6f96cb4654a0602d66827d18a46`
- `ml/nlu_v1/datasets/quarantine/plan07-review-decisions-001/signed/human-review-001/batch_manifest.json` — SHA-256 `512468c7d773befcbc9e51077973bd918c3d9dd7c1e1d67e501271e0bc72633f`
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_registry.json` — SHA-256 `4f811a53fdea4f14174bf8cc89ad1bd0ee1eccca289226d7ca07f2d7069b8203`
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_review_manifest.json` — SHA-256 `c8947bb7c546fd2720dc5329efe314b6a68a82480097d14aeb465a47f031e57c`
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/quarantined.jsonl` — SHA-256 `c10967f18dcd434d7e0161a9ee1b47c03ff8bcb6956cb44ed6e35fae31b33403`
- `ml/nlu_v1/datasets/raw/plan05-seed17.manifest.json` — SHA-256 `0e949fb14c7bd33c2c45bb43a5d570f54b5179f913c099a2634fbd894a64cd8f`
- `ml/nlu_v1/datasets/raw/v0_clinc150_full/license-evidence.txt` — SHA-256 `e6bc9e9c474700b708f568bac9e5a8a9bcb2b1dad53442f5ba449fcb848b8e76`
- `ml/nlu_v1/datasets/raw/v0_clinc150_full/observation.json` — SHA-256 `c71d320f5ca69259640acb7ec24eaf9eb5aac7a78ccaa39bafd57fc262c32c2e`
- `ml/nlu_v1/datasets/raw/v0_massive_1_1_en_us/license-evidence.txt` — SHA-256 `db1466b051afd1d9ad34b7bd6346a1857f568a0c5f6534168813b4ad3c26013e`
- `ml/nlu_v1/datasets/raw/v0_massive_1_1_en_us/observation.json` — SHA-256 `05b0bcdc6091b82d96e5c475775eec90b290402543d375b727382b808d95e4b4`
- `ml/nlu_v1/splitting/split.py` — SHA-256 `6ef65b0a807926df43b225701ccdebd7d2dbc1a47070389eb3ab5eb7fdd2a2a1`
- `ml/nlu_v1/synthetic/generate.py` — SHA-256 `3778458d542104264a6ff211a7adbfe2c23da74ed8677cb27c2adb0fd4842eaa`
- `ml/nlu_v1/synthetic_registry.py` — SHA-256 `8218cb0f862ffade0231266c79689f4725c109b894f626d91ab34dcdc7086caa`
