# Phase 7 bounded English language verification

Status: complete bounded language baseline on 2026-10-09; local and GitHub API 26/36 validation passed. Phases 8–19 remain pending.

## Delivered scope

The composer uses EnglishRules, LanguageSpecialist and a read-only AgentReadPort through the existing LocalAgentOrchestrator. The context advertises LOCAL_READ and answer-only authority. Stored autonomy cannot enable mutations. No executor, action acceptance, receipt, permission, schema migration, network access, request persistence or model is added.

Supported closed grammar:

| Intent | Example | Result |
| --- | --- | --- |
| CHECK_TASKS | What tasks do I have? / Show my tasks | Up to five open saved task titles |
| CHECK_MEMORY | What do you know about my DSP exam? / Search memory for DSP exam | Up to five matching explicit nonexpired saved memories |
| WHAT_DID_I_PROMISE | What did I promise Rahul? | Saved promises and recorded status for exactly one matching local person |
| CHECK_CALENDAR | When am I free tomorrow? | Access unavailable; free time remains unknown |
| CREATE_REMINDER | Remind me tomorrow at 8 pm to call Rahul | Resolved date/time/zone and explicit scheduling-unavailable result |
| CREATE_TASK | Add a task to study | Recognition only; creation unavailable |
| CREATE_TIMER | Set a timer for 5 minutes | Bounded seconds/minutes extraction; execution unavailable |

Rules accept case variation, optional “please”, outer whitespace and terminal punctuation. Memory matching still requires all search words and uses the existing Phase 5 ranking and candidate bounds. “My” before a query is optional syntax. Answers identify local saved records and label bounded excerpts or incomplete searches. Promise names match saved local display names without contacts access; zero or multiple people produce a follow-up. Promise status is quoted from storage, without inferring fulfillment. Read errors produce generic failures, never partial or falsely empty answers.

Reminder date resolution accepts today, tomorrow or ISO YYYY-MM-DD. It uses the request timestamp and one captured device zone. Time requires AM/PM or a two-digit 24-hour HH:mm form. Invalid dates/times, past instants, absent date/title, bare hours, DST gaps and DST overlaps remain unresolved and produce follow-up questions. A reminder title containing “call Rahul” is a reminder, never a call or contact lookup. Timers accept 1 second through 1 day. Entity spans preserve original UTF-16 text and emoji.

Negation conservatively rejects the entire request before reads. Quoted, hypothetical, compound read requests, unsupported wording and languages receive clarification. The negation rule intentionally also rejects titles containing words such as “not” or “stop”. Rule match score 1.0 is a deterministic match marker, not calibrated statistical confidence.

## Evaluation and limitations

The versioned [45-case synthetic development corpus](evaluation/english-rules-v1.txt) specifies expected intents and pipeline outcomes at a fixed clock and zone. It has five cases for each of seven recognized intents, five unknown cases and five negated cases. It was authored with this implementation and is a regression corpus, not an independent held-out natural-language evaluation. No blind test accuracy, broad English coverage, confidence calibration, representative physical-phone latency, memory/battery measurements or Hinglish support is claimed. The dataset contract's larger independently reviewed family-split dataset remains a gate for broader language enablement and learned models.

JVM regressions separately cover exact UTF-16 spans, midnight/noon, zone date rollover, invalid/past times, DST gaps/overlaps, unsupported input sources, negation before reads, duration bounds, generic failures and cancellation. Android regressions exercise real encrypted local reads, explicit/derived/expiry separation, open task filtering, bounded output, zero/duplicate person resolution, no mutation or receipt records, missing-key failure and the shared composer's clarification/restoration behavior.

Follow-up answers are fresh complete requests; there is no persisted conversation or automatic merging of a reply with earlier text. Unsupported catalog intents, reordering, misspellings, relative durations for reminders, contact resolution, memory entity inference and platform effects remain unavailable. Phase 8 must implement binding acceptance, fresh policy/capability checks, durable idempotency, lifecycle, verification and receipt/audit transactions. The integrated Phase 8–9 reminder gate remains unchanged.

## Validation

Debug/release builds, all 58 JVM tests, debug/release lint and Detekt passed. All 45 corpus cases matched their expected intent and safe pipeline outcome: per-label precision/recall and macro F1 are 1.0 on this development corpus only (seven recognized intent labels plus UNKNOWN). Negated cases reject before reads. This is not an independent accuracy estimate. Release APK inspection shows no internet or dangerous permissions. Schema v6 and dependency declarations are unchanged; documentation links resolve and git diff --check passes. All 102 Android tests passed on both owned API 26/34 emulators, with zero failures, errors or skips. The initial offline device invocation failed because the UTP host additional-output plugin was not cached; rerunning with dependency resolution enabled fetched it and ran the full suites. GitHub API 26/36 results will be recorded separately. Physical-phone and broad language validation remain later gates.

## Branch consolidation

Before implementation, main was fast-forwarded through the complete existing history of codex/database-persistence. codex/navigation-shell, codex/ci-recovery and metis-phase-1 were already ancestors, so they required no additional conflict resolution. The uncommitted Phase 6 completion notes were preserved in commit 931168c. Branch references are retained; no history was rewritten or deleted.

The [Phase 7 GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37870891856) passed the build/static job and both API 26/36 runtime jobs for implementation commit 80208a8916466c0cf6cf34ca399bdd5a03bb220d. The bounded baseline is complete; Phase 8 may begin.
