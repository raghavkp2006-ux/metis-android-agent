# Contributing

Work one phase at a time using the [roadmap](docs/ROADMAP.md). Changes to product assumptions must update the affected contracts and acceptance criteria. Do not describe planned commands as implemented capabilities.

## Setup

Install JDK 17 (the recommended CI/local baseline) and Android Studio or the Android command-line SDK. Install Android SDK Platform 36, Build Tools 35.0.0, and Platform Tools. Accept the Android SDK licenses. Set ANDROID_HOME to your SDK or create an ignored local.properties containing `sdk.dir=/absolute/path/to/sdk`.

Clone the repository and use the included wrapper; no system Gradle is needed. SDK tools and dependencies require internet for first setup. The installed app does not.

```sh
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebugAndroidTest
```

For instrumented tests, boot an API 26+ emulator or connect an authorized Android device, then run:

```sh
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Unsigned optimized release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`. Unit, lint, Detekt, and Android reports are under `app/build/reports/`; machine-readable test outcomes also appear in `app/build/test-results/` and `app/build/outputs/androidTest-results/`.

## Review and checks

- Keep UI free of business logic and platform action dispatch.
- Request only the permission needed by an implemented capability. Preserve offline/local behavior.
- Add meaningful regression tests for new safety-critical behavior and run the relevant checks. Test both success and denied/ambiguous/failed outcomes for real actions.
- Test visible UI changes with dark mode, large fonts, insets, and accessibility. Full design-system checks start in Phase 2.
- State what ran, what passed, and what could not run. Never substitute a compiled test APK for a device test result.
- Run `git diff --check`; preserve synthetic-only test data and keep private artifacts out of the repository.

GitHub Actions verifies PRs and pushes to main/codex branches and can also be dispatched manually. Android smoke tests run independently on APIs 26 and 36. A missing emulator report is not a passing device test.
