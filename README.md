# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: an Android foundation, Compose design system, navigation shell, and four Phase 4 database slices. Room stores tasks, schedule blocks, memory records and explicit planning preferences locally with Keystore-encrypted personal values. Today, Plan, and You observe saved records; You searches saved memory through a temporary in-memory FTS4 index. Search checks 20 recent eligible records by default and offers explicit expansion to 200, with visible limits. Today displays saved prerequisite counts. Schema v4 adds typed preferences for day start, focus-block length and week start, shown in You without inferred defaults. Explicit upgrades preserve existing encrypted data and task dependencies. An explicit debug-only control can seed synthetic fixtures into an empty database. The shared temporary request draft remains disabled for sending. Commands, reminders, preference editing, relevance ranking, planning, voice, and autonomous actions are not enabled yet. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target, and JDK 17.

## Phase progress

Each phase or phase slice is committed separately, with its README status and verification record updated together. Existing phase history is preserved.

| Phase | Implementation | Status |
| --- | --- | --- |
| 0 | Product, protocol, data, and UI contracts | Documented and checked |
| 1 | Android foundation, Gradle, and CI | Complete baseline |
| 2 | Compose design system | Complete baseline |
| 3 | Adaptive navigation and shared temporary request draft | Complete baseline |
| 4 | Encrypted persistence, saved-record screens, bounded memory search, links, task prerequisites, explicit preferences, and migrations | Four slices implemented; full gate remains open |
| 5–19 | Memory retrieval, agent pipeline, actions, planning, and production hardening | Pending |

Phase 4 validation: debug/release builds, 18 JVM tests, 49 Android tests on API 34, lint, and Detekt passed for the preference slice. GitHub CI also passed build checks and API 36, but API 26 exposed keyboard/focus timeouts and skipped fixture cleanup. The compatibility fix clears Compose text focus on navigation, provides a Search keyboard action, verifies keyboard Back using the visible viewport, and guarantees record cleanup even when focus assertions fail; fresh API 26/36 verification is pending. Details and measured search-performance limitations are recorded in the slice reports below. Remaining work includes the other tables/relationships, broader developer inspection, representative performance/security/device checks, and additional migrations. Memory search preserves disk encryption, uses bound queries, and excludes expired records before decryption; no plaintext search database, network access, or autonomous actions are introduced.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), [Phase 4 search and migration verification](docs/PHASE_4_SEARCH_VERIFICATION.md), [Phase 4 task dependencies verification](docs/PHASE_4_DEPENDENCIES_VERIFICATION.md), [Phase 4 preferences verification](docs/PHASE_4_PREFERENCES_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).

The [agent training plan](docs/AGENT_TRAINING_PLAN.md) explains how individual specialists will be evaluated and when trained models are justified. No ML model is currently trained or bundled; deterministic parsing starts in Phase 7 and evaluated ML improvements belong to Phase 15. User preferences and memory remain local context, with no automatic training upload.
