# Phase 10 decision engine

Complete baseline locally on 2026-10-09; final build, static checks and API 26/34 regression passed.

## Working flow

The shared composer supports `What should I do?`, `What should I do now?`, `What should I do next?`, and `What should I do with 30 minutes?` (1–1,440 minutes). It returns one saved-task suggestion, its recorded deadline/zone, priority, estimated effort, weighted score contributions, input limitations and snapshot time. Invalid durations need clarification; unknown or negated requests do not generate a recommendation. A recommendation request invalidates any outstanding mutation proposal. It never starts, completes, edits or schedules a task and has no Accept action. Answer-only users can request this advisory answer.

The query reads tasks, prerequisites, goals, projects and explicit preferences from one Room transaction snapshot. Only open, nonrecurring tasks with active linked goals/projects and completed prerequisites are eligible. Missing prerequisite records also block eligibility. With an explicit duration, the task must have a known estimate that fits; unknown estimates are not treated as fitting. Without a duration, the response states that available time is unknown. The saved focus-block preference never proves free time.

## Scoring policy: TASK_DECISION_V1

Every suggested winner stores the exact contribution values and total in encrypted `score_components_json`. These fixed rules are not calibrated probabilities or trained weights.

| Component | Contribution |
| --- | --- |
| Deadline | 0.35 × urgency: due now/past = 1; future under 24 hours = 0.9; under 72 hours = 0.6; under 7 days = 0.3; later = 0.1; no deadline = 0 |
| Priority / recorded importance | 0.30 × task priority / 3 |
| Active goal | 0.15 × (goal priority + 1) / 4; unlinked = 0 |
| Overdue | 0.10 if strictly before the snapshot time; otherwise 0 |
| Explicit focus preference | 0.10 when a known task estimate fits the saved focus-block duration; otherwise 0 |
| Effort penalty | Subtract 0.10 × min(estimated seconds / 14,400, 1); unknown estimate uses a fixed 0.05 penalty |

The total is clamped to 0–1. Equal scores use deadline, creation time and UUID in that order, making selection independent of database iteration order. Calendar availability, behavioral success and context switching are explicitly unknown and contribute nothing; their stored components are JSON null. Day-start and week-start preferences do not establish availability and are unused. There is no invented separate task-importance signal. The app does not infer behavior, observe other apps or train a model.

More than 1,000 saved task rows causes an explicit unsupported response rather than a partial ranking. No eligible candidate produces an honest empty answer without a fabricated recommendation or success event. Candidate reads are local and exhaustive within this baseline; broader scale/performance optimization remains hardening work.

## Persistence, replay and privacy

The winner, versioned components, reason, request/task-revision evidence, scored-source IDs, generated/expiry timestamps and correlated RECOMMENDATION_SHOWN event commit atomically. Recommendation content remains Keystore encrypted in existing schema v6. Raw request text and task notes are not copied into the recommendation. SHOWN means a suggestion was generated for an explicit answer, not proof that a person read it, accepted it or completed work. VERIFIED_LOCAL on its event verifies local persistence only; it grants no execution authority.

The snapshot lifetime is five minutes and task changes can invalidate it sooner. History in You shows up to ten recent generated/expiry timestamps and expandable saved explanations, clearly labeled historical rather than current task status. Old recommendations are never automatically executed or treated as a live plan. Repeating a request UUID that already generated a stored suggestion, including after restart or privacy redaction, asks for a fresh request instead of presenting a stale recommendation or recording another event. A new request UUID evaluates current inputs. Empty and failed evaluations have no stored recommendation and may be evaluated again.

Generated recommendations own personal content through their encrypted source evidence, including the selected task ID; they do not introduce a direct task-link blocker for guarded creation undo or confirmed deletion. Task deletion removes its source-owned recommendation and redacts the derived event. Deleting a scored goal, project or focus preference also removes dependent recommendations and redacts their event metadata. Existing event tombstones retain only generic history and request IDs. Key/read/write failures become visible failure answers; no empty-result fallback, key replacement, upload, new permission, dependency or physical schema migration was introduced.

## Verification

Ten JVM scenarios cover urgency/priority boundaries, explicit budget and unknown estimates, completed/missing/cancelled prerequisites, task lifecycle and recurrence, active goals/projects, explicit focus preference, deterministic ties, score bounds and duration grammar/negation. Nine device tests cover encrypted components, exact score reconstruction, no task/action/reminder mutation, answer-only behavior, concurrent request replay, database reopen, stale-request handling, atomic event-write rollback/retry, empty/ineligible answers, task/goal/project/preference deletion/redaction, guarded creation undo and confirmed deletion after advice, proposal invalidation and missing-key safety.

The real-app composer tests check a fitting recommendation, stored history/explanation, Timeline correlation and an empty answer. A dark-theme/double-font-size test checks explanation expansion/collapse and touch-target reachability in the existing edge-to-edge shell.

Final debug/release builds, debug/release lint, Detekt and all 77 JVM tests passed. The final full Android regression passed 160 executed tests on each local API 26 and API 34 emulator, with zero failures/errors. Each device also reported three existing explicitly ignored host-only tests: notification app-op mutation and the two restart/reboot stages. These skips are not passing checks, and those host procedures were not rerun in Phase 10. The final run includes the source-ownership fix and both guarded undo/deletion regression cases.

Verification command: `./gradlew.bat --offline --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:connectedDebugAndroidTest`. Connected XML reports are in `app/build/outputs/androidTest-results/connected/debug/`; JVM XML reports are in `app/build/test-results/testDebugUnitTest/`. Markdown links and whitespace checks passed. Schema v6, dependencies and permissions are unchanged.

No GitHub CI, physical-device, manual screenshot or measured latency/battery claim is made at this stage. Planning and availability calculations remain Phase 11.
