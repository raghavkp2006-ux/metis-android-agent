# V14 development validation record

Status: **NO_GO for ML**, deterministic v14 app enabled, historical evidence preserved. No fresh independent evaluation or human-batch F1 is claimed.

| Check | Measured result |
| --- | --- |
| New grammar families authored before implementation | 15 families; 75 positive and 458 prohibited requests |
| Native/Python production replay | 9,366 rows; exact admissions, positive entities and complete queries agree |
| Existing local admissions preserved | 633/633, including calendar; approved exposed saved-record reads 23/23 |
| User 214-query exposed batch | 17 reads, 185 follow-ups, 12 denials; baseline 1 read |
| Full-request constraint preservation | Entire request retained in every native follow-up |
| Forced read labels | 37,464 trials; no mismatched/unsupported label reaches context or read |
| Forged extraction | Shifted/expanded spans, wrong types/raw text/query values blocked before context or direct dispatch |
| Original forbidden reads | 0/14 reads; original approval/results unchanged |
| New classifier families | 2,016 assistant-authored rows; no exact earlier structured-data duplicates |
| Classifier partitions | 3,708 train; 1,026 calibration; 965 selection; 315 synthetic test |
| Same-test retained v12 / v14 macro-F1 | 0.6416 / 0.7110; synthetic only |
| V14 synthetic UNKNOWN false accepts | 2; both blocked by the read gate |
| Numeric disabled export parity | Passed; no historical classifier refit and no scored-version retuning |
| Fitting reproducibility | Single-thread rerun reproduced the first export exactly; runner now limits numeric threads without weakening the scored-export guard |
| JVM tests | 132 passed |
| Python tests | 42 retained behavior tests and 5 new regressions passed |
| Earlier evaluators | V2–v6 commands rerun successfully; 68 earlier Python tests passed, with frozen approvals/results unchanged |
| Static analysis and APKs | Detekt, debug/release lint and APK builds passed; instrumentation compiled |
| Physical phone | 8 targeted tests passed on CPH2691 Android 16 / API 36; 44.254 seconds suite duration |
| ML measurements | Unexecuted: model not packaged or eligible |

The first phone run installed an outdated instrumentation build that compared time-sensitive ranking scores instead of stored records. The rebuilt fixture compares stored contents/metadata. The next run exposed the emoji variation-selector tokenizer bug in the real adapter; production tokenization was corrected without changing query text or offsets. The final complete rerun passed all eight test methods, including all 533 new grammar requests (75 matching reads and 458 prohibited cases), title/attribution decoys, exact unique-person ambiguity/no-match cases, and the six retained local-calendar methods. Initial and final logs remain under ignored output. No run cleared installed personal data; every storage test uses temporary encrypted databases and test keys, and the UI test uses an in-memory fake. Suite duration is not a model latency measurement.

The current read adapter retains its existing bounded saved-memory lookup. An admitted request may honestly return no match; retrieval is neither guaranteed recall nor a factual identity assertion. Unsupported task/promise filters and contextual pronouns still cannot be satisfied by a broader lookup. Semantic labels remain distinct from current supported runtime dispositions.

The classifier's two UNKNOWN false accepts and thresholded F1 below 0.90 independently fail the quality gates. Representative calibration and independent evaluation also remain absent. Test results do not authorize lowering the frozen cutoff or enabling ML. New fitting or threshold changes require another separately versioned candidate and fresh synthetic test families; real independent evaluation requires unseen human-reviewed input after that freeze.
