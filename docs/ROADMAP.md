# Phase roadmap

Canonical numbering: section 56 of the [source plan](reference/original-build-plan.md). The source's detailed sections 60–73 reuse phase numbers for different work. This roadmap uses the complete sequence and carries their content into the matching gates, avoiding two competing implementations.

One phase at a time. Pass and record its exit condition before beginning the next phase. A document establishes a contract; it does not prove implemented Android behavior.

| Phase | Deliverable | Exit condition | Status |
| --- | --- | --- | --- |
| 0 | Product contract | Required product/protocol/data/UI/dataset contracts written; links and consistency checked | Documented; validation recorded below |
| 1 | Repository + CI | Gradle wrapper/catalog, Kotlin/Compose app scaffold; compile, lint, unit tests, static analysis; Android smoke tests where possible; seven required project documents | Complete; build and API 26/36 runtime CI passed on 2026-10-06 ([report](PHASE_1_VERIFICATION.md)) |
| 2 | Design system | Theme/tokens/components with loading, error, empty, denial, accessibility and previews | Complete baseline; CI and emulator review passed on 2026-10-06 ([report](PHASE_2_VERIFICATION.md)) |
| 3 | Navigation shell | Today, Plan, Agent, Timeline, You; shared composer entry and state/back/inset handling | Complete baseline; local build, visual review and API 26/34/36 tests passed on 2026-10-07 ([report](PHASE_3_VERIFICATION.md)) |
| 4 | Database | Room entities/DAOs/repository interfaces, exported schema, constraints/indexes/FTS, seed/inspection debug paths, migration/data tests | Complete database baseline on 2026-10-08; all 22 schema v6 tables, encrypted fields, migrations, search, privacy cleanup and saved-data screens; local API 26/34 and GitHub API 26/36 passed ([completion report](PHASE_4_COMPLETION.md)) |
| 5 | Memory engine | Local structured CRUD/search/ranking with factual/derived separation and encryption | Complete baseline on 2026-10-08; manual memory workflows, deterministic ranking, structured retrieval and reviewed retention passed build/static checks and all 95 Android tests on local API 26/34 and GitHub API 26/36 ([report](PHASE_5_VERIFICATION.md)) |
| 6 | Agent protocol | Request/result flow, orchestrator interfaces, context/capability snapshots, typed proposals | Pending |
| 7 | Language engine | Evaluated rule baseline, extraction/resolution, unknown/negation/ambiguity handling | Pending |
| 8 | Action engine | Policy-gated typed executors, persisted lifecycle, verification/receipts, permissions, retry/undo; reminder milestone below | Pending |
| 9 | Event engine | Persisted normalized outcomes, timeline and correlated audit; reminder milestone including visible timeline passes | Pending |
| 10 | Decision engine | Explainable scoring and “What should I do?” with stored score components | Pending |
| 11 | Planner | “Plan my day/evening”; free-time/conflict calculations, deadline-first baseline, user acceptance and reminder proposals | Pending |
| 12 | Proactive system | Deadlines/missed tasks/promises/conflicts; opt-in triggers, notification priority, rate limiting/suppression | Pending |
| 13 | Voice | Speech -> same AgentRequest; permission denial, offline capability and online opt-in handled | Pending |
| 14 | Habits/insights | Daily/weekly reflection; repeated-evidence statistics with consent and provenance | Pending |
| 15 | Advanced ML | Held-out gains over rules meet latency/memory/battery/safety gates; keep rules if no gains | Pending |
| 16 | Emotional support | Bounded structured support flows; no diagnosis, professional replacement, or emotional dependency claims | Pending |
| 17 | Hardening | Security/permission/process-death/restart/corruption tests; battery, performance, device, UI/accessibility checks | Pending |
| 18 | Pilot | Consenting real-user evaluation of success, wrong actions, failures, battery and compatibility without default raw telemetry | Pending |
| 19 | Production | Store-ready build, policies, release signing and versioning, review of target SDK and production requirements | Pending |

Phase 1 documents: README.md, ARCHITECTURE.md, PRIVACY.md, SECURITY.md, CONTRIBUTING.md, DATASETS.md, MODEL_CARD.md. It uses Gradle Kotlin DSL and a version catalog. CI must clearly distinguish executed checks from device checks unavailable on a runner; no passing claims for skipped tests.

## First vertical slice gate (Phases 8–9)

Input: “Remind me tomorrow at 8 to call Rahul.”

1. Shared composer creates AgentRequest with a fixed current time and zone in tests.
2. Language identifies CREATE_REMINDER, resolves tomorrow, extracts time and person/title; it does not select CALL_PERSON.
3. Missing AM/PM produces a follow-up. Person ambiguity is resolved when the action actually needs a person reference; a plain text reminder title does not require broad contacts access.
4. Proposal shows explicit date, time, zone, title, scheduling precision, reason, and any delivery limitations.
5. Policy applies the current autonomy setting, requested scope, and capabilities. Level 1 requires explicit proposal acceptance.
6. Store a pending action and reminder; schedule through the appropriate Android mechanism; verify scheduling and finalize the receipt/event/audit. Failure is visible and recoverable.
7. Timeline displays the real outcome. Delivery produces a separate trigger/delivery outcome; creating a reminder does not prove delivery and never makes the call.
8. Acceptance replay, permission revocation, crash/restart, cancelled proposal, negated input, ambiguous time/person, unavailable exact access, and disabled notifications all fail safely.

Do not start decision/planning features until this integrated gate passes. Phases 8–9 may build the pieces in sequence, but each later phase relies on a verified flow.

## Follow-on capability gates

- Tasks: create/update/complete/delete/postpone/search, priority/deadlines, recurrence/projects/goals; “What tasks do I have?” works through the composer. Implement read parsing in Phase 7 and mutations in Phase 8.
- Calendar: read/insertion, conflicts, availability; “When am I free tomorrow?” requires a granted calendar reader and avoids treating missing access as an empty calendar.
- Memory: “What did I promise Rahul?”, “What do you know about my DSP exam?”, and project/person queries operate locally through Phase 5 retrieval and Phase 7 parsing.
- Planner: propose schedule from tasks, deadlines, preferences, and known availability; accepting a plan persists blocks and separately authorizes required reminders.
- Proactive/behavioral features: opt-in observation, suppression, repeated evidence, and explainable daily/weekly reflection.

The complete acceptance scenario remains “I have an exam tomorrow. Help me prepare”: clarify scope, create goal/tasks, calculate known availability, propose and accept a schedule/reminders, track outcomes, detect missed sessions, revise, review, and answer progress questions. It is a later integration target, not Phase 0 functionality.

## Phase 0 verification record

The v0.1 baseline preserves the original source and covers each item from its section 57: supported intents/actions, risk/permission matrices, schema v1, design tokens/navigation, AgentRequest/action/event schemas, dataset schema, and Definition of Done. Validation passed: repository-relative Markdown links resolve; all 32 source intent names are preserved; all 19 source tables are specified; the roadmap contains exactly Phases 0–19; example entity offsets match their text; and git diff --check reports no whitespace errors. No app build, CI run, model evaluation, or Android device test has been performed in this documentation phase.

Requirements changes must update related contracts and their gates in the same change; completed phases need targeted re-verification when their assumptions change.
