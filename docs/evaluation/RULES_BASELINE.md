# METIS Rules Baseline Evaluation Harness

This document describes the rules baseline evaluation harness used to evaluate the production English grammar (`EnglishRules` and `GraphReadGrammarV18`) against the NLU test datasets.

## Artifacts
- **Harness Source**: `docs/evaluation/RulesBaselineEvaluationTest.kt`
- **Output Predictions (JSON)**:
  - `ml/nlu_v1/heldout/rules_preds_test.json` (128 rows, test.csv)
  - `ml/nlu_v1/heldout/rules_preds_smoke.json` (100 rows, raghav_test_100.csv)
- **Output Predictions (CSV)**:
  - `ml/nlu_v1/heldout/rules_preds_test.csv` (128 rows, test.csv)
  - `ml/nlu_v1/heldout/rules_preds_smoke.csv` (100 rows, raghav_test_100.csv)

## Exact Regeneration Instructions
To re-run the rules baseline evaluation through Gradle:

1. Copy the harness to the unit test source tree:
   ```bash
   cp docs/evaluation/RulesBaselineEvaluationTest.kt app/src/test/java/dev/metis/agent/evaluation/
   ```

2. Execute the test runner task:
   ```bash
   ./gradlew :app:testDebugUnitTest --tests dev.metis.agent.evaluation.RulesBaselineEvaluationTest
   ```

3. Remove the temporary harness from `app/` to ensure no changes to production application code:
   ```bash
   rm app/src/test/java/dev/metis/agent/evaluation/RulesBaselineEvaluationTest.kt
   ```

## Rules Mapping Specification (per `intents.md`)
- `UPDATE_TASK` with `'prioritize task:'` $\rightarrow$ `PRIORITIZE_TASK`
- `UPDATE_TASK` with `'rename task:'` $\rightarrow$ `RENAME_TASK`
- `WHAT_DID_I_PROMISE` $\rightarrow$ `PERSON_COMMITMENTS`
- `GraphReadKindV18.PROJECT_PENDING` $\rightarrow$ `PROJECT_STATUS`
- Direct 1:1 mappings for `CREATE_TASK`, `COMPLETE_TASK`, `DELETE_TASK`, `POSTPONE_TASK`, `CREATE_REMINDER`, `CREATE_PROMISE`, `CHECK_CALENDAR`, `CHECK_TASKS`, `CHECK_MEMORY`, `PLAN_DAY`, `WHAT_SHOULD_I_DO`, `CREATE_TIMER`, `CREATE_ALARM`, `CALL_PERSON`, `SEND_MESSAGE`.
- Negated and unmatched utterances fall back to `UNKNOWN`.
