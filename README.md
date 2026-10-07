# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: an Android foundation, Compose design system, navigation shell, and first Phase 4 persistence slice. Room stores tasks, schedule blocks, and memory records locally with Keystore-encrypted personal text. Today, Plan, and You observe saved records; an explicit debug-only control can seed synthetic fixtures into an empty database. The shared temporary request draft remains disabled for sending. Commands, reminders, memory retrieval/ranking, planning, voice, and autonomous actions are not enabled yet. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target, and JDK 17.

## Phase progress

Each phase or phase slice is committed separately, with its README status and verification record updated together. Existing phase history is preserved.

| Phase | Implementation | Status |
| --- | --- | --- |
| 0 | Product, protocol, data, and UI contracts | Documented and checked |
| 1 | Android foundation, Gradle, and CI | Complete baseline |
| 2 | Compose design system | Complete baseline |
| 3 | Adaptive navigation and shared temporary request draft | Complete baseline |
| 4 | Encrypted task, schedule, and memory persistence; saved-record screens | First slice implemented and locally verified; full gate remains open |
| 5–19 | Memory retrieval, agent pipeline, actions, planning, and production hardening | Pending |

Phase 4 validation: debug/release builds, 10 JVM tests, 22 Android tests on API 34, lint, and Detekt passed. Remaining Phase 4 work includes the other tables, in-memory FTS, and future schema upgrade migrations. API 26/36 CI has not been run for this slice.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).
