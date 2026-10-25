# Day 1 rules development baseline

**Diagnostic development results only. No training, fitting, tuning, network, downloads or installs.**

Measurement contract committed at `385971405d0354400c4c527b83db19c5e039eb35`; SHA-256 `d68d5b1386302b186ab1cf357731454d5d26abefa151190723e4a2a9002b6129`. Hash receipt committed separately before execution.

Gold rows: 100; evaluated: 97; excluded: 3.
All evaluated rows use empty tasks and no prior turn. Fixture rows were not run because supplied `now` is missing.
Primary gate: **FAIL — insufficient n**. No independent test supplied or searched for.
At least 190 valid independent test rows are needed for the primary risk gate.

## Metrics

| Metric | Estimate | Bootstrap 95% CI |
|---|---:|---|
| abstain_rate | 0.979381 | [0.948454, 1.000000] (2000 draws) |
| accuracy | 0.010309 | [0.000000, 0.030928] (2000 draws) |
| accuracy_at_90_conf | 0.500000 | [0.000000, 1.000000] (1738 draws) |
| clarification_precision | 0.053191 | [0.010638, 0.103093] (2000 draws) |
| cost_weighted_error | 1.041237 | [0.907216, 1.247423] (2000 draws) |
| coverage | 0.012987 | [0.000000, 0.041667] (2000 draws) |
| entity_accuracy | 0.216495 | [0.134021, 0.298969] (2000 draws) |
| forbidden_write_count | 0.000000 | [0.000000, 0.000000] (2000 draws) |
| inference_latency_ms | 1.609253 | [0.542526, 3.514678] (2000 draws) |
| intent_accuracy | 0.237113 | [0.154639, 0.319588] (2000 draws) |
| latency_p50_ms | 0.446400 | [0.407300, 0.478900] (2000 draws) |
| latency_p95_ms | 3.013000 | [1.375200, 13.699300] (2000 draws) |
| macro_f1 | 0.055285 | [0.023529, 0.091088] (2000 draws) |
| safe_handling_rate | 0.268041 | [0.185567, 0.350515] (2000 draws) |
| unseen_author_accuracy | not found / undefined | [not found / undefined, not found / undefined] (0 draws) |
| wrong_write_rate | 0.010309 | [0.000000, 0.030928] (2000 draws) |
| wrong_write_wilson_upper | 0.056105 | [0.038094, 0.087020] (2000 draws) |

Bootstrap: 2,000 utterance draws, seed 17. Wilson upper bound is the primary rare-event risk bound.
Latency measures this host JVM with cold parser/JIT initialization included for the first row; process startup and I/O excluded.
Unseen-author accuracy: **not found**, because a training-author manifest was not supplied.
Rule confidence is a grammar score, not a calibrated probability.

## Author and primary-intent counts

| Author | Rows |
|---|---:|
| Raghav | 100 |

One author only: no nonempty author-isolated train/development split can be made from this sheet.
No split of this development sheet was performed.

| Intent | Rows |
|---|---:|
| CHECK_CALENDAR | 4 |
| CHECK_TASKS | 3 |
| COMPLETE_TASK | 9 |
| CREATE_PROMISE | 7 |
| CREATE_REMINDER | 8 |
| CREATE_TASK | 9 |
| DELETE_TASK | 6 |
| PERSON_COMMITMENTS | 4 |
| PLAN_DAY | 3 |
| POSTPONE_TASK | 7 |
| PRIORITIZE_TASK | 5 |
| PROJECT_STATUS | 5 |
| REFLECTION | 3 |
| RENAME_TASK | 6 |
| UNKNOWN | 20 |
| WHAT_SHOULD_I_DO | 1 |

Every intent except UNKNOWN has fewer than 12 primary rows. Missing code intents have zero rows.
Clause-level counts are recorded separately in `ml/data/dev_own_audit.json`.

## Label and fixture mismatches

CSV intents absent from code: CREATE_PROMISE, PERSON_COMMITMENTS, PRIORITIZE_TASK, PROJECT_STATUS, REFLECTION, RENAME_TASK.

Code intents absent from the CSV (including all clauses): CALL_PERSON, CATCH_UP, CHECK_GOALS, CHECK_HABITS, CHECK_MEMORY, CHECK_PERSON, CHECK_PROGRESS, CREATE_ALARM, CREATE_CALENDAR_EVENT, CREATE_GOAL, CREATE_TIMER, DAILY_REVIEW, EMOTIONAL_SUPPORT, PLAN_WEEK, SEND_MESSAGE, SET_PREFERENCE, START_FOCUS, START_NAVIGATION, START_REFLECTION, STOP_FOCUS, UNSUPPORTED, UPDATE_TASK, WEEKLY_REVIEW, WHAT_DID_I_MISS, WHAT_DID_I_PROMISE.

These labels are preserved; no aliases or guessed fixes were applied. Entity values all pass the verbatim check.

| Row | Schema defect |
|---:|---|
| 3 | intent absent from code: RENAME_TASK |
| 4 | intent absent from code: PRIORITIZE_TASK |
| 7 | intent absent from code: CREATE_PROMISE |
| 8 | intent absent from code: PERSON_COMMITMENTS |
| 19 | intent absent from code: PROJECT_STATUS |
| 20 | intent absent from code: REFLECTION |
| 30 | intent absent from code: RENAME_TASK |
| 31 | intent absent from code: PRIORITIZE_TASK |
| 35 | intent absent from code: CREATE_PROMISE |
| 38 | intent absent from code: PROJECT_STATUS |
| 39 | intent absent from code: PERSON_COMMITMENTS |
| 41 | intent absent from code: REFLECTION |
| 49 | intent absent from code: RENAME_TASK |
| 50 | intent absent from code: PRIORITIZE_TASK |
| 54 | intent absent from code: CREATE_PROMISE |
| 55 | intent absent from code: CREATE_PROMISE |
| 58 | intent absent from code: PROJECT_STATUS |
| 59 | intent absent from code: PERSON_COMMITMENTS |
| 68 | intent absent from code: RENAME_TASK |
| 69 | intent absent from code: PRIORITIZE_TASK |
| 73 | intent absent from code: CREATE_PROMISE |
| 76 | intent absent from code: PROJECT_STATUS |
| 78 | intent absent from code: REFLECTION |
| 83 | intent absent from code: PRIORITIZE_TASK |
| 85 | intent absent from code: CREATE_PROMISE |
| 87 | intent absent from code: RENAME_TASK |
| 88 | intent absent from code: PROJECT_STATUS |
| 89 | intent absent from code: PRIORITIZE_TASK |
| 93 | intent absent from code: CREATE_PROMISE |
| 96 | intent absent from code: RENAME_TASK |
| 98 | intent absent from code: PERSON_COMMITMENTS |

| Row | Fixture exclusion |
|---:|---|
| 95 | fixture now missing or invalid; no execution under substituted state |
| 99 | fixture now missing or invalid; no execution under substituted state |
| 100 | fixture now missing or invalid; no execution under substituted state |

The following per-intent list includes every evaluated row whose complete label the current pipeline could not reproduce.
It distinguishes intent, primary entities, ordered clauses, clarification, and write errors. These are observed reproducibility
mismatches, not automatic proof that the human intended meaning is wrong.

## Per-intent failures

### CHECK_CALENDAR

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 18 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 37 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 57 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 75 | UNKNOWN | intent, entities, ordered meaning, clarification |

### CHECK_TASKS

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 36 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 56 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 74 | UNKNOWN | intent, entities, ordered meaning, clarification |

### COMPLETE_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 2 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 15 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 28 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 29 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 48 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 67 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 81 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 90 | UNKNOWN | intent, entities, ordered meaning, clarification |

### CREATE_PROMISE

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 7 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 35 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 54 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 55 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 73 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 85 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 93 | UNKNOWN | intent, entities, ordered meaning |

### CREATE_REMINDER

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 6 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 17 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 34 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 53 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 72 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 84 | CREATE_REMINDER | entities, ordered meaning, clarification |
| 91 | UNKNOWN | intent, entities, ordered meaning |
| 97 | UNKNOWN | intent, entities, ordered meaning |

### CREATE_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 1 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 14 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 26 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 27 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 46 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 47 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 66 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 82 | CREATE_TASK | entities, ordered meaning, wrong write |

### DELETE_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 16 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 33 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 52 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 71 | UNKNOWN | intent, entities, ordered meaning |
| 86 | UNKNOWN | intent, entities, ordered meaning, clarification |

### PERSON_COMMITMENTS

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 8 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 39 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 59 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 98 | WHAT_DID_I_PROMISE | intent, entities, ordered meaning, clarification |

### PLAN_DAY

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 40 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 60 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 77 | UNKNOWN | intent, entities, ordered meaning, clarification |

### POSTPONE_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 5 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 32 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 51 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 70 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 83 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 92 | UNKNOWN | intent, entities, ordered meaning |

### PRIORITIZE_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 4 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 31 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 50 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 69 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 89 | UNKNOWN | intent, entities, ordered meaning, clarification |

### PROJECT_STATUS

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 19 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 38 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 58 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 76 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 88 | UNKNOWN | intent, entities, ordered meaning, clarification |

### REFLECTION

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 20 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 41 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 78 | UNKNOWN | intent, entities, ordered meaning, clarification |

### RENAME_TASK

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 3 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 30 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 49 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 68 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 87 | UNKNOWN | intent, entities, ordered meaning, clarification |
| 96 | UNKNOWN | intent, entities, ordered meaning |

### UNKNOWN

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 9 | UNKNOWN | clarification |
| 10 | UNKNOWN | clarification |
| 11 | UNKNOWN | clarification |
| 12 | UNKNOWN | clarification |
| 13 | UNKNOWN | clarification |
| 21 | UNKNOWN | clarification |
| 22 | UNKNOWN | clarification |
| 23 | UNKNOWN | clarification |
| 24 | UNKNOWN | clarification |
| 25 | UNKNOWN | clarification |
| 42 | UNKNOWN | clarification |
| 43 | UNKNOWN | clarification |
| 44 | UNKNOWN | clarification |
| 45 | UNKNOWN | clarification |
| 62 | UNKNOWN | clarification |
| 63 | UNKNOWN | clarification |
| 64 | UNKNOWN | clarification |
| 65 | UNKNOWN | clarification |
| 79 | UNKNOWN | clarification |
| 80 | UNKNOWN | clarification |

### WHAT_SHOULD_I_DO

| Row | Predicted intent | Mismatch |
|---:|---|---|
| 61 | UNKNOWN | intent, entities, ordered meaning, clarification |

Full texts, raw predictions, costs, confusion matrix and individual label defects are in `baseline_rules/`.

## Public train-only pool

Total: 49306. All three requested MASSIVE locales are present locally.

| Source / locale / original split | Emitted rows |
|---|---:|
| CLINC150:en:oos_train | 100 |
| CLINC150:en:train | 15000 |
| MASSIVE:en-US:train | 11402 |
| MASSIVE:hi-IN:train | 11402 |
| MASSIVE:ta-IN:train | 11402 |

| Intent | Rows |
|---|---:|
| CHECK_CALENDAR | 1698 |
| CHECK_PERSON | 381 |
| CREATE_ALARM | 546 |
| CREATE_CALENDAR_EVENT | 2430 |
| SEND_MESSAGE | 1062 |
| UNKNOWN | 25325 |
| UNSUPPORTED | 17864 |

All official dev/validation/test splits are excluded. CLINC OOS/OOD is UNKNOWN.
Preserved unsupported mappings remain UNSUPPORTED. Candidate intent labels still need row review;
entities were not imported and no slot-training eligibility is claimed.
Pool SHA-256: `943292a93b120002ed6dcb27918436938f3ef415b3b051a19b35959416cf8b85`. Artifact: `build/ml-day1/public_train_pool.jsonl`.

## Verification and limits

- 19 unit/JVM integration tests passed before the dev baseline, including the appended-line leakage negative test.
- Pinned MiniLM CPU ONNX runtime verified offline on a synthetic pair; exact-repeat inference passed. No fit or tune.
- Two harness runs over the same saved prediction file are byte-identical (including metrics, failures, confusion matrix).
- The full APK/Room/Android execution path was not run. Exporter uses production domain agents and policy with in-memory ports.
- Read ports measure dispatch, not retrieved answer contents. Planner adapter supports fixture tasks without estimates only.
- No actual write acceptance/executor is available in the exporter.
- Current multi-intent behavior remains one whole request; production may swallow subsequent clauses into a task title.
- Independent-test file, frozen-test hash/manifest, train-author manifest and adoption evidence: **not found**.
- Live Day 5 CI gate intentionally exits nonzero when explicit external paths are absent; no test path is discovered.
- CI hosted execution: **not found**; local contract and integration tests are verified.
- PolicyEngine, safety core and legacy evidence were not modified.
