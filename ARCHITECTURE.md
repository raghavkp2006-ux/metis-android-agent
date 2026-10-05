# Architecture

METIS is an Android-only, local-first personal assistant. The [Phase 0 contracts](docs/PRODUCT_CONTRACT.md) define its planned behavior. Phase 1 implements a single `:app` module with a launchable Compose foundation screen, build tooling, and verification. No agent engine, database, actions, or ML is implemented yet.

## Boundaries

`MainActivity` handles the Android lifecycle and edge-to-edge window setup. `presentation/FoundationScreen` renders a stateless, scrollable, system-light/dark screen. It contains no command parsing, policy, storage, or Android actions. The temporary Material defaults are replaced by the design system in Phase 2; main navigation arrives in Phase 3.

Future package/module dependencies follow presentation -> agent/domain -> repository interfaces -> data/platform adapters. The domain owns AgentRequest, typed actions, policy decisions, receipts, and events. Text and voice use one orchestrator. Android integrations implement replaceable interfaces. Hilt, Room, DataStore, and WorkManager are introduced when a phase actually needs them, not as unused dependencies.

Personal storage is app-private, with sensitive fields protected using Keystore-backed authenticated encryption when storage is implemented. The manifest disables backup and cleartext traffic and requests no permissions. Explicit legacy and modern backup rules exclude both credential/device-protected storage and device transfer. The debug Compose tooling dependency may add an internal signature permission; it grants no network or dangerous access.

## Build

Gradle Kotlin DSL and `gradle/libs.versions.toml` hold the pinned plugin/dependency versions. Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.20, and the matching Kotlin Compose compiler plugin build against API 36, with minimum API 26 and Java/Kotlin bytecode target 17. Release enables R8 optimization and is unsigned; no signing key is stored in the repository.

CI runs compilation, debug/release lint with warnings treated as errors, unit privacy regressions, Detekt static analysis, and instrumented APK compilation. Separate emulator jobs run launch/recreation and packaged privacy checks on APIs 26 and 36. CI results are not available until the workflow is pushed and executed.

Keep the [roadmap](docs/ROADMAP.md) phase gates explicit. A launchable scaffold is not a working reminder agent.
