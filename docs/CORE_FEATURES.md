# Implemented core features

Status as of 2026-10-09. This distinguishes working app flows from storage and protocol foundations. Phase 8 is complete locally: debug/release builds, lint, Detekt, 66 JVM tests and 143 Android tests on each of API 26/34 pass.

| Core feature | Working now | Remaining work |
| --- | --- | --- |
| Android app and navigation | Kotlin/Compose app; Today, Plan, Agent, Timeline and You; adaptive navigation, shared request composer, accessibility and restoration handling | Physical-device and production hardening |
| Local private storage | Room schema v6 with all 22 planned tables, Keystore-encrypted personal fields, explicit migrations, revision checks and atomic privacy cleanup | Broader corruption, performance and release hardening |
| Saved-data screens | Real saved tasks, schedule blocks, projects/goals, explicit preferences, people, reminders and factual history; observation updates screens | Stored reminders do not deliver notifications; saved schedules do not plan or schedule anything |
| Memory | Manual add/edit/delete, explicit versus derived filters, ranked local word search, structured links, expiry review and explicit cleanup | Automatic inference and behavioral learning are unavailable |
| English language baseline | Closed rule grammar; reads open tasks, explicit nonexpired memory and local promises; fixed-time date/time resolution; unknown, negated and ambiguous input handling | General language understanding, Hinglish, conversational follow-up merging and learned models are unavailable |
| Task creation and edits, Phase 8 | “Add a task to study” -> review -> Accept -> encrypted local task, durable action record, verified receipt and audit; rename, priority, postpone, replay protection, retry/cancel, revision-checked undo, linked-task deletion denial and content-scrubbed permanent deletion; accepted-action history in You | Projects/goals and recurring-task mutation remain unavailable |
| Task completion, Phase 8 | `complete task: Study` -> exact-title match to one open task -> review -> Accept -> verified local status change; durable recovery and revision/dependency-checked status undo; history distinguishes completion and undone actions | Recurring tasks and ambiguous titles cannot execute; reminders/schedules are not changed |
| Reminder/time requests | Approximate reminder proposal with explicit late-delivery disclosure; WorkManager unique scheduling, notification permission control, registration readback, cancellation, process/reboot recovery, separate delivery verification, privacy deletion and uncertain-post handling | Exact alarms, calls/messages, timer execution and event timeline remain unavailable |
| Agent protocol | Typed requests/actions/proposals/context/capabilities/receipts; correlation and policy checks; request text stays transient | Other typed action catalog entries are foundations, not executable capabilities |

Calendar availability, autonomous planning, proactive reminders, voice, habit analysis, emotional-support flows and ML remain future phases. No internet permission, telemetry, training upload or automatic behavioral collection is enabled.

Verification and exact limits: [roadmap](ROADMAP.md), [Phase 7](PHASE_7_VERIFICATION.md), [Phase 8 task creation](PHASE_8_VERIFICATION.md), [Phase 8 task completion](PHASE_8_TASK_COMPLETION.md).
