# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: the Android foundation, private local storage and memory, typed agent protocol, bounded English rules, confirmed task/reminder actions, explainable task decisions, accepted deadline-first plans, proactive updates, voice input and opt-in behavioral reflection. Room schema v6 stores all 22 planned tables with Keystore-encrypted personal fields, explicit migrations and transaction-protected updates. The shared composer reads saved tasks, explicit nonexpired memories and promises, and answers explicit task-recommendation requests with stored score explanations. Review and Accept can create, complete, rename, prioritize, postpone or permanently delete a uniquely identified supported task; register an approximate local reminder; or save whole-task schedule blocks after a freshness and policy recheck. Pending actions, verified receipts and audit records support recovery and replay protection. Task undo rechecks revisions and relevant dependencies; deletion has no undo and removes personal action history. Reminders require enabled notifications, may run late, and record notification posting separately from registration. Voice uses Android on-device speech recognition when available; online fallback is off by default and requires a separate opt-in. Phase 14 adds default-off, user-triggered local daily/weekly outcome summaries and repeated-evidence insights; it does not use private text or run in the background. External calendar availability and weighted/autonomous planning remain unavailable. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target and JDK 17.

## Phase progress

Each phase or phase slice is committed separately, with its README status and verification record updated together. Existing phase history is preserved.

| Phase | Implementation | Status |
| --- | --- | --- |
| 0 | Product, protocol, data, and UI contracts | Documented and checked |
| 1 | Android foundation, Gradle, and CI | Complete baseline |
| 2 | Compose design system | Complete baseline |
| 3 | Adaptive navigation and shared temporary request draft | Complete baseline |
| 4 | Encrypted persistence, saved-record screens, bounded memory search, links, task prerequisites, explicit preferences, migrations and debug inspection | Complete database baseline; seven slices committed |
| 5 | Local memory CRUD, ranked/structured retrieval and explicit retention | Complete baseline; local and GitHub checks passed |
| 6 | Typed request/result pipeline, context and capability snapshots, proposals and executor interfaces | Complete baseline; local and GitHub checks passed |
| 7 | Bounded English rules, date/time resolution and local read answers | Complete bounded baseline; local and GitHub checks passed |
| 8 | Confirmed task creation/completion/rename/priority/postpone/delete, durable receipts/audit, recovery/undo and approximate WorkManager reminders | Complete locally on API 26/34; historical slice reports below |
| 9 | Transactional normalized task/reminder outcomes, encrypted timeline and correlated policy audit | Complete baseline; build/static, 67 JVM tests, full regression and focused UI checks passed locally on API 26/34 ([report](docs/PHASE_9_VERIFICATION.md)) |
| 10 | Explainable task suggestions, persisted score components and inspectable history | Complete baseline locally; build/static checks, 77 JVM tests and 160 executed Android tests per API 26/34 device passed; host-only skips recorded ([report](docs/PHASE_10_VERIFICATION.md)) |
| 11 | Deadline-first day/evening proposals, saved-data free-time gaps, explicit acceptance and separate reminder reviews | Complete baseline locally; build/static checks, 88 JVM tests and 171 executed Android tests per API 26/34 passed; host-only skips recorded ([report](docs/PHASE_11_VERIFICATION.md)) |
| 12 | Proactive local updates for deadlines, missed tasks, repeated postponements, promises and saved schedule conflicts, with opt-in, notification priority and suppression | Complete baseline locally; 94 JVM tests, debug/release lint and builds, Detekt and API 26/34 Android regression passed ([report](docs/PHASE_12_VERIFICATION.md)) |
| 13 | Voice input via Android recognition, with explicit microphone disclosure, on-device preference and opt-in online fallback through the shared request path | Complete bounded baseline locally; 95 JVM tests passed and the 360-case API 26/34 regression had zero failures, with six host-dependent skips ([report](docs/PHASE_13_VERIFICATION.md)) |
| 14–19 | Habits/insights through production | Pending |

Phase 4 validation: debug/release builds, 21 JVM tests, lint and Detekt passed. All 85 Android tests passed locally on API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37779790806), including migrations, saved-data screens, missing-key failures and atomic privacy cleanup. Release APKs exclude debug controls. Results and the earlier dependency-resolution failure are recorded in the [Phase 4 completion report](docs/PHASE_4_COMPLETION.md). Encryption, safe key failure, no-network permissions and backup exclusions remain intact. Physical-device and broader performance/security hardening remain later gates.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), [Phase 4 search and migration verification](docs/PHASE_4_SEARCH_VERIFICATION.md), [Phase 4 task dependencies verification](docs/PHASE_4_DEPENDENCIES_VERIFICATION.md), [Phase 4 preferences verification](docs/PHASE_4_PREFERENCES_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).

The [agent training plan](docs/AGENT_TRAINING_PLAN.md) explains how individual specialists will be evaluated and when trained models are justified. No ML model is currently trained or bundled; deterministic parsing starts in Phase 7 and evaluated ML improvements belong to Phase 15. User preferences and memory remain local context, with no automatic training upload.

The [projects/goals report](docs/PHASE_4_PLANNING_VERIFICATION.md) covers schema v5, task links and deletion behavior. The [debug inspection report](docs/PHASE_4_INSPECTION_VERIFICATION.md) covers the fifth database slice and its diagnostic limits. The [CI recovery report](docs/PHASE_4_CI_RECOVERY.md) records the API 26 keyboard and fixture cleanup fixes.

Phase 5 passed debug/release builds, 26 JVM tests, lint, Detekt and all 95 Android tests on local API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37798814741). The [memory report](docs/PHASE_5_VERIFICATION.md) explains manual edits, provenance boundaries, ranking factors, working-memory expiry, reviewed deletion and measured latency limits. Memory changes stay local; memory mutation commands and model training remain unavailable.

Phase 6's [protocol report](docs/PHASE_6_VERIFICATION.md) describes the typed models, proposal review and shared composer flow. Submitted text is cleared from the restorable draft; request/results stay in RAM and are discarded when the sheet closes. The Phase 7 parser adds local read answers without an executor, conversation persistence, new permission or training upload. Durable policy-gated execution belongs to Phase 8.

Phase 6 passed debug/release builds, 48 JVM tests, lint, Detekt and all 98 Android tests on local API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37803899355). Its protocol baseline is complete. See the [Phase 7 report](docs/PHASE_7_VERIFICATION.md) for the current language scope and validation limits.

See the [core feature status](docs/CORE_FEATURES.md) and [Phase 8 task-action verification](docs/PHASE_8_VERIFICATION.md) for working flows and remaining gates.

The historical [Phase 8 completion slice](docs/PHASE_8_TASK_COMPLETION.md) adds `complete task: Study` for one exact-title open task. It requires proposal acceptance, rechecks revisions and prerequisites, changes only local status, and supports guarded status undo. Recurring tasks and ambiguous titles require a fresh supported request.

Phase 9 records supported task and reminder action outcomes in the same transaction as their receipts. Timeline shows encrypted persisted history, explicit verification, local times with zones and expandable correlated policy reasons. Reminder registration, notification posting and uncertain/denied delivery remain separate outcomes. Privacy deletion removes personal event details. See the [event engine report](docs/PHASE_9_VERIFICATION.md) for verification and limits.

Phase 10 answers `What should I do?` and `What should I do with 30 minutes?` with a saved-task suggestion, deterministic score components and explicit unknown-input limits. It excludes unfinished prerequisites and unsupported task states. You retains encrypted historical explanations; the suggestion never starts a task or accepts a plan. See the [decision engine report](docs/PHASE_10_VERIFICATION.md).

Phase 13 adds user-started voice transcription in the shared composer. It prefers Android's on-device recognizer where available; online speech fallback remains disabled until explicitly enabled in You. Transcripts remain editable and are submitted through the same AgentRequest path only after Send. See the [voice report](docs/PHASE_13_VERIFICATION.md) for privacy behavior, verification and device limits.
