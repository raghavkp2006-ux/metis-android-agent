# METIS frontend redesign verification

Date: 2026-10-16 (Asia/Calcutta).

## Implemented

- Chat-first Compose shell with one global text/voice composer and nine focused workspaces.
- Phone drawer and collapsible 280dp sidebar at 840dp; centered 800dp chat/composer and 1120dp workspace limits.
- Coordinated system-following light/dark blue themes, semantic colors, rounded cards and METIS orb.
- First-launch welcome with bundled native mountain/lake vector artwork and one private completion flag. No new raster images or mockups are required.
- Temporary in-memory conversation with stable request IDs, workspace/entity origin, explicit domain mapping, processing/errors, proposals, verified receipts and undo.
- Historical actions remain readable but inactive. Duplicate execution is blocked; New chat cancels processing, invalidates late callbacks and cannot interrupt a commit/undo.
- Sending from a workspace opens and scrolls to the new chat turn; navigation alone preserves existing chat position. A new-message affordance supports reading earlier content.
- Changing workspaces closes the keyboard so it does not keep covering the new page on a phone.
- Existing task, planning/calendar, memory, people, project/goal, reflection, history, privacy and settings flows remain reachable. Preferences and histories have their own intended workspaces.
- Scenic imagery is restricted to welcome. The installed application retains its existing local agent, encrypted storage, schemas, permissions and confirmation pipeline.
- Updated UI contract, privacy explanation, navigation tests and feature-screen regressions. Superseded bottom-tab shell and request sheet removed.

## Automated verification

Passed on the implemented frontend:

- 143 JVM tests, zero failures/errors/skips.
- Debug APK and optimized unsigned release APK builds.
- Debug and release lint with warnings treated as errors.
- Detekt and `git diff --check`.
- The full isolated instrumentation suite on API 26 and API 34: 200 executed tests, zero failures on each target; three declared host-only tests are skipped by the Android runner.
- A focused 44-test frontend pass and 8 later welcome/navigation smoke tests on each API target, including explicit light/dark welcome text contrast.

The frontend suite covers default chat, all destinations, draft/session retention across rotation/navigation, shortcut prefilling, reset confirmation, generation/cancellation protection, stale and duplicate action rejection, confirmed task actions/undo, planning/reminders, memory editing/search, privacy/consent, 200% text, voice disclosure and the 839/840dp boundary.

The full suite includes the older storage and agent regressions. Its first API 26 pass exposed test timing and direct-database-write visibility issues in the new shell; the tests now wait for editor state and explicitly refresh records after test-only database writes. The final clean runs passed on both API targets.

Commands:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt --max-workers=2 --console=plain
.\gradlew.bat :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest --max-workers=2 --console=plain
```

Instrumentation used only the isolated `dev.metis.agent.capabilitytest` package on disposable API 26/34 emulators, with explicit device serials. On the connected Android 16 phone, the isolated build was opened for a visual check, then removed; the installable debug `dev.metis.agent` build was installed and launched without clearing data. No instrumentation ran on the physical phone.

Manual visual checks covered small phone and landscape layouts in both themes, empty and populated chat, and tablet layouts with the expanded and collapsed sidebar. The app was also checked at the 600dp and 840dp boundaries, with a multiline composer and 200% font scaling through instrumentation. An actual process stop/restart confirmed submitted temporary chat content disappears while the clean greeting returns. On the connected phone, the dark welcome text and button were readable at the device's 360dp effective width. The welcome cards now explicitly use the theme's readable content color.

## Artifacts and practical limits

- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Build/device logs and local screen captures: ignored `.android-local/frontend-*` files.
- JVM XML reports: `app/build/test-results/testDebugUnitTest/`.
- Lint and Detekt reports: `app/build/reports/`.

Tests use synthetic data only. Physical speech-provider behavior and a manual spoken TalkBack pass remain device-dependent checks; accessible labels/state, contrast and large-text behavior are covered by the UI implementation and emulator checks. No new external calendar, contacts, persisted chat history, habits, cloud backend or natural-language capability is implied by this redesign.
