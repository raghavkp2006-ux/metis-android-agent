# Phase 15 continuation: read-query development candidate

Status on 2026-10-10: **NO_GO; Phase 15 remains active.** Development tooling and the pending review package are implemented. Independent benchmark evaluation, Android integration, and device measurements are not complete. The app still uses rules with no packaged model or ML runtime.

## Delivered

- Preserved and committed the original experiment as `f9dd951` with its rejected [v2 report](PHASE_15_VERIFICATION.md). V2 dataset, evaluator, manifest, and reference results remain unchanged.
- Added a separate [v3 experiment](evaluation/phase15/read_queries_v3/README.md) for four read intents plus UNKNOWN, stronger negatives, family-separated calibration/model selection, conservative learned acceptance, actual JVM rule comparisons, and coverage reporting.
- Added a [user review guide](evaluation/phase15/read_queries_v3/REVIEW_GUIDE.md), 100 unapproved fictional draft examples, 25 blank human challenge slots, complete-review validation, source-bound freezing, and one-evaluation receipts. No user approvals or independent passing claims are fabricated.
- Added a native snapshot task, Windows runner, and CI development evaluation. No application production source or dependency was changed.

## Development dataset and training

The 7,200 intent-only examples split into 5,110 train / 370 calibration / 720 threshold-model-selection / 1,000 test rows, covering 110/8/16/23 families. Each positive base prompt and all its negative wrappers share a family and partition. Training includes at least 140 positive examples per read intent plus 4,540 UNKNOWN examples. No normalized duplicates or family overlap are accepted.

Positive wording reuses exposed v2 templates. Negative examples cover quoted, hypothetical, compound, injection-like, negated, unsupported-language, creation, and unrelated requests. The larger sample is intentionally negative-heavy. Its test partition is for development comparison only; author exposure and heuristic grouping prevent an independent blind accuracy claim. Empty entity arrays do not establish entity correctness.

TF-IDF word 1–2 grams and balanced logistic regression fit train only. A scalar temperature fits separate calibration families. Threshold-validation families choose C from 1/4/16/64 and cutoff from 0.90–0.99, requiring zero false accepts and some accepted queries. Ties prefer smaller C/lower cutoff. The selected C is 1, cutoff 0.90, and temperature approximately 0.16627. The first unbalanced development trial rejected everything; it was not counted as a useful safety success. All four balanced candidates were compared using validation only.

The conservative eligibility filter trades coverage for rejection. It is not a general language detector or complete injection defense. The hybrid preserves native known intents and negation behavior. Creation and other non-read intents are projected to UNKNOWN only for the read-scope metric; the separate preservation check verifies that their original rule predictions are retained. No mutation is dispatched by this experiment.

## Results

| Development test measurement | Rules | Learned candidate | Rules-first hybrid |
| --- | ---: | ---: | ---: |
| Macro F1, five labels | 0.2682 | 0.8171 | 0.7913 |
| Read coverage, 110 positive prompts | 9.09% | 65.45% | 66.36% |
| False accepts, 890 negative prompts | 10 | 0 | 10 |

The candidate accepts 72/110 positive read requests, all correctly on this generated sample. It also rejects all 24 historical strict probes within its narrower read scope. These results do not establish independent accuracy, seven-intent parity with v2, end-to-end read correctness, or device inference performance.

The hybrid's ten false accepts are compound memory requests, such as `what do you know about the lab report and send a message to Nila`. The broad existing memory rule treats the suffix as part of its search topic. The learned candidate rejects it, but known rule matches are preserved by this experiment. These are incorrect read-intent classifications against the v3 rejection contract, not sent messages or executed external actions. The failures remain explicit in [results-v3.json](evaluation/phase15/read_queries_v3/results-v3.json); no production rule behavior was silently changed.

The native runner exports 7,250 requests using actual production rules and the read-only language harness. All 50 historical regression cases still match intent, status, and entity annotations, including 14 time/duration normalization decisions and zero negated reads. Those historical checks do not cover every v3 negative or validate an ML entity adapter.

## Decision and review checkpoint

Retain rules. Hybrid F1 is below 0.90 and strict rejection still fails. User-reviewed evaluation, deployment calibration, candidate entity/outcome/policy integration, and Android parity/device-cost gates remain unmet. A high development improvement over rules cannot override them.

The review package has not been frozen or scored. Completing it requires user review of 100 draft examples and five fresh human-authored prompts per label. Freezing checks complete annotations, original UTF-16 entity spans, duplicate/exposed text, per-label human coverage, and candidate/data/native-source provenance. Scoring reports the human-authored subset separately and requires its F1/gain alongside full-benchmark rejection checks. Local receipts prevent accidental repeated scoring; they are workflow protection, not a security boundary or proof of reviewer identity.

Android adapter work is intentionally deferred under the agreed plan until reviewed offline gates pass. There are no weights bundled, new production runtime, background training, private-data collection, or uploads. Representative-device latency (<100 ms target), RAM, size, and battery remain unmeasured. Phase 16 has not started.

## Verification

The v3 Python suite passes 26 checks for split integrity, wrapped-family leakage, duplicate/truncated data, reproducibility, training/selection boundaries, zero-coverage rejection, source/negation/known-rule preservation, high-confidence rejection, review completeness, UTF-16 annotations, human challenge requirements, stale/tampered freezes, and repeat-scoring refusal. The original 13 Python checks also pass.

All 100 existing JVM tests pass with zero failures/errors/skips after a fresh `:app:testDebugUnitTest --rerun-tasks` run. The final Windows wrapper passes sequentially, exporting 7,250 native requests and reproducing the NO_GO decision. An earlier overlapping Gradle run caused transient missing-test-class failures; the fresh run and sequential wrapper resolved them.

Debug/release lint, Detekt, debug/release APK builds, and instrumentation APK assembly pass locally. Original v2 evaluation reproduces its NO_GO and 13/24 rejection failures. Changed-document local links and `git diff --check` pass. Android instrumentation was not run on a device; assembly is not a device-test result. Physical-device performance/battery and remote GitHub CI completion remain unclaimed. Reproduction commands and pinned dependencies are linked from the [experiment README](evaluation/phase15/read_queries_v3/README.md).
