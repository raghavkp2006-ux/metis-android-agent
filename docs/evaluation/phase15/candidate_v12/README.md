# Phase 15 v12 development candidate

V12 corrects deterministic retrieval scope and adds closed recall productions. Its separately versioned offline classifier experiment remains **NO_GO**, disabled and unbundled. This is exposed regression/development evidence, not a fresh independent human evaluation.

The [50 submitted requests](user-development.json) are preserved verbatim and tested individually, with no conversation history, as explicitly confirmed by the user. Intent, expected scope and outcome annotations are **assistant drafts**, not human approval. These requests informed development; excluding their rows from fitting does not make their diagnostic scores independent. Review the proposed labels and constraints in [user-review.md](user-review.md).

## Code behavior

`ReadGrammarV12` gives complete note/promise recall wrappers precedence over generic topic recall. “Remind me of my notes about TOPIC” searches TOPIC; “Remind me of the promises I made to PERSON” uses the existing exact, unique saved-person lookup. This fixes ten v11 scope mismatches without changing v10 source artifacts.

Closed productions also cover recalling a named plan, a coordinated literal person/topic subject, statements attributed to a named person and notes for a meeting with a named person. The memory subjects retain attribution and descriptive constraints. A name in a memory subject does not assert a contact identity. Article annotations follow the existing convention while complete retrieval text, including titles such as “The Who,” remains intact. Annotation limits remain 200 UTF-16 units (100 for promise recipients); complete queries remain bounded at 256 and requests at 512. No truncation is used to admit an oversized request.

`ReadSafetyV12` validates the original request before context construction, and revalidates intent, exact spans and the complete query before specialist dispatch. Forced classifier labels and forged extraction values cannot grant read authority. External negation, appended actions, compounds, unsupported filters and unresolved references retain the prior boundary. Recognized complete calendar requests return unavailable with no context/read calls. Repository interfaces, storage schema and search candidate limits are unchanged.

The submitted batch currently has four bounded memory reads (9, 19, 20, 27), four calendar-unavailable forms (23–26), and 42 requests requiring clarification or rejection. Semantic intent recognition alone cannot implement task topic/status/date filters, promise action-content filters, unnamed recipients, contextual references or multiple requests. Their full original constraints remain available in follow-ups. An honest bounded no-match is a valid read result.

## Data and method

Before new grammar implementation, six separate assistant-authored families defined 30 valid examples paired with date/count filters, outer negation, actions, compounds and malformed text, plus five unresolved references: 215 rows. Exact earlier-data duplicates are excluded from the separately authored classifier families. The complete native/Python replay contains 6,603 requests, including the frozen benchmark, v7/v8/v9/v10/v11 development data and this increment. Historical candidates, frozen approvals and first evaluation results remain byte-for-byte unchanged.

The [classifier protocol](protocol.json) predates fitting: a learned READ/UNKNOWN head and a separate four-intent head combine probabilities before bounded scalar temperature calibration. Both heads use word/character TF-IDF with optional request-derived cues. No parser outputs, example IDs, expected entities or query annotations are features. Four predeclared configurations use C=1/4 with/without cues; selection maximizes macro F1 subject to zero sampled UNKNOWN accepts at cutoffs 0.90–0.99, otherwise abstaining.

There are 898 new synthetic classifier rows after two exact exclusions. Combined with retained v11 partitions, fitting uses 2,700 rows, calibration 648 and selection 650; families and new topic/person slots are disjoint. Only the 150 new synthetic test rows are scored after selection. The previously inspected v11 test and all 50 user queries are separately labelled exposed diagnostics. User rows enter none of training, calibration or selection. Numeric exports reconstruct both learned heads and calibrated probabilities; a scored version cannot silently change weights, settings, model/data or protocol bindings.

## Validation record

Measured on 2026-10-12, without retuning from reported test results:

| Measurement | Result |
| --- | ---: |
| Retained v11 / v12 thresholded macro F1 on the same 150 new synthetic test requests | 0.6410 / **0.8929** |
| V12 raw argmax macro F1 on those 150 requests | 1.0000 |
| UNKNOWN false accepts on those requests | 0 for both candidates |
| V12 thresholded promise recall / task recall | 50.0% / 77.8% |
| Exposed v11 500-request test diagnostic, retained v11 / v12 thresholded macro F1 | 0.7872 / 0.9482 |
| User batch, assistant-draft labels: retained v11 / v12 thresholded macro F1 | 0.2948 / **0.4639** |
| Correctly scoped native/Python reads across the 6,603-request replay | 509 |
| Prohibited requests / forbidden reads | 5,674 / **0** |
| Forced read-label attempts on prohibited rows / reads | 22,696 / **0** |
| Previously exposed approved benchmark reads | 23 / 23 |
| Retained v9 / v7 / v8 / v10 development reads | 299 / 27 / 18 / 78, all preserved |
| V11 correctly scoped reads, before / after scope corrections | 20 / 30 |
| New assistant development reads | 30 / 30 |
| User batch actual dispositions | 4 answers, 40 follow-ups, 2 denied, 4 calendar unavailable |
| Historical artifact files unchanged, raw-byte comparison against preceding commit | 90 / 90 |
| JVM / targeted physical-device JUnit cases | 119 / 58 passed |
| Direct Python regressions / retained v7 tests nested within historical suite | 110 / 6 passed |

The selected configuration is C=4 with request-derived cues, temperature 0.9284184491 and cutoff 0.98. Selection macro F1 is 0.8576 with zero sampled UNKNOWN false accepts. The new synthetic test has 13 known-intent abstentions; raw argmax perfection does not justify lowering the selected cutoff after seeing the test. Its NLL is 0.04825, Brier score 0.02422 and top-label ten-bin ECE 0.03582, all synthetic diagnostics. The 0.90 thresholded F1 gate still fails, alongside independent evaluation, representative calibration, useful ML read gain and Android model parity/resources. **NO_GO remains explicit.**

Entities, complete queries, actual dispositions and constraint retention are reported separately from semantic classifier scores. The v11 corpus still contains 420 desired read paraphrases that production conservatively clarifies; v12 does not claim complete natural-language coverage. Two default context calls on existing v11 task-action examples remain action-path behavior with no reads/effects; all prohibited forced-read-label attempts, all original benchmark negatives and all 46 user nonreads cause zero context/read calls. The original 14 forbidden reads remain blocked.

JVM/Python regressions, native source/input/exporter bindings, historical integrity, debug/release lint, detekt, debug/release APK assembly and instrumentation compilation passed. The legacy v2–v6 evaluator runs use fresh native snapshots and ignored output; historical behavior tests retain v7–v11 assertions while current-source certification uses v12. Full reports, native snapshots, disabled numeric exports, device logs and test XML stay under ignored `app/build/reports/phase15/v12/`. The first scoring output is retained in `first-score/`.

The physical-device test uses isolated synthetic encrypted databases with matching records and decoys, exact queries, duplicate saved people and unchanged-record assertions. One initial test fixture exceeded the existing 20-candidate search bound and honestly returned no match. Fixtures were split into batches of at most 18 memories; production bounds were preserved. The initial failed run is retained separately. These are deterministic Android measurements, not model latency/RAM/battery measurements. Earlier full-suite permission/setup failures remain documented in the [device record](../../../PHASE_15_DEVICE_VERIFICATION.md).

## Reproduction

Use Python 3.12 with the pinned parent requirements and JDK 17/Android SDK 36. From the repository root:

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v12/historical_tests.py
.\.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/candidate_v12 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/candidate_v12/regression-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v12/native-regressions.tsv"
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v12/v12_evaluate.py
```

An optional `--previous-export app/build/reports/phase15/v11/disabled-candidate-v11.json` compares the retained v11 numeric export on the **same 150 new test rows**, without retraining v11. CI does not require locally retained ignored exports and reports that comparison unavailable if absent. Historical manifests certify historical production; current CI uses the v12 manifest while retaining historical behavior tests and replacing only obsolete source-certification assertions.

## Remaining gates and user input

Review/correct the draft labels, intended saved-record scope and permitted constraints in [user-review.md](user-review.md). Those corrections can support a later version's development/calibration work; this already-exposed batch cannot become a blind evaluation. After a subsequent candidate freeze, obtain fresh separately authored and reviewed queries for a one-time independent evaluation. Representative human calibration, useful safe-read gains, repository validation and Android model parity/resource measurements remain separate gates. Synthetic F1 improvement and connected-device deterministic tests do not authorize ML enablement.
