# Phase 15 v9: disabled lightweight classifier experiment

`phase15-classifier-v9` is a separately versioned **assistant-authored development experiment**, completed on 2026-10-11 with **NO_GO**. No model or ML runtime is packaged or enabled in Android. The fixed v8 parser and read gate remain authoritative. The original [failed IRS-003 evaluation](../human_review_003/first_evaluation/README.md), approvals, results and exposure receipt are unchanged. V6/v7 artifacts are preserved, and the v8 compatibility correction is documented separately.

## Data and fitting

The templates, family partitions, exclusions, feature grid and selection policy were defined before the first v9 score. This increment does not tune them using the reported holdout. The committed data contains **1,699 rows in 100 template families**. One exact match with earlier exposed data was quarantined before fitting. Every earlier checked-in Phase 15 CSV/JSON utterance is an exact normalized exclusion, never a fitting source. This does not establish semantic independence: familiar vocabulary, related constructions and assistant authorship remain limitations.

There are 100 ordinary examples per read intent (99 tasks after exclusion), plus paired unsupported-filter, negation and compound/action variants. Unsupported-filter examples retain their semantic read label but require clarification; negation/action combinations use UNKNOWN. Calendar is always unavailable. Fictional people/topics are disjoint across partitions. A person fixture assumes one exact saved match; the classifier does not resolve actual people. No private messages, audio, contacts or timelines are used. The language scope is typed English/Indian English; this experiment makes no Hinglish, voice-noise or broad multilingual support claim.

The CSV projection follows the dataset contract (`source=synthetic`); the richer JSON explicitly records assistant provenance, no human approval, read disposition, literal query and unsupported constraints. Calibration/selection are disjoint validation subsets. Authored data has no third-party corpus license asserted. Final schema metadata and CSV projection do not change the scored utterances, labels, families or model configuration.

| Partition | Families | Rows | Permitted use |
| --- | ---: | ---: | --- |
| Train | 70 | 1,189 | Vocabulary, IDF and classifier weights |
| Calibration | 5 | 85 | Scalar temperature only |
| Selection | 10 | 170 | Feature/C choice and threshold |
| Development holdout | 15 | 255 | Report only, after selection |

This implements a 70/15/15 family split with calibration and selection forming disjoint validation subsets. Related slot/polite/prohibited variants stay together. Exact duplicates, exposure matches, invalid UTF-16 spans, missing labels, fixture leakage, family drift and false human-approval flags are rejected. The holdout is now exposed development data, not a new independent evaluation.

The feature grid is word TF-IDF (1–2 grams), with or without character `char_wb` TF-IDF (3–5 grams), and balanced logistic regression with C=1/4, seed 1509. Preprocessing is NFKC, lowercase and whitespace collapse. Literal references and constraints are retained; no expected-entity masking is used. Each model fits on train only. Temperature fits calibration NLL within [0.25, 4]. Selection tries cutoffs 0.90–0.99 and requires zero UNKNOWN label accepts on selection families; ties favor macro F1, fewer known clarifications and smaller feature/C choices. If none qualifies, learned output abstains. Test/exposed labels cannot enter these functions.

The selected development configuration uses word+character features, C=1, temperature **0.684659**, and cutoff **0.90**. Selection eligibility is a threshold-search condition, not adoption approval.

## Results and complete outcomes

| Holdout semantic-intent metric | Native v8 | Raw classifier | Threshold classifier / rules-first hybrid |
| --- | ---: | ---: | ---: |
| Macro F1, five labels | 0.1385 | 0.7498 | 0.4390 |
| UNKNOWN label accepts, 135 negative rows | 0 | 12 | 0 |

The selected cutoff recognizes no memory or promise intents in this holdout. Its macro F1 is below the 0.90 project target. On training/calibration/selection rows the native raw rule labels sometimes identify read intent inside unsafe requests; those semantic false accepts remain visible in the report even though the request gate blocks reads. Intent labels and executed read safety are separate measures.

| Complete read measure | V9 holdout | Exposed IRS-003 | V8 development | Retained v7 development |
| --- | ---: | ---: | ---: | ---: |
| Expected read outcomes | 45 | 23 | 18 | 27 |
| Native v8 port calls | 0 | 8 | 18 | 27 |
| Simulated rules-first v9 reads | 0 | 8 | 18 | 27 |
| Forbidden native / simulated reads | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Exact admitted positive entity annotations | No reads | 8/8 | 18/18 | 27/27 |
| Blocked annotated-filter rows | 60/60 | 62/62 | None | None |
| Exact dispositions | 195/255 | 102/145 | 61/61 | 60/60 |

All 27 retained v7 development reads, 18 v8 development reads and eight exposed reads remain admitted. Across **all** v9 partitions, v8 and the hybrid each admit **54** reads (train 34, calibration 5, selection 15, holdout 0). The report retains each suite's counts individually. No additional read is admitted by the classifier in any section.

The holdout has 30 positive rows requiring an entity; the fixed parser extracts none of those expected entities (recall 0, precision undefined). This explains why an intent-only gain cannot solve retrieval. Exposed native annotation matches are 9/22 entity-bearing expected-read rows, including one correct extraction that still clarifies. Including the task row with an empty entity annotation gives the v8 report's 10/23 exact matches. These denominators and stages are reported separately.

Every prohibited development row is also replayed under every read label: **5,600 forced-label attempts** across all v9 partitions, zero simulated reads. Exposed/v8/v7 regressions add 488/172/132 such attempts. The complete original request remains in every simulated plan; unsupported filters cause no read. That is constraint retention and conservative blocking, not implemented filtering. The v8 native regression report separately verifies all 14 original forbidden reads remain blocked, full literal queries and source-bound native parity. Calendar remains unavailable.

The outcome bottleneck is structural: the fixed safety grammar only admits complete requests that deterministic rules already handle. The classifier can suggest intent for additional paraphrases, but cannot authorize unsupported grammar or manufacture a validated person/topic. V9 therefore has **zero safe-read gain** and remains NO_GO despite its raw intent improvement. The next candidate must improve validated request/entity handling and useful outcomes before model adoption can be reconsidered.

Calibration on the 255 synthetic holdout rows changes NLL 0.4601 → 0.4051, multiclass Brier 0.2325 → 0.2180 and ten-bin top-label ECE 0.0883 → 0.0468. These are diagnostic scores, not representative deployment calibration: only five synthetic families fitted temperature, and no fresh human calibration evidence exists.

## Validation and artifacts

Six v9 Python regression methods pass: provenance/data reproduction, family/exposure/fixture/span corruption, partition restrictions, numeric export parity and tampering, threshold abstention/output isolation, and confident wrong labels at the read gate. Python numeric export parity matches all 1,699 rows within 1e-12; additional probes cover Unicode, apostrophes, full-width text and unknown inputs. This reconstructs feature configuration and numeric weights without pickle; it is not an Android inference adapter.

The separate v8 compatibility regression keeps optional literal-query fields null when equal to the annotation. The native exporter keeps memory annotations in the historical schema and actual retrieval text in `query_base64`. A JVM regression verifies the original `my DSP 😀 exam` request, exact UTF-16 offsets and complete dispatched query. Snapshots now bind the exporter as well as input and production sources, so stale exporter output cannot certify v8/v9.

The v9 manifest binds code, data, pinned dependencies and the v8 manifest. CI checks existing historical pipelines, v8 regressions and this separately disabled experiment. Reports, row-level predictions, split/fitting audit, all selection trials, calibration scores and the numeric export stay in ignored `app/build/reports/phase15/v9/`. `disabled-candidate-v9.json` records `ml_enabled=false`, `ml_packaged=false`, dataset/manifest hashes and payload integrity. It is never an app asset.

The current checkpoint passed 111 JVM tests, v9's six Python methods, existing Python regressions, detekt, lint and APK compilation. Device instrumentation is unexecuted locally because no Android device is attached. Native snapshots use synthetic read ports; simulated hybrid results do not prove Room outcomes, Android model parity, latency, RAM or battery. GitHub CI completion is not claimed from local validation.

## Reproduce and remaining gates

Use Python 3.12 with the existing pinned Phase 15 development dependencies and JDK 17. First reproduce the [v8 snapshots/report](../read_coverage_v8/README.md), including the historical root snapshot, then run:

```powershell
.\.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/classifier_v9 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/classifier_v9/development-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v9/native-development.tsv"
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/classifier_v9/v9_evaluate.py
```

Use `./gradlew` on Linux/macOS and omit `--offline` without cached dependencies. `v9_data.py` reproduces authored data; `--manifest` intentionally rebinds development changes and never grants approval. CI does not regenerate either. Default evaluation never writes historical results, human approvals or exposure receipts.

Remaining Phase 15 work, in order: improve the separately versioned classifier plus validated entity/outcome candidate; establish suitable calibration; freeze a fixed candidate and obtain genuinely fresh human-authored/reviewed queries; validate actual repository/policy outcomes; then build an Android parity adapter and measure representative devices. A failed candidate does not justify collecting another supposedly independent score against unchanged shortcomings. No user input is needed for current development; fresh human queries and an Android device will be requested when those gates are ready. Phase 14's bounded baseline remains complete, and Phase 15 remains open.
