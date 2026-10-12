# Phase 15 v14: broader guarded recall and disabled classifier experiment

V14 adds complete saved-memory recall forms and a separately versioned offline classifier experiment. **ML remains disabled/unbundled and NO_GO.** The user's 214 requests are exposed development data; their labels are drafts with six directly confirmed meaning clarifications, not independent evaluation or blanket human approval. Historical candidates v6–v13, the original failed evaluation/approvals/results and intake 004 remain unchanged.

## App behavior

The Android app explicitly selects `ReadCandidate.V14`. Classification/extraction, pre-context admission and specialist dispatch use the same selected version; historical constructor defaults remain V12 for reproducible older harnesses. New closed productions cover “What did I say about…”, “What do you remember about…”, “Find some memory about…”, recollection of a talk with a named person, and coordinated named-person/topic notes. Grammar uses captured spans and full-match productions, not example strings or IDs.

The annotated entity and complete literal query are separate. Grammatical articles are excluded from the annotation while remaining in retrieval text, including “The Who”. A conversation recipient is annotated as a memory subject, with `my talk with NAME` retained as the complete query; this does not resolve a contact or infer identity. Coordinated subjects retain both components. Person validation remains exact and syntactically bounded. Queries remain bounded to 256 UTF-16 units, annotations to 200 (100 for promise recipients), requests to 512, and existing repository limits remain unchanged. Full requests and extracted values are revalidated before any context or read access.

A physical-device regression exposed a pre-existing memory-search tokenizer defect: the variation selector in an emoji could become a standalone FTS term and prevent a matching note from being found. Tokens now begin with a Unicode letter/number; combining marks can extend that token but cannot form an isolated search term. Complete query text and entity offsets remain unchanged. Accented/decomposed words and literal operator quoting are preserved. There is no storage migration or read-port change.

Task subject/status/date filters, promise action-content filters, unresolved standalone references, outer negation, appended actions, compound reads and unsupported calendar constraints still require clarification/rejection before reads. The six [user clarifications](review-clarifications.json) confirm semantic intent, not implementation of those filters. The API query means an actual scheduled-reminder lookup, “any activity” means a cross-store search, and sharing with Aditi is advice; these cannot be replaced by a broader task, memory or calendar lookup. Negative remembered statements are not automatically converted into affirmative promises. The [updated review proposal](user-review.md) shows current supported dispositions separately from draft meanings. Three clarifications arrived after first classifier scoring; they caused no fitting or threshold changes.

## Development evidence

Fifteen assistant-authored grammar families were defined before implementation: 75 positive reads, each paired with six prohibited variants, plus eight malformed/unresolved cases (533 rows). New data excludes normalized exact earlier-data duplicates. Articles, apostrophes, Unicode, coordination and attribution have explicit independently authored expected spans and queries.

The current native/Python replay contains **9,366 cases**: 6,603 retained cases, 533 new grammar cases, 214 exposed user requests and 2,016 new synthetic classifier cases. All 633 existing local read admissions (including the v13 calendar adapter), 23 approved exposed saved-record reads and v7/v8/v9/v10 positive coverage remain preserved. The new grammar correctly scopes all 75 new positives. User-batch admissions rise from **1 to 17**, with 185 follow-ups and 12 negation denials; these counts are capability diagnostics, not semantic correctness or factual-answer scores. None of the assistant-flagged unresolved references is admitted. All 14 original forbidden reads remain blocked.

Every read label is forced against every regression request (37,464 trials). Mismatched/unsupported labels cause zero context/read calls. Forged entity types, spans, raw values and complete query values are checked before context and by direct specialist calls; structurally impossible spans also fail at the request constructor. Follow-ups retain the entire request. Native snapshots bind both input bytes and current production source. Full predictions and reports stay in ignored `app/build/reports/phase15/v14/`.

## Classifier and calibration result

The predeclared [protocol](protocol.json) uses an isolated copy of the retained v12 two-head word/character TF-IDF training engine. It does not refit or overwrite a historical candidate. The four C/cue configurations and six thresholds were declared before fitting. New synthetic families have disjoint splits and fictional slots. Retained training/calibration/selection partitions are reused, while every earlier inspected test and every user request is excluded from fitting/calibration/selection.

Partitions contain 3,708 fitting, 1,026 synthetic calibration, 965 selection and 315 new synthetic test rows. The selected configuration is C=4 without cues, temperature 0.6517549282, cutoff 0.90. Selection macro-F1 is 0.8018 with zero sampled UNKNOWN accepts. On the same 315 new synthetic test requests, retained v12 / v14 thresholded macro-F1 is **0.6416 / 0.7110**; v14 raw argmax is 0.8442. V14 has **two UNKNOWN false accepts** on that test, both blocked by the full-request gate. Numeric export parity passed. The first scoring output and disabled numeric export are retained under ignored `first-score/`; there was no retuning from test results.

The improvement is limited to these harder assistant-authored synthetic families. It is not comparable to v12's 0.8929 on a different, earlier test set, and does not establish real-user accuracy. Human-batch macro-F1 is explicitly unscored until reviewed labels exist. Representative human calibration, fresh independent evaluation, useful ML read gains and Android model parity/resources remain unmet. Do not enable/package the model or present deterministic phone tests as model latency/RAM/battery measurements.

## Reproduction

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v14/integrity.py
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v14/historical_tests.py
.\.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/candidate_v14 -p 'test_*.py' -v
.\gradlew.bat :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v14/evaluate.py
```

Current CI certifies v14 sources and runs retained behavior tests. The evaluator limits numeric thread pools to one to reproduce the first scored export exactly; parallel BLAS reductions otherwise triggered the unchanged guard against modifying a scored export. Earlier source manifests certify their historical checkout; they are preserved rather than rewritten to bless changed shared files. `integrity.py` also validates the unchanged original intake certificate and all frozen artifact bytes. [Validation details](regression-report.md) record device scope and limitations.

Next: review/correct semantic meanings and entities/queries/dispositions for the exposed user batch, implement separately scoped task/promise filters, then use approved family-separated human development/calibration input for the next classifier candidate. Keep the remaining fresh holdout unseen. Request that holdout only after a subsequent eligible candidate and thresholds are frozen; independent evaluation and Android model measurements are later gates, not completed by v14.
