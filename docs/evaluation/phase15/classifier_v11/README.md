# Phase 15 v11 disabled classifier experiment

V11 improves intent recognition on a new assistant-authored development holdout, but remains **NO_GO**. ML is disabled and unbundled. This increment changes offline evaluation tooling and adds a JVM safety test; it does not change production parsing, storage, read ports or the Android app's behavior. V6–v10 and the first failed human evaluation, approvals and results are preserved byte-for-byte.

## Method and provenance

`protocol.json` and 150 development families were authored before the first full candidate fit. There are 3,750 typed English/Indian English requests, with 2,250 training rows and 500 each for calibration, selection and test. The family split is 90/20/20/20. Related slots, polite prefixes, retrieval-date/count filters, outer negation, appended actions and compounds stay in the same family. Fictional people/topics are disjoint across partitions; no exact earlier-data duplicate enters fitting. These are synthetic families, not independent speakers or scenarios; shared words and related paraphrase constructions limit generalization.

Word TF-IDF 1–2 grams and character `char_wb` TF-IDF 3–5 grams are compared at the fixed configurations in the protocol. Optional request-derived lexical counts are learned features, not intent assignments or admission rules. Expected labels, IDs, entities, query annotations, family metadata and parser outputs never enter features. Vocabulary/IDF/weights use train only. Temperature uses calibration only, bounded to [0.25, 4]. Global and per-intent cutoffs use selection only, have a 0.90 floor, and require zero sampled UNKNOWN accepts on selection. Neither the test nor exposed regressions enter fitting or selection.

The selected model uses word+character features, **C=4, no cue features**, temperature **0.480004**, and a global cutoff **0.90**. Per-intent cutoffs and added lexical features did not win the predefined selection criterion. Their inclusion in the experiment is not a claim that they improved results. Calibration now covers 20 synthetic families instead of v9's five; it still does not establish representative deployment calibration.

## Results, 2026-10-12

| On the same 500 new development test requests | Macro F1 | UNKNOWN false accepts |
| --- | ---: | ---: |
| Retained v9 numeric classifier, original cutoff | 0.3908 | 0 |
| V11 raw classifier | 0.8936 | 13 |
| V11 classifier after confidence rejection | 0.7872 | 5 |
| V10 parser plus v11 simulated semantic labels | 0.7872 | 5 |

The v9 comparison reuses the existing numeric export and verifies its integrity/data/manifest bindings; **v9 was not retrained**. The same-test gain is **+0.3963**. V9's historical 0.4390 used a different, smaller holdout, so comparing that directly with 0.7872 would not isolate improvement. The v9 export remains ignored build output; CI can reproduce v11 without it and explicitly reports an unavailable historical comparison instead of refitting v9.

V11's test F1 by intent is task 0.9167, memory 0.8991, promise 0.4000, calendar 0.8571 and UNKNOWN 0.8629. Promise recall is **25%**. The five UNKNOWN false accepts are task-creation requests mislabeled as task reads; they belong to one held-out family. All five are blocked by the full-request read gate. Passing selection's sampled UNKNOWN check did not generalize to this development holdout.

On the **already-exposed** 255-row v9 holdout, the new threshold classifier scores 0.6688, and the current v10-parser hybrid scores 0.8764. This hybrid includes deterministic improvements since v9 and cannot be attributed entirely to ML. It is diagnostic regression evidence, not fresh evaluation.

The new holdout's temperature calibration changes NLL 0.2560 → 0.2472 and ten-bin ECE 0.0595 → 0.0561, while Brier slightly worsens 0.13015 → 0.13058. These are synthetic diagnostics; the report does not claim uniform calibration improvement.

## Complete outcomes and safety

All 23 exposed approved reads, 299 v9 reads, 27 v7 reads, 18 v8 reads and 78 v10 development reads retain their exact entities and full queries. The 14 original forbidden reads remain blocked by unchanged production validation. The combined suites have 21,772 Python forced-label attempts on non-read rows; none reads. A new native JVM test adds **13,200** forced-label/forged-entity attempts on the new development corpus with zero context or read-port calls, plus direct specialist revalidation. The existing v10 native corpus safety tests also pass.

There are important remaining outcome failures. Of 450 new semantically valid read requests across all partitions, the unchanged parser calls a read port for only 30. **Only 20 have the expected intent, entity and complete query.** Ten new positive paraphrases expose existing scoping mismatches: a broad “remind me of” memory construction consumes a note-recall wrapper or a promise-recall subject. Five have a longer memory subject than annotated, and five route promise recall as memory search. These ten calls are reported as wrong-scope positive reads, separately from the zero calls on rows labelled non-read. Thus zero non-read-row calls does **not** mean all retrieval behavior is correct. The new holdout itself has 10 correctly scoped calls out of 60 desired reads. These gaps must be corrected in a separately versioned parser/entity candidate before adoption or candidate freeze for fresh human evaluation.

The classifier adds **zero safe read coverage** over v10: it cannot manufacture validated entities or authorize an unsupported grammatical form. The simulation retains existing native reads and subjects, including the newly exposed scoping limitations; it does not silently fix them using expected annotations. Native snapshots also record two context calls on ordinary action commands under the app's default action classification, without reads, proposals or completed actions. This is distinct from the forced-read-label safety check, which permits zero context calls on prohibited requests. Full requests and unsupported constraints are retained in follow-ups.

## Validation and reproduction

Local verification passed 116 JVM tests and 98 direct Python tests plus six nested retained v7 checks, including 12 new v11 methods. Debug/release lint, Detekt, debug/release APK builds and instrumentation APK compilation pass. Historical byte checks cover 77 v6–v10/human-review artifacts, with the unchanged v10 manifest still certifying production. The production app contains no new model assets or runtime. No new device or model runtime measurements were performed: the earlier 57 physical-device read/storage tests remain their separately documented evidence.

Use Python 3.12 with the pinned Phase 15 requirements and JDK 17:

```powershell
.\.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/classifier_v11 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/classifier_v11/development-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v11/native-development.tsv"
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_coverage_v10/regression-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v10/native-regressions.tsv"
$env:OMP_NUM_THREADS='1'
$env:OPENBLAS_NUM_THREADS='1'
$env:MKL_NUM_THREADS='1'
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/classifier_v11/v11_evaluate.py --legacy-export app/build/reports/phase15/v9/disabled-candidate-v9.json
```

Omit `--legacy-export` when the retained export is unavailable. Linux uses `./gradlew` and `python`; the standalone classifier workflow generates both source-bound native snapshots. Reports, row-level errors, fitting/selection audits, probabilities and disabled numeric exports stay in ignored `app/build/reports/phase15/v11/`. Reproduction refuses to overwrite a previously scored numeric candidate with changed model/data/protocol or selection. The first local score is also archived there; report accounting was expanded to distinguish wrong-scope calls without retuning the model. A successful CI/tool run is not ML adoption approval.

## What is necessary next

The next development candidate must improve promise recognition, distinguish task creation from task retrieval, and correct the newly exposed retrieval scoping gaps while preserving all existing valid reads. Its model and parser changes need separate measurements. It must add useful, correctly scoped outcomes over the deterministic baseline; an intent-only score gain is insufficient.

Representative human development/calibration requests would help address synthetic wording limits, as described in [human data requirements](human-data-requirements.md). Once the complete candidate is ready, freeze it and obtain a separate unseen human-authored/reviewed evaluation. The v11 test set is now exposed development evidence and must never be reused as independent evaluation. Android model parity, latency, RAM and battery remain later gates. ML stays disabled.
