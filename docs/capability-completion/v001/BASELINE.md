# Capability completion v001 — failed baseline

Date: 2026-10-15, Asia/Calcutta.

**Status: FAILED. STOP before capability implementation.**

**Personal-data preservation: FAILED. The attempted isolated instrumentation run targeted the installed personal METIS package. Its private data changed during tests, and runner cleanup removed the package and its private data directory. The user confirmed that it contained personal data without an independent backup. No restorable backup was captured.**

## Source and scope

- Baseline source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4`.
- Initial branch: `main`; development branch created: `codex/capability-completion-v001`.
- The complete user instruction is retained in `request.txt`.
- No production source or capability implementation was changed. No schema migration, new language dataset, MiniLM training, personalization implementation or production ML enablement occurred.
- The pre-existing untracked Phase 15 artifacts were retained, not added to a commit or rewritten.
- SHA-256 checks of all 681 pre-existing Phase 15 evidence files (excluding Python caches) found zero changed or missing files. See `evidence-before.json` and `evidence-check.json`.

## Host baseline

The verification command used `--rerun-tasks` and `--continue`, so the selected checks executed rather than relying on cached test results:

```powershell
.\gradlew.bat --no-daemon --continue --rerun-tasks :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease :app:detekt
```

| Check | Result |
| --- | --- |
| Debug JVM tests | PASS: 141 tests, zero failures/errors/skips |
| Release JVM tests | PASS: 141 tests, zero failures/errors/skips |
| Debug APK | PASS: 11,626,716 bytes |
| Unsigned release APK | PASS: 1,949,442 bytes |
| Instrumentation APK build | PASS: 1,863,208 bytes; compilation is not device verification |
| Debug/release lint | PASS |
| Detekt | PASS |
| Overall host Gradle invocation | PASS: 136 tasks executed |
| Direct Python discovery across 26 directories | FAIL: 219 executions, 11 errors and one assertion failure across eight historical suites |
| Current V16 historical compatibility runner | PASS: 58 executions, zero failures |

Python direct discovery ran each directory containing `test_*.py` in its own interpreter using the existing `.venv-phase15` runtime and `-B`. It did not change the historical tests to make them pass. The failing directories were `candidate_v12`, `candidate_v14`, `candidate_v15`, `classifier_v11`, `classifier_v9`, `read_coverage_v10`, `read_coverage_v7` and `read_coverage_v8`.

Most direct-discovery errors concern historical current-source certificates that no longer match newer production sources; one assertion concerns historical projection/exposure expectations. The existing `read_candidate_v16/historical_tests.py` deliberately adapts obsolete certificates and passes. These results are reported separately: the direct failures were not hidden, and obsolete frozen evidence was not rewritten. This is not evidence of 12 new app behavior regressions.

## Physical baseline and data-loss incident

Device: CPH2691, Android 16 / API 36, connected over USB.

Before the run, `dev.metis.agent` was installed with version 0.1.0 / versionCode 1. Notification and microphone runtime permissions were denied. The original package had been installed on 2026-10-12 and updated on 2026-10-13.

Inspection correctly identified that existing screen/platform tests call `clearAllTables()` on the default app database. The agent attempted to isolate testing with a temporary Gradle init script, intended to build `dev.metis.agent.capabilitybaseline.v001` and a separate test package.

The isolation was faulty: the plugin callback ran before the project's Android DSL, which subsequently set the application ID back to `dev.metis.agent`. The test application ID remained separate, but its instrumentation target was the personal app. The build directory was also resolved relative to the init script, rather than the intended app directory. The agent failed to inspect the generated APK/manifest application IDs before initiating the connected test task.

The run selected 202 tests and executed 16 before being interrupted: 15 reported success; the in-progress sixteenth reported failure when instrumentation was stopped. That interrupted failure must not be classified as an established product regression. The overall physical suite did not complete.

By the time isolation failure was identified, a screen test using the default personal database had already run. Before/after hashes showed changes to the personal database, WAL and shared-memory files. After instrumentation was stopped, the connected test runner's cleanup removed the target installation. Read-only verification then found:

- `pm list packages -u dev.metis.agent` did not list the application package.
- `run-as dev.metis.agent` returned `unknown package`.
- `/data/user/0/dev.metis.agent` no longer existed.

The agent captured file hashes before the run, not a restorable database/Keystore backup. The user confirmed that the installation contained personal data without an independent backup. A targeted workspace search found no database backup. Recovery has not been achieved; APK reinstallation would not restore those records. No further phone changes were performed after confirming the loss.

The failed init script is retained as inert evidence in `failed-isolation-v001.init.gradle.txt`. **Do not execute it.** No corrected device harness has been validated or run.

## Current capability evidence

No before-to-after capability improvement is claimed. The existing source contains bounded task reads/actions, local memory reads/manual editing, saved-promise reads, local calendar reads/manual event editing, approximate reminders, deterministic recommendations, accepted day/evening plans, opt-in proactive updates, voice submission and user-triggered reflection. Existing storage/type definitions do not establish Agent routing or executable capability support.

The 15 completed physical test cases cover accepted-task storage/recovery/undo behavior, behavioral consent, one reflection screen, local calendar screen CRUD and local calendar storage/read behaviors. They are contaminated by the failed isolation and data-preservation outcome and do not establish a passing baseline or a final capability matrix. The remaining 186 selected tests were not completed. Real notification delivery, microphone transcription, physical reboot recovery and the full user-requested capability scenarios are not verified by this run.

## Artifact locations

Host-local raw artifacts are under the ignored directory `app/build/reports/capability-completion/v001/`:

- `gradle-baseline.log`, `jvm-summary.json`;
- `python-baseline.log`, `python-suites.json`, `python-historical-runner.log`;
- `device-baseline.log`;
- pre-run package metadata and pre/post personal-file hashes (no private file contents exported);
- the failed device run's XML/HTML/logs under `build/capability-completion/v001/isolated/`.

Personal-device metadata and logcat output remain in ignored local output; they are not copied into this documentation namespace.

## Gates and next boundary

| Gate | Result |
| --- | --- |
| Host Android build/unit/static baseline | PASS |
| Every discovered Python test passes directly | FAIL |
| Current historical compatibility tests | PASS |
| Full physical instrumentation baseline | INCOMPLETE / INTERRUPTED |
| Personal installation and personal data preserved | FAIL |
| V16/V17 source/evidence preserved | PASS for the recorded Phase 15 evidence files |
| Final capability matrix | NOT CREATED |
| `READY_FOR_LANGUAGE_SCHEMA_FREEZE` | FALSE |

Per the user's explicit stop rule, return this failed baseline before major implementation. Capability completion phases 2 onward have not started. The two intentionally unsupported boundaries remain automatic global training from personal data and unrestricted arbitrary external-app/device control.

Any future device test harness must finalize a distinct application ID after project DSL configuration, inspect both APK IDs and the instrumentation target before installation, refuse the personal package, and account for runner uninstall behavior. A passing build or a different test-package ID alone is not proof of isolation. Device testing must remain stopped until the data-loss incident and recovery options have been addressed with the user.
