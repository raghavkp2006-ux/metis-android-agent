# Phase 15 v10 deterministic read candidate

V10 supports the 23 currently approved exposed benchmark reads, all 299 labelled v9 development reads (99 task, 100 memory, 100 promise), and the retained 27 v7 / 18 v8 development reads. These are exposed regressions and assistant-authored development evidence. They are **not a fresh independent evaluation**. ML remains disabled and unbundled; no v9 retraining or adoption decision is implied.

## Implementation

`ReadGrammarV10` uses complete grammatical productions with captured spans. `v10_grammar.py` defines the productions and generates `ReadPatternsV10.kt`; the Python replay uses the same expressions. Runtime parsing has no dataset lookup, benchmark-ID match, ML dependency, storage access or parser/validator recursion.

`ReadSafetyV10` validates the entire original request before context construction, compares intent and extracted entities before admission, and repeats that validation at specialist dispatch. Forged types, spans and normalized query values confer no read authority. Calendar requests must match a complete supported calendar production and remain unavailable, with zero context/read calls.

Task forms retrieve the existing OPEN-task scope. “Not completed” is task-state wording; the complete “things to do and I can't remember any of them” production is one recall request. The negation guard remains effective outside validated constructions. Saved-note statements and attached recall questions retrieve saved memories, including requested numbers/passwords, attribution and descriptive locations. Relationship descriptions remain literal memory-search subjects; they neither resolve contacts nor assert identity. Promise queries still require exactly one exact, case-insensitive saved-person match.

Annotation spans retain the existing UTF-16 article convention separately from the full literal retrieval subject. Titles such as “The Who,” attribution, requested fields, location and descriptive clauses reach the read port intact. Closed constructions admit remembered “not …ing after …pm,” descriptive weather/month subjects and facility hours. Retrieval-date filters, outer negation and appended actions/compound requests continue to block access. Clarifications retain the complete original request and its constraints.

Memory queries are bounded to **256 UTF-16 units** without truncation in the agent path. Annotation bounds (200 for topics, 100 for people) and the 512-unit request bound remain. The read-port signatures, storage schema, explicit-memory restriction, expiry filtering, result bounds and honest no-match behavior are unchanged.

## Development and preservation

The initial assistant-authored families were defined before grammar implementation. `development-cases.json` records 623 new cases, including 78 reads, paired unsafe/malformed/unresolved variants and query-boundary probes. Exact earlier-data duplicates are excluded. Later boundary additions are also assistant-authored development probes. No synthetic row is described as human-authored or approved.

`regression-input.tsv` combines those cases with the frozen 145 benchmark cases, all 1,699 v9 cases, 61 v8 cases and 60 v7 cases: **2,588 requests**. Query projections are separate v10 development annotations; frozen entity/disposition/filter annotations remain unchanged. The new manifest binds current code and data and records byte hashes of every retained v6–v9 artifact and human-review-003 file.

Historical manifests remain unchanged and certify their historical shared source versions. CI runs their behavior tests and relocates only two obsolete current-source manifest assertions to the v10 suite. Their data projection, provenance, exposure and integrity audits still run. CI does not rerun v9 candidate selection or overwrite its recorded results. Small historical classifier test fixtures still exercise export and partition handling; they do not retrain or select the v9 candidate.

## Reproduction

Use the pinned Python development environment and JDK/SDK configured for this repository:

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_coverage_v10/historical_tests.py
.\.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/read_coverage_v10 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot -Pphase15ReadInput=docs/evaluation/phase15/read_coverage_v10/regression-input.tsv -Pphase15ReadOutput=app/build/reports/phase15/v10/native-regressions.tsv
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_coverage_v10/v10_evaluate.py
```

Reports stay under ignored `app/build/reports/phase15/v10/`. The report separates read coverage, positive entities, complete subjects, dispositions and constraint preservation. Conservative follow-up/denial outcomes on prohibited cases are not misreported as exact unsupported/reject disposition matches. Native tests force every read/calendar label across the aggregate corpus and forge spans/types/queries; prohibited requests must cause zero context/read calls. Existing v2–v6 evaluators and historical Python behavior tests remain part of verification.

Repository instrumentation fixtures contain matching records and decoys for titles, attribution, locations, requested fields and content negation, plus OPEN tasks and exact unique Unicode names. They assert unchanged stored data and honest no-match responses. Instrumentation is compiled; local execution and Android measurements remain unexecuted unless a device is available.

## Local validation, 2026-10-11

| Regression set | Native/Python accepted reads | Exact positive entities and complete queries |
| --- | ---: | ---: |
| Approved exposed benchmark | 23 / 23 | 23 / 23 |
| V9 development | 299 / 299 | 299 / 299 |
| Retained v7 development | 27 / 27 | 27 / 27 |
| Retained v8 development | 18 / 18 | 18 / 18 |
| New v10 development | 78 / 78 | 78 / 78 |

All 2,143 labelled non-read cases caused **zero read-port or context calls**. All 14 original forbidden reads remain blocked. Python and JVM forced-label checks cover 8,572 prohibited-label attempts, including forged extraction values in JVM checks. Additional JVM cases forge entity kinds, spans and normalized subjects before context and direct dispatch. The approved exposed coverage improves from historical v7's 7 and v8's 8 reads to 23.

Disposition accuracy is distinct: native dispositions match 105/145 exposed annotations and 1,284/1,699 v9 annotations. Most differences are conservative denials of negated requests versus annotated clarification; some unsupported forms also clarify. These differences do not become reads. All follow-up messages retain their original request, including constraints; all 400 v9 filter cases and 61 prohibited annotated benchmark constraint cases remain blocked. The approved task recall's incomplete-status constraint maps to OPEN as specified.

Validation passed: **115 JVM tests**, **86 direct Python tests plus 6 nested retained v7 behavior tests**, current v10 integrity/native replay, historical v2–v6 evaluators, debug/release lint, Detekt, debug/release APK compilation and instrumentation APK compilation. Historical evaluators retain their separate NO_GO decisions. All 65 retained candidate/evaluation files match their byte bindings. APK inspection found no trained model artifacts. No Android device was connected: repository instrumentation execution and device measurements were **unexecuted**. GitHub CI completion is not claimed by local validation.

## Remaining gates

The gains come from deterministic parsing. Separately versioned classifier work, representative calibration, fresh human-authored/reviewed evaluation after candidate freeze, repository runtime validation, and Android parity/latency/RAM/battery measurements remain separate gates in that order. Synthetic coverage does not authorize ML enablement or guarantee factual answers.
