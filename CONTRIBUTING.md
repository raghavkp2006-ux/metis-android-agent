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

## Debug persistence inspection

Physical schema v3 adds saved task prerequisites. Today shows counts and You's debug-only inspection shows total links. Use the internal dependency repository for graph writes; direct DAO/SQL writes bypass cycle validation. Existing debug fixtures are unchanged, and no task/dependency editor or planner is enabled. Debug/release KSP exports to the same schema directory are ordered to avoid concurrent JSON writers.

On a disposable debug install, open You and choose **Load synthetic records (debug)**. This explicitly seeds three synthetic records in one transaction only if the database is empty. Inspect the saved task in Today, schedule block in Plan, and explicit memory in You; relaunch to check persistence. You also shows record counts. Repeating the seed or using a nonempty database fails without replacing data. Neither seeding nor inspection is included in release builds. No content is printed into logs or exported.

Room's physical v1 schema is versioned under `app/schemas/`. Do not edit exported JSON manually or add destructive fallback. Any physical schema change needs a version increment, explicit migrations (including integrity triggers), and data-preservation tests. See [the first slice verification record](docs/PHASE_4_VERIFICATION.md) for implemented scope and remaining phase requirements.

Physical v2 and the explicit 1 -> 2 migration are also versioned. You supports bounded local memory word search; it does not submit requests. Use [the search/migration verification record](docs/PHASE_4_SEARCH_VERIFICATION.md) when checking privacy, candidate limits, typed entity links, or upgrade behavior.

## Review and checks

- Keep UI free of business logic and platform action dispatch.
- Request only the permission needed by an implemented capability. Preserve offline/local behavior.
- Add meaningful regression tests for new safety-critical behavior and run the relevant checks. Test both success and denied/ambiguous/failed outcomes for real actions.
- Test visible UI changes with dark mode, large fonts, insets, and accessibility. Full design-system checks start in Phase 2.
- State what ran, what passed, and what could not run. Never substitute a compiled test APK for a device test result.
- Run `git diff --check`; preserve synthetic-only test data and keep private artifacts out of the repository.

GitHub Actions verifies PRs and pushes to main/codex branches and can also be dispatched manually. Android smoke tests run independently on APIs 26 and 36. A missing emulator report is not a passing device test.
