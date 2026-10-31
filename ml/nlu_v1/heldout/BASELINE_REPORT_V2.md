# METIS NLU Baseline Evaluation Report V2

> **Single evaluation pass on frozen settings.**
> Evaluated: (A) Human test set `test.csv` (author SM, 128 rows); (B) AI-written smoke test `heldout/raghav_test_100.csv` (100 rows).
> Model tuned strictly on tuning data (`dev_clean.csv` + `data.csv` author VN, 445 rows). No tuning performed on test sets.

## 1. Audit & Invalidation of Previous Baseline

The prior baseline reported in `BASELINE_REPORT.md` is **INVALID and SUPERSEDED**.

### 1.1 Previous Model Class List
The model evaluated in the previous run (`model.json`) possessed only **16 classes**:
```json
[
  "CHECK_CALENDAR",
  "CHECK_TASKS",
  "COMPLETE_TASK",
  "CREATE_PROMISE",
  "CREATE_REMINDER",
  "CREATE_TASK",
  "DELETE_TASK",
  "PERSON_COMMITMENTS",
  "PLAN_DAY",
  "POSTPONE_TASK",
  "PRIORITIZE_TASK",
  "PROJECT_STATUS",
  "REFLECTION",
  "RENAME_TASK",
  "UNKNOWN",
  "WHAT_SHOULD_I_DO"
]
```

### 1.2 Root Cause of Invalidation
The 5 newly ratified intents were **completely missing** from the output space of `model.json`:
- **Missing classes (5)**: `CALL_PERSON, CHECK_MEMORY, CREATE_ALARM, CREATE_TIMER, SEND_MESSAGE`
- Because these 5 classes were not in the model's vocabulary, all 100 utterances in `raghav_test_100.csv` were structurally guaranteed to be rejected as `UNKNOWN`.
- The previous report's '100% rejection rate' on Section B was vacuous and misleading.

## 2. Dataset Assembly & 21-Class Model Training

A new multi-class logistic regression model was trained on the combined dataset pool:
- `ml/nlu_v1/generated/train_pool_clean.csv`: **2,030 rows** (audited synthetic & web-derived pool across all 21 classes)
- `ml/nlu_v1/data.csv` (authors `AK`, `PR`, `DJ`, `Raghav`): **484 rows** (campus ground truth, mapped legacy tag `multi` -> `multi_intent`)
- **Total Training Corpus**: **2514 rows** across **21 classes**

### 2.1 Training Row Count per Label (Target: >= 60 rows)

| Intent Label | Category | Training Rows | Compliance Status |
|---|---|---:|---|
| `CALL_PERSON` | ACTION | 92 | **PASS (>= 60 rows)** |
| `CHECK_CALENDAR` | READ | 129 | **PASS (>= 60 rows)** |
| `CHECK_MEMORY` | READ | 78 | **PASS (>= 60 rows)** |
| `CHECK_TASKS` | READ | 129 | **PASS (>= 60 rows)** |
| `COMPLETE_TASK` | FORBIDDEN WRITE | 125 | **PASS (>= 60 rows)** |
| `CREATE_ALARM` | ACTION | 80 | **PASS (>= 60 rows)** |
| `CREATE_PROMISE` | ACTION | 118 | **PASS (>= 60 rows)** |
| `CREATE_REMINDER` | ACTION | 138 | **PASS (>= 60 rows)** |
| `CREATE_TASK` | ACTION | 115 | **PASS (>= 60 rows)** |
| `CREATE_TIMER` | ACTION | 86 | **PASS (>= 60 rows)** |
| `DELETE_TASK` | FORBIDDEN WRITE | 133 | **PASS (>= 60 rows)** |
| `PERSON_COMMITMENTS` | READ | 118 | **PASS (>= 60 rows)** |
| `PLAN_DAY` | READ | 122 | **PASS (>= 60 rows)** |
| `POSTPONE_TASK` | FORBIDDEN WRITE | 137 | **PASS (>= 60 rows)** |
| `PRIORITIZE_TASK` | FORBIDDEN WRITE | 138 | **PASS (>= 60 rows)** |
| `PROJECT_STATUS` | READ | 117 | **PASS (>= 60 rows)** |
| `REFLECTION` | ACTION | 119 | **PASS (>= 60 rows)** |
| `RENAME_TASK` | FORBIDDEN WRITE | 130 | **PASS (>= 60 rows)** |
| `SEND_MESSAGE` | ACTION | 71 | **PASS (>= 60 rows)** |
| `UNKNOWN` | REJECT | 226 | **PASS (>= 60 rows)** |
| `WHAT_SHOULD_I_DO` | READ | 113 | **PASS (>= 60 rows)** |
| **TOTAL** | — | **2514** | **All 21 classes >= 71 rows** |

### 2.2 Model Class List (21 classes)
```json
[
  "CALL_PERSON",
  "CHECK_CALENDAR",
  "CHECK_MEMORY",
  "CHECK_TASKS",
  "COMPLETE_TASK",
  "CREATE_ALARM",
  "CREATE_PROMISE",
  "CREATE_REMINDER",
  "CREATE_TASK",
  "CREATE_TIMER",
  "DELETE_TASK",
  "PERSON_COMMITMENTS",
  "PLAN_DAY",
  "POSTPONE_TASK",
  "PRIORITIZE_TASK",
  "PROJECT_STATUS",
  "REFLECTION",
  "RENAME_TASK",
  "SEND_MESSAGE",
  "UNKNOWN",
  "WHAT_SHOULD_I_DO"
]
```

## 3. Hyperparameter Tuning & Threshold Calibration (Tuning Data Only)

Tuning was conducted exclusively on the combined validation set:
- `ml/nlu_v1/generated/dev_clean.csv` (317 rows) + `ml/nlu_v1/data.csv` author `VN` (128 rows) = **445 tuning rows**.
- **No test data** (`test.csv`, `dev_own.csv`, `raghav_test_100.csv`) was ever used in training or tuning.

### 3.1 Hyperparameter Grid Search (C and class_weight)

| Regularization C | class_weight | Dev Accuracy | Dev Macro-F1 | Status |
|---:|---|---:|---:|---|
| 0.50 | `balanced` | 95.06% | 0.9527 | |
| 1.00 | `balanced` | 95.96% | 0.9597 | |
| 2.00 | `balanced` | 95.96% | 0.9597 | |
| 5.00 | `balanced` | 96.40% | 0.9647 | |
| 10.00 | `balanced` | 97.08% | 0.9711 | |
| 15.00 | `balanced` | 97.30% | 0.9738 | |
| 20.00 | `balanced` | 97.53% | 0.9758 | **[SELECTED BEST]** |
| 0.50 | `None` | 94.83% | 0.9516 | |
| 1.00 | `None` | 96.18% | 0.9622 | |
| 2.00 | `None` | 96.18% | 0.9615 | |
| 5.00 | `None` | 96.85% | 0.9672 | |
| 10.00 | `None` | 97.08% | 0.9699 | |
| 15.00 | `None` | 97.08% | 0.9699 | |
| 20.00 | `None` | 97.53% | 0.9749 | |

> **Selected Parameters**: `C = 20.0`, `class_weight = 'balanced'`. Feature extractor: Dual-channel TF-IDF (word n-grams 1-2 + char n-grams 2-5).

### 3.2 Threshold Calibration (False-Action Rate <= 1.0%)

Per prompt specification, the reject threshold $\tau$ was chosen on tuning data as the **lowest threshold with a false-action rate of 1.0% or less**:
- A **false action** is defined as any write or device action (Tier 1 forbidden write, Tier 2 write, or external action) predicted when the true label is a read intent, `UNKNOWN`, or a different action.

| Threshold $\tau$ | False Actions | False-Action Rate | Forbidden Errors | Accept Rate | Accuracy on Accepted |
|---:|---:|---:|---:|---:|---:|
| 0.00 | 6 | 1.35% | 1 | 93.03% | 97.34% |
| 0.10 | 6 | 1.35% | 1 | 93.03% | 97.34% |
| 0.20 | 6 | 1.35% | 1 | 92.58% | 97.57% |
| 0.23 | 5 | 1.12% | 0 | 92.36% | 97.81% |
| 0.24 **[CHOSEN THRESHOLD $\tau=0.24$]** | 4 | 0.90% | 0 | 92.13% | 98.05% |
| 0.25 | 4 | 0.90% | 0 | 91.69% | 98.04% |
| 0.30 | 1 | 0.22% | 0 | 90.34% | 99.00% |
| 0.40 | 0 | 0.00% | 0 | 88.09% | 100.00% |
| 0.50 | 0 | 0.00% | 0 | 87.64% | 100.00% |

> **Operating Threshold**: **$\tau = 0.24$** achieves **0.90% false-action rate** (4/445 rows) and **0 forbidden errors** on tuning data.

## 4. SHA-256 Manifest Verification (Pre- and Post-Evaluation)

The protected manifest `ml/nlu_v1/heldout/sha256_manifest.txt` was verified immediately before evaluation and re-verified immediately after evaluation.

| Protected File | Expected SHA-256 Digest | Pre-Eval Status | Post-Eval Status |
|---|---|---|---|
| `model.json` | `cb70f8ebd7579eb84ba3adba...` | `PASS` | `PASS` |
| `predict.py` | `4064ff0ce410bd7e8a7c9cfc...` | `PASS` | `PASS` |
| `reference_tests.json` | `840db37162334da8dbfdbea7...` | `PASS` | `PASS` |
| `test.csv` | `28a4cd6958a21c3ddafa25bb...` | `PASS` | `PASS` |
| `heldout\raghav_test_100.csv` | `fbb69b15caa2207e5f5ab997...` | `PASS` | `PASS` |
| `config.yaml` | `fa64f210ad066b2e80b07746...` | `PASS` | `PASS` |
| `run_all.py` | `21c2deb11c76eff999e41222...` | `PASS` | `PASS` |

> **Verification Result**: **ALL PASS**. Zero protected files were modified or corrupted.

## 5. Rules Baseline Implementation (JVM Harness)

Per prompt instructions, the rules baseline was executed directly against the app's production Kotlin grammar through a dedicated JUnit harness:
- **Harness**: `app/src/test/java/dev/metis/agent/evaluation/RulesBaselineEvaluationTest.kt`
- **Execution**: Executed in JVM runtime via `./gradlew testDebugUnitTest --tests dev.metis.agent.evaluation.RulesBaselineEvaluationTest`.
- **Grammar Modules**: `EnglishRules` (closed regex grammar) + `GraphReadGrammarV18` (graph topic/project queries) + `ReadGrammarV8` (memory queries).
- **Label Mapping per `intents.md`**:
  - `UPDATE_TASK` with `'prioritize task:'` -> `PRIORITIZE_TASK`; `'rename task:'` -> `RENAME_TASK`
  - `WHAT_DID_I_PROMISE` -> `PERSON_COMMITMENTS`
  - `GraphReadKindV18.PROJECT_PENDING` -> `PROJECT_STATUS`
  - Direct 1:1 maps for `CREATE_TASK`, `COMPLETE_TASK`, `DELETE_TASK`, `POSTPONE_TASK`, `CREATE_REMINDER`, `CREATE_PROMISE`, `CHECK_CALENDAR`, `CHECK_TASKS`, `CHECK_MEMORY`, `PLAN_DAY`, `WHAT_SHOULD_I_DO`, `CREATE_TIMER`.
  - Unmatched / negated utterances fall back to `UNKNOWN`.

## 6. Section A: Human Test Set — `test.csv` (Author SM, 128 rows)

Natural human student utterances collected on campus across 16 domain intents. Evaluated on the 21-class taxonomy.

### 6.1 Headline Systems Comparison

| System | Operating Mode | Accuracy | Macro-F1 | False-Action Rate (95% Wilson CI) | Forbidden Errors | Missed Command Rate | Accept Rate | Acc on Accepted |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| **Model** | No threshold (argmax) | 96.09% | 0.9607 | 4/128 (3.12%, [1.22%, 7.76%]) | 2 | 0/120 (0.00%) | 96.88% | 95.97% |
| **Model** | Tuned threshold ($\tau=0.24$) | 96.88% | 0.9704 | 3/128 (2.34%, [0.80%, 6.66%]) | 1 | 0/120 (0.00%) | 96.09% | 96.75% |
| **Rules Baseline** | JVM EnglishRules | 8.59% | 0.0492 | 0/128 (0.00%, [0.00%, 2.91%]) | 0 | 117/120 (97.50%) | 2.34% | 100.00% |
| **Hybrid (Rules first)** | No threshold | 96.09% | 0.9607 | 4/128 (3.12%, [1.22%, 7.76%]) | 2 | 0/120 (0.00%) | 96.88% | 95.97% |
| **Hybrid (Rules first)** | Tuned threshold ($\tau=0.24$) | 96.88% | 0.9704 | 3/128 (2.34%, [0.80%, 6.66%]) | 1 | 0/120 (0.00%) | 96.09% | 96.75% |

### 6.2 Per-Label Precision, Recall, and F1 (Human Test — Model No Threshold)

| Intent Label | Precision | Recall | F1 Score | Ground Truth Support |
|---|---:|---:|---:|---:|
| `CHECK_CALENDAR` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `CHECK_TASKS` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `COMPLETE_TASK` | 1.0000 | 0.8750 | 0.9333 | 8 |
| `CREATE_PROMISE` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `CREATE_REMINDER` | 0.8889 | 1.0000 | 0.9412 | 8 |
| `CREATE_TASK` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `DELETE_TASK` | 0.8000 | 1.0000 | 0.8889 | 8 |
| `PERSON_COMMITMENTS` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `PLAN_DAY` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `POSTPONE_TASK` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `PRIORITIZE_TASK` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `PROJECT_STATUS` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `REFLECTION` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `RENAME_TASK` | 1.0000 | 1.0000 | 1.0000 | 8 |
| `UNKNOWN` | 1.0000 | 0.5000 | 0.6667 | 8 |
| `WHAT_SHOULD_I_DO` | 0.8889 | 1.0000 | 0.9412 | 8 |

### 6.3 Top Confusion Pairs (Human Test — Model No Threshold)

| True Ground Truth | Predicted Label | Error Count | Description |
|---|---|---:|---|
| `COMPLETE_TASK` | `DELETE_TASK` | 1 | Misclassification |
| `UNKNOWN` | `WHAT_SHOULD_I_DO` | 1 | Misclassification |
| `UNKNOWN` | `DELETE_TASK` | 1 | Misclassification |
| `UNKNOWN` | `CALL_PERSON` | 1 | Misclassification |
| `UNKNOWN` | `CREATE_REMINDER` | 1 | Misclassification |

### 6.4 Worst Errors on Human Test (`test.csv`)

Listed by severity (Forbidden-tier errors -> False actions -> Out-of-scope misclassifications):

| # | Row | Category | True Intent | Predicted Label | Confidence | Utterance Text |
|---:|---:|---|---|---|---:|---|
| 1 | Row 27 | `FORBIDDEN_TIER` | `COMPLETE_TASK` | `DELETE_TASK` | 0.674 | cross off the robu order tracking task from my to-do list |
| 2 | Row 123 | `FORBIDDEN_TIER` | `UNKNOWN` | `DELETE_TASK` | 0.178 | clear everyone's tasks from the shared team workspace right now |
| 3 | Row 126 | `FALSE_ACTION` | `UNKNOWN` | `CREATE_REMINDER` | 0.629 | remnd me how to convert newton meters to foot pounds |
| 4 | Row 124 | `FALSE_ACTION` | `UNKNOWN` | `CALL_PERSON` | 0.620 | yaar harsh ko call lagake pooch le ki lab ki chabi kiske paas hai |
| 5 | Row 121 | `READ_CONFUSION` | `UNKNOWN` | `WHAT_SHOULD_I_DO` | 0.493 | i should really clean up my workbench before starting the next build |

## 7. Section B: AI-Written Smoke Test — `raghav_test_100.csv` (100 rows)

> [!CAUTION]
> **AI-WRITTEN SMOKE TEST NOTICE (OPTIMISTIC ESTIMATE)**
> This dataset was synthetically generated by Claude (`author_id = AI_CLAUDE_TEST`) to smoke-test the 5 new intents (`CREATE_TIMER`, `CREATE_ALARM`, `CALL_PERSON`, `SEND_MESSAGE`, `CHECK_MEMORY`).
> It is **NOT** a human test set. Because synthetic utterances are cleaner, more standardized, and less colloquially noisy than real user speech, **scores reported here are optimistic and represent an upper bound**.
> Results on this set are reported **strictly separately** and are **NEVER merged into a headline number with `test.csv`**.

### 7.1 Headline Systems Comparison (AI Smoke Test)

| System | Operating Mode | Accuracy | Macro-F1 | False-Action Rate (95% Wilson CI) | Forbidden Errors | Missed Command Rate | Accept Rate | Acc on Accepted |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| **Model** | No threshold (argmax) | 84.00% | 0.8920 | 8/100 (8.00%, [4.11%, 15.00%]) | 0 | 3/100 (3.00%) | 97.00% | 86.60% |
| **Model** | Tuned threshold ($\tau=0.24$) | 82.00% | 0.8799 | 7/100 (7.00%, [3.43%, 13.75%]) | 0 | 6/100 (6.00%) | 94.00% | 87.23% |
| **Rules Baseline** | JVM EnglishRules | 4.00% | 0.0712 | 0/100 (0.00%, [0.00%, 3.70%]) | 0 | 96/100 (96.00%) | 4.00% | 100.00% |
| **Hybrid (Rules first)** | No threshold | 84.00% | 0.8920 | 8/100 (8.00%, [4.11%, 15.00%]) | 0 | 3/100 (3.00%) | 97.00% | 86.60% |
| **Hybrid (Rules first)** | Tuned threshold ($\tau=0.24$) | 82.00% | 0.8799 | 7/100 (7.00%, [3.43%, 13.75%]) | 0 | 6/100 (6.00%) | 94.00% | 87.23% |

### 7.2 Per-Label Precision, Recall, and F1 (AI Smoke Test — Model No Threshold)

| Intent Label | Precision | Recall | F1 Score | Ground Truth Support |
|---|---:|---:|---:|---:|
| `CALL_PERSON` | 0.9412 | 0.8000 | 0.8649 | 20 |
| `CHECK_MEMORY` | 0.9412 | 0.8000 | 0.8649 | 20 |
| `CREATE_ALARM` | 0.9474 | 0.9000 | 0.9231 | 20 |
| `CREATE_TIMER` | 0.9500 | 0.9500 | 0.9500 | 20 |
| `SEND_MESSAGE` | 1.0000 | 0.7500 | 0.8571 | 20 |

### 7.3 Top 10 Confusion Pairs (AI Smoke Test — Model No Threshold)

| True Ground Truth | Predicted Label | Error Count | Description |
|---|---|---:|---|
| `CREATE_ALARM` | `CREATE_REMINDER` | 2 | Misclassification |
| `CALL_PERSON` | `UNKNOWN` | 2 | Misclassification |
| `CHECK_MEMORY` | `PERSON_COMMITMENTS` | 2 | Misclassification |
| `CREATE_TIMER` | `CREATE_REMINDER` | 1 | Misclassification |
| `CALL_PERSON` | `CHECK_MEMORY` | 1 | Misclassification |
| `CALL_PERSON` | `CREATE_TIMER` | 1 | Misclassification |
| `SEND_MESSAGE` | `CHECK_CALENDAR` | 1 | Misclassification |
| `SEND_MESSAGE` | `PROJECT_STATUS` | 1 | Misclassification |
| `SEND_MESSAGE` | `CALL_PERSON` | 1 | Misclassification |
| `SEND_MESSAGE` | `CREATE_ALARM` | 1 | Misclassification |

### 7.4 20 Worst Errors on AI-Written Smoke Test (`raghav_test_100.csv`)

Ranked by severity (False actions on multi-intent commands -> high-confidence action confusions -> missed commands):

| # | Row | Category | True Intent | Predicted Label | Confidence | Utterance Text |
|---:|---:|---|---|---|---:|---|
| 1 | Row 33 | `FALSE_ACTION` | `CREATE_ALARM` | `CREATE_REMINDER` | 0.954 | wkae me at 8:30 |
| 2 | Row 56 | `FALSE_ACTION` | `CALL_PERSON` | `CREATE_TIMER` | 0.941 | call dad and set a timer for 10 minutes |
| 3 | Row 76 | `FALSE_ACTION` | `SEND_MESSAGE` | `CREATE_ALARM` | 0.878 | message Dad I'll be home by 8 and set an alarm for 7 |
| 4 | Row 73 | `FALSE_ACTION` | `SEND_MESSAGE` | `CALL_PERSON` | 0.697 | txt Sana taht i'll call later |
| 5 | Row 36 | `FALSE_ACTION` | `CREATE_ALARM` | `CREATE_REMINDER` | 0.606 | wake me at 5:30 tomorrow and call Ria at 6 |
| 6 | Row 80 | `FALSE_ACTION` | `SEND_MESSAGE` | `CREATE_PROMISE` | 0.565 | Message Professor Rao that I will submit tomorrow. |
| 7 | Row 15 | `FALSE_ACTION` | `CREATE_TIMER` | `CREATE_REMINDER` | 0.466 | start a 20 minute timer and remind me to drink water |
| 8 | Row 82 | `FALSE_ACTION` | `CHECK_MEMORY` | `CREATE_PROMISE` | 0.200 | meri bike ki service date kab note ki thi batao |
| 9 | Row 86 | `MISSED_COMMAND` | `CHECK_MEMORY` | `UNKNOWN` | 0.181 | paneer recipe jo maine yaad ke liye rakhi thi dikhao |
| 10 | Row 53 | `MISSED_COMMAND` | `CALL_PERSON` | `UNKNOWN` | 0.133 | phoen mom |
| 11 | Row 52 | `MISSED_COMMAND` | `CALL_PERSON` | `UNKNOWN` | 0.217 | calll Rohit |
| 12 | Row 84 | `READ_CONFUSION` | `CHECK_MEMORY` | `PERSON_COMMITMENTS` | 0.596 | Rahul ne book ka naam kya suggest kiya tha, maine note kiya tha |
| 13 | Row 81 | `READ_CONFUSION` | `CHECK_MEMORY` | `PERSON_COMMITMENTS` | 0.405 | maine wifi password kya save kiya tha |
| 14 | Row 55 | `READ_CONFUSION` | `CALL_PERSON` | `CHECK_MEMORY` | 0.341 | call Arjun and ask him about the notes |
| 15 | Row 67 | `READ_CONFUSION` | `SEND_MESSAGE` | `CHECK_CALENDAR` | 0.274 | Dev ko likh do ki meeting 4 baje hai |
| 16 | Row 72 | `READ_CONFUSION` | `SEND_MESSAGE` | `PROJECT_STATUS` | 0.252 | mesage Alok: where are you |

## 8. Architectural Analysis & Deployment Recommendation

### 8.1 Comparative Analysis
1. **Rules-Only Baseline Fails on Natural Language**:
   - The production rules engine achieved only **8.59% accuracy** on the human test set and **4.00%** on the smoke test.
   - While it achieved a 0% false-action rate, it rejected **97.50%** of valid in-scope student requests as `UNKNOWN`.
   - Rigid keyword templates cannot cope with natural phrasing, Hindi/Hinglish vocabulary, voice hesitations, or typos.

2. **Standalone Model Excels in Generalization but Risks False Actions**:
   - The 21-class logistic regression model achieved **96.09% accuracy** and **0.9607 Macro-F1** on human test data with **0% missed commands**.
   - However, without thresholding, out-of-scope commands like `'clear everyone\'s tasks...'` slip through as forbidden writes (`DELETE_TASK`).

3. **Hybrid Architecture with Tuned Threshold Delivers Optimal Safety and Coverage**:
   - Operating at the calibrated reject threshold (**$\tau = 0.24$**) reduces false actions from 3.12% to **2.34%** on human data and suppresses forbidden-tier errors.
   - It maintains an **accept rate of 96.09%** and **96.75% accuracy on accepted rows**, with zero missed commands on valid student requests.

### 8.2 Final Recommendation: Deploy HYBRID Architecture with $\tau = 0.25$
Deploy the **Hybrid (Rules First -> ML Fallback at $\tau = 0.25$)** architecture:
1. **Rules Gate (Stage 1)**: For exact matching command templates (e.g., `'create task: ...'`, `'delete task: ...'`), the rules engine fires with 100% precision.
2. **ML Fallback (Stage 2)**: For all queries where rules return `UNKNOWN`, invoke the 21-class linear model. If top class confidence $\ge 0.24$, route to the agent; otherwise, abstain and ask for clarification.
3. **Confirmation Gate (Stage 3)**: Any predicted Tier 1 intent (`COMPLETE_TASK`, `DELETE_TASK`, `POSTPONE_TASK`, `PRIORITIZE_TASK`, `RENAME_TASK`) must require an interactive confirmation step before database mutation.

