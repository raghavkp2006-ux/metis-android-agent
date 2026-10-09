# Phase 11 planner

Baseline implementation and local verification status are recorded here after the final regression run.

## Planner behavior

The Plan destination can open an editable `Plan my day` or `Plan my evening` request, and the shared composer accepts `Plan my day tomorrow` or a user-entered local window such as `Plan my day tomorrow from 09:00 to 17:00`. Day defaults to the explicit day-start preference, or 08:00; evening defaults to 18:00; both end at 22:00. An implicit same-day window begins at the next five-minute boundary. Default windows are labeled as assumptions in the review. Only one local date is supported. Invalid, past, overnight or DST-gap/overlap boundaries ask for clarification.

The local planner reads tasks, prerequisites, goals, projects, explicit day-start preference, saved schedules and enabled routines. Accepted/completed saved blocks consume their overlapping interval; proposed/cancelled blocks do not. Existing overlapping commitment pairs are reported. Open, nonrecurring tasks with known whole-task estimates and completed prerequisites are ordered earliest-deadline-first, then priority, creation time and UUID. A task is placed only when its full estimated duration fits a free gap and ends by its deadline. Unknown durations, incomplete/missing prerequisites, inactive links, booked tasks, missed deadlines, enabled routines and work beyond 16 proposed blocks are not scheduled. Nothing is split and schedule acceptance does not mark tasks complete.

## Acceptance and reminders

Generating a proposal saves no plan blocks. An explicit review and Accept saves encrypted schedule-block text plus a local action run, policy audit and `PLAN_ACCEPTED` Timeline outcome in one Room transaction. Acceptance compares a deterministic snapshot of all source inputs, refreshes current profile authority/capabilities, rechecks the canonical displayed proposal and verifies every persisted block before committing. Concurrent proposals over changed inputs go stale. Replayed accepted requests return their existing receipt; a new request evaluates current inputs. The plan receipt and review state that no reminder was authorized.

Plan displays a separate optional reminder-review control for each future accepted block. It rounds the block start up to a minute, presents the existing approximate reminder proposal and requires its own Accept; users can undo/cancel that reminder through the existing reminder flow. Calendar access and external commitments are not read. User-entered windows confirm only the input the user provided; routine expansion, quiet hours, energy and availability outside saved commitments are unavailable. Enabled routines cause an explicit no-plan response.

Generated block task provenance is held in encrypted block evidence instead of a direct schedule-to-task foreign link. This lets privacy deletion remove generated blocks and redact their accepted-plan action/event/audit trail without making those blocks an unrelated-task deletion blocker. Deleting a generated block also privacy-scrubs the plan that owns it. No schema migration or new permission is introduced.

## Verification

## Final verification — 2026-10-09

Phase 11 is complete as a bounded local baseline. Debug and release APK assembly, debug/release lint, Detekt and JVM tests passed. The JVM suite ran 88 tests with no failures. Connected Android regression ran 174 tests per emulator on API 26 and API 34: 171 executed successfully and 3 were skipped on each device. The skipped checks depend on host notification app-op or host process-restart behavior and are not reported as passing.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
```

The test XML reports are archived under the ignored `.android-local/phase11-full-reports/` directory. No physical calendar provider, GitHub CI, device screenshots, measured latency or battery result is claimed. Planner V1 deliberately uses earliest deadline first; weighted planning, conflict resolution with external calendar and optimization remain future work. No schema migration, dependency or Android permission was added.
