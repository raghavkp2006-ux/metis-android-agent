# Chat read-safety replacement TODO

Phase 0 follow-up. These 20 historical test methods remain archived because they require `docs/evaluation` fixtures. This file plans replacement coverage; it does not claim those replacements exist or pass.

## Gate before any Phase 6 chat code changes

- [ ] Implement and review self-contained synthetic replacement guard tests before changing any chat/agent production code, including the Phase 6 chat-created event path.
- [ ] Keep replacement fixtures in Kotlin test code or reviewed test resources. Do not depend on `docs/evaluation`, legacy ML/private archives, live production source hashes or generated certificates.
- [ ] Fix the clock and zone explicitly; use synthetic text only. Assert exact intent/scope/query for allowed requests and zero context construction, read-port calls and action/proposal dispatch for rejected requests.
- [ ] Test incorrect forced classifier labels, forged extraction, missing/ambiguous entities, negation/compound/unsupported requests, UTF-16 spans and query bounds. Include fixed-zone date windows and calendar ambiguity/DST cases where applicable.
- [ ] Exercise both orchestrator admission and direct specialist defense where the existing contracts support them. Verify allowed reads happen exactly once and cannot become writes or proposals.
- [ ] Run the existing active unit suite and the replacement tests; record executed/passed/failed/skipped counts and map each replacement to the archived method below. Keep original archives unchanged and report their skips separately.
- [ ] For Phase 6, add separate tests for proposal/accept authorization and overlap rechecking at acceptance, preserving the existing proposal pipeline. Do not extend language grammar as part of replacing these guards.

## Archived methods and replacement plan

- [ ] `CalendarV13Test.kt#exposedUserBatchHasFourCalendarAndFourMemoryReadsOnly` — Use synthetic calendar/memory requests and instrumented fake ports to verify exact dispatch counts and scopes.
- [ ] `CalendarV13Test.kt#exposedBenchmarkClarificationsRemainBlockedWithCalendarEnabled` — Use synthetic clarification cases; force calendar classification and assert zero context construction and zero read calls.
- [ ] `CalendarV13Test.kt#otherExposedCalendarRequestsStillNeedClarification` — Cover ambiguous and incomplete synthetic calendar requests; assert clarification without context or reads.
- [ ] `CandidateV12ReadTest.kt#completeCorpusPreservesScopeAndBlocksEveryForcedUnsafeLabel` — Create a reviewed synthetic request matrix with expected scopes; iterate all forced read labels and deny unsafe combinations before context.
- [ ] `CandidateV12ReadTest.kt#newRecallCannotForgeEntityTypesSpansOrCompleteQuery` — Forge entity types, UTF-16 spans and full query text on synthetic requests; assert zero context/read calls.
- [ ] `CandidateV14Test.kt#completeNativePythonReplayAndAllForcedReadLabels` — Replace corpus replay with explicit synthetic safe/unsafe rows and forced-label checks; verify exact intent, preserved query and dispatch count.
- [ ] `CandidateV14Test.kt#forgedSpansTypesAndCompleteQueriesFailBeforeContextAndDispatch` — Forge synthetic memory spans, types and complete query strings; assert rejection before context/dispatch.
- [ ] `CandidateV15Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels` — Exercise synthetic typed scopes, entities, query preservation and every forced read label against fake context/read counters.
- [ ] `CandidateV15Test.kt#forgedScopesAndExtractionNeverReachReadPorts` — Mutate synthetic typed scopes and extraction fields; assert no context construction or specialist reads.
- [ ] `CandidateV16Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels` — Use current synthetic scope/date examples with a fixed clock and zone; verify exact entities, queries and forced-label admission.
- [ ] `CandidateV16Test.kt#forgedScopesAndExtractionNeverReachReadPorts` — Forge current synthetic scopes, date windows and extraction values; assert rejection before reads.
- [ ] `ClassifierV11SafetyTest.kt#everyProhibitedRequestRejectsEveryForcedReadLabelBeforeContext` — Build a synthetic prohibited-request matrix; force every read label and assert context/read counters remain zero.
- [ ] `EnglishRulesTest.kt#versionedDevelopmentCorpusMatchesIntentAndSafeOutcome` — Replace historical language-corpus loading with a bounded synthetic intent/outcome table, preserving safe unsupported outcomes without adding grammar.
- [ ] `HumanDevelopment004Test.kt#exportCurrentLocalCalendarEnabledBehavior` — Use a fixed clock/zone and synthetic local calendar requests to assert current behavior directly, with no report export or source hashes.
- [ ] `HumanDevelopment004Test.kt#forcedReadLabelsAndForgedExtractionCannotBypassAdmission` — Force every read label and forge extracted entities on synthetic unsafe requests; assert admission cannot be bypassed.
- [ ] `ReadCoverageV10Test.kt#completeCorpusChecksCoverageSpansQueriesConstraintsAndAllForcedLabels` — Create a synthetic coverage matrix for exact UTF-16 spans, full queries, constraints and all forced labels.
- [ ] `ReadCoverageV10Test.kt#forgedTypesSpansAndCompleteQueriesCannotReachContextOrPorts` — Forge types, spans and complete query text from synthetic safe requests; assert zero context/port calls.
- [ ] `ReadCoverageV7Test.kt#developmentCasesPreserveReferencesAndCannotGainAuthorityFromWrongLabels` — Check synthetic reference-preservation cases and every wrong forced label; assert labels cannot add authority.
- [ ] `ReadCoverageV8Test.kt#developmentCasesCheckSpansLiteralQueriesAndEveryForcedLabelBeforeContext` — Check synthetic UTF-16 spans, literal queries and every forced label before context, including supplementary Unicode characters.
- [ ] `ReadSafetyV6Test.kt#exposedNonreadsCannotReadEvenUnderEveryIncorrectClassifierLabel` — Create synthetic non-read and negated requests; force each read label and assert no context or read calls.

Completion requires reviewer sign-off on the coverage mapping and passing evidence before the first chat production-code change. Phase 1 remains blocked on user confirmation independently of this future gate.
