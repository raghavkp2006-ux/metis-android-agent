# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: the Android foundation, Compose navigation shell and seven Phase 4 persistence slices. Room schema v6 stores all 22 planned tables with Keystore-encrypted personal fields, explicit migrations and transaction-protected updates. Today, Plan, You and Timeline observe real saved data, including tasks, schedules, memory, projects/goals, preferences, people, reminders/routines and event history. Memory search uses temporary in-memory FTS4 with visible 20/200 candidate limits. Debug-only seeding and aggregate inspection never ship in release. The shared request draft remains disabled for sending; reminder records do not schedule Android notifications. Commands, planning, voice, behavioral inference and autonomous actions remain unavailable. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target and JDK 17.

## Phase progress

Each phase or phase slice is committed separately, with its README status and verification record updated together. Existing phase history is preserved.

| Phase | Implementation | Status |
| --- | --- | --- |
| 0 | Product, protocol, data, and UI contracts | Documented and checked |
| 1 | Android foundation, Gradle, and CI | Complete baseline |
| 2 | Compose design system | Complete baseline |
| 3 | Adaptive navigation and shared temporary request draft | Complete baseline |
| 4 | Encrypted persistence, saved-record screens, bounded memory search, links, task prerequisites, explicit preferences, migrations and debug inspection | Complete database baseline; seven slices committed |
| 5–19 | Memory retrieval, agent pipeline, actions, planning, and production hardening | Pending |

Phase 4 validation: debug/release builds, 21 JVM tests, lint and Detekt passed. All 85 Android tests passed locally on API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37779790806), including migrations, saved-data screens, missing-key failures and atomic privacy cleanup. Release APKs exclude debug controls. Results and the earlier dependency-resolution failure are recorded in the [Phase 4 completion report](docs/PHASE_4_COMPLETION.md). Encryption, safe key failure, no-network permissions and backup exclusions remain intact. Physical-device and broader performance/security hardening remain later gates.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), [Phase 4 search and migration verification](docs/PHASE_4_SEARCH_VERIFICATION.md), [Phase 4 task dependencies verification](docs/PHASE_4_DEPENDENCIES_VERIFICATION.md), [Phase 4 preferences verification](docs/PHASE_4_PREFERENCES_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).

The [agent training plan](docs/AGENT_TRAINING_PLAN.md) explains how individual specialists will be evaluated and when trained models are justified. No ML model is currently trained or bundled; deterministic parsing starts in Phase 7 and evaluated ML improvements belong to Phase 15. User preferences and memory remain local context, with no automatic training upload.

The [projects/goals report](docs/PHASE_4_PLANNING_VERIFICATION.md) covers schema v5, task links and deletion behavior. The [debug inspection report](docs/PHASE_4_INSPECTION_VERIFICATION.md) covers the fifth database slice and its diagnostic limits. The [CI recovery report](docs/PHASE_4_CI_RECOVERY.md) records the API 26 keyboard and fixture cleanup fixes.
