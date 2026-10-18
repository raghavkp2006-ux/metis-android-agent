1. Phase and branch/commit hashes

CI flake triage only. Base: `ed8b2c48d83b82c0ae11dffdb697139ded8f378f`; branch: `codex/ci-flake-triage`.
PR #7 is parked as draft at `ae7c84ae1115d740fed2196fefa76bc85b487ddb`. No Phase 3 commits were copied or pushed; no merge was performed. Original checkout edits were preserved in place; implementation uses a separate worktree.

2. Files changed (path, one-line purpose each)

- `.github/workflows/android.yml`: explicit cold boot, 4096M RAM / 512M heap, readiness setup; independent manual dispatches can complete without cancelling each other.
- `tools/capability_testing/emulator_ready.py`: emulator-only bounded boot/package-manager readiness polling; set and read back all three animation scales at zero.
- `tools/capability_testing/harness.py`: use ADB sync file transfer for APK readback, retain exact SHA-256 verification; restore readiness/animation setup after the proof reboot.
- `tools/capability_testing/test_harness.py`: exact-transfer, truncated/changed-transfer refusal, and physical-device refusal regressions.
- `app/src/androidTest/java/dev/metis/agent/IsolatedAppStateRule.kt` and MainActivity test suites: cancel outstanding synthetic reminders and clear synthetic records before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/FrontendTestNavigation.kt`: synchronize records reload, action completion, and viewport visibility centrally.
- `MemoryEditorScreenTest.kt`, `PlannerScreenTest.kt`, `ReminderScreenTest.kt`, `HabitCheckinScreenTest.kt`: use the shared synchronization mechanisms / await rendered habit controls.
- `HabitCheckinScreenTest.kt`, `HabitCheckinScreenshotTest.kt`: close the Activity, cancel fixture ViewModels, and close/delete the isolated fixture database and key.
- `docs/ci-flake-triage/failed-run-evidence.json`: original failure summaries, checked-out commits, and downloaded artifact SHA-256s.
- `docs/CI_FLAKE_TRIAGE_CHECKER_REPORT.md`: this report.

3. Commands run (exact) and their real result summaries, including test counts

Triage was reported in chat before any fixes. Retrieved all six failed-job logs plus each failed API's uploaded artifact. All six build jobs succeeded. The artifact evidence is recorded in `ci-flake-triage/failed-run-evidence.json`.

| Root-cause class | Run | API | Failing class#method | Error / stack top |
|---|---|---:|---|---|
| Async fixture/records load | [39112134248](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39112134248) | 26 | `MemoryEditorScreenTest#derivedFilterAndExpiryReviewNeverEditProvenanceOrDeleteWithoutConfirmation` | ComposeTimeoutException after 10000 ms; waitUntil -> test line 77 |
| DB write before UI readiness | [39121683094](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39121683094) | 26 | `HabitCheckinScreenTest#archiveRestoreAndDeleteFlow` | performScrollTo: restored habit delete node missing; fetchOneOrThrow -> test line 118 |
| DB write before UI readiness | [39126531482](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39126531482) | 36 | `HabitCheckinScreenTest#archiveRestoreAndDeleteFlow` | Same missing delete node; fetchOneOrThrow -> test line 118 |
| Async action / lazy conversation visibility | [39140352969](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39140352969) | 26 | `PlannerScreenTest#realComposerAcceptsConflictFreePlanThenSeparatelyReviewsAcceptsAndCancelsReminder` | ComposeTimeoutException after 10000 ms; waitUntil -> test line 85 |
| Async scroll/layout visibility | [39142093920](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39142093920) | 26 | `ReminderScreenTest#proposalDisclosesPrecisionAndOnlyAcceptedReminderCanBeCancelled` | Component not displayed; assertIsDisplayed -> test line 75 |
| ADB binary stream truncation | [39142096893](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39142096893) | 36 | `None: failed before instrumentation` | REFUSED: installed APK is not the verified APK; installed_target -> harness line 173 |

Five failures are instrumentation failures; the API 36 PR run failed before tests. Its readback artifact is a 120,320-byte truncated ZIP, rather than a complete APK. The hash refusal is retained; readback now uses `adb pull` instead of `exec-out cat`.

Planner and reminder classifications identify synchronization/visibility mechanisms supported by the failing location and source; the old artifacts do not contain a semantic tree or action-state dump proving every intermediate state. There is no evidence of OOM/ANR or boot timeout in these six failures. The PR workflow checked out merge commit `eef3ab53`, while the push workflow checked out head `ae7c84a`.

Local command:

```powershell
python -m unittest discover -s tools/capability_testing -p 'test_*.py' -v
```

Result: 11 tests passed, including 3 new regression tests.

```powershell
.\gradlew.bat --no-daemon :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest
```

Result: BUILD SUCCESSFUL in 1m 47s; 68 actionable tasks, all executed.

Full workflow proof: pending. No PR will be opened before five consecutive full green workflows.

4. New tests added and what each proves

- `InstalledApkReadbackTest#test_complete_transfer_still_requires_both_exact_hashes`: both installed APKs use serial-targeted ADB file transfer and retain identity checks.
- `InstalledApkReadbackTest#test_truncated_or_changed_readback_is_refused`: incomplete and changed bytes still fail exact verification.
- `InstalledApkReadbackTest#test_readiness_setup_refuses_physical_serial_before_adb`: physical serial `ed01e333` is refused without issuing an ADB command.

5. Anything NOT RUN or UNVERIFIED

Five full green workflows remain pending. Physical-device testing is intentionally paused. No local emulator instrumentation has been run for this branch yet. No Gradle connected*, install*, or uninstall* tasks were invoked.

6. Known risks, shortcuts, TODOs

No new ignores, removed assertions, loosened tolerances, test retries, workflow retries, or infrastructure retry wrappers. Boot readiness polling is bounded and occurs before instrumentation, including after reboot. Scroll synchronization waits for actual visibility with existing assertion semantics. CI already requested animations disabled; explicit readback verifies those settings after the harness reboot too.

7. Open questions for me

None. Phase 3 remains stopped until the user confirms resumption after this task.
