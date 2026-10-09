# Phase 12 — proactive system

Phase 12 is complete as a bounded local baseline. It delivers opt-in checks over saved records with priority selection and notification suppression.

## Behavior

- The Proactive updates switch on You defaults off. Enabling it schedules an immediate review and a six-hour periodic WorkManager review. Turning it off cancels both jobs and dismisses the current proactive notice.
- Open tasks due within 24 hours or already past due are candidates. Deadlines are low priority at 12–24 hours, medium at 2–12 hours, and urgent under two hours. Missed tasks are important. Completed and cancelled tasks are excluded.
- An open promise with a due time within the next 24 hours is a candidate. It is urgent when due within two hours and important otherwise. Fulfilled and cancelled promises are excluded.
- Three successful postponements of the same still-open task within 30 days create an important candidate. Postponement history comes from verified local task actions; other edits do not count.
- Overlapping accepted saved schedule blocks that intersect the current or next 24-hour period create an important candidate. Proposed and cancelled blocks are excluded.
- Candidate selection is deterministic by priority, trigger kind and opaque record identity. The selected notification uses a matching low, standard or urgent Android channel. Android channel settings can lower or disable a channel.
- Notifications use generic text, private lock-screen visibility and a generic public version. The limiter allows at most two notifications per rolling 24 hours, keeps at least four hours between notices, and suppresses the same item for 24 hours.
- Consent and limiter state use app-private preferences. The ledger stores hashed identities and timestamps only. Notification content stays out of preferences; tasks, schedules and promises are read through encrypted local storage.
- No database migration, new permission, network access, behavioral inference or automatic action was added. Android notification permission remains required for delivery. WorkManager timing is approximate and does not guarantee urgent delivery.

## Verification — 2026-10-09

- Debug and release APK assembly passed.
- Debug and release lint, Detekt and all 94 JVM tests passed.
- The full connected Android regression passed on API 26 and API 34. Gradle started 175 tests per device; three pre-existing host-dependent reminder checks were skipped on each device, with no failures.
- The focused ProactiveScreenTest passed both tests on API 26 and API 34. It confirms the switch starts disabled, enabling schedules periodic work, disabling cancels it, notification text excludes task titles, and repeated checks of the same item are suppressed.
- The full regression also exercised the existing task, planner, reminder, privacy and navigation flows. No physical-device or production rollout check is claimed.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebug :app:assembleRelease
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.metis.agent.ProactiveScreenTest'
```
