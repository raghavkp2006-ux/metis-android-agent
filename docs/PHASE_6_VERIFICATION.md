# Phase 6 agent protocol verification

Status: implemented and local validation passed on 2026-10-08; GitHub validation pending. Physical Room schema v6 and the encrypted storage baseline are unchanged.

## Delivered behavior

The shared composer creates a typed AgentRequest from button or IME submission on any destination. RequestViewModel prevents duplicate submission while processing, clears submitted text from the restorable shell draft, and exposes a transient AgentResult. The shipped UnsupportedLanguage baseline reports: “Command understanding is not available yet. No action was taken.” It never guesses an intent or produces an action. Closing the sheet cancels processing and clears the response; editing a new draft clears the previous result. No microphone, model, conversation persistence or platform execution is enabled.

The domain protocol includes all 32 planned intents plus nonexecutable UNKNOWN/UNSUPPORTED; requests, typed extracted entities, fixed time/zone/context, capability snapshots, proposals, follow-up questions, explanations, results, receipts and events. Kotlin UUID/Instant/ZoneId values carry identity and time. Text and collections are bounded, confidence is finite in 0–1, durations/revisions are validated, UTF-16 offsets must exactly match original text without splitting surrogate pairs, and resolved local times must match a valid zone offset. DST overlap requires a selected matching instant; gaps cannot be represented as resolved time.

All 14 action types have sealed typed payloads. Task/memory/goal updates carry typed targets and revisions; preferences reuse the registered typed value rules. Reminder/alarm/calendar times retain local time and zone, and reminders specify exact/approximate precision. Dialer and messaging payloads expose handoff modes only; message destinations reject arbitrary schemes, URI query/body injection and malformed single destinations. Plan blocks carry explicit revisions and reminder proposal IDs require separate authorization. No opaque map/JSON or raw classifier string is dispatchable.

Classifier, extractor, context builder, specialist, policy, orchestrator and generic typed executor interfaces are replaceable. LocalAgentOrchestrator handles negated, unknown, below-0.90 confidence and unresolved inputs before suggestions, checks request correlation, routes suggestions through proposal policy, and produces no execution receipts. Below-0.90 confidence conservatively asks a follow-up. Exceptions become generic failures; coroutine cancellation propagates. The baseline context reports answer-only authority and no implemented agent capabilities even if a dormant profile stores higher autonomy.

ProposalPolicy derives minimum risk and capability requirements from the sealed action, disallows unsupported higher autonomy, and checks current revisions/capabilities. Proposals require confirmation, expire within five minutes, and defensively copy evidence/collections. Preview review rejects expiry, capability revocation or changed revisions. Receipts enforce verified success versus unverified pending/failure/unknown and handoff; dialer/message/maps receipts cannot claim success. Event metadata is typed; storage catalog names remain compatible.

## Privacy and remaining gates

Submitted requests/results stay in RAM and are discarded on sheet dismissal/process loss. The existing bounded unsent draft may still be retained by Android instance-state restoration; it is cleared on submission. No request logging, telemetry, encryption/key change, new permission, automatic memory update or training upload is introduced. Manual Phase 5 memory controls remain their own explicit user editing flow.

Proposal review is not acceptance or execution authority. No action can execute in Phase 6. Phase 7 adds evaluated deterministic English rules and safe read adapters, including negation and ambiguity cases. Phase 8 must add immutable bound acceptance, reference/platform revalidation, durable idempotency, pending/reconciliation states, and atomic mutation/receipt/audit adapters. Phase 9 adds event encoding and persistence. The current typed models do not serialize themselves into the older encrypted storage JSON fields. No process-death/external-action recovery or language-accuracy claim is made in this phase.

## Validation

Local debug/release builds, all 48 JVM tests, debug/release lint, Detekt and instrumented APK compilation passed. All 98 Android tests passed on owned API 26/34 emulators. Release APK inspection confirms no internet/dangerous permissions and excludes the debug inspector. Schema v6 and dependency versions are unchanged; updated documentation links resolve and git diff --check passes. GitHub results are pending.

New JVM regressions cover catalog compatibility, original UTF-16 extraction, malformed values, DST gaps/overlaps, unsafe external payloads, minimum risk/confirmation/expiry, truthful receipts, request correlation, defensive collections, autonomy/capability/revision review, unknown/negated/unresolved requests, unsupported sources, failure sanitization and cancellation. Android regressions cover shared composer submission/restore and request duplicate/cancel/error handling, including a dependency delaying cancellation. No external-action, real-user language accuracy or physical-phone validation is claimed.
