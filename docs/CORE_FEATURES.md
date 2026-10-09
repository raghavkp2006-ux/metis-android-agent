# Implemented core features

Status as of 2026-10-09. This distinguishes working app flows from storage and protocol foundations. Phase 7's bounded language baseline passed local and GitHub checks. Phase 8's first task-action slice passed local builds, lint, Detekt, 63 JVM tests and 110 Android tests on each of API 26/34; GitHub verification is pending.

| Core feature | Working now | Remaining work |
| --- | --- | --- |
| Android app and navigation | Kotlin/Compose app; Today, Plan, Agent, Timeline and You; adaptive navigation, shared request composer, accessibility and restoration handling | Physical-device and production hardening |
| Local private storage | Room schema v6 with all 22 planned tables, Keystore-encrypted personal fields, explicit migrations, revision checks and atomic privacy cleanup | Broader corruption, performance and release hardening |
| Saved-data screens | Real saved tasks, schedule blocks, projects/goals, explicit preferences, people, reminders and factual history; observation updates screens | Stored reminders do not deliver notifications; saved schedules do not plan or schedule anything |
| Memory | Manual add/edit/delete, explicit versus derived filters, ranked local word search, structured links, expiry review and explicit cleanup | Automatic inference and behavioral learning are unavailable |
| English language baseline | Closed rule grammar; reads open tasks, explicit nonexpired memory and local promises; fixed-time date/time resolution; unknown, negated and ambiguous input handling | General language understanding, Hinglish, conversational follow-up merging and learned models are unavailable |
| Task action, Phase 8 first slice | “Add a task to study” -> review -> Accept -> encrypted local task, durable action record, verified receipt and audit; replay protection, retry/cancel of pending acceptance, revision/link-checked undo; accepted-action history in You | Only plain creation without deadlines, reminders, priorities or project/goal links is executable. Update/complete/delete/postpone commands remain unavailable |
| Reminder/time requests | Recognizes reminder/task/timer wording; resolves unambiguous future dates/times or asks for clarification | Android scheduling, notification permissions, delivery, reboot reconciliation and external-action recovery remain unimplemented |
| Agent protocol | Typed requests/actions/proposals/context/capabilities/receipts; correlation and policy checks; request text stays transient | Other typed action catalog entries are foundations, not executable capabilities |

Calendar availability, autonomous planning, proactive reminders, voice, habit analysis, emotional-support flows and ML remain future phases. No internet permission, telemetry, training upload or automatic behavioral collection is enabled.

Verification and exact limits: [roadmap](ROADMAP.md), [Phase 7](PHASE_7_VERIFICATION.md), [Phase 8 task slice](PHASE_8_VERIFICATION.md).
