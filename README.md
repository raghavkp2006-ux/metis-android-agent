# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: an Android foundation, Compose design system, navigation shell, and three Phase 4 database slices. Room stores tasks, schedule blocks, and memory records locally with Keystore-encrypted personal text. Today, Plan, and You observe saved records; You searches saved memory through a temporary in-memory FTS4 index. Search checks 20 recent eligible records by default and offers explicit expansion to 200, with visible limits. Schema v3 adds saved task prerequisites with transactional cycle checks and cascade deletion; Today displays prerequisite counts. Explicit upgrades from v1/v2 preserve existing encrypted data. An explicit debug-only control can seed synthetic fixtures into an empty database. The shared temporary request draft remains disabled for sending. Commands, reminders, relevance ranking, planning, voice, and autonomous actions are not enabled yet. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target, and JDK 17.

## Phase progress

Each phase or phase slice is committed separately, with its README status and verification record updated together. Existing phase history is preserved.

| Phase | Implementation | Status |
| --- | --- | --- |
| 0 | Product, protocol, data, and UI contracts | Documented and checked |
| 1 | Android foundation, Gradle, and CI | Complete baseline |
| 2 | Compose design system | Complete baseline |
| 3 | Adaptive navigation and shared temporary request draft | Complete baseline |
| 4 | Encrypted persistence, saved-record screens, bounded memory search, links, task prerequisites, and explicit migrations | Three slices implemented; full gate remains open |
| 5–19 | Memory retrieval, agent pipeline, actions, planning, and production hardening | Pending |

Phase 4 validation: debug/release builds, 15 JVM tests, 41 Android tests on API 34, lint, and Detekt passed for the current implementation. Details and measured search-performance limitations are recorded in the slice reports below. Remaining work includes the other tables/relationships, broader developer inspection, representative performance/security/device checks, and additional migrations. API 26/36 CI has not been run for these slices. Memory search preserves disk encryption, uses bound queries, and excludes expired records before decryption; no plaintext search database, network access, or autonomous actions are introduced.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), [Phase 4 search and migration verification](docs/PHASE_4_SEARCH_VERIFICATION.md), [Phase 4 task dependencies verification](docs/PHASE_4_DEPENDENCIES_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).
