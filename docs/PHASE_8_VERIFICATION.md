# Phase 8 task-action slice verification

Status: first slice implemented and locally verified on 2026-10-09. Phase 8 remains in progress. This slice delivers plain local task creation, durable accepted-action records and guarded undo; the Android reminder milestone and other task/external mutations are pending.

## User flow

“Add a task to study” produces a proposal showing the exact title, local-write risk, reason, lack of deadline/reminder and five-minute expiry. Nothing is saved until Accept. Cancel, draft editing, a new request and sheet dismissal invalidate unaccepted proposals. ConfirmedTaskAgent binds acceptance to the canonical immutable proposal object; a copied/forged or superseded proposal cannot execute. The composer disables edits and dismissal while an accepted mutation completes. No higher autonomy can skip acceptance.

After acceptance, LocalAcceptedTaskStore persists an encrypted PENDING action and confirmation audit. It then rechecks current policy/expiry and commits the task, verified SUCCEEDED receipt and allow audit in one Room transaction. Task IDs derive deterministically from the action identity; the unique stored idempotency key binds request/proposal/payload/risk/reason/expiry. Replaying a completed accepted action returns the original receipt without creating another task. A success receipt means a task row was read back and verified; it does not claim any platform effect or notification delivery.

Only plain TaskMutation.Create is supported, with no deadline, priority, project, goal or reminder. Titles are bounded to 500 UTF-16 units and exclude control characters. Other typed actions and unsupported task mutations cannot dispatch. The original read-only Phase 7 orchestrator remains available for its regression corpus; the production composer uses the confirmed-task session and delegates read/reminder/timer responses to the same LanguageSpecialist.

## Policy, recovery and undo

Autonomy is read from current active local profile metadata inside action transactions. No profile defaults to suggestion mode for this explicitly confirmed local feature. Answer-only or conflicting profiles deny execution. Higher stored autonomy is capped at suggestion mode. Only LOCAL_READ/LOCAL_WRITE implementation capabilities are advertised; no Android permission or new schema/dependency is introduced.

Accepted task actions in You survive model/database recreation. A failed mutation/receipt/audit transaction rolls back all task effects, leaving the previously committed PENDING reservation. Explicit Retry revalidates the persisted, versioned plain-task payload, original acceptance deadline and current policy. Expired/revoked pending work becomes an unverified failed result; it does not execute. Cancel pending task records cancellation before any mutation. There is no automatic startup/background execution. A key/storage failure remains visible without resetting the database or creating a replacement key.

Undo requires a matching stored receipt and unchanged task revision, plus no newly linked schedule, memory, dependency, reminder, promise, focus, event, recommendation, insight or other action record. It removes only the newly created task, verifies absence and saves a separate undo action/audit atomically. Edits and links block undo. Existing privacy cleanup scrubs the original action's personal payload, receipt and evidence; it retains the idempotency tombstone and prevents replay from recreating deleted personal data. Undo replay is harmless. Other task deletions and cascade cleanup are not enabled through this flow.

Once explicitly accepted, mutation storage runs to a durable outcome even if a coroutine is cancelled. If the process dies before the completion transaction commits, the accepted reservation is reviewable in You; it is never reported as verified success. No claim is made that host tests reproduce every physical process-kill timing. Android effects need a separate reconciliation protocol in the reminder slice.

## Verification

Debug/release builds, debug/release lint and Detekt passed. All 63 JVM tests and all 110 Android tests on each of local API 26 and API 34 passed with no failures or skips. Regression coverage includes canonical proposal binding, duplicate/late acceptance, cancellation, policy revocation, expiry, database reopen, concurrent replay, receipt-write rollback/retry, pending cancellation, missing-key safety, changed/linked-task undo denial, privacy tombstones and visible composer review/accept/cancel/undo. Release APK permissions remain limited to the app's own dynamic-receiver permission. GitHub verification is pending for this slice and will be recorded separately.

The initial Android run caught an invalid audit policy tag and an incorrect test button label. Reservation transactions rolled back before task creation. The tag now follows the stored uppercase identifier contract, and the UI test uses the actual string resource; the full corrected run passed. Static-analysis findings were resolved before the final run.

## Remaining Phase 8 gates

- Task update/complete/delete/postpone and target ambiguity/revision resolution.
- Android reminder scheduling, permission/notification denial, precision disclosure, schedule verification, retry/cancel/undo, restart/reboot reconciliation and delivery outcome handling.
- Other typed executor capabilities remain unavailable until their own policy, permission and verified-outcome gates pass.
- Phase 9 event encoding/persistence and Timeline integration, followed by the full Phase 8–9 reminder acceptance scenario. No new timeline event is emitted by this slice.

See [implemented core features](CORE_FEATURES.md) for the app-wide status.
