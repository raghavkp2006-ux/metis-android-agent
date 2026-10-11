# Phase 15 v8: entity spans and complete retrieval queries

V8 (`phase15-read-coverage-v8`) is a separately versioned deterministic development candidate. **ML remains disabled and unbundled; adoption remains NO_GO.** This is development and exposed regression work, not a newly trained classifier or a fresh independent evaluation. The [original IRS-003 first evaluation](../human_review_003/first_evaluation/README.md), its approvals, frozen data, results and exposure receipt are unchanged.

## Behavior and interfaces

Memory entities retain exact original UTF-16 spans. Under the annotation convention, a grammatical leading `my` and one article (`the`, `a`, `an`) are excluded from the annotated span. The complete literal topic is carried separately in the existing `NormalizedValue.Text` field, including those words. For `Can you show me notes about The Who?`, the annotation is `Who`, while the repository query remains `The Who`. This is an annotation convention, not a renaming or shortening of the requested title.

Compatibility correction: when the complete query already equals the entity text, its optional normalized field stays null and dispatch uses that exact entity text. The separate text field is required only when the query differs. This preserves the historical entity schema without shortening any retrieval query. The v8 development manifest is explicitly rebound for this correction; no frozen human evaluation is changed.

The native exporter keeps literal retrieval text in its separate `query_base64` column. Its historical entity-normalization column retains time, duration and reference normalization, while memory annotation values remain unchanged. JVM tests check this separation, and v8 reports check every dispatched literal query against the Python candidate. Historical evaluators, expected annotations and manifests are not rewritten.

`AgentReadPort` and the database schema are unchanged. The language specialist passes the validated complete topic to `memories(query, at)`, rather than rebuilding retrieval text from the shorter entity span. New full productions support polite `can/could you` forms, `show me notes about`, `do you have notes about` and recall-only `remind me of TOPIC`. Task and promise reads accept the polite prefix while retaining their existing restrictions. Scheduling forms still use their original action grammar.

Admission checks the complete original envelope and grammar before context construction. Extracted read entities, including their literal-query field, are also compared against a fresh parse before context construction. The specialist repeats this validation immediately before dispatch. Incorrect labels, forged spans/types/queries, unsupported filters, quoted commands, ambiguous references and compound/action requests cannot authorize a read. Only the recognized `remind me of` recall prefix is exempt from the action-word prohibition; action words inside its topic still block access. Follow-up questions retain the complete original request. Calendar remains unavailable. Promise lookup still requires exactly one matching saved person before any promise lookup; the adapter is unchanged.

Conservative restrictions remain: temporal words in a topic may still require clarification, pronoun/relationship resolution is not added, and advanced filters are not implemented. V8 does not force every exposed approved read to succeed.

## Data, integrity and reproducibility

The 61 assistant-authored cases in eight scenario families were defined before the v8 grammar implementation. Each new production has paired filter, negation, compound/action, ambiguous-reference and quotation cases. Additional cases cover articles/titles, Unicode, apostrophes, tasks, named people and recall/action boundaries. They are explicitly development-only, with `human_authored=false` and `human_approved=false`. Exact exposed duplicates are rejected. Shared vocabulary and familiar forms remain possible; these cases are not independent evidence or training/calibration data.

The v8 manifest binds candidate sources, native integration/tests, authored inputs and the retained v6/v7 candidate artifacts. Source bindings normalize checkout line endings; native snapshots bind exact current production source bytes and input bytes. V8 reports are restricted to ignored `app/build/reports/phase15/v8/`.

V6/v7 files and manifests are preserved byte-for-byte. V7's historical manifest binds the shared production files as they existed at v7; it intentionally is not refreshed to certify v8. Current CI uses the v8 manifest, reruns six retained v7 Python behavior checks and all retained JVM safety/coverage tests, and snapshots all 60 v7 development cases against current production. The omitted historical current-source manifest assertion is replaced by v8's current-source and retained-artifact integrity checks. Historical v7 results remain historical.

## Executed validation

| Check | Result |
| --- | --- |
| Android JVM suite | 111 passed, zero failures/errors/skips |
| V8 Python suite | 7 passed, including 6 nested retained v7 behavior checks |
| Retained v6 / v5 / root Python suites | 7 / 22 / 13 passed |
| Historical safety snapshot compatibility | 50/50 intents, outcomes and annotations; full literal query separately exported |
| Debug lint / detekt | Passed |
| Debug app / instrumentation APK compilation | Passed |
| Frozen human-review and retained v6/v7 file audit | 47 files unchanged |

No Android device was connected. Instrumentation was compiled, not executed. A new instrumented test covers actual saved-memory title matching, recall/action separation and unchanged stored records; it is not claimed as executed evidence. Existing unique-person resolution tests remain. Native snapshots use synthetic ports, not Room or a deployed model. No Android ML parity or latency/RAM/battery result is claimed.

| Development / exposed measure | V8 development | Exposed IRS-003 | Retained v7 development |
| --- | ---: | ---: | ---: |
| Requests | 61 | 145 | 60 |
| Expected read outcomes | 18 | 23 | 27 |
| V7 baseline reads | 1 | 7 | 27 |
| V8 Python / native reads | 18 / 18 | 8 / 8 | 27 / 27 |
| Forbidden Python / native reads | 0 / 0 | 0 / 0 | 0 / 0 |
| Forced-label attempts on prohibited outcomes | 172 | 488 | 132 |
| Forbidden reads under those labels | 0 | 0 | 0 |
| Exact dispositions | 61 / 61 | 102 / 145 | 60 / 60 |
| Exact entity annotations on expected-read rows | 18 / 18 | 10 / 23 | 27 / 27 |
| Exact expected entities on admitted positive reads | 18 / 18 | 8 / 8 | 27 / 27 |
| Python/native admission agreement | 61 / 61 | 145 / 145 | 60 / 60 |
| Accepted native entity-span agreement | 18 / 18 | 8 / 8 | 27 / 27 |
| Literal-query agreement, including empty nonreads | 61 / 61 | 145 / 145 | 60 / 60 |
| Original request strings retained | 61 / 61 | 145 / 145 | 60 / 60 |

All 14 original forbidden reads remain blocked, including replay of their recorded v5 labels. All seven v7 exposed reads and all 27 v7 development reads remain admitted. Exposed coverage improves **7 to 8 of 23**, and entity-annotation matches improve **2 to 10 of 23**; two of the ten correct extractions still clarify. The remaining 15 approved reads clarify. All 62 exposed rows with nonempty annotated filters cause no read; this is conservative blocking, not successful filter extraction or filtering. The perfect authored development result is implementation-oriented and does not measure generalization.

The full report, per-row predictions, native literal queries, v7 comparisons and frozen evidence bindings are in `app/build/reports/phase15/v8/development-regression-report.json`. Classifier quality, representative calibration, fresh reviewed quality, real adapter execution and device gates remain open. The original failed independent score is not replaced.

## Reproduce

From the repository root with Python 3.12 and JDK 17:

```powershell
python -m unittest discover -s docs/evaluation/phase15/read_coverage_v8 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:phase15RulesSnapshot :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_coverage_v8/development-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v8/native-development.tsv"
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_coverage_v7/development-input.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v8/native-retained-v7.tsv"
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/human_review_003/first_evaluation/frozen-benchmark.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v8/native-exposed.tsv"
python docs/evaluation/phase15/read_coverage_v8/v8_evaluate.py
```

Use `./gradlew` on Linux/macOS and omit `--offline` when dependencies are not cached. The v8 Python candidate uses the standard library only; retained v5/root tests use the existing Phase 15 development environment. Regenerate authored inputs with `v8_data.py` only after intentionally editing them. Bind changed v8 development sources explicitly with `v8_data.py --manifest`, then regenerate snapshots and validate. Neither operation grants human approval or restores evaluation independence. CI does not refresh manifests.

Next work follows the training plan: develop a separately versioned lightweight classifier with separate fitting/validation/calibration data; freeze a fixed candidate before collecting genuinely fresh human-reviewed evaluation data; then satisfy actual repository/policy and Android/device gates. ML enablement requires measured benefit and all adoption gates, not these regression gains.
