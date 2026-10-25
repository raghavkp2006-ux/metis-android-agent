# English references and two-intent requests

Base: main after PR #25, `ca8ec77`. Branch: `feat/references-multi-intent`.

Reference state contains typed mentions from requests and their proposals in at most the preceding three user turns. A single
utterance containing two intents is one turn, so different people mentioned in that turn are
equally recent. Mentions expire at ten minutes and cannot cross a conversation session. New chat
and ViewModel disposal clear the state. Empty/unknown turns count toward the three-turn limit.
No state is persisted and no resolved name is logged. Diagnostic strings for the new state are
redacted. Explicit named entities in read requests and write requests are eligible; names are
never inferred from free-text answer messages. Task mutations still resolve a unique supported
saved task and its revision through the existing store before proposing a change.

| Utterance / preceding context | Outcome |
|---|---|
| `I promised Rahul the slides`, then `remind me tomorrow to call him` | Resolve Rahul; ask for the missing reminder time. A time answer produces a reviewable reminder proposal. |
| `what did I promise Rahul`, then `I promised him the notes` | Reuse the explicit person mention from the read query; propose the promise for Rahul. |
| `complete it` without a recent task mention | Ask which item is meant; do not propose a mutation. |
| `Who did you promise?`, followed by `him` | Resolve the person and resume the existing pending action; ambiguous references ask for a choice first. |
| Two different people in the most recent turn, then `call him` in a reminder | Ask `Who do you mean?` with choices; no automatic selection. |
| `complete it` after a reminder mention sharing a task's title | Ask for the task; do not silently convert the reminder into a task reference. |
| A mention is ten minutes old, outside the last three turns, or from another session | Ask for a referent. |
| `add a task to read and add a task to write` | Two separate cards, each with its own canonical proposal and Accept. |
| `add a task to read, then remind me tomorrow at 8 pm to study` | Two separate proposals; each still uses its existing policy and acceptance path. |
| `add a task to read and create something` | Keep the task proposal and ask for a supported request for the other part. Clarifying that part does not consume its sibling. |
| `add a task to buy bread and milk` | One task titled `buy bread and milk`. |
| `add a task to "read and create notes"` | One task; quoted conjunctions are not split. |
| Three recognized intent clauses | Ask to split into at most two requests; prepare no actions. |
| A negated compound request | Prepare no actions. |
| Accept either card, repeat its Accept, then accept the other | One write for each distinct accepted proposal; repeating Accept does not write again. |
| Cancel, navigate away, or start a new chat | Invalidate unaccepted proposals. No unaccepted action executes. |

Splitting requires `and` or `, then` followed immediately by `remind`, `add`, `create`, `complete`,
`postpone`, `delete`, or `promise`. Split requests are limited to supported task, reminder, and
promise actions. Other parts ask for clarification instead of dispatching recommendations or
read paths with possible side effects. `promise Rahul the slides` is the imperative equivalent
of `I promised Rahul the slides`. `complete it`, `delete that`, and `postpone it until ...` are
typed task-reference forms.

Each card uses a fresh instance of the existing agent chain, sharing the existing stores and
policy. Its canonical proposal and clarification remain in that instance. The existing
`ClarificationCoordinator` binds request, question, session, and expiry for each pending answer;
its implementation and existing timeouts are unchanged. When both cards need clarification,
answers and numbered choices are handled in card order. Either ready proposal can be accepted
independently. There is no bulk Accept or automatic dependency execution.

Protected existing tests, semantics-tree diagnostics, test wait/timeout logic, ReadGrammar,
ReadSafety, Phase 15 files, permission declarations/policy, schema, Room version, PolicyEngine,
and INTERNET access are unchanged.

Local validation passed with:

```powershell
.\gradlew.bat --no-daemon --continue :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease :app:detekt
```

- JVM: 347 tests in 59 suites; 327 passed, 20 historical archived skips, zero failures/errors.
  Twenty new JVM test methods cover rules, pending state, action flows, and ViewModel transitions.
  All existing test/helper source files are unchanged.
- Debug APK: built successfully.
- Debug and release lint: each has zero errors/warnings and 13 informational dependency hints.
- Detekt: zero findings with the existing baseline and configuration.
- `git diff --check`: passed. Protected-file audit: zero matching changes.

Changed production files:

- `app/src/main/java/dev/metis/agent/domain/agent/EnglishConversationRules.kt` (new)
- `app/src/main/java/dev/metis/agent/domain/agent/EnglishConversationState.kt` (new)
- `app/src/main/java/dev/metis/agent/domain/agent/EnglishRules.kt`
- `app/src/main/java/dev/metis/agent/domain/agent/PromiseResolution.kt`
- `app/src/main/java/dev/metis/agent/domain/agent/ConfirmedTaskAgent.kt`
- `app/src/main/java/dev/metis/agent/domain/agent/ConfirmedReminderAgent.kt`
- `app/src/main/java/dev/metis/agent/domain/agent/ConfirmedPromiseAgent.kt`
- `app/src/main/java/dev/metis/agent/domain/decision/DecisionAgent.kt`
- `app/src/main/java/dev/metis/agent/domain/planner/PlannerAgent.kt`
- `app/src/main/java/dev/metis/agent/presentation/navigation/RequestViewModel.kt`
- `app/src/main/java/dev/metis/agent/presentation/navigation/ChatWorkspace.kt`

New JVM files under `app/src/test/java/dev/metis/agent/`: `EnglishConversationRulesTest.kt`,
`EnglishConversationStateTest.kt`, `ConversationActionFlowTest.kt`, `ConversationViewModelTest.kt`,
and `ConversationTestFixtures.kt`. This report is the seventeenth changed file.

Android emulator/physical-device UI verification is deferred; JVM tests do not establish rendered
two-card behavior on a device. Untyped entities appearing only in answer prose deliberately
remain unresolved and require an explicit user clarification.
