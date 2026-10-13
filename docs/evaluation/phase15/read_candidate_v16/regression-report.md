# V16 development regression report

Validated on 2026-10-13. Production selects deterministic v16, with ML disabled and no model assets. No classifier was fitted, no new semantic F1 was scored, and no independent evaluation took place.

| Evidence | Result |
| --- | --- |
| Native/Python replay | 11,442 requests agree; 45,768 forced read-label trials |
| New assistant-authored cases | 1,208, including 326 valid reads; separate appointment-kind scope gold |
| Retained v15 admissions | 909/909 preserved |
| Reviewed human development | 96/96 permitted reads admitted, previously 44/96; 269 prohibited requests cause zero reads |
| Original exposed benchmark | 23/23 approved reads; 122 prohibited requests blocked, including all 14 original failures |
| Entities | 96/96 exact reviewed UTF-16 annotations |
| Complete retrieval query | 94/96 literal subjects; the other two use the explicitly named calendar person, giving 96/96 effective queries |
| Task status | 43/44 against original annotations, 44/44 against previously confirmed policy; preserved original and separate OPEN-policy erratum |
| Constraints/dispositions | New authored query/scope gold agrees natively; every clarification retains the full request. Full human stored-answer/constraint accuracy has not been measured |
| JVM | 141 tests passed, including five v16 tests |
| Python | 144 tests passed across current and historical suites |
| Android build | Debug/release APKs and instrumentation compiled; Debug/Release lint and Detekt passed |
| Physical phone | Four new encrypted-storage tests passed; seven unchanged UI tests passed on retry |

The connected CPH2691 runs Android 16/API 36. Isolated fixtures exercise matches and decoys, metadata-before-decryption, literal attribution and negative content, person uniqueness, bounds, missing/no-match results and unchanged stored data. Appointment queries exclude same-person meeting decoys. No personal data was cleared and no local emulator was used.

The first combined phone run passed 10/11 tests, with `CalendarV13ScreenTest` failing because no Compose hierarchy was found. Cause remains unconfirmed. The unchanged seven UI tests then passed on retry with temporary USB stay-awake enabled; the original setting was restored to zero. Both raw logs remain in ignored `app/build/reports/phase15/read-v16/`. Initial static-analysis findings were corrected before the successful full build; no test assertions were weakened.

These datasets are exposed development regressions. All retrieval coverage gains come from deterministic parsing. Original v6–v15 artifacts, approvals and results remain unchanged. Historical behavior tests remain; only the obsolete assertion that the v15 manifest certifies current shared source is superseded by the v16 certificate.

The separate classifier preparation implements reviewed source-bound loading, deduplication and family separation, predeclared grid/thresholds, single-thread fitting, calibration/selection separation, numeric export/parity, diagnostics and first-result protection. It stops before importing/fitting a model when human packets are incomplete. Remaining input: 30 calendar-event, 12 promise and six other calibration additions, exact 100-row subset review/freeze, and a separate reviewed/frozen 50-row selection collection. Fresh 150-request independent evaluation follows candidate freeze. Android ML parity, latency, memory and battery gates remain unexecuted. Current remote CI is a separate pending result until verified.
