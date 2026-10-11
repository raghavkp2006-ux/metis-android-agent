# Phase 15 v6: bounded read safety candidate

Version: `phase15-read-safety-v6`. Development regression evidence, 2026-10-11. **ML remains disabled and unbundled; adoption decision remains NO_GO.** This is a new deterministic admission policy and native safeguard, not a newly trained classifier.

The implementation follows the next-work priorities in the [IRS-003 first evaluation](../human_review_003/first_evaluation/README.md): classifier-independent safety, concrete references, constraint preservation, and regressions on already exposed cases. The original v5 code/data, frozen approval, annotations, first results, native snapshot and exposure receipt are unchanged. No fresh benchmark was collected, approved, frozen or scored. No independent evaluation claim is made.

## Code changes

- `ReadSafetyV6` reparses the entire original request before accepting a read label. `LocalAgentOrchestrator` applies it **before context construction**, which can itself query saved profile settings. Unsupported calendar requests return before context construction as well.
- `LanguageSpecialist` rechecks admission and verifies that supplied entities exactly match the native parse before calling a read port. A label or arbitrary extracted noun phrase cannot independently authorize access. Missing, duplicate or forged entities cannot become a read.
- Person reads require an explicit name spelling with one to three capitalized components, optional apostrophes/hyphens and an optional symbol suffix (including the historical `Ravi 😀` fixture). Pronouns, relationship references, temporal nouns and generic object phrases clarify. The existing local adapter still requires exactly one matching saved person before reading promises; syntactic validation does not assert that a person exists.
- Memory reads require a concrete nonempty topic. Generic/incomplete references and filter-like qualifiers clarify. Original UTF-16 spans and full topic strings are retained. The bounded grammar also recognizes `what did I write [down]/note/save about <topic>`; authored development probes exercise this with ceramic glazing, acoustic resonance and river erosion.
- Action, compound, quoted/complex, control/format-character and unsupported filter wording cannot fall through to unfiltered reads. Date, age, subject, status, priority, category and other constraints outside the narrow grammar remain in the original request and clarification; no constraint is stripped to obtain an admissible query. The Python `ReadPlan.request_text` retains every request verbatim. Advanced constraints are **not** claimed as implemented structured filters.
- `candidate.py` provides a separately versioned, standard-library-only Python admission candidate. It has no model, fitting or repository dependency. `evaluate.py` replays recorded v5 labels and forces each read label, writes only ignored v6 reports, checks frozen evidence hashes, and rejects stale/missing/duplicate native snapshot rows. CI runs these regressions independently of historical evaluations.

Supported reads remain bounded: open saved tasks; explicit nonexpired memories matching the complete accepted topic; or all saved promises for one uniquely resolved person. Calendar access remains unavailable. Existing confirmed task/reminder action flows retain their own acceptance boundary.

## Validation

Executed locally on 2026-10-11:

| Check | Result |
| --- | --- |
| Android JVM tests, including v6 pre-context/read-port spies | 103 passed, no failures or skips |
| New Python v6 regressions and evidence integrity | 7 passed |
| Existing v5 Python regressions | 22 passed |
| Existing Phase 15 evaluation integrity tests | 13 passed |
| Historical native safety/resolution fixtures | 50 / 50 matched intents, outcomes and entities |
| Android detekt and debug lint | Passed |
| Debug application and instrumentation APK compilation | Passed |
| Frozen artifact hashes and tracked diff | Unchanged |

The full v6 development report is written to `app/build/reports/phase15/v6/exposed-regression-report.json`, with a new native snapshot in the same directory. It retains per-row outcomes, expected truth, original request text, frozen evidence hashes and current native source binding.

| Exposed IRS-003 regression measure | Result |
| --- | ---: |
| Exposed requests | 145 |
| Approved non-read outcomes | 122 |
| Recorded forbidden reads replayed through v6 | 0 / 14 |
| Forbidden simulated reads from the standalone candidate | 0 |
| Forced read-label attempts on prohibited outcomes | 488 |
| Forbidden reads under those forced labels | 0 |
| Forbidden native read-port calls | 0 |
| Candidate reads / approved read outcomes | 4 / 23 |
| Native read-port calls | 4 |
| Exact dispositions | 98 / 145 |
| Exact entities, including empty expectations | 107 / 145 |
| Original request strings retained | 145 / 145 |

Safety results are reported separately from coverage. Conservative rejection has substantial false clarification: many legitimate names, topic phrases and broader wording remain unsupported. These results do not satisfy the reviewed intent/entity/filter/disposition quality gate. No intent F1 improvement, representative calibration, Android model parity, real repository/policy adoption gate or physical-device cost result is claimed. The native snapshot uses synthetic port spies; it does not read real personal records. No Android device was connected; instrumentation was compiled, not executed.

## Reproduce

From the repository root, Python 3.12 and JDK 17:

```powershell
python -m unittest discover -s docs/evaluation/phase15/read_safety_v6 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:detekt :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/human_review_003/first_evaluation/frozen-benchmark.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/v6/native-exposed.tsv"
python docs/evaluation/phase15/read_safety_v6/evaluate.py
```

Use `./gradlew` on Linux/macOS and omit `--offline` if dependencies are not cached. No Python ML dependencies are required for v6. Historical Python tests use the existing pinned v5 requirements; installing them for development tests does not enable Android ML.

`exposed-regressions.tsv` is a derived projection of the frozen IDs, requests and approved dispositions for JVM tests. Python tests verify it against the original frozen benchmark; JVM tests verify its IDs/text against the original frozen native input. To regenerate only this new projection, run `python docs/evaluation/phase15/read_safety_v6/regressions.py`. This command does not modify the original evidence.

Remaining work: broader separately sourced development families, then a separate fresh reviewed benchmark after fixing the next candidate, followed by representative calibration, actual adapter/policy evidence and Android/device gates. None is implied by passing these exposed safety regressions.
