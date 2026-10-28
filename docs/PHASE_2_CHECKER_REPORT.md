1. Phase and branch/commit hashes

Phase 2: daily check-in and habit-management UI. Branch: `codex/phase-2-checkin-ui`.
Base commit: `297b282` (merge of PR #5, the Phase 1 schema/domain work on `main`).
Implementation commit: `61ff3d9` ("Add Phase 2 daily check-in and habit management UI").
This report is committed as a follow-up commit on the same branch; obtain its hash with:
`git log -1 --format=%H -- docs/PHASE_2_CHECKER_REPORT.md`.
Implementation tree verified here: `git diff --stat 297b282..61ff3d9` = 14 files changed, 1052 insertions(+), 0 deletions.

Only Phase 2 is implemented. No merge to `main` was performed. Phase 3 has not started.

The UI reads and writes exclusively through the Phase 1 `DisciplineRepository` contract. No schema, migration, DAO, permission, `habits`-table, chat/agent or PRIVACY-relevant surface was changed. Nothing new is exposed, so `PRIVACY.md` is unchanged.

2. Files changed (path, one-line purpose each)

- `gradle/libs.versions.toml`: Pin `kotlinx-coroutines-test` to 1.7.3 and declare its version alias, matching the resolved coroutines 1.7.3.
- `app/build.gradle.kts`: Add the `kotlinx-coroutines-test` unit-test dependency (one line).
- `app/src/main/java/dev/metis/agent/presentation/navigation/HabitCheckinViewModel.kt`: New ViewModel holding Today/backfill/check-in state, ticks, mood/note, habit create/archive/restore/delete, conflict and key-failure handling.
- `app/src/main/java/dev/metis/agent/presentation/navigation/HabitWorkspace.kt`: New Compose screen (Today section, day chips + manual backfill, expected-habit ticks, mood chips, note, management section, create form, delete confirmation).
- `app/src/main/java/dev/metis/agent/presentation/navigation/Workspace.kt`: Add `Workspace.HABITS("Habits", "Your daily check-in")` and its `screenContext()` mapping.
- `app/src/main/java/dev/metis/agent/presentation/navigation/ShellViewModel.kt`: Map `Workspace.HABITS -> ShellDestination.TODAY`.
- `app/src/main/java/dev/metis/agent/presentation/navigation/MetisAppShell.kt`: Add the `HABITS` navigation icon (import + mapping).
- `app/src/main/java/dev/metis/agent/presentation/navigation/WorkspacePage.kt`: Route `Workspace.HABITS -> HabitWorkspace()` behind the existing loading/failed gate.
- `app/src/test/java/dev/metis/agent/FakeDisciplineRepository.kt`: New in-memory fake mirroring revision/expected-day/archive rules for JVM tests.
- `app/src/test/java/dev/metis/agent/HabitCheckinViewModelTest.kt`: New 12-method JVM test suite for the ViewModel.
- `app/src/androidTest/java/dev/metis/agent/HabitCheckinScreenTest.kt`: New 3-method Compose instrumentation suite over the real encrypted repository.
- `app/src/androidTest/java/dev/metis/agent/HabitCheckinScreenshotTest.kt`: New screenshot fixture rendering the real screen and writing the two review PNGs.
- `docs/phase2/phase2-today-empty.png`: Emulator screenshot of the empty Today screen.
- `docs/phase2/phase2-today-populated.png`: Emulator screenshot of Today with two habits and one tick.
- `docs/PHASE_2_CHECKER_REPORT.md`: This report.

3. Commands run (exact) and their real result summaries, including test counts

All commands ran from `C:\antigravity_projects\metis-repo` in PowerShell with JDK 17 and Android SDK `C:\Android\sdk`. Raw logs are under the ignored `.local-evidence/phase2/` directory (local evidence, not committed).

Full local verification (mirrors the CI build job):

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest --continue *> .local-evidence\phase2\final-verification.log
```

Real output:

```text
BUILD SUCCESSFUL in 30s
173 actionable tasks: 9 executed, 164 up-to-date
```

Fresh unit-test run (to obtain real counts rather than an UP-TO-DATE task):

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --rerun-tasks *> .local-evidence\phase2\unit-fresh.log
```

Real output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 30s
26 actionable tasks: 26 executed
```

JUnit XML totals (`app/build/test-results/testDebugUnitTest`): 38 suites, 204 tests discovered/executed, 204 passed, 0 failed, 0 errors, 20 skipped (pre-existing archival skips, unchanged). `HabitCheckinViewModelTest` alone: 12 tests, 0 failed, 0 errors, 0 skipped.

Lint (both variants): `app/build/reports/lint-results-debug.txt` and `-release.txt` each report `0 errors, 0 warnings, 14 hints`.

Detekt: `:app:detekt` succeeded; `app/build/reports/detekt/detekt.xml` contains 0 file/finding entries and `detekt.txt` is empty (no findings above the default-config thresholds).

Capability-test packaging and APK identity verification (archive/identity, `verify` mode):

```powershell
python tools\capability_testing\harness.py verify --sdk "C:\Android\sdk" --app app\build\outputs\apk\capabilityTest\app-capabilityTest.apk --test app\build\outputs\apk\androidTest\capabilityTest\app-capabilityTest-androidTest.apk --output app\build\reports\phase2-harness-verify
```

Real output:

```json
{"status": "PASS", "mode": "verify", "application_id": "dev.metis.agent.capabilitytest"}
```

Instrumented tests on the API 26 emulator (`emulator-5556`, AVD `metis_v15_api26_fixture`, `ro.build.version.sdk=26`; both capabilityTest APKs installed with `adb install -r`):

```powershell
adb -s emulator-5556 shell am instrument -w -e class dev.metis.agent.HabitCheckinScreenTest dev.metis.agent.capabilitytest.test/dev.metis.agent.testing.IsolatedAndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -e class dev.metis.agent.HabitCheckinScreenshotTest dev.metis.agent.capabilitytest.test/dev.metis.agent.testing.IsolatedAndroidJUnitRunner
```

Real output: `OK (3 tests)` in 9.807s and `OK (1 test)` in 2.504s respectively.

Screenshots were re-pulled from the emulator into `docs/phase2/`; `git status --porcelain docs/phase2` was empty afterward, i.e. the regenerated PNGs are byte-identical to the committed ones (40857 bytes and 37147 bytes) and the rendering is deterministic.

Remote CI (GitHub Actions, run `39115434310`, event `push`, head `61ff3d9`): conclusion `success`. Jobs: `Compile, unit tests, lint, static analysis` = success; `Android smoke tests (API 26)` = success; `Android smoke tests (API 36)` = success. The `android-smoke` jobs run the guarded harness `proof` and `suite` sequences on the emulator, so APK-attested proof and the full isolated suite passed on both API 26 and API 36. Run URL: `https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39115434310`.

4. New tests added and what each proves

`HabitCheckinViewModelTest` (JVM, `FakeDisciplineRepository`, fixed clock `2026-03-10T09:00:00Z`/UTC, `UnconfinedTestDispatcher`):

- `todayAndWindowComeFromTheInjectedZone`: Today and the today..today-7 window are derived from the injected `zone`, not the device zone.
- `futureAndOlderDaysAreRejected`: A future day and a day older than 7 back-days are rejected with the backfill-rejection message.
- `manualDateParsingRejectsBadInput`: Malformed manual `yyyy-MM-dd` input is rejected.
- `tickCreatesThenTogglesTheCheckin`: A tick creates the day's check-in, and re-ticking the same habit clears it.
- `moodAndNotePersistAcrossReload`: Mood (1..5) and note round-trip across a reload.
- `expectedHabitsFollowCadence`: Expected habits follow each definition's cadence/start.
- `archiveExcludesFromExpectedAndRestoreCreatesNew`: Archiving removes a habit from expected days; restore creates a fresh identity.
- `archiveOfSameDayHabitIsRejectedWithMessage`: Same-day archive is rejected by design and surfaces the archive-rejection message.
- `staleRevisionShowsTheSavedValueInsteadOfOverwriting`: A stale revision raises the conflict state, reloads, and shows the current saved value rather than overwriting it.
- `keyFailureShowsFailedWithoutReplacementData`: A key-read failure yields the failed state with the existing `storage_error` semantics, never replacement data.
- `createRejectsStartOutsideTheWindow`: Creating a habit with a start outside `[today-7, today]` is rejected.
- `deleteRemovesTheHabit`: Delete removes the definition (cascading its ticks).

`HabitCheckinScreenTest` (Compose instrumentation, real `LocalDisciplineRepository` + `StorageTestFixture`):

- `createTickUntickAndBackfillRejection`: Creating a habit, ticking then unticking it, and the UI rejection of an out-of-window backfill day.
- `archiveRestoreAndDeleteFlow`: Archive (via the confirmation UI), restore-as-new and delete-with-confirm flows.
- `screenUsesTouchTargetsAndSurvivesTwoHundredPercentFont`: Interactive targets meet the 48dp minimum and the screen remains usable at 200% font scale.

`HabitCheckinScreenshotTest` is a capture fixture (1 instrumentation test) that renders the real screen over the encrypted repository and writes `phase2-today-empty.png` and `phase2-today-populated.png`.

No existing test assertions were deleted or rewritten; all Phase 0/1 tests and their 20 documented archival skips remain as before.

5. Anything NOT RUN or UNVERIFIED

- Local API 36 instrumentation: NOT RUN locally. No `android-36` system image is installed on this machine, so API 36 evidence comes only from the green CI `android-smoke` job.
- Physical device `ed01e333` (CPH2691, API 36): NOT RUN and NOT touched. No synthetic APK was installed on it and no data was mutated.
- Local emulator execution was limited to API 26 (`emulator-5556`). The local `verify` harness mode was run; the full `proof`/`suite` harness modes were exercised only by CI, not locally.
- PR creation: NOT performed programmatically. There is no `gh` CLI and no `GITHUB_TOKEN`/`GH_TOKEN` in this environment, so the pull request must be opened manually (see section 7).

6. Known risks, shortcuts, TODOs

- No Jetpack Navigation is used; the screen is wired through the existing hand-rolled `Workspace`/`ShellDestination` + `Crossfade` shell, matching the established pattern.
- `MetisAppShell`'s positional call-site signature is unchanged, so the existing large-font and responsive navigation tests are unaffected.
- `kotlinx-coroutines-test` is pinned to 1.7.3 to match the resolved coroutines version; a future coroutines upgrade should move both together.
- The hidden mood/note area of the populated screenshot is below the fold in the captured viewport; the full interaction is covered by the instrumentation tests rather than the single screenshot.
- CI remains the sole source of API 36 evidence.
- No Detekt baseline changes were needed; the new code passes the default-config thresholds.

7. Open questions for me

- Please open the PR manually, since this environment cannot (no `gh`, no token):
  `https://github.com/raghavkp2006-ux/metis-android-agent/compare/main...codex/phase-2-checkin-ui?expand=1`
- After CI re-runs on the final commit, confirm whether you want the branch merged to `main`; I will not merge or start Phase 3 without an explicit request.

8. Addendum (Phase 2.1): clock-derived "Today"

Checker feedback on the Phase 2 commit: the Today date and the today..today-7 window were computed once when the ViewModel was constructed and never recomputed, so an app left open across midnight (or a timezone change) kept showing a stale day. Phase 2.1 fixes this without changing scope.

Change (same branch, follow-up commit):

- `HabitCheckinViewModel.kt`: `today`, the window and `initialState` are now computed by functions (`today()`/`range()`/`windowDays()`) that read the injected `clock`/`zone` on every call instead of caching a value at construction. A public `refreshToday()` recomputes the day/window and re-validates `selectedDate`: if the previously selected day is still inside the new window it is kept (so an in-progress backfill is not disturbed); only if it has fallen out of the window is the selection reset to the new `today()` and the draft cleared. `init { reload(); startMidnightTimer() }` starts an in-`viewModelScope` timer that sleeps until the next local midnight (computed in the injected zone) and then calls `refreshToday()`.
- `HabitWorkspace.kt`: observes the lifecycle owner and calls `model.refreshToday()` on `Lifecycle.Event.ON_START` (resume), so returning to the screen after midnight/zone change re-derives the day; the observer is removed on dispose.

No schema, repository contract, DAO, permission, `habits`-table or chat/agent surface changed. Still reads/writes only through `DisciplineRepository`.

9. Phase 2.1 tests added and re-verification at the final commit

Added to `HabitCheckinViewModelTest` (JVM, advancing fake `MutableClock`):

- `todayWindowAndSelectionAdvanceAcrossMidnight`: advancing the clock past local midnight updates `today`, the window and the default selection to the new day.
- `todayFollowsZoneChangeWithoutRewritingRecordedDays`: changing the clock's zone moves `today`/window accordingly while already-recorded days are left untouched.
- `midnightTimerRefreshesTodayWithoutRewritingRecordedDays`: the internal midnight timer fires and refreshes `today` without rewriting recorded days (no advance of the fake dispatcher time required beyond the timer delay).

Final-commit local verification (all from `C:\antigravity_projects\metis-repo`):

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest --continue --rerun-tasks *> .local-evidence\phase2\phase21-final-verification.log
```

Real output: `BUILD SUCCESSFUL in 5m 32s` / `173 actionable tasks: 173 executed`. JUnit XML totals: 38 suites, **207 tests**, 0 failures, 0 errors, 20 skipped (same pre-existing archival skips). `HabitCheckinViewModelTest` alone: 15 tests, 0/0/0. Lint debug and release: `0 errors, 0 warnings, 14 hints` each. Detekt: 0 findings.

API 26 instrumentation re-run with the Phase 2.1 APKs (`emulator-5556`): `HabitCheckinScreenTest` `OK (3 tests)` in 10.898s, `HabitCheckinScreenshotTest` `OK (1 test)` in 2.763s. Re-pulled PNGs are byte-identical to the committed `docs/phase2/phase2-today-{empty,populated}.png` (SHA-256 unchanged), i.e. the fix does not alter rendering.

Remote CI is re-run on this final commit (build job + `android-smoke` API 26 and API 36); that CI result is the gate and is recorded in the commit/PR discussion rather than duplicated here.

10. Physical-device manual checklist (device `ed01e333`, CPH2691, API 36)

Isolation rules followed: only the separate-package synthetic build was installed — `app-capabilityTest.apk` with `application_id = dev.metis.agent.capabilitytest` (label "METIS Synthetic Test", launcher `dev.metis.agent.MainActivity`), confirmed before install. Every command used `adb -s ed01e333`. Only synthetic data was created. No `pm clear`, no uninstall, no adb backup and no `rm` was issued. CI on API 26/36 remains the gate; the results below are additional evidence.

| # | Checklist item | Result | Screenshot(s) in `docs/phase2/device/` |
| - | -------------- | ------ | -------------------------------------- |
| 1 | Launch + reach Habits via the drawer | PASS | `01-launch.png`, `02-after-nav-tap.png`, `03-drawer.png`, `04-habits.png`, `08-habits.png` |
| 2 | Create a habit ("Read 20 pages", Daily, start 2026-10-18) | PASS | `09-created.png` |
| 3 | Tick / untick / re-tick by touch | PASS | `10-ticked.png`, `11-unticked.png`, `12-re-ticked.png` |
| 4 | Archive by touch | PASS (shows "Archived since 2026-10-19") | `13-archived.png` |
| 5 | Restore-as-new by touch | PASS (archived + active rows visible) | `14-restored.png`, `15-two-cards.png`, `16-today-after-restore.png` |
| 6 | force-stop and cold reopen: data persistence | PASS (tick + both habit rows persist) | `17-persisted.png` |
| 7 | 200% font on Today (no clipping, still usable) | PASS (`font_scale` set to 2.0 then restored to 1.0) | `18-font200.png` |
| 8 | TalkBack on Today | PASS (TalkBack onboarding + green focus highlight over app nodes; TalkBack disabled afterward) | `19-talkback.png`, `20-talkback-habits.png` |
| 9 | Timezone-change midnight rollover (change timezone, not the system clock) | PASS | `21-tz-rollover.png`, `22-tz-oldday-intact.png` |

Item 9 method: `settings put global auto_time_zone 0` then `cmd alarm set-timezone Pacific/Kiritimati`, which moved the device's local date from Sun 18 Oct to Mon 19 Oct. Returning to the app showed the Today chip at 2026-10-19 and the Yesterday chip at 2026-10-18 while the previously selected 2026-10-18 remained selected (it is still inside the new window). Tapping the new Today showed Monday 19 Oct with the habit unchecked; tapping Yesterday showed Sunday 18 Oct with its tick intact — the recorded day was not rewritten. Note this exercises the resume path via a timezone change; the internal midnight-timer path is exercised by the JVM test in section 9. Afterwards the device timezone was restored to `Asia/Kolkata` with `auto_time_zone 1`, `font_scale` to `1.0`, and accessibility services to `null` / `accessibility_enabled 0`.

11. Updated NOT RUN / unverified, and one flag

- Device anomaly (flag for verification): at the start of the device session `pm list packages` showed the production `dev.metis.agent` package present (alongside `dev.metis.agent.test`); by the end of the session `dev.metis.agent` was absent (`pm list packages -u` and `pm path` both empty). No uninstall, `pm clear`, `rm` or adb backup was issued against any package during this session — the only package action was `adb install -r` of `dev.metis.agent.capabilitytest`. `AppIsolation.kt` only namespaces resources and performs no self-removal. The cause is undetermined and is called out here rather than silently ignored.
- Local API 36 instrumentation: still NOT RUN locally (no `android-36` image); API 36 evidence comes only from the CI `android-smoke` job, which is the gate.
- Physical-device results are additional evidence, not a substitute for CI; items not exercised on the device (e.g. key-unreadable/stale-revision conflict UI on hardware) remain covered by the JVM/instrumentation suites only.
- PR creation: still NOT performed programmatically (no `gh`, no token).

## 12. Post-acceptance device and task audit (2026-10-18)

Physical device `ed01e333` remains paused. The Phase 2 session record above confirms that `dev.metis.agent` was present before that session and absent afterward, while the only reported package action was installing `dev.metis.agent.capabilitytest`. It records no uninstall, `pm clear`, `rm`, or backup command. The cause remains **undetermined**; the snapshot below confirms the current state but does not explain the earlier disappearance.

For this audit, `ANDROID_SERIAL` was unset. The only device commands issued were the following read-only package queries, each explicitly scoped to `ed01e333`:

| Timing | Command | Result |
| --- | --- | --- |
| Before | `adb -s ed01e333 shell pm list packages` | Exit 0. Includes `dev.metis.agent.capabilitytest` and `dev.metis.agent.test`; does not include `dev.metis.agent`. |
| Before | `adb -s ed01e333 shell pm path dev.metis.agent` | Exit 1; empty output. |
| After | `adb -s ed01e333 shell pm list packages` | Exit 0. Same relevant package set as before. |
| After | `adb -s ed01e333 shell pm path dev.metis.agent` | Exit 1; empty output. |

The full before/after package lists and empty path-query outputs are preserved locally under `.local-evidence/phase2/device-audit-20261018/` (`before-pm-list-packages.txt`, `before-pm-path-dev.metis.agent.txt`, `after-pm-list-packages.txt`, and `after-pm-path-dev.metis.agent.txt`). The audit made no device changes.

The PowerShell PSReadLine history contains no METIS `connected*`, `install*`, or `uninstall*` Gradle invocation and no ADB command explicitly targeting `ed01e333`. It contains these serial-unqualified command entries from separate SignalChain/DSP project sessions: `gradlew.bat installDebug` (six entries) and `gradlew.bat clean installDebug` (one entry); `adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk` (five entries); `adb install -r app/build/outputs/apk/release/app-release.apk` (two entries); `adb install app/build/outputs/apk/release/app-release.apk` (one entry); and `adb uninstall com.signalchain.app` (one entry). Because those commands have no `-s` serial, history cannot establish whether they used this phone. The accepted Phase 2 device record is the available evidence for the METIS phone session; it records an install of `dev.metis.agent.capabilitytest` and interactive checklist actions but does not preserve a complete shell transcript or the full install command line.

With `ANDROID_SERIAL` unset, `gradlew.bat --no-daemon tasks --all` listed `app:connectedAndroidTest`, `app:connectedCapabilityTestAndroidTest`, `app:connectedCheck`, `app:installCapabilityTest`, `app:installCapabilityTestAndroidTest`, `app:installDebug`, `app:uninstallAll`, `app:uninstallCapabilityTest`, `app:uninstallCapabilityTestAndroidTest`, `app:uninstallDebug`, and `app:uninstallRelease`. This was a task listing only; none of those tasks ran. `app/build.gradle.kts` configures `doFirst` guards for these device tasks, but the task names remain visible in Gradle's list.

Standing rule: unset `ANDROID_SERIAL`; never invoke Gradle `connected*`, `install*`, or `uninstall*` tasks while a physical device is attached; scope every ADB call with `-s <serial>`; use only the synthetic capabilityTest target and instrumentation package IDs; and put the before/after `pm list packages` and `pm path dev.metis.agent` outputs in each phone-session `CHECKER_REPORT`. Device `ed01e333` is limited to those two read-only queries until the package anomaly is explained.
