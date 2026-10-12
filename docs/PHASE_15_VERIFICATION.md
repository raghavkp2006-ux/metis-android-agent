# Phase 15 development evaluation and model no-go

Current continuation, 2026-10-12: [v14](evaluation/phase15/candidate_v14/README.md) adds guarded memory-recall wording and explicitly selects its gate in the Android app while retaining historical constructor behavior. The new 214-query exposed batch improves from 1 to 17 bounded reads; unresolved references and unsupported filters still clarify. A separately fitted disabled classifier improves thresholded macro-F1 from retained v12's 0.6416 to 0.7110 on the same 315 new synthetic test requests, but two UNKNOWN false accepts keep ML NO_GO; both are blocked by the read gate. Human labels remain drafts with six user-confirmed meaning clarifications. Validation passed 132 JVM tests, 47 direct Python tests, 9,366 native/Python regressions, lint/Detekt, debug/release builds and eight targeted CPH2691 Android 16 tests. Phone testing also exposed and verified a fix for isolated emoji-mark search tokens. Historical candidates, original approvals/results and intake 004 remain unchanged. See the [v14 validation record](evaluation/phase15/candidate_v14/regression-report.md); representative human calibration, fresh unseen evaluation, useful safe ML read gains and Android model measurements remain pending.

Current continuation, 2026-10-12: [v13](evaluation/phase15/candidate_v13/README.md) adds a built-in local calendar using existing encrypted accepted schedule blocks, with month/day navigation and manual event editing. Closed calendar read forms now answer the four calendar questions in the previously exposed 50-query batch; other calendars remain outside scope. Validation passed 126 JVM tests, 42 historical Python tests, six targeted API 34 emulator tests, lint/Detekt and debug/release builds. After reconnection, the same six targeted tests also passed on the CPH2691 physical phone running Android 16 / API 36, using isolated fixtures without clearing installed user data. Earlier candidate results, approvals and first human evaluation remain frozen, and ML stays disabled. See the [v13 regression record](evaluation/phase15/candidate_v13/regression-report.md) for measured scope. The next macro-F1 measurement needs a frozen candidate and fresh human-reviewed holdout queries.

Current continuation, 2026-10-12: [v12](evaluation/phase15/candidate_v12/README.md) fixes ten note/promise scope mismatches, adds closed saved-record recall forms and records 50 user-submitted requests as separate exposed development cases with assistant-draft labels. On the same 150 new synthetic test requests, retained v11 / v12 thresholded macro F1 is 0.6410 / 0.8929; on the user's unapproved draft labels it is 0.2948 / 0.4639. ML remains disabled/unbundled and NO_GO. All original supported reads are preserved, with zero forbidden reads across 5,674 nonread regressions. Final validation passed 119 JVM and 58 targeted physical-device tests, including the 50-query batch against isolated synthetic records, plus Python regressions, lint/detekt and debug/release builds. The broader device suite remains outside this passing claim. Review [draft query labels and constraints](evaluation/phase15/candidate_v12/user-review.md) before future calibration work; fresh independent evaluation and Android model measurements remain unmet. Historical sections below retain their original scope and results.

Status on 2026-10-10: the bounded candidate evaluation is finished with `NO_GO`. The ML adoption gates have not passed. The Android app retains its deterministic language rules; no trained model or ML runtime is packaged or enabled.

Current continuation, 2026-10-11: [v10](evaluation/phase15/read_coverage_v10/README.md) completes all 23 approved exposed reads, all 299 labelled v9 development reads and retained v7/v8 positives through deterministic parsing. It preserves complete literal subjects and blocks all prohibited reads before context. ML remains disabled; this is exposed regression evidence. The [v8](evaluation/phase15/read_coverage_v8/README.md) and [v9](evaluation/phase15/classifier_v9/README.md) candidates, the first failed human evaluation, approvals and results remain unchanged. V9's recorded F1 of 0.4390 and zero added safe reads still imply NO_GO for that classifier. The sections below remain the historical v2 experiment record.

Device follow-up, 2026-10-12: [physical-device verification](PHASE_15_DEVICE_VERIFICATION.md) passed 57 read/storage tests, including all seven language-read cases, after correcting one invalid test fixture. The broader instrumentation run still has permission/setup and suite-stability failures; ML remains disabled. The original 2026-10-11 device-unexecuted statement records that earlier run and is superseded only for the explicitly measured device scope.

Classifier follow-up, 2026-10-12: the separate [v11 development experiment](evaluation/phase15/classifier_v11/README.md) improves thresholded macro F1 from retained v9's 0.3908 to 0.7872 on the same 500 new assistant-authored test requests. Five UNKNOWN intent false accepts remain, all blocked by the read gate; there is no added safe read coverage. New positive paraphrases expose ten existing parser/entity/query scoping mismatches. ML remains NO_GO, with no new human evaluation or Android model measurement. Historical candidates and results are unchanged; [human data requirements](evaluation/phase15/classifier_v11/human-data-requirements.md) separate development/calibration input from later unseen evaluation.

## Delivered

The repository now has a reproducible development experiment, a native JVM baseline runner, versioned synthetic data with integrity hashes, validation-only confidence calibration, safety/resolution fixtures, and explicit adoption gates. The CI build job runs the experiment and uploads its report. Successful execution means the tooling worked; the report's separate decision determines model eligibility. This candidate's decision is `NO_GO`.

## Dataset and scope

The corrected [v2 intent corpus](evaluation/phase15/intent-v2.csv) contains 1,600 synthetic utterances for seven language intents and `UNKNOWN`: 1,130 train, 240 validation, and 230 test. There are 157 family groups, split 110/24/23. Similar template stems are joined before an approximately 70/15/15 family split; integer rounding per label and merged template families explain the row ratios. The corpus has no exact normalized duplicates or families shared across splits. Each intent has at least 140 training examples.

These examples are authored in the repository with fictional slots. They contain no private messages, audio, contact exports, notifications, or user timelines. `case_kind=intent_only` rows annotate semantic intent; their empty entity arrays do not claim an entity extraction evaluation, and their product-level `expected_outcome` labels are not scored as executed actions. The separate [50-case safety corpus](evaluation/phase15/safety-v2.csv) fully annotates the expected production rule intent, UTF-16 entity spans, normalization, and the read-only language harness's status at a fixed clock and zone. Two typo probes describe the current grammar's follow-up behavior and are reported separately from unsafe acceptance.

The initial v1 experiment was inspected and corrected during development, including task-label errors and a Python transcription of the rules. Its earlier scores are superseded. The v2 families are isolated from fitting and threshold selection, but this is a single-author synthetic development evaluation with prior exploratory exposure. It is not an independent blind or real-user accuracy benchmark. The [manifest](evaluation/phase15/manifest-v2.json) records dataset/source hashes and that limitation.

## Method

The `:app:phase15RulesSnapshot` JVM task calls the production `EnglishRules` and `languageAgentOrchestrator` directly with fictional read ports. It exports all 1,650 requests and ties the snapshot to hashes of its input and relevant production source. Stale snapshots, missing predictions, incomplete label coverage, duplicate IDs/text, invalid UTF-16 annotations, family leakage, and partial dataset writes are rejected. The helper lives in unit-test sources and is excluded from application APKs.

The candidate is word TF-IDF (1–2 grams, lowercase, Unicode accent stripping, sublinear term frequency) plus logistic regression (`C=1`, `max_iter=2000`, seed 1501). Vocabulary, IDF, and classifier weights are fitted on train only. Eight validation families (80 rows) fit a scalar temperature against negative log likelihood; the other sixteen validation families (160 rows) select the lowest cutoff from 0.90 to 0.99 with no unsupported validation false accepts. Calibration and cutoff selection use disjoint families. The selected temperature is approximately 0.1349 and cutoff is 0.90, matching the orchestrator's existing high-confidence requirement.

The simulated candidate preserves recognized rule intents, unsupported input sources, and the rule engine's negation guard. It only attempts classification where the rules return unnegated `UNKNOWN`. This simulation is intent-only; it does not implement a model-compatible entity extractor or action adapter. The approach follows the documented [TF-IDF](https://scikit-learn.org/stable/modules/feature_extraction.html) and [logistic-regression](https://scikit-learn.org/stable/modules/generated/sklearn.linear_model.LogisticRegression.html) interfaces. Confidence fitting uses data separate from model training, as required by the [calibration guidance](https://scikit-learn.org/stable/modules/calibration.html), with a bounded [SciPy scalar minimizer](https://docs.scipy.org/doc/scipy/reference/generated/scipy.optimize.minimize_scalar.html).

## Results

| Measurement | Result |
| --- | ---: |
| Production rules macro F1 on 230 generated test prompts | 0.0288 |
| ML raw argmax macro F1 on those prompts | 0.9571 |
| Rules-first calibrated candidate macro F1 at 0.90 | 0.9958 |
| Native safety intent/status/entity exact matches | 50 / 50 each |
| Native annotated time/duration normalization decisions | 14 / 14 |
| Native negated requests reaching reads | 0 / 8 |
| Candidate high-confidence false accepts on strict rejection probes | **13 / 24** |

The ordinary test families deliberately stress wording outside the closed grammar. The rules' low score on those families does not describe correctness within their supported grammar. The candidate's high score is limited to easy generated prompts. After validation calibration, its synthetic-test top-label ECE is 0.0227 and Brier score is 0.0274, yet it is highly confident on unsafe or unsupported wording.

For example, a quoted timer command becomes `CREATE_TIMER`, a hypothetical task request becomes `CREATE_TASK`, and a compound calendar/call request becomes `CHECK_CALENDAR`. These are incorrect candidate classifications in the experiment, not executed Android actions. The actual rules reject these probes. The eight negation probes remain blocked in the candidate simulation because the existing deterministic guard is retained. Recognition of the two typo probes is reported as a possible language improvement, not counted as unsafe acceptance.

The native entity and outcome results describe the existing read-only language harness, with synthetic successful reads and zero proposed or completed actions. They do not validate the candidate's entity handling, policy integration, or external effects. Complete per-label metrics, confusion matrices, calibration diagnostics, and failed probe IDs are in the [reference result](evaluation/phase15/results-v2.json).

## Decision and remaining gates

Reject this candidate for deployment. The machine-readable decision requires every gate and cannot be made eligible by a good F1 score alone. The strict rejection gate fails, independent evaluation is absent, and deployment calibration, candidate entities/outcomes/policy, Android parity, and representative-device cost gates remain unmet. Model size, device latency, RAM, and battery were not measured because the safety failure stops adoption before packaging. No host timing is represented as a phone measurement.

A future candidate needs independently authored and reviewed frozen data with difficult negatives, complete entity/outcome evaluation, policy validation, and Android parity/device measurements. Further tuning on these inspected probes would be development work and would require fresh independent evaluation before adoption. This record closes the present candidate with a no-go decision; it does not declare the ML enablement gates passed.

## Reproduction

Use Python 3.12 with the [pinned dependencies](evaluation/phase15/requirements.txt) installed in an isolated environment. The [runtime record](evaluation/phase15/runtime.txt) lists the local versions. From the repository root on Windows:

```powershell
python -m venv .venv-phase15
.\.venv-phase15\Scripts\python.exe -m pip install -r docs/evaluation/phase15/requirements.txt
.\docs\evaluation\phase15\run_evaluation.ps1 -Python .\.venv-phase15\Scripts\python.exe
```

Use `-Offline` only when Gradle dependencies are already cached. The wrapper stops on each failed step and writes native predictions and the fresh experiment report under `app/build/reports/phase15/`. It does not regenerate the checked-in corpus. Intentional dataset changes use `build_dataset.py`, require review of the manifest and annotations, and supersede prior evaluation claims. CI performs the equivalent commands on Linux and retains `NO_GO` as a valid evaluation outcome without enabling a model.

## Checks

The local native snapshot exported 1,650 requests. All 50 native safety/resolution cases matched their expected intent, outcome, and entities, including 14 normalization decisions. The Python integrity suite covers malformed/truncated corpora, leakage, duplicate text, UTF-16 annotations, atomic generation, stale/missing snapshots, disjoint validation families, deterministic reproduction, and adoption gates that fail closed.

Local verification passed: 13 Python integrity regressions, all 100 existing JVM tests, debug/release lint, Detekt, debug/release APK builds, and instrumentation APK assembly. The native snapshot exported 1,650 requests and its 50 safety/resolution cases passed. The experiment completed successfully with a separate `NO_GO` adoption decision.

The Android production sources are unchanged by this phase. Android instrumentation was compiled but was not rerun on a device; physical-device latency/RAM/battery and GitHub CI completion remain unclaimed. `git diff --check` passed after the final documentation/artifact update.
