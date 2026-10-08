# Phase 4 API 26 CI recovery

The preference slice's [GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37714325812) passed build/static checks and API 36. API 26 ran 49 tests with four failures: two keyboard waits timed out, then two saved-record assertions encountered 21 memory fixtures whose cleanup had been skipped.

The Android test harness now checks actual interactive keyboard windows with UiAutomation rather than inferring keyboard visibility from activity window insets. This covers modal dialog keyboards and API 26 without changing production navigation. The test rule enables interactive-window retrieval only for its own lifetime and restores the prior automation flags. An unavailable snapshot fails explicitly. The search fixtures are removed in a nested finally block even if keyboard dismissal verification throws.

No app permissions, dependencies, production behavior, encryption, or schema changes are introduced. Back dismissal and fixture assertions remain enabled.

Validation: Android test APK compilation, 18 JVM tests, debug/release lint, and Detekt passed locally. Fresh full API 26/36 runs are pending; this report does not claim they passed yet.
