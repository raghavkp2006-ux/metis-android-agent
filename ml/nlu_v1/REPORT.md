# single frozen split, 1 test author, indicative only

validation only, test not run

Split: train = AK, PR, DJ, Raghav (477 rows); validation = VN (128 rows); test = SM (128 rows, frozen, unopened, not scored).

Evaluation: author-held-out validation on VN; reject threshold chosen from pooled train grouped-CV OOF and VN predictions on a 0.05-step grid (lowest with zero Tier 1 false accepts + 0.05 margin). Test author SM is frozen and will not be scored until Phase B.

## Validation metrics (VN, author-held-out)

| System | Accuracy | Selective accuracy | Macro-F1 | Coverage | WRITE coverage | Tier 1 false accepts | Tier 2 false accepts | False actions (count; rate) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ML-only validation (VN, operating t=0.45) | 0.343750 | 100.00% | 0.397915 | 28.12% | 25.00% | 0 | 0 | 0 (0.00%) |
| ML-only validation (VN, sensitivity t=0.20) | 0.914062 | 100.00% | 0.924858 | 85.16% | 95.31% | 0 | 0 | 0 (0.00%) |
| Rules-only | - | - | - | - | - | rules_pred empty | rules_pred empty | rules_pred empty |
| Hybrid (rules first) | - | - | - | - | - | rules_pred empty | rules_pred empty | rules_pred empty |

Operating reject threshold: **0.45** (selected by pooling train OOF and VN predictions; lowest zero Tier 1 threshold was 0.40 + 0.05 margin).
VN-only sensitivity threshold: **0.20** (lowest zero Tier 1 threshold on VN alone was 0.15 + 0.05 margin; shown for sensitivity only, not used as operating point).

- **Tier 1 (zero tolerance):** COMPLETE_TASK, DELETE_TASK, POSTPONE_TASK, PRIORITIZE_TASK, RENAME_TASK
- **Tier 2:** CREATE_TASK, CREATE_REMINDER, CREATE_PROMISE, REFLECTION
- **False actions:** incorrect predictions into any of the 8 WRITE intents (Tier 1 + CREATE_TASK, CREATE_REMINDER, CREATE_PROMISE).
- Coverage is fraction not rejected as UNKNOWN. WRITE coverage is measured over ground-truth WRITE intents. Selective accuracy is measured on covered predictions.

rules_pred empty — rules predictions have not been filled yet. Rules-only and hybrid evaluation deferred to Phase B.

## Out-of-Scope (True UNKNOWN) Evaluation at Operating Threshold (0.45)

| Subset | True UNKNOWN Total | Accepted as non-UNKNOWN | Tier 1 False Accepts | Tier 2 False Accepts | READ False Accepts |
|---|---:|---:|---:|---:|---:|
| **Validation (VN)** | 8 | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) |
| **Train Grouped-CV OOF** | 37 | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) |
| **Pooled (OOF + VN)** | 45 | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) |

## Per-intent precision / recall (VN at Operating Threshold 0.45)

| Intent | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| CHECK_CALENDAR | 1.0000 | 0.3750 | 0.5455 | 8 |
| CHECK_TASKS | 1.0000 | 0.2500 | 0.4000 | 8 |
| COMPLETE_TASK | 1.0000 | 0.3750 | 0.5455 | 8 |
| CREATE_PROMISE | 1.0000 | 0.1250 | 0.2222 | 8 |
| CREATE_REMINDER | 1.0000 | 0.1250 | 0.2222 | 8 |
| CREATE_TASK | 1.0000 | 0.1250 | 0.2222 | 8 |
| DELETE_TASK | 1.0000 | 0.1250 | 0.2222 | 8 |
| PERSON_COMMITMENTS | 1.0000 | 0.2500 | 0.4000 | 8 |
| PLAN_DAY | 1.0000 | 0.3750 | 0.5455 | 8 |
| POSTPONE_TASK | 0.0000 | 0.0000 | 0.0000 | 8 |
| PRIORITIZE_TASK | 1.0000 | 0.7500 | 0.8571 | 8 |
| PROJECT_STATUS | 1.0000 | 0.8750 | 0.9333 | 8 |
| REFLECTION | 0.0000 | 0.0000 | 0.0000 | 8 |
| RENAME_TASK | 1.0000 | 0.3750 | 0.5455 | 8 |
| UNKNOWN | 0.0870 | 1.0000 | 0.1600 | 8 |
| WHAT_SHOULD_I_DO | 1.0000 | 0.3750 | 0.5455 | 8 |

## Validation coverage versus threshold grid (VN)

| Threshold | Coverage | WRITE coverage | Selective accuracy | Tier 1 false accepts | Tier 2 false accepts | False actions |
|---:|---:|---:|---:|---:|---:|---:|
| 0.05 | 95.31% | 100.00% | 95.08% | 1 | 3 | 2 |
| 0.10 | 94.53% | 100.00% | 95.87% | 1 | 2 | 2 |
| 0.15 | 89.06% | 98.44% | 100.00% | 0 | 0 | 0 |
| 0.20 *[VN Sensitivity]* | 85.16% | 95.31% | 100.00% | 0 | 0 | 0 |
| 0.25 | 73.44% | 81.25% | 100.00% | 0 | 0 | 0 |
| 0.30 | 64.06% | 70.31% | 100.00% | 0 | 0 | 0 |
| 0.35 | 50.78% | 51.56% | 100.00% | 0 | 0 | 0 |
| 0.40 | 37.50% | 39.06% | 100.00% | 0 | 0 | 0 |
| 0.45 **[Operating Point]** | 28.12% | 25.00% | 100.00% | 0 | 0 | 0 |
| 0.50 | 22.66% | 17.19% | 100.00% | 0 | 0 | 0 |
| 0.55 | 11.72% | 7.81% | 100.00% | 0 | 0 | 0 |
| 0.60 | 7.81% | 3.12% | 100.00% | 0 | 0 | 0 |
| 0.65 | 3.91% | 1.56% | 100.00% | 0 | 0 | 0 |
| 0.70 | 1.56% | 0.00% | 100.00% | 0 | 0 | 0 |
| 0.75 | 0.00% | 0.00% | 0.00% | 0 | 0 | 0 |
| 0.80 | 0.00% | 0.00% | 0.00% | 0 | 0 | 0 |
| 0.85 | 0.00% | 0.00% | 0.00% | 0 | 0 | 0 |
| 0.90 | 0.00% | 0.00% | 0.00% | 0 | 0 | 0 |
| 0.95 | 0.00% | 0.00% | 0.00% | 0 | 0 | 0 |

## Style-subset metrics (VN at Operating Threshold 0.45)

| Style | Count | Accuracy | Selective accuracy | Macro-F1 | Coverage | WRITE coverage | Tier 1 false accepts | Tier 2 false accepts | False actions (count; rate) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| hinglish | 32 | 0.281250 | 100.00% | 0.259259 | 21.88% | 18.75% | 0 | 0 | 0 (0.00%) |
| typo | 16 | 0.250000 | 100.00% | 0.196429 | 18.75% | 0.00% | 0 | 0 | 0 (0.00%) |
| voice | 16 | 0.312500 | 100.00% | 0.259615 | 25.00% | 25.00% | 0 | 0 | 0 (0.00%) |

## Grouped CV (leave-one-author-out) within train authors (477 rows)

| Metric | Value |
|---|---:|
| Raw OOF Accuracy (argmax) | 0.9308 |
| Raw OOF Macro-F1 (argmax) | 0.9303 |
| OOF Accuracy at Operating Threshold (0.45) | 0.232704 |
| OOF Selective Accuracy | 100.00% |
| OOF Macro-F1 | 0.264423 |
| OOF Coverage | 15.51% |
| OOF WRITE Coverage | 8.43% |
| OOF Tier 1 False Accepts | 0 |
| OOF Tier 2 False Accepts | 0 |
| OOF False Actions | 0 (0.00%) |

## Limitations

- single frozen split, 1 test author, indicative only: single frozen split with 1 test author; results are indicative, not definitive.
- The reject threshold was determined by pooling train grouped-CV OOF and VN predictions on a 0.05 grid; zero Tier 1 false accepts is a calibration constraint, not an absolute guarantee.
- The test set (SM, 128 rows) is frozen in `test.csv` and remains completely unopened and unscored during Phase A.
- The fixed logistic-regression configuration is not authorization. Any action requires the existing rules and policy engine.
- Phase B (frozen test on SM) is deferred until rules_pred is filled and explicit instruction is given.
