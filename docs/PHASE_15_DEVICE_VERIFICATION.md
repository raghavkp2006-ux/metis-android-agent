# Phase 15 v10 device verification — 2026-10-12

Device: CPH2691, Android 16 / API 36. The deterministic v10 production code from `cfe5601` was exercised on a physical phone. ML remains disabled and unbundled. This is Android instrumentation with synthetic saved records, not fresh human query evaluation or representative ML performance evidence.

## Results

| Run | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Initial full instrumentation suite | 173 | 9 | 3 |
| Corrected read/storage suites | 57 | 0 | 0 |
| Isolated planning/preferences UI rerun | 2 | 0 | 0 |

The corrected read/storage run includes all **7 `LanguageReadTest` cases**, including both new v10 repository fixtures. It also exercises memory search, database constraints/migrations, foundation round trips, reference cleanup, lifecycle/migration handling and the memory engine. Matching records and decoys verify literal attribution, requested fields, titles, locations and remembered negation. Tests verify OPEN-task scope, exact unique Unicode saved-person matching, honest no-match responses, explicit/nonexpired memory access and unchanged stored records.

The initial v10 task fixture omitted `completedAt` on a COMPLETED task. The domain constructor rejected that invalid test record before the read assertions could run. The fixture now supplies the required timestamp. Production parsing, repository code, record constraints and schema are unchanged. The v10 manifest refresh changes only that instrumentation-source binding; v6–v9 artifacts and frozen approvals/results remain unchanged.

The other initial failures were:

- Five notification tests failed because Android denied automated permission grants: `Neither user 2000 nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS`. These affect `PlannerScreenTest`, `ProactiveScreenTest`, two `ReminderPlatformTest` cases and `ReminderScreenTest`.
- `PlanningScreenTest` and `PreferenceScreenTest` reported missing Compose hierarchies in the full run. Both passed when rerun separately. The full-run cause remains unconfirmed; those passes do not retroactively make the first run successful.
- One `VoiceInputScreenTest` expected microphone permission to be denied, but this phone already had it granted.

Three assumption-based cases were skipped: the disabled-notifications scenario and the two opt-in restart stages. The full suite is **not certified passing**. Notification permission setup, the microphone test's initial state, full-suite UI stability and restart coverage remain unresolved.

## Validation and evidence

After the fixture correction, debug lint, Detekt, all seven v10 Python integrity/regression tests and the v10 native/Python report replay passed. Its read coverage and safety results remain 23/23 approved reads, 299/299 v9 reads, retained v7/v8 positives and zero forbidden reads. All 65 retained historical artifact bindings still verify.

Raw first-run and rerun logs, APK hashes and the machine-readable `device-verification.json` remain in ignored `app/build/reports/phase15/v10/device/`. The first failed run is retained separately from successful reruns. AndroidJUnitRunner case identifiers give the initial 185-case totals above; the Gradle progress display double-counted the three skipped cases as 188, which is not used as the unique test count.

Reproduce the targeted repository checks from the repository root:

```powershell
.\gradlew.bat --offline --no-daemon :app:detekt :app:lintDebug :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=dev.metis.agent.LanguageReadTest,dev.metis.agent.MemorySearchTest,dev.metis.agent.PersonalDatabaseTest,dev.metis.agent.PersonalMigrationTest,dev.metis.agent.FoundationRoundTripTest,dev.metis.agent.FoundationReferenceCleanupTest,dev.metis.agent.FoundationLifecycleTest,dev.metis.agent.FoundationMigrationTest,dev.metis.agent.MemoryEngineTest"
```

This adds physical-device saved-record retrieval evidence for one API-36 phone. It does not close API-26/device-matrix parity, representative latency/RAM/battery measurements, classifier quality, representative calibration or fresh independent human evaluation gates. Test-suite durations are not reported as request latency or battery measurements.
