# IRS-003 first approved evaluation: NO_GO

**First scored 2026-10-10 against unchanged v5. The reviewed gate failed.** Human approval and freshness confirmation were received before freezing and inference. The first score is preserved; annotations and candidate were not changed after observing predictions.

## Result

| Measure | First score |
| --- | ---: |
| Human-approved examples | 145 |
| Intent macro F1 | **0.4328** (required >= 0.90) |
| Correct positive intents | 31/102 |
| Correct intents including UNKNOWN | 68/145 |
| UNKNOWN given a read-intent label | 6/43 |
| Forbidden simulated reads | **14** (required 0) |
| Exact expected dispositions | 86/145 |
| End-to-end entity matches, nonempty expected entities | 3/37 |
| Isolated entity matches given expected intent | 4/37 |
| Annotated filter-field matches | 1/62 |
| Exact entire filter dictionaries, including empty | 84/145 |
| Native non-read matches in this set | 0 (preservation is not exercised here) |

The 14 forbidden simulated reads comprise five UNKNOWN boundary requests and nine requests whose approved outcome was clarification. They show failure to decline unsupported constraints, unresolved references or incorrect routing. **No repository data was read and no real mutation executed**: outcomes are the existing Python simulation after native-rule/ML intent combination.

Classifier abstention, intent confusion, entity extraction and constraint preservation all contribute. The approved benchmark includes extended filter truth (topic, subject, age, priority and richer dates) which v5 does not implement. Strict filter/disposition failures must be distinguished from forbidden reads; they do not all represent unsafe actions. The low intent F1 and 14 forbidden simulated reads are independent reasons this candidate fails adoption.

## Forbidden simulated-read examples

| Frozen ID | Query | Approved outcome / failure |
| --- | --- | --- |
| 0014 | the insurance renewal. did i set a reminder for that | UNKNOWN/clarify became a promise read |
| 0017 | what tasks are older than a month now | Clarification required for age filtering; unfiltered task read |
| 0021 | i need to renew my library card. has that been sitting there | Clarification required for topic lookup; unfiltered task read |
| 0026 | what did i write about the trip to munnar last year | Memory/date clarification became a promise read |
| 0052 | i told arjun i would cover his shift. when was that | Subject constraint ignored |
| 0057 | i told sanjay i would review his manuscript. has that come due | Subject/deadline clarification bypassed |
| 0065 | i told vikram i would help him with his resume | Ambiguous creation/confirmation became a promise read |
| 0066 | who did i say i would pick up from the station | Missing person became a promise read |
| 0070 | remind me i promised to take my sister to the zoo | Reminder command became a promise read |
| 0071 | did i tell rohan i would pay him back by end of month | Subject/date restriction ignored |
| 0103 | i need to fly to mars | Unsupported request became a promise read |
| 0129 | what is the most urgent thing on my list right now | Priority clarification bypassed |
| 0132 | pull up whatever i have saved about the flat rent | Approved cross-store request reduced to memory read |
| 0139 | remind me what i need to do before the trip to kerala | Task context query became a promise read |

Full IDs use prefix `p15v5-fresh-`. [Failure analysis](failure-analysis.csv) maps them back to the source review IDs and records all intent/disposition/entity/filter mismatches. No annotation was revised to improve this result.

## Frozen evidence

- [Approval record](human-approval.json): direct user approval, scope and exact reviewed-file hashes.
- [Approved annotations](approved-review.csv), [frozen benchmark](frozen-benchmark.json), [native input](frozen-benchmark.tsv).
- [Native snapshot](native-rules.tsv): production EnglishRules over the frozen input; Gradle snapshot task succeeded.
- [First reviewed result](first-reviewed-result.json): all 145 row predictions, annotated truth, metrics, gate result and hashes of the full generated report, frozen input, native snapshot, approval and export.
- [Exposure receipt](exposure-receipt.json): benchmark consumed exactly once in this workflow.

The evaluated candidate parameters and exported JSON hash **exactly match the pre-existing frozen v5 reference**. Native historical regressions remained unchanged in the full evaluator run. The complete generated development/regression report and disabled export remain in ignored `app/build/reports/phase15/irs003-first/`; the complete reviewed section and binding hashes are retained in Git here. No `--record-reference` was used. Neither v5 source/data nor its original reference result was overwritten.

## Gate status and next work

| Gate | State |
| --- | --- |
| Human-authored data, approval, minimum coverage and candidate-bound freeze | Completed for this first evaluation |
| Reviewed accuracy/rejection/entities/filters/dispositions | **Failed** |
| Representative calibration | Pending separate representative data |
| Actual repository/policy outcomes | Pending prerequisite quality gates and actual adapter evidence |
| Android parity and physical-device costs | Pending prerequisite gates and devices |

These 145 queries are now **exposed regression evidence**, never fresh independent evaluation for a later candidate. No additional queries are needed to explain this first failure; retain the result and improve a separately versioned candidate.

The next candidate should prioritize classifier-independent read safety: do not turn unsupported/compound/action requests into reads; require a valid person/topic rather than extracting an arbitrary noun phrase; preserve requested constraints and decline unsupported filtering before repository access. Broader wording coverage and entity extraction then need development data from separate sources and families. Evaluate on these exposed regressions during development, but collect a separate fresh reviewed benchmark only after the next candidate is fixed. Keep model selection/calibration data separate. ML stays disabled and unbundled; overall decision remains **NO_GO**.
