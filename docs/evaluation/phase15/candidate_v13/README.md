# Phase 15 v13: built-in local calendar

V13 adds an on-device calendar to METIS and admits closed calendar read requests against accepted local schedule records. This is a **deterministic development candidate**, separately versioned from v12. ML remains disabled and unbundled. The 50 earlier user queries, approved benchmark, frozen approvals, first evaluation, and v6–v12 candidate results are retained as exposed historical evidence; this increment is not a fresh independent evaluation.

The former Plan tab is labelled **Calendar**. It shows a month grid and daily accepted entries, and lets a person add, edit, or delete standalone local events. Entries use the existing encrypted `schedule_blocks` table and revision checks, so no schema migration or Android calendar permission is required. Planner-owned blocks remain visible but cannot be changed by the calendar editor. The editor validates dates, 24-hour times, end after start, and daylight-saving gaps/overlaps before saving. Calendar events do not create reminders.

The app handles the four complete calendar questions in the submitted batch: tomorrow-afternoon plans, upcoming meetings, meetings with a named person this week, and the next meeting concerning a named topic. Existing complete calendar forms such as “Show my calendar” and “When am I free tomorrow?” also use the local adapter. The latter lists saved commitments; it does not claim that an empty list proves free time across other calendars. A person name must occur in an explicit “with NAME” meeting phrase in the stored title or reason; a mere mention is insufficient. Topics match literal whole phrases. Accepted records are scanned in a bounded one-year/look-ahead or requested day/week window, with a 100-candidate and five-result display limit and explicit incompleteness language.

`ReadSafetyV12` still validates the complete original request before context construction, and `LanguageSpecialist` revalidates intent and extraction before dispatch. Added calendar scope parsing runs only after that gate. Unsupported filters, negation, compounds, actions and unresolved references cannot use a forced calendar classifier label to reach a read. The historical constructors default to unavailable for reproducible v12 behavior; the Android app explicitly enables the local adapter. External Android/Google calendars, recurrence, scheduling by natural-language command, and free/busy inference across other sources are outside v13's implemented scope.

The [regression report](regression-report.md) records measured checks and limits. The [v13 manifest](development-manifest-v13.json) binds the current implementation and raw bytes of frozen evidence without rewriting an earlier manifest. The assistant-authored positive/negative calendar families live in `CalendarV13Test`; the earlier 50-user batch remains exposed development data and retains its separate-request interpretation.

Reproduce from the repository root with JDK 17, Android SDK 36, and Python 3.12:

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v13/historical_tests.py
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/candidate_v13/v13_integrity.py
.\gradlew.bat :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

On an available Android device, run only `CalendarV13StorageTest` and `CalendarV13ScreenTest` (or other tests known to use isolated fixtures). Some unrelated screen tests clear the installed app's database, so they must never be run against a user's populated installation. The v13 storage test uses a temporary encrypted database and test-only keystore alias; the screen test uses an in-memory fake repository.

The next Phase 15 step remains a separately frozen classifier/calibration candidate. For a credible macro-F1 measurement, new human-authored requests need human-reviewed intent, disposition, entity and constraint labels; hold back a portion unseen until after the candidate and thresholds are frozen. Sending all 300 for development first would make the resulting score an exposed diagnostic, not an independent evaluation.
