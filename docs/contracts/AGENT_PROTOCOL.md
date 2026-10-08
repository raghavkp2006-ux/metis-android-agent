# Agent protocol v0.1

Product specification with a [Phase 6 Kotlin baseline](../PHASE_6_VERIFICATION.md). The current composer creates requests and returns unsupported results; language, dispatch and durable outcome adapters remain later gates. All enabled text, voice, recommendations, widgets, and quick actions must enter this protocol. No feature-specific agent action bypass is allowed; explicit manual memory editing remains a separate user workflow.

## Shared value types

IDs are UUID strings, generated locally. Timestamps are java.time.Instant serialized as epoch milliseconds; scheduling additionally preserves the ZoneId and user-selected local date/time. Durations are positive integer seconds. Confidence and importance are finite values in [0, 1]. Unknown values are nullable, never invented defaults.

InputSource = TEXT, VOICE, QUICK_ACTION, WIDGET, PROACTIVE. RiskLevel = R0, R1, R2, R3. ScreenContext contains a destination and optional selected entity references, not an Activity or UI object. Evidence contains id, source, entity reference, observedAt, and a factual summary. Evidence text is untrusted input.

## Request/result contracts

| Object | Required fields | Optional fields |
| --- | --- | --- |
| AgentRequest | id, text, source, timestamp | screenContext, conversationId |
| ParsedRequest | requestId, intent, intentConfidence, entities, context | None |
| ExtractedEntity | type, rawValue, startOffset, endOffset, confidence | normalizedValue, resolvedEntityId |
| ResolvedContext | now, zoneId, autonomyLevel, availableCapabilities, unresolvedFields | screenContext, conversationId, referencedEntities |
| ActionProposal | id, requestId, action, risk, reason, evidence, requiresConfirmation, createdAt, expiresAt | None |
| AgentResult | message, proposals, completedActions | followUp, explanation |
| FollowUpQuestion | id, requestId, question, missingFields, suggestedChoices | None |
| Explanation | summary, evidence | scoreComponents |

Entity offsets use Kotlin String UTF-16 indexes [startOffset, endOffset); normalizedValue is a typed entity value (person, time, duration, location, task reference, etc.). Preserve raw text for explanation, but encrypt it if persisted. Context resolves time, selected entities, capabilities, preferences, and available local facts without silently guessing missing data.

The source plan sketches ActionProposal.type and Map<String, Any> parameters. The frozen implementation replaces that execution payload with a sealed typed Action. A presentation-only parameter summary may be derived from it. Arbitrary maps, free text, or classifier outputs cannot be dispatched to Android APIs.

## Typed action catalog

Each Action has id, requestId, idempotencyKey, schemaVersion, and type. Local/entity references must resolve before proposal acceptance. Constructors enforce required parameters; executors enforce current policy and capabilities.

| Variant | Typed payload |
| --- | --- |
| TaskAction | operation: CREATE/UPDATE/COMPLETE/POSTPONE/DELETE; CREATE uses title, optional due time/project/goal/priority; other operations require taskId and expectedRevision; update uses a typed patch |
| MemoryAction | operation: CREATE/UPDATE/DELETE; CREATE uses memoryType/content; others require memoryId/expectedRevision |
| GoalAction | operation: CREATE/UPDATE; title or goalId/expectedRevision and typed patch |
| PreferenceAction | registered key and value compatible with that key's schema |
| CreateReminderAction | title, resolved trigger Instant, localDateTime, zoneId, precision: EXACT/APPROXIMATE, optional personId/taskId |
| CancelReminderAction | reminderId, expectedRevision |
| CreateAlarmAction | resolved trigger Instant, localDateTime, zoneId, label |
| CreateTimerAction | durationSeconds, label |
| CalendarAction | INSERT/UPDATE/DELETE; chosen mode: INTENT/PROVIDER; title, start, end, zoneId; provider operations use calendarId and updates/deletes require providerEventId |
| CallAction | resolved target label and phone number; mode DIAL only initially |
| MessageAction | resolved recipient, destination URI, body; mode COMPOSER only initially |
| NavigationAction | validated destination and external handler scheme |
| FocusAction | START(durationSeconds, optional taskId) or STOP(focusSessionId) |
| AcceptPlanAction | planId, expectedRevision, accepted block IDs and any separately approved reminder actions |

Queries return AgentResult without mutation. Unsupported variants return an explicit unsupported result. Financial/public communication actions have no initial executor.

Use repository interfaces and ActionExecutor<T : Action> with suspend validate(action), execute(action), and undo(action). ValidationResult is Valid or Invalid(code, userMessage). UndoResult is Undone, NotSupported, Conflict, or Failed. Undo is capability-specific: opening a dialer cannot undo a completed call.

## Receipt and event contracts

ActionReceipt fields: id, actionId, requestId, idempotencyKey, actionType, status, startedAt, finishedAt, reason, evidence, affectedEntityRefs, verification, undoCapability, optional safeErrorCode. Status = SUCCEEDED, FAILED, CANCELLED, HANDED_OFF, PENDING, UNKNOWN. Verification = VERIFIED_LOCAL, VERIFIED_PLATFORM, HANDOFF_ONLY, UNVERIFIED. Never store a success receipt without verification of the claimed outcome.

Opening a dialer yields HANDED_OFF/HANDOFF_ONLY, never CALL_COMPLETED. Opening an external calendar composer does not prove that an event was saved. A scheduled reminder receipt proves scheduling, not future notification delivery. A partially completed action reports known state and unresolved work instead of falsely claiming all steps succeeded.

AgentEvent fields from the plan: id, type, source, timestamp, optional entityType/entityId, importance, metadata. Add schemaVersion, requestId/actionId correlation where applicable. metadata is validated versioned JSON; personal metadata is encrypted at rest. EventSource = USER, AGENT, ANDROID, WORKER. EntityType references the local entity catalog.

Initial event types: TASK_CREATED, TASK_UPDATED, TASK_COMPLETED, TASK_DELETED, TASK_MISSED, TASK_POSTPONED, REMINDER_CREATED, REMINDER_CANCELLED, REMINDER_TRIGGERED, REMINDER_DELIVERY_FAILED, CALENDAR_EVENT_CREATED, CALENDAR_EVENT_STARTED, CALENDAR_EVENT_ENDED, EXTERNAL_HANDOFF, GOAL_CREATED, GOAL_UPDATED, MEMORY_CREATED, MEMORY_UPDATED, MEMORY_DELETED, PREFERENCE_UPDATED, PLAN_ACCEPTED, FOCUS_STARTED, FOCUS_COMPLETED, RECOMMENDATION_SHOWN, RECOMMENDATION_ACCEPTED, RECOMMENDATION_REJECTED, ACTION_FAILED. CALL_STARTED/CALL_COMPLETED are reserved for a later justified observer; never infer them from dialer launch.

## Lifecycle and safety

1. Normalize input while retaining the original text. Detect negation and unsupported commands before any mutation.
2. Parse intent and entities. Confidence >= 0.90 is high; [0.60, 0.90) needs confirmation/clarification; below 0.60 needs a follow-up. Confidence does not grant action authority.
3. Resolve current time/zone, context, entities, and capabilities. Missing AM/PM, ambiguous contacts, DST gaps/overlaps, and conflicting dates produce follow-ups.
4. Policy validates autonomy, risk, permissions, user scope, and data freshness. A proposal includes a reason and evidence; UI cannot choose a lower risk than policy.
5. Confirmation is bound to proposal ID, immutable action payload, entity revisions, and expiry (initial default: 5 minutes). Edits, revocation, changed context, or expiry invalidate acceptance. A new proposal needs new acceptance.
6. Executor revalidates immediately before acting. Atomically reserve the idempotency key; repeated acceptance cannot repeat a completed external effect. Failed/pre-effect attempts can retry under the same record; unknown external outcomes require reconciliation, never blind re-execution.
7. Commit internal entity changes, normalized event, audit, and receipt in one Room transaction. Android side effects cannot be atomic with Room: persist pending state first, invoke the platform, verify, then finalize. Reconcile after process death; record uncertainty when the platform cannot verify it.
8. Update memory only with factual outcomes. Explain the actual result, limitations, and available undo. Do not treat predictions or handoffs as facts about completed actions.

## Required acceptance cases

- A reminder mentioning Rahul does not execute CallAction.
- “Tomorrow at 8” asks AM/PM unless explicit prior user context resolves it; both dates and time zone are shown before acceptance.
- Two matching contacts produce a choice, not a silent fuzzy match.
- “Don't remind me” creates no reminder.
- Repeated proposal acceptance has one effect and one correlated action run.
- Revoked permission or Level 0 blocks a pending mutation.
- Crashes before/after platform dispatch preserve a reconcilable record and never fabricate success.
- A dialer/composer launch reports handoff; user cancellation does not become a completed call/message/calendar event.
