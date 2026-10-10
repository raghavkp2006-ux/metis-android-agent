# Phase 15 development experiment

Decision: **NO_GO**. The candidate achieves a high generated-test score but falsely accepts 13 of 24 strict rejection probes at the app's 0.90 confidence cutoff. It is not packaged or enabled. See [the phase report](../../PHASE_15_VERIFICATION.md) and [reference machine-readable result](results-v2.json).

## Data provenance and annotation

`intent-v2.csv` has 1,600 synthetic utterances from the templates and fictional slots in `build_dataset.py`. There is no external corpus, scraped text, real-user data, or third-party dataset license. The repository has no LICENSE file; this experiment does not assign a separate distribution license to the data. The examples and annotations were authored/reviewed during this coding session and have not received independent human review.

The seven evaluated labels are task listing, memory queries, promise queries, calendar queries, reminder creation, task creation, and timer creation; unsupported requests use `UNKNOWN`. Each class has at least 140 training rows. The intent annotations describe the request's semantic category, not an implemented ability or permission. An intent-only row's `expected_outcome` describes the requested product flow (read/proposal/reject), not the current pipeline's result; it is not scored as an execution result. Its empty entity array does not assert that no entities exist.

`safety-v2.csv` has 50 development probes, including 24 strict rejection cases, ambiguity, DST, duration limits, input-source restrictions, Unicode, and recognized commands from current and other enabled grammar paths. Here `intent` and `expected_status` describe the current production rules/read-only harness. Typo cases check the current grammar's follow-up behavior and are reported separately from unsafe candidate acceptance. Entity annotations use UTF-16 offsets and explicitly record resolved values or intentional non-resolution. Fixed clock: 2026-10-09T05:30:00Z; default zone: Asia/Kolkata, with explicit New York DST cases. Read-port fixtures are fictional. No action executor is exercised.

Similar template stems are grouped before a seeded approximately 70/15/15 family split, with per-label integer rounding. The actual row split is 1,130/240/230 across 110/24/23 families. No group crosses splits and no exact normalized duplicate exists. This heuristic grouping does not replace independent review of paraphrase leakage. Vocabulary/IDF/weights use train only. Eight validation families (80 rows) calibrate temperature; the other sixteen (160 rows) select the cutoff. Test data and the safety probes are excluded from fitting. Earlier v1 results were explored and corrected, so v2 is a development experiment, not an independent blind benchmark.

## Reproduction and gates

Install the pinned `requirements.txt` in a Python 3.12 environment, then run `run_evaluation.ps1 -Python <path-to-python>` on Windows. `-Offline` applies to Gradle only. Equivalent commands for any host with the project SDK/JDK configured:

```sh
python -m unittest discover -s docs/evaluation/phase15 -p 'test_*.py' -v
./gradlew --no-daemon :app:phase15RulesSnapshot
python docs/evaluation/phase15/evaluate.py
```

Fresh predictions and results go under `app/build/reports/phase15/`; `results-v2.json` in this directory is the recorded local reference. Library/platform variations may change floating point diagnostics; provenance records the runtime and relevant hashes. CI runs the same commands and uploads the build reports. A successful process exit means evaluation completed; it never enables a model, and a `NO_GO` result is expected for this candidate.

The native runner calls production `EnglishRules` and the read-only language orchestrator directly. Its input/source hashes must match the corpus and current grammar. `manifest-v2.json` pins generated data and its source. Only intentional dataset revisions should rerun `build_dataset.py`; inspect the changed manifest and annotations before replacing an evaluation reference. Tests reject leakage, duplicated/missing data, stale snapshots, broken UTF-16 spans, partial output replacement, and missing adoption gates.

The adoption decision requires every recorded gate. Synthetic F1 cannot bypass safety, independent evaluation, deployment calibration, candidate entities/outcomes/policy, Android parity, or device latency/RAM/battery evidence. Those unmeasured gates remain explicitly false. The experiment contains no Android inference adapter, packaged weights, runtime dependency, background collection, remote upload, or automatic personalization.
