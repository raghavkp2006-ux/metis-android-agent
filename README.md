# metis-android-agent
METIS is a local-first autonomous Android personal agent that uses structured memory, deterministic reasoning, lightweight on-device ML, and Android APIs to plan, remember, recommend, and act while keeping user data private.

Current implementation: the Android foundation, saved-data screens, completed Phase 4 database and Phase 5 memory baselines, plus the Phase 6 typed agent protocol. Room schema v6 stores all 22 planned tables with Keystore-encrypted personal fields, explicit migrations and transaction-protected updates. Today, Plan, You and Timeline observe real saved data. You supports manual memory add/edit/delete, explicit/derived filters, ranked local search and reviewed expiry cleanup. The shared composer processes requests locally through one orchestrator and reports that command understanding is unavailable; it performs no action or database write. Typed actions, proposals, context/capability snapshots, receipts and executor interfaces establish the next phases' boundaries. Reminder records still do not schedule Android notifications. Commands, planning, voice, behavioral inference and autonomous actions remain unavailable. The build uses Kotlin, Compose Material 3, API 26 minimum, API 36 compile/target and JDK 17.

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
| 7–19 | Language, actions, planning and production hardening | Pending |

Phase 4 validation: debug/release builds, 21 JVM tests, lint and Detekt passed. All 85 Android tests passed locally on API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37779790806), including migrations, saved-data screens, missing-key failures and atomic privacy cleanup. Release APKs exclude debug controls. Results and the earlier dependency-resolution failure are recorded in the [Phase 4 completion report](docs/PHASE_4_COMPLETION.md). Encryption, safe key failure, no-network permissions and backup exclusions remain intact. Physical-device and broader performance/security hardening remain later gates.

See the [roadmap](docs/ROADMAP.md), [product contract](docs/PRODUCT_CONTRACT.md), [Phase 1 verification](docs/PHASE_1_VERIFICATION.md), [Phase 2 verification](docs/PHASE_2_VERIFICATION.md), [Phase 3 verification](docs/PHASE_3_VERIFICATION.md), [first Phase 4 slice verification](docs/PHASE_4_VERIFICATION.md), [Phase 4 search and migration verification](docs/PHASE_4_SEARCH_VERIFICATION.md), [Phase 4 task dependencies verification](docs/PHASE_4_DEPENDENCIES_VERIFICATION.md), [Phase 4 preferences verification](docs/PHASE_4_PREFERENCES_VERIFICATION.md), and [contribution/setup guide](CONTRIBUTING.md).

The [agent training plan](docs/AGENT_TRAINING_PLAN.md) explains how individual specialists will be evaluated and when trained models are justified. No ML model is currently trained or bundled; deterministic parsing starts in Phase 7 and evaluated ML improvements belong to Phase 15. User preferences and memory remain local context, with no automatic training upload.

The [projects/goals report](docs/PHASE_4_PLANNING_VERIFICATION.md) covers schema v5, task links and deletion behavior. The [debug inspection report](docs/PHASE_4_INSPECTION_VERIFICATION.md) covers the fifth database slice and its diagnostic limits. The [CI recovery report](docs/PHASE_4_CI_RECOVERY.md) records the API 26 keyboard and fixture cleanup fixes.

Phase 5 passed debug/release builds, 26 JVM tests, lint, Detekt and all 95 Android tests on local API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37798814741). The [memory report](docs/PHASE_5_VERIFICATION.md) explains manual edits, provenance boundaries, ranking factors, working-memory expiry, reviewed deletion and measured latency limits. Memory changes stay local; commands and model training remain unavailable.

Phase 6's [protocol report](docs/PHASE_6_VERIFICATION.md) describes the typed models, proposal review and shared composer flow. Submitted text is cleared from the restorable draft; request/results stay in RAM and are discarded when the sheet closes. No parser, executor, conversation persistence, new permission or training upload is enabled. Phase 7 will add evaluated deterministic language rules, followed by durable policy-gated execution in Phase 8.

Phase 6 passed debug/release builds, 48 JVM tests, lint, Detekt and all 98 Android tests on local API 26/34 and in [GitHub CI on API 26/36](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37803899355). Its protocol baseline is complete; Phase 7 is next.
