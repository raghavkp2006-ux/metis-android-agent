# Architecture

METIS is an Android-only, local-first personal assistant. The [Phase 0 contracts](docs/PRODUCT_CONTRACT.md) define its planned behavior. Phase 1 supplies the Android scaffold, build tooling, and verified CI. Phase 2 establishes reusable presentation components and an explicit light/dark theme. No agent engine, database, actions, or ML is implemented yet.

## Boundaries

`MainActivity` handles the Android lifecycle and edge-to-edge window setup. `presentation/FoundationScreen` renders a stateless, scrollable, system-light/dark screen. It uses `presentation/designsystem` for theme/tokens, the composer, and empty state. Requests remain disabled until the agent pipeline exists; main navigation arrives in Phase 3.

The design system renders supplied display models and emits callbacks. It never authorizes actions, parses commands, grants permissions, or writes storage. `ProposalDisplay`, `ReceiptDisplay`, and `ComposerState` are presentation data, not the Phase 6 domain protocol. A proposal is unavailable by default; the future domain supplies current acceptance authority. Handoff/pending receipts are visibly unverified, and undo appears only when a callback is supplied.

The isolated `DesignSystemTestActivity` and synthetic component gallery are debug-only. The host is exported for emulator visual review, has no side effects or user data, and is absent from release. Production `MainActivity` never shows synthetic tasks or receipts.

Future package/module dependencies follow presentation -> agent/domain -> repository interfaces -> data/platform adapters. The domain owns AgentRequest, typed actions, policy decisions, receipts, and events. Text and voice use one orchestrator. Android integrations implement replaceable interfaces. Hilt, Room, DataStore, and WorkManager are introduced when a phase actually needs them, not as unused dependencies.

Personal storage is app-private, with sensitive fields protected using Keystore-backed authenticated encryption when storage is implemented. The manifest disables backup and cleartext traffic and requests no permissions. Explicit legacy and modern backup rules exclude both credential/device-protected storage and device transfer. The debug Compose tooling dependency may add an internal signature permission; it grants no network or dangerous access.

## Build

Gradle Kotlin DSL and `gradle/libs.versions.toml` hold the pinned plugin/dependency versions. Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.20, and the matching Kotlin Compose compiler plugin build against API 36, with minimum API 26 and Java/Kotlin bytecode target 17. Release enables R8 optimization and is unsigned; no signing key is stored in the repository.

CI runs compilation, debug/release lint with warnings treated as errors, JVM privacy/contrast regressions, Detekt static analysis, and instrumented APK compilation. Separate emulator jobs run launch/recreation, packaged privacy, and component interactions on APIs 26 and 36. The Phase 1 [recovery run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37465741379) passes all three jobs; Phase 2 has a separate verification record.

Keep the [roadmap](docs/ROADMAP.md) phase gates explicit. A launchable scaffold is not a working reminder agent.
