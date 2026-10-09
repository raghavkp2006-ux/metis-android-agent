# Phase 14 — habits and insights

Status: complete bounded baseline on 2026-10-10.

## Behavior and privacy

- You provides a separate, default-off local behavioral-analysis consent switch. Withdrawing consent disables analysis first, then removes saved habits and Phase 14 derived insights. Enabling consent starts a fresh analysis period by clearing old Phase 14 records first.
- Daily and weekly reflections run only after consent and only when the user taps a review action. Summaries count normalized task-completed, task-postponed, and focus-completed events. The analyzer reads event types and timestamps only. It does not inspect event payloads, task titles, notes, memory, requests, speech transcripts, or audio.
- Weekday patterns require at least three observations on three distinct dates sharing that weekday in the rolling 28-day evidence window. Results are deterministic, carry a versioned method and source watermark, and are persisted as encrypted aggregate insight statistics. The stored confidence is the observed distinct-weekday support divided by the four-week maximum; it is an evidence-support score, not a calibrated probability.
- Saved reflections are shown in You while consent is enabled. Users can delete derived reflections or recompute them from the saved factual event history. Reflection does not rewrite append-only events; correcting source facts uses existing privacy deletion paths.
- The You switch is the source of truth for behavioral consent in this single-profile app. It is a separate local-only setting. The reserved profile consent column remains false and grants no authority. Planning preferences do not opt users in.
- No background analysis, notification, network access, new permission, model, or training flow was added.

## Verification — 2026-10-10

- Debug and release APK assembly passed.
- Debug and release lint and Detekt passed.
- All 100 JVM tests passed, including consent denial, local-time daily/weekly boundaries, future-event exclusion, rolling evidence window and repeated-pattern threshold coverage.
- The API 34 connected Android regression passed: 182 test cases total, zero failures, 179 executed and three host-dependent reminder tests skipped. The Phase 14 consent screen and encrypted storage guard/deletion tests ran and passed.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebug :app:assembleRelease
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
```

This is an aggregate informational baseline. It does not make recommendations, alter planning scores, schedule actions, or infer routines from private text. API 26 device coverage and broader production hardening remain in Phase 17.
