# Chat read-safety replacement TODO

Phase 0 follow-up. These 20 historical test methods remain archived because they require `docs/evaluation` fixtures. Phase 6b replacement coverage is in `ChatReadSafetyReplacementTest.kt`. The historical tests remain archived.

## Gate before any Phase 6 chat code changes

- [x] Implement and review self-contained synthetic replacement guard tests before changing any chat/agent production code. The 20 Phase 6b replacements precede Phase 6c; the chat-created event path is deferred below.
- [x] Keep replacement fixtures in Kotlin test code or reviewed test resources. Do not depend on `docs/evaluation`, legacy ML/private archives, live production source hashes or generated certificates.
- [x] Fix the clock and zone explicitly; use synthetic text only. Assert exact intent/scope/query for allowed requests and zero context construction, read-port calls and action/proposal dispatch for rejected requests.
- [x] Test incorrect forced classifier labels, forged extraction, missing/ambiguous entities, negation/compound/unsupported requests, UTF-16 spans and query bounds. Include fixed-zone date windows and calendar ambiguity/DST cases where applicable.
- [x] Exercise both orchestrator admission and direct specialist defense where the existing contracts support them. Verify allowed reads happen exactly once and cannot become writes or proposals.
- [x] Run the existing active unit suite and the replacement tests; record executed/passed/failed/skipped counts and map each replacement to the archived method below. Keep original archives unchanged and report their skips separately.
- [x] For Phase 6c, add separate tests for proposal/accept authorization and overlap rechecking at acceptance, preserving the existing proposal pipeline. `ChatAcceptanceSafetyTest` covers both paths without changing language grammar.

## Phase 6c disposition

| Deliverable | Status | Reason |
| --- | --- | --- |
| Chat proposal requires explicit canonical acceptance | Done | `ChatAcceptanceSafetyTest#chatTaskProposalWritesOnlyAfterCanonicalAcceptAndRecordsOutcome` checks no task, run, audit, or event before accept; copied and replayed proposals are denied; canonical accept produces one verified outcome. |
| Recheck overlap at accept | Done | `ChatAcceptanceSafetyTest#chatPlanAcceptRejectsOverlapAddedAfterProposalWithoutReceiptOrAudit` saves an overlapping commitment between chat proposal and accept, then checks denial and absence of plan writes, receipts, audit, and events. |
| Chat-created event path | Deferred | The TODO names this path but defines no chat-create action. `AgentEventType` and `FoundationCatalog.EVENT_TYPES` have no `CHAT_CREATED` entry. A standalone chat event would bypass the proposal/accept/receipt path; adding a new action or event identifier would expand the frozen protocol. Existing accepted task and plan events remain tied to their receipts. |

## Archived methods and replacement plan

- [x] `CalendarV13Test.kt#exposedUserBatchHasFourCalendarAndFourMemoryReadsOnly` — Use synthetic calendar/memory requests and instrumented fake ports to verify exact dispatch counts and scopes.
- [x] `CalendarV13Test.kt#exposedBenchmarkClarificationsRemainBlockedWithCalendarEnabled` — Use synthetic clarification cases; force calendar classification and assert zero context construction and zero read calls.
- [x] `CalendarV13Test.kt#otherExposedCalendarRequestsStillNeedClarification` — Cover ambiguous and incomplete synthetic calendar requests; assert clarification without context or reads.
- [x] `CandidateV12ReadTest.kt#completeCorpusPreservesScopeAndBlocksEveryForcedUnsafeLabel` — Create a reviewed synthetic request matrix with expected scopes; iterate all forced read labels and deny unsafe combinations before context.
- [x] `CandidateV12ReadTest.kt#newRecallCannotForgeEntityTypesSpansOrCompleteQuery` — Forge entity types, UTF-16 spans and full query text on synthetic requests; assert zero context/read calls.
- [x] `CandidateV14Test.kt#completeNativePythonReplayAndAllForcedReadLabels` — Replace corpus replay with explicit synthetic safe/unsafe rows and forced-label checks; verify exact intent, preserved query and dispatch count.
- [x] `CandidateV14Test.kt#forgedSpansTypesAndCompleteQueriesFailBeforeContextAndDispatch` — Forge synthetic memory spans, types and complete query strings; assert rejection before context/dispatch.
- [x] `CandidateV15Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels` — Exercise synthetic typed scopes, entities, query preservation and every forced read label against fake context/read counters.
- [x] `CandidateV15Test.kt#forgedScopesAndExtractionNeverReachReadPorts` — Mutate synthetic typed scopes and extraction fields; assert no context construction or specialist reads.
- [x] `CandidateV16Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels` — Use current synthetic scope/date examples with a fixed clock and zone; verify exact entities, queries and forced-label admission.
- [x] `CandidateV16Test.kt#forgedScopesAndExtractionNeverReachReadPorts` — Forge current synthetic scopes, date windows and extraction values; assert rejection before reads.
- [x] `ClassifierV11SafetyTest.kt#everyProhibitedRequestRejectsEveryForcedReadLabelBeforeContext` — Build a synthetic prohibited-request matrix; force every read label and assert context/read counters remain zero.
- [x] `EnglishRulesTest.kt#versionedDevelopmentCorpusMatchesIntentAndSafeOutcome` — Replace historical language-corpus loading with a bounded synthetic intent/outcome table, preserving safe unsupported outcomes without adding grammar.
- [x] `HumanDevelopment004Test.kt#exportCurrentLocalCalendarEnabledBehavior` — Use a fixed clock/zone and synthetic local calendar requests to assert current behavior directly, with no report export or source hashes.
- [x] `HumanDevelopment004Test.kt#forcedReadLabelsAndForgedExtractionCannotBypassAdmission` — Force every read label and forge extracted entities on synthetic unsafe requests; assert admission cannot be bypassed.
- [x] `ReadCoverageV10Test.kt#completeCorpusChecksCoverageSpansQueriesConstraintsAndAllForcedLabels` — Create a synthetic coverage matrix for exact UTF-16 spans, full queries, constraints and all forced labels.
- [x] `ReadCoverageV10Test.kt#forgedTypesSpansAndCompleteQueriesCannotReachContextOrPorts` — Forge types, spans and complete query text from synthetic safe requests; assert zero context/port calls.
- [x] `ReadCoverageV7Test.kt#developmentCasesPreserveReferencesAndCannotGainAuthorityFromWrongLabels` — Check synthetic reference-preservation cases and every wrong forced label; assert labels cannot add authority.
- [x] `ReadCoverageV8Test.kt#developmentCasesCheckSpansLiteralQueriesAndEveryForcedLabelBeforeContext` — Check synthetic UTF-16 spans, literal queries and every forced label before context, including supplementary Unicode characters.
- [x] `ReadSafetyV6Test.kt#exposedNonreadsCannotReadEvenUnderEveryIncorrectClassifierLabel` — Create synthetic non-read and negated requests; force each read label and assert no context or read calls.

## Phase 6b coverage mapping

All replacement names below are methods of `ChatReadSafetyReplacementTest`. Each method uses only in-code synthetic fixtures. The fake ports count context snapshots, specialist dispatches, reads, and policy reviews. Rejected paths also check the direct specialist where its parsed-request contract permits that call.

Local gate: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt` passed. JUnit reported 274 total, 254 executed/passed, 0 failed, 20 skipped. The 20 skips are the unchanged original archived methods; all 20 replacements executed and passed with 0 skips.

| Archived method | Replacement test | What it proves |
| --- | --- | --- |
| `CalendarV13Test#exposedUserBatchHasFourCalendarAndFourMemoryReadsOnly` | `calendarBatchDispatchesExactKindsAndCounts` | Four calendar scopes and four literal memory queries each read once. |
| `CalendarV13Test#exposedBenchmarkClarificationsRemainBlockedWithCalendarEnabled` | `calendarClarificationsBlockForcedLabelsBeforeContext` | Negated and compounded calendar requests cannot construct context or read under forced labels. |
| `CalendarV13Test#otherExposedCalendarRequestsStillNeedClarification` | `calendarIncompleteAndAmbiguousRequestsNeedClarification` | Incomplete and ambiguous calendar forms are rejected before dispatch. |
| `CandidateV12ReadTest#completeCorpusPreservesScopeAndBlocksEveryForcedUnsafeLabel` | `v12SyntheticScopesAndEveryForcedUnsafeLabel` | V12 task and memory intent/query, every forced label, and 256/257 UTF-16 query bound. |
| `CandidateV12ReadTest#newRecallCannotForgeEntityTypesSpansOrCompleteQuery` | `v12ForgedTypesSpansAndCompleteQueriesCannotRead` | Changed type, span, raw text, and complete query fail admission and direct validation. |
| `CandidateV14Test#completeNativePythonReplayAndAllForcedReadLabels` | `v14SyntheticSafeUnsafeRowsAndForcedLabels` | Safe V14 memory query reads once; unsafe extensions fail every read label. |
| `CandidateV14Test#forgedSpansTypesAndCompleteQueriesFailBeforeContextAndDispatch` | `v14ForgedSpansTypesAndQueriesFailBeforeDispatch` | Forged V14 type, raw text, and query cannot reach ports. |
| `CandidateV15Test#nativePythonScopesEntitiesQueriesAndForcedLabels` | `v15TypedScopesEntitiesAndForcedLabels` | Typed V15 memory scope and query are exact; compound request fails forced labels. |
| `CandidateV15Test#forgedScopesAndExtractionNeverReachReadPorts` | `v15ForgedScopesAndExtractionNeverRead` | Forged typed scope and extracted type are rejected by specialist and admission. |
| `CandidateV16Test#nativePythonScopesEntitiesQueriesAndForcedLabels` | `v16CurrentScopesDatesAndForcedLabels` | Current V16 calendar scope and literal query dispatch once; compound variant fails. |
| `CandidateV16Test#forgedScopesAndExtractionNeverReachReadPorts` | `v16ForgedScopesDatesAndExtractionNeverRead` | Forged scope subject, date, and entity type cannot read. |
| `ClassifierV11SafetyTest#everyProhibitedRequestRejectsEveryForcedReadLabelBeforeContext` | `prohibitedMatrixRejectsEveryReadLabel` | Write, negated, unsupported, and compound requests fail all forced read labels. |
| `EnglishRulesTest#versionedDevelopmentCorpusMatchesIntentAndSafeOutcome` | `boundedEnglishIntentAndSafeOutcomeTable` | Bounded intent table preserves safe outcome for an unsupported compound request. |
| `HumanDevelopment004Test#exportCurrentLocalCalendarEnabledBehavior` | `fixedLocalCalendarWindowAndAmbiguity` | Captured clock/zone, tomorrow calendar window, DST day length, and ambiguous day rejection. |
| `HumanDevelopment004Test#forcedReadLabelsAndForgedExtractionCannotBypassAdmission` | `forcedLabelsAndForgedExtractionCannotBypassAdmission` | Forced labels and forged extracted type cannot bypass admission. |
| `ReadCoverageV10Test#completeCorpusChecksCoverageSpansQueriesConstraintsAndAllForcedLabels` | `syntheticCoverageSpansQueriesConstraintsAndLabels` | Exact V16 entity UTF-16 span and full query; constrained request fails every label. |
| `ReadCoverageV10Test#forgedTypesSpansAndCompleteQueriesCannotReachContextOrPorts` | `forgedTypesSpansAndQueriesCannotReachContextOrPorts` | Forged entity fields fail before context and reads. |
| `ReadCoverageV7Test#developmentCasesPreserveReferencesAndCannotGainAuthorityFromWrongLabels` | `referencePreservationAndWrongLabelsCannotAddAuthority` | Named reference stays literal; wrong forced labels cannot read. |
| `ReadCoverageV8Test#developmentCasesCheckSpansLiteralQueriesAndEveryForcedLabelBeforeContext` | `supplementaryUtf16SpansAndEveryWrongLabel` | Supplementary emoji is counted in UTF-16 span and wrong labels fail before context. |
| `ReadSafetyV6Test#exposedNonreadsCannotReadEvenUnderEveryIncorrectClassifierLabel` | `syntheticNonreadsAndNegationRejectEveryReadLabel` | Synthetic writes, negation, and compound requests cannot read under any forced label. |

Completion requires reviewer sign-off on the coverage mapping and passing evidence before the first chat production-code change. Phase 1 remains blocked on user confirmation independently of this future gate.
