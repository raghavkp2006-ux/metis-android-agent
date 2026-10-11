# Phase 15 v7: first read-coverage increment

Version: `phase15-read-coverage-v7`, 2026-10-11. **Development only; ML disabled and unbundled; NO_GO.** This implements the first coverage step following [v6 read safety](../read_safety_v6/README.md). It does not train a model, collect a fresh benchmark or satisfy an adoption gate.

## Changes

`ReadGrammarV7` defines complete retrieval productions for open/pending/unfinished/incomplete task lists, explicit notes/memory searches, and named-person promise queries. For example:

- `Do I have any pending tasks?`
- `Find my notes about friction in pulleys.`
- `What have I recorded about indigo dyeing?`
- `Show my promises to Mihir Sen.`

`ReadSafetyV7` distinguishes topic prepositions (`in`, `on`, `with`, `at`) from recognized date, age, priority, status, tag, sorting and other unsupported filters. The entire accepted topic reaches the memory port. `Find my notes about friction in pulleys with tag exam` clarifies before saved context or a read port is accessed; it never searches for a shortened topic. Quotes, actions, compounds, unresolved references and invalid envelopes remain blocked. Person-name validation and unique saved-person resolution retain their v6 boundaries. Existing confirmed action handling remains separate from read admission; calendar access is unavailable.

The orchestrator applies the v7 gate before context construction, and the language specialist rechecks the same parse and exact entities before dispatch. Python and JVM checks share the same authored cases and compare original UTF-16 spans. V6 Python sources, development artifacts and recorded report are retained; v7 uses its own directory, source/data manifest, snapshots and output report. A rerun of a historical comparison against current native code must use its current source binding.

## Development data and provenance

`v7_data.py` records **60 new assistant-authored examples in 10 scenario families**, defined before implementing the grammar. Product/read-port requirements informed these fictional scenarios: task inventory, task-state wording, craft journal, mechanics study, garden log, sound design, field notebook, equipment checkout, volunteer commitments and reference boundaries. Each scenario keeps its positive requests, unsupported constraints and action/compound variants together.

These are synthetic development cases, not separately collected human examples. Their manifest records `human_authored=false`, `human_approved=false` and `split=development`; no train/calibration/test independence is claimed. Exact duplicates of the exposed IRS-003 queries are rejected. Familiar retrieval wording and overlap with historical vocabulary remain possible. No private records, notifications or voice data were collected. The cases do not enter fitting, model selection or calibration.

`development-manifest-v7.json` binds the Python candidate, native grammar/gates and authored data. Source hashes normalize checkout line endings; native snapshots independently bind the exact current source bytes and input. Regenerating this development manifest is explicit and never grants approval. Output reports are restricted to ignored `app/build/reports/phase15/v7/`.

## Executed validation

| Check | Result |
| --- | --- |
| Android JVM suite | 105 passed, no failures or skips |
| V7 Python tests | 7 passed |
| Retained v6 Python tests | 7 passed |
| Existing v5 Python tests | 22 passed |
| Existing Phase 15 integrity tests | 13 passed |
| Historical native safety/resolution fixtures | 50 / 50 exact intents, dispositions and entities |
| Debug lint and detekt | Passed |
| Debug app and instrumentation APK compilation | Passed |
| Frozen IRS-003 evidence hashes / tracked diff | Unchanged |

No Android device was connected. Instrumentation was compiled, not executed; native snapshots use synthetic ports. Read-admission/span agreement on these fixtures does not establish Android ML parity, real adapter evidence or device cost measurements.

| Development / exposed measure | New development families | Exposed IRS-003 |
| --- | ---: | ---: |
| Requests | 60 | 145 |
| Expected read outcomes | 27 | 23 |
| V6 Python candidate reads | 1 | 4 |
| V7 Python / native reads | 27 / 27 | 7 / 7 |
| Forbidden simulated / native port reads | 0 / 0 | 0 / 0 |
| Forced-label attempts on prohibited outcomes | 132 | 488 |
| Forbidden reads under those labels | 0 | 0 |
| Exact dispositions | 60 / 60 | 101 / 145 |
| Exact entities on expected read outcomes | 27 / 27 | 2 / 23 |
| Python/native read-admission agreement | 60 / 60 | 145 / 145 |
| Accepted original-span agreement | 27 / 27 | 7 / 7 |
| Original request strings retained | 60 / 60 | 145 / 145 |

All 14 originally forbidden reads remain blocked when replaying their recorded v5 labels. The perfect authored development result reflects implementation-oriented scenarios and is not a generalization estimate. Exposed coverage improves **4 → 7 of 23** approved reads, while the remaining 16 clarify. Entity agreement with reviewed truth remains weak: several accepted raw topics include a leading `the` that the approved entity annotation excludes. The full original topic is deliberately preserved for retrieval; no broad entity-accuracy gain is claimed.

The full report, per-row predictions, retained v6 comparison, frozen evidence hashes and current native source binding are in `app/build/reports/phase15/v7/development-regression-report.json`. The reviewed accuracy/entity/filter/disposition gates remain unmet. Broad language understanding, pronoun/relationship resolution and advanced filtering are not enabled.

## Reproduce

From the repository root, Python 3.12 and JDK 17 (no Python ML libraries needed for v7):

```powershell
python -m unittest discover -s docs/evaluation/phase15/read_coverage_v7 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:phase15RulesSnapshot :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_coverage_v7/development-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v7/native-development.tsv"
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/human_review_003/first_evaluation/frozen-benchmark.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v7/native-exposed.tsv"
python docs/evaluation/phase15/read_coverage_v7/v7_evaluate.py
```

Use `./gradlew` on Linux/macOS; omit `--offline` when dependencies are not cached. CI performs the v7 checks without refreshing manifests or changing frozen evaluation artifacts.

Only after intentionally changing the authored cases, run `python docs/evaluation/phase15/read_coverage_v7/v7_data.py` and review its diff. After intentionally fixing candidate sources, bind them with `python docs/evaluation/phase15/read_coverage_v7/v7_data.py --manifest`, then regenerate native snapshots and validate. Neither command approves or freezes a human benchmark.

## Remaining work

Continue separately sourced development of broader wording and entity/query representations, including distinguishing annotation spans from the complete retrieval query without dropping constraints. The current 7/23 exposed coverage and 2/23 reviewed read-entity matches do not justify proceeding to ML enablement. Once the candidate is fixed, collect a genuinely new human-authored/reviewed benchmark with candidate-bound approval before its first score. Keep model-selection and representative-calibration data separate. Actual repository/policy validation and Android/device gates follow the prerequisite quality gates. The original IRS-003 first result remains the immutable failed evaluation.
