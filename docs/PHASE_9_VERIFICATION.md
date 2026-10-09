# Phase 9 event engine

Complete baseline, implemented and locally verified on 2026-10-09.

## Persisted outcomes

Supported confirmed task creation, completion, rename, priority, postpone, deletion and undo now append normalized events. Reminder registration, cancellation, verified notification posting and denied/uncertain delivery produce separate events. The event writer runs inside the action-run save transaction, so an event encryption/storage failure rolls back the local mutation and receipt while preserving the earlier pending reservation for reviewed retry. The surrounding executor also commits its policy audit in that transaction.

Events use the existing append-only schema v6 table and encrypted version-1 metadata containing a readable result, verification, historical status, operation and optional title/error code. Stable UUIDs derived from action ID and outcome status prevent duplicate recording during acceptance/retry/worker replay. Unknown outcomes are unverified; a later verified outcome, if supported, is a separate historical event. Pending reservations and unaccepted, dismissed, negated or ambiguous proposals create no success events. Cancellation of accepted pending work uses ACTION_FAILED with explicit CANCELLED metadata because the frozen catalog has no generic cancellation event.

Every generated event correlates to its request and action, whose policy audit remains independently persisted. Entity ownership follows the correlated action rather than a second direct entity link: generated history does not introduce an artificial linked-record blocker for task undo/deletion. Privacy deletion traverses those action links and clears encrypted event details and audit content while retaining generic historical types and idempotency tombstones. Deletion outcomes contain no deleted title. Existing pre-Phase-9 receipts are not backfilled or re-executed.

## Reminder milestone

REMINDER_CREATED means verified approximate WorkManager registration. REMINDER_TRIGGERED requires an active local notification readback and never claims the notification was read or a call was made. Revoked notifications and interrupted/unverifiable posts produce REMINDER_DELIVERY_FAILED with UNVERIFIED metadata. Resume reconciliation now records a separate unavailable-delivery child outcome for a missing/denied registration; repeated reconciliation cannot duplicate it or replace the original successful registration receipt.

WorkManager dispatch remains a platform side effect outside atomic guarantees: a durable claim precedes posting, and interrupted uncertain posts are never blindly repeated. Existing Phase 8 permission, cancellation, expiry, replay and restart checks remain required.

## Timeline

Timeline observes persisted events and correlated audits, newest first with stable ID ordering for timestamp ties. Rows show type, optional title, local date/time with zone, readable outcome and verification. Expandable details show source, request/action correlation and policy decisions/reasons. Loading, empty, read-error/retry and redacted states are explicit; personal display rows are discarded on read failures. Fifty rows render initially, with a Show more history control. Reads currently load the existing repository's full history; large-history storage pagination and performance belong to hardening.

## Verification

Added instrumentation checks for event-write rollback/retry, encrypted metadata, request/action/audit correlation, database reopen, privacy redaction, task mutation/undo/deletion events, and absence of events for unaccepted/negated input. Existing reminder tests now verify separate registration/posting/cancellation/failure events, single posting under replay, reconciliation idempotency and deletion redaction. The real composer reminder screen test checks Timeline registration, cancellation and expanded policy reasons.

Final debug/release builds, debug/release lint, Detekt and all 67 JVM tests passed. The full Android regression run passed 147 executed tests on each local API 26 and API 34 emulator, with zero failures/errors. Each device also reported three existing explicitly ignored host-only tests: notification app-op mutation and the two restart/reboot stages. These skips are not passing checks, and those host procedures were not rerun in Phase 9. Notification denial and database reopen are covered by the executed store tests.

After that run, the empty-state resource reference was corrected and a dark-theme/double-font-size populated Timeline test was added. The final three-test UI run passed on each API 26/34 device with no skips: large-font navigation, populated Timeline outcome/audit expansion and touch-target accessibility, and the real reminder composer/Timeline flow. The new test uses the existing edge-to-edge shell and scrollable audit controls. The full regression reports are retained locally under `.android-local/phase9-full-reports/`; the latest focused reports are in the standard Gradle connected-test directory. Markdown links and whitespace checks passed. Schema v6, dependencies and permissions are unchanged; no network or telemetry was introduced.

Verification caught and resolved initial Detekt complexity/constant findings and the final unused empty-state resource. No GitHub run, physical-device review, manual screenshot review, or production/performance claim is made here. Decision scoring and planning remain Phase 10 onward.
