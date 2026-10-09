# Phase 8 confirmed task completion

Status: implemented and locally verified on 2026-10-09. Phase 8 remains in progress, with reminder scheduling and other mutations pending. GitHub verification for this slice is pending.

## Working flow

`complete task: Study!` looks for exactly one OPEN task with that title, ignoring letter case but preserving title punctuation. Optional “please” and “the” are accepted. Substrings, fuzzy selection, multiple matching open tasks and recurring tasks do not produce an executable proposal. Negated input is rejected before storage reads. Supported action titles are bounded to 500 UTF-16 units without control characters. Other command forms require a fresh supported request.

The proposal displays the selected title, status change, risk, reason and five-minute acceptance deadline. It binds the selected task ID and revision. Nothing changes before explicit acceptance. Acceptance is bound to the canonical session proposal and invalidated by cancellation, editing, dismissal or a new request, using the same session mechanism as task creation.

Completion changes only status to COMPLETED and sets completedAt. Notes, priority, deadlines, project/goal links, schedules, reminders and memories stay as they are. Recurrence advancement, reminder cancellation, schedule completion and Phase 9 events are not implied or executed.

## Durable safety and recovery

Acceptance rechecks current policy, expiry, the selected task revision, OPEN status, absence of recurrence and completion of all prerequisites. The encrypted pending action is linked to the existing task so privacy deletion also scrubs pending personal content. Retry refreshes policy, expiry, title, revision, status, recurrence and prerequisite state; stale or revoked work becomes failed without changing the task. Pending cancellation is explicit. No startup execution is added.

The status update, read-back verification, success receipt and allow audit commit in one Room transaction after the durable pending reservation. A receipt-write failure rolls back the task update and leaves pending work for review. The persisted idempotency key prevents duplicate status changes and returns the original historical receipt after replay. Success describes the verified local action, not notification delivery or the current status after a later edit/undo.

Undo reopens only the unchanged completed task at the receipt revision and clears completedAt. It refuses to reopen a prerequisite with completed dependent tasks. The status restoration, verification, separate linked undo action and audit commit atomically. Undo replay does not increment the revision again. History in You distinguishes creation from completion and marks a completed undo without offering it again. Task privacy deletion scrubs completion and undo records while preserving their idempotency tombstones. Creation undo retains its stronger no-linked-record deletion guard.

## Verification

Debug/release builds, debug/release lint and Detekt passed, together with all 66 JVM tests. All 118 Android tests passed on each of local API 26 and API 34 with zero failures, errors or skips. Release APK permissions remain limited to the app's own dynamic-receiver permission; schema v6, migrations and dependencies are unchanged. Documentation links and whitespace checks passed.

Coverage includes exact-title punctuation and UTF-16 extraction, canonical acceptance, negation, unresolved context and answer-only policy, ambiguity, recurring tasks, concurrent replay, field preservation, encryption, stale revisions, unfinished prerequisites, rollback/retry, expiry, cancellation, policy revocation, pending task deletion, database reopen, repeated undo, edited task conflicts and completed dependent tasks. A real-app composer test reviews the title, accepts completion and verifies undo.

The original 45-case Phase 7 corpus remains a regression set for its original seven intents; this additional completion grammar is tested separately. No broader language-accuracy claim is made.

Verification recovery: an early title-extraction regression caught trailing outer whitespace; the non-greedy capture now excludes it while retaining punctuation. The first device invocation found no connected emulators, and a later interrupted run left partial reports and synthetic fixtures. After restart, those leftovers caused three existing API 34 screen tests to fail on duplicate records/nonempty counts. The test app was installed and its synthetic data reset successfully on each owned emulator before the final full rerun. Partial/interrupted results are excluded from passing counts.

See [task creation verification](PHASE_8_VERIFICATION.md), [core features](CORE_FEATURES.md) and [roadmap](ROADMAP.md).
