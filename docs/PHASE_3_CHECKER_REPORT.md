# Phase 3 CHECKER REPORT

## 1. Phase and branch/commit hashes

Phase 3 only, branch `codex/phase-3-heatmap-streaks`, [PR #7](https://github.com/raghavkp2006-ux/metis-android-agent/pull/7). Original implementation: `604b38aa3f288eb51a713d60fcca62239b41f50f`.

On 19 October 2026 the user authorized merging accepted PR #8. Main merge: `40e2aac044565c286affdbf76acd460dbf02c4f1`. Merge `57dfbf2` brings main into Phase 3 without rebase or force-push. Conflict resolution preserved main's accepted flake tests and superseded older Phase 3 fixture fixes. A subsequent new API36 main-merge failure required only the narrow DecisionScreenTest adjustment documented below; the accepted workflow remains unchanged.

This report and screenshots are committed with the completion changes. Obtain its commit with `git log -1 --format=%H -- docs/PHASE_3_CHECKER_REPORT.md`. Exact final-head push/PR/full-rerun SHA, URLs, conclusions and pass counts are published in PR #7's validation section and the delivery report, without creating another unverified commit. Earlier-head greens do not satisfy this gate.

## 2. Files changed (path, one-line purpose each)

Final delta against main `40e2aac`, excluding already-accepted flake changes:

- `app/src/main/java/dev/metis/agent/domain/discipline/HabitContribution.kt`: pure 371-day range, score, five intensities, totals and streak/rest rules.
- `app/src/main/java/dev/metis/agent/presentation/navigation/HabitContributionViewModel.kt`: bounded reads, selection, safe read failure/retry, resume and midnight refresh.
- `app/src/main/java/dev/metis/agent/presentation/navigation/HabitContributionWorkspace.kt`: accessible 53x7 grid, patterned marks, font-scaled legend, streaks, totals and day details.
- `app/src/main/java/dev/metis/agent/presentation/navigation/HabitWorkspace.kt`: progress integration and refresh on saved habit/check-in/tick changes.
- `app/src/main/res/values/strings.xml`: progress text, descriptions and plurals.
- `app/src/test/java/dev/metis/agent/HabitContributionCalculatorTest.kt`: 13 fixed-clock calendar/score/streak/backfill cases.
- `app/src/test/java/dev/metis/agent/HabitContributionViewModelTest.kt`: four refresh, failure/recovery, week/zone and midnight cases; clear ViewModels after each test.
- `app/src/androidTest/java/dev/metis/agent/HabitContributionScreenTest.kt`: two Compose cases for layout/accessibility/selection/theme/font and same-screen tick refresh; themed screenshot Surface.
- `app/src/androidTest/java/dev/metis/agent/DecisionScreenTest.kt`: on the newly observed API36 failure, use existing exact-result synchronization and lazy-list scrolling before the unchanged recommendation assertions.
- `tools/capability_testing/harness.py`: harvest only four allowlisted PNGs from the synthetic package before cleanup.
- `docs/phase3/heatmap-empty.png`: empty API26 emulator capture.
- `docs/phase3/heatmap-populated.png`: populated API26 emulator capture.
- `docs/phase3/heatmap-dark.png`: dark API26 emulator capture.
- `docs/phase3/heatmap-font-200.png`: 200% font API26 emulator capture.
- `docs/phase3/emulator-api26-*.txt`: retained historical synthetic-emulator package snapshots.
- `docs/phase3/resume-verification.json`: current local counts, screenshot hashes and package-isolation evidence.
- `docs/PHASE_3_CHECKER_REPORT.md`: this seven-section record.

No schema, migration, DAO, repository contract, permission, inferred `habits` table, consent or chat/agent change. Reads use `DisciplineRepository.habits(range)` and `checkins(range)` with the same 371-date range (within the 400-date bound).

## 3. Commands run (exact) and their real result summaries, including test counts executed/passed/failed/skipped

PowerShell directory: `C:\antigravity_projects\metis-repo-phase-3-heatmap-streaks`, JDK17, SDK `C:\Android\sdk`. Logs: ignored `.local-evidence/resume/`. `ANDROID_SERIAL` unset.

```powershell
git fetch origin
git merge --no-edit origin/main
git restore --source=origin/main --staged --worktree app/src/androidTest/java/dev/metis/agent/HabitCheckinScreenTest.kt app/src/androidTest/java/dev/metis/agent/MemoryEditorScreenTest.kt app/src/androidTest/java/dev/metis/agent/PlannerScreenTest.kt
git diff --check
git commit -m "Merge main with accepted CI stabilization into Phase 3"
Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest :app:verifyCapabilityTestApks *> .local-evidence/resume/verify.log
.\gradlew.bat --no-daemon :app:testDebugUnitTest *> .local-evidence/resume/unit-final.log
python -m unittest discover -s tools/legacy_ml -p 'test_*.py' -v *> .local-evidence/resume/python-legacy.log
python -m unittest discover -s tools/capability_testing -p 'test_*.py' -v *> .local-evidence/resume/python-harness.log
python tools/legacy_ml/check.py --apks app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk *> .local-evidence/resume/archive.log
```

Full verification: `BUILD SUCCESSFUL in 3m 14s`, 174 tasks (42 executed, 132 up-to-date). Then the explicit backfill/zone JVM case was added and all JVM tests reran: `BUILD SUCCESSFUL in 31s`, 26 tasks (3 executed, 23 up-to-date). Final JVM XML: **224 discovered, 204 passed, 0 failures/errors, 20 existing skips**. Calculator: 13/13; contribution ViewModel: 4/4. Lint debug/release each: 0 errors, 0 warnings, 13 hints. Detekt: no findings. APK identity, archive and packaged-asset checks passed. Python legacy: 8/8; harness: 11/11.

Dedicated, previously cleaned API26 AVD cold-booted at `emulator-5558`, 4096MB RAM:

```powershell
Start-Process -FilePath C:\Android\sdk\emulator\emulator.exe -ArgumentList '-avd','metis_phase3_api26_clean_20261018','-port','5558','-no-window','-no-snapshot','-no-boot-anim','-memory','4096' -WindowStyle Hidden -RedirectStandardOutput .local-evidence/resume/emulator.log -RedirectStandardError .local-evidence/resume/emulator-error.log
python tools/capability_testing/emulator_ready.py --sdk C:\Android\sdk --serial emulator-5558
python tools/capability_testing/harness.py proof --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --serial emulator-5558 --output .local-evidence/resume/proof *> .local-evidence/resume/proof.log
C:\Android\sdk\platform-tools\adb.exe -s emulator-5558 shell getprop ro.kernel.qemu
python tools/capability_testing/harness.py proof --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --serial emulator-5558 --output .local-evidence/resume/proof-second *> .local-evidence/resume/proof-second.log
python tools/capability_testing/harness.py suite --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --serial emulator-5558 --proof .local-evidence/resume/proof-second/result.json --output .local-evidence/resume/suite *> .local-evidence/resume/suite.log
```

Readiness verified boot/package manager and three animation scales at zero. First proof: seed/restart each passed (`OK (1 test)`); pre-reboot ADB `getprop ro.kernel.qemu` returned exit255 with stdout `1`. Stack: `harness.py:289 -> _command:127 -> read:118 -> _command:129 -> run:49`, `RuntimeError: Command failed (255): C:\Android\sdk\platform-tools\adb.exe`. Reported before investigation/retry. Cleanup passed; production unchanged. A direct repeat returned `1`, exit0. No harness change or retry wrapper. The separately logged second proof passed seed, process restart and emulator reboot (three single-test invocations), cleanup and production isolation.

The first full suite had 224 passes, one failure and three existing skips. The new `HabitContributionScreenTest.savedTickRefreshesProgressWithoutLeavingTheWorkspace` timed out at line169 (`ComposeTimeoutException`, `AndroidComposeUiTestImpl.waitUntil:504`, `AndroidComposeTestRule.waitUntil:339`). It expected plural “1 of 1 habits” and “1 of 1 expected habits”; the resource correctly uses singular “habit”. This was reported before the two assertions were corrected to the actual singular resource, retaining the exact counts and score. Accepted flake code was not changed. The complete failure excerpt is retained in `resume-verification.json`.

The same full Gradle verification command was repeated after that correction, logging to `.local-evidence/resume/verify-final.log`: `BUILD SUCCESSFUL in 1m 1s`, 174 tasks (13 executed, 161 up-to-date). The proof and suite commands above were then repeated with output paths `.local-evidence/resume/proof-final` and `.local-evidence/resume/suite-final`, respectively; the suite used `--proof .local-evidence/resume/proof-final/result.json`. Final proof passed all three single-test invocations, reboot, cleanup and unchanged production checks. Local final full-suite counts and screenshot hashes: `docs/phase3/resume-verification.json`. Final-head CI requires build/API26/API36 green on push and pull_request, followed by a second complete workflow attempt. Exact links/counts are published on PR #7 and in the delivery report, never inferred from earlier runs.

Final local full suite: `OK (225 tests)`, 201.651 seconds, **225 passed, 0 failed, 3 existing skips**; isolation, reboot verification, cleanup and unchanged production checks passed. All four regenerated screenshots were visually inspected. Following the narrow DecisionScreenTest helper adjustment described below, the full Gradle command was run again with log `.local-evidence/resume/verify-decision.log`: `BUILD SUCCESSFUL in 25s`, 174 tasks (8 executed, 166 up-to-date). No product source changed after the local screenshot suite. Final CI runs the helper adjustment at the exact final commit.

## 4. New tests added and what each proves

New main-merge failure (reported before investigation/change): [run39166066141](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39166066141), API36, `DecisionScreenTest.composerRecommendsFittingTaskAndPersistsInspectableExplanationWithoutAcceptanceOrMutation`, `ComposeTimeoutException: Condition still not satisfied after 10000 ms`, stack `AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:504) -> AndroidComposeTestRule.waitUntil(AndroidComposeTestRule.android.kt:339) -> DecisionScreenTest.kt:49`. Artifact: 223 executed, 222 passed, one failure, three existing skips. Its full failure excerpt and artifact SHA256 are retained in `resume-verification.json`. The old test waited for an unscrolled lazy-chat text node; the accepted shared helper instead waits for the exact action result and scrolls the lazy item into view. This addresses the observed synchronization hazard without changing product code or weakening expected results. The local screenshot suite used the APK built immediately before this narrow helper change; final-head CI runs the updated test.

`HabitContributionCalculatorTest` (13 JVM):

- `localTodayAndHeatmapRangeComeFromFixedClockAndZone`: injected today, Monday/Sunday bounds, 371 dates.
- `dayScoreUsesOnlyExpectedCompletedHabitsAndAppliesBands`: expected denominator and five intensity boundaries.
- `zeroCompletionIsZeroScoreButNoExpectedHabitsIsNoData`: zero versus absent expectations.
- `noDataAndFutureDatesNeverCountAsStreakFailures`: future/no-data neutrality.
- `oneRestPerRollingSevenCalendarDaysKeepsRunButDoesNotAddToCount`: rests seven calendar days apart.
- `secondRestSixDaysLaterBreaksRunAndStartsCurrentStreakAfterward`: rolling rest limit/new run.
- `currentAndLongestStreakAreComputedSeparately`: current/best/qualifying totals.
- `streakCountsScheduledDaysAndNoDataDatesDoNotConsumeRest`: cadence gaps neutral.
- `cadenceAndExclusiveArchiveBoundarySetExpectedDenominator`: start/cadence/archive expectations.
- `daylightSavingLeapDayAndYearEdgesUseLocalDateSteps`: DST 23/25-hour dates, leap day, month/year edges.
- `backfillUpdatesTheRecordedDayAndZoneChangeDoesNotMoveIt`: past score update and original date/zone preservation.
- `disciplineRangeAcceptsExactlyFourHundredDatesAndRejectsTheNext`: range bound.
- `viewIsExactlyFiftyThreeMondayFirstWeeksAndFutureDatesAreNeutral`: 53x7/future cells.

`HabitContributionViewModelTest` (four JVM):

- `refreshReadsSavedTicksAndPreservesSelectedDate`: saved tick refresh preserving valid selection.
- `unreadableKeyHidesOldProgressAndRetryRecovers`: failed reads never fabricate empty progress; retry works.
- `resumeAcrossWeekAndZoneChangeRecomputesTodayAndBoundedRange`: today/range refresh and obsolete selection clamp.
- `midnightTimerRefreshesWithoutResume`: clock/dispatcher advance today while open.

`HabitContributionScreenTest` (two Compose):

- `heatmapHasFiftyThreeWeeksSelectsCellsAndRemainsReadableAtLargeFont`: 371 cells/53 columns, date/count descriptions, 48dp targets, selected completed habits, dark/200% font and four emulator PNGs.
- `savedTickRefreshesProgressWithoutLeavingTheWorkspace`: real encrypted repository + real check-in/progress screens; tick updates saved score to 100% without navigation/resume.

No tests deleted or newly skipped. Existing Phase3 screenshot fixture now paints its theme background and includes live-refresh coverage; no assertion removed. Only the newly failing DecisionScreenTest wait was changed: it now uses the accepted `awaitRequestText` helper, which checks the exact action result, scrolls its lazy list item into view and asserts it is displayed. Its persistence, no-acceptance and no-mutation assertions are retained. Other accepted flake changes and workflow remain unchanged.

## 5. Anything NOT RUN or UNVERIFIED

- Physical `ed01e333`: **NOT RUN**, paused; no command targeted it.
- Local API36: **NOT RUN**, no installed image; evidence comes from CI emulator suites.
- Manual TalkBack speech/navigation: **NOT RUN**; automated semantics, traversal indices, patterns, bounds and selection provide stated accessibility evidence.
- Final CI is an external gate. Use exact final-head results on PR #7/delivery report, never historical Phase3/flake runs.

## 6. Known risks, shortcuts, TODOs

Streaks/totals are explicitly bounded to the 53-week view, not lifetime. Only qualifying days increment; no-expected days are neutral. A below-half expected day consumes rest without incrementing. A second fewer than seven calendar days later breaks the run.

Intensities: 0/no data, >0–<25%, 25–<50%, 50–<75%, >=75%. Dot patterns/text supplement colour. Grid/enlarged legend scroll horizontally; summary/details vertically. Screenshots are actual API26 Compose captures using synthetic data; 200% uses a Compose density override. Themed Surface makes dark contrast reviewable.

Initial local proof infrastructure failure is retained in section3, separately from the passing second proof. No failure suppressed. No Gradle connected*, install* or uninstall* task ran. The guarded harness targets only an explicitly selected emulator and allowlisted synthetic packages, with identity, cleanup and production-state checks.

![Empty heatmap](phase3/heatmap-empty.png)

![Populated heatmap](phase3/heatmap-populated.png)

![Dark-mode heatmap](phase3/heatmap-dark.png)

![200% font heatmap](phase3/heatmap-font-200.png)

## 7. Open questions for me

None for implementation. Stop after exact final-head push/PR/second-full-attempt gate is green. PR #7 remains for user review. Phase4 has not started.
