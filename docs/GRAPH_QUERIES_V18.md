# Additive saved-data graph reads (V18)

Base: merged main `ae9cc8063ac854e33a4e66ea51a1c703e6ef094c` (PR #26).
Branch: `feat/graph-queries`. No merge is authorized.

## Behavior

| Utterance / condition | Outcome |
| --- | --- |
| `what is pending in RF project?` | Resolve the saved project exactly; return open tasks linked directly by project ID or through a saved goal's project ID. Sort deadlines ascending, undated last; show total and earliest deadline (including overdue). |
| `what did I promise Rahul?` | Delegate to the existing read, preserving its entire output, all statuses, and original saved order. No added label or reordering. |
| `what did I promise Rahul that is still open?` | Open promises only, with an explicit open scope label, count and saved due dates; overdue first and undated last. |
| `what promises to Rahul are still open?` | Same explicit open-promise scope. |
| `what did I promise Rahul that is pending?` | Equivalent explicit open-promise scope. |
| `what is pending with Rahul?` | Open promises, open linked tasks, and PENDING/SCHEDULED reminders; report source counts, saved deadlines and a pending scope label. |
| Unknown exact name with saved prefix matches, e.g. `what is pending with Ra?` | Say no saved name matches exactly; offer saved prefix choices, without selecting automatically. |
| Unknown name without a saved prefix match | Say no saved project/person matches and no saved names match the prefix. No invented names or fuzzy matches. |
| Multiple saved records with the same exact name | Ask which saved record; numbered choices are stable within that question. Recheck the selected ID against current saved matches before reading content. |
| Known name with no open records | State `Nothing is pending`, with zero source counts. |
| More than five results | Show five records and `And N more`, using the full matching count. Saved text excerpts are capped at 500 characters per row. |
| `what did I promise Rahul`, then `what is pending with him` | Reuse the existing typed person memory and read only after resolution. |
| `I promised Rahul the slides`, then `what is pending with her` | Reuse the same existing person memory. No new storage or reference mechanism. |
| Missing, equally recent, expired or out-of-session pronoun | Ask `Who do you mean?`; no graph-port read occurs. Existing three-turn / ten-minute limits and session reset apply unchanged. |
| `what is pending in Research`, then `complete it` | A project is not inserted into task reference memory. Ask which item if no prior task/item referent exists. |
| `remind me about the RF project`; `add a task to finish pending work` | Never graph productions; retain the existing action/unsupported path. |
| Negation, extra constraints, unknown grammar, compound/injection-style input, controls/format characters, overlong input | V18 rejects before any saved-data access. It does not discard constraints or turn a substring into a read. |

Tasks have no direct Person column. The person graph therefore follows existing explicit
`SavedPromise.personId/taskId` and `SavedReminder.personId/taskId` links. The edge is a
saved fact even when its promise/reminder is closed; the returned task must itself be OPEN.
Tasks are deduplicated by stored ID. Task titles and contact data never imply an edge.

Only SavedPerson rows resolve people. No contact-provider access, network access, proposals,
write methods, executors, new permission, schema/Room version or PolicyEngine change is involved.
Storage failure produces a failed answer, never an empty-result claim. No names or task text
are logged; V18 plans, choices, pending payloads and rows have redacted diagnostic strings.

## Registration and validation

`GraphReadPortV18` extends the existing `AgentReadPort`; its legacy operations delegate to
`LocalAgentReads`. The new graph method requires an immutable V18 plan. Both the dispatching
agent and local port independently revalidate the complete original request and exact typed
name span. Inputs are limited to TEXT/VOICE, 512 UTF-16 units, 100-unit person names and
200-unit project names; historical safe-subject checks are reused without modifying them.
Unsupported constraints and unresolved pronouns never become executable plans.

The new agent delegates every production admitted by ReadSafetyV16 to the historical path.
The EnglishRules addition runs only after all existing rules fail, preserving precedence.
Saved-name choices use the existing ClarificationCoordinator/PendingClarification identity,
session and expiry checks. The existing conversation state resolves person pronouns before
graph dispatch; V18 introduces no second reference memory. No names are persisted by V18.

Exactly two existing files are edited:

```diff
--- EnglishRules.kt
+++ EnglishRules.kt
@@
         val match = RULES.firstNotNullOfOrNull { rule -> rule.pattern.matchEntire(text)?.let { rule to it } }
-            ?: return LanguageParse(AgentIntent.UNKNOWN)
+            ?: return GraphReadGrammarV18.parse(request) ?: LanguageParse(AgentIntent.UNKNOWN)
```

```diff
--- AgentApp.kt
+++ AgentApp.kt
@@
-            RequestViewModel(dev.metis.agent.domain.planner.PlannerAgent(DecisionAgent(promises,
-                repository.decisions), repository.planner), reminderClarification = reminders,
+            RequestViewModel(dev.metis.agent.data.storage.registerGraphReadsV18(
+                dev.metis.agent.domain.planner.PlannerAgent(DecisionAgent(promises,
+                    repository.decisions), repository.planner), repository), reminderClarification = reminders,
```

All other additions are new V18 source/test files and this document. Historical ReadGrammar,
ReadSafety, ReadPatterns, ReadForms and validator files, all existing tests, the protected UI
tests, semantics-tree helper, wait/timeout logic, Phase 15 files and storage/policy/manifest
files remain unchanged.

## Verification

V18 table-driven tests cover accepted/rejected grammar, safe envelopes, injection-like and
overlong inputs, historical read-plan equality, write collisions, forged plans, saved joins,
counts, sorting, empty/unknown/ambiguous/many results, name-choice expiry/session/cancellation,
storage failure, and real RequestViewModel read/write reference follow-ups. The plain-promise
port-contract regression pins the exact historical message and order, including closed rows.
It verifies delegation, not a device/Room execution.

Command: `./gradlew.bat --no-daemon :app:testDebugUnitTest --rerun :app:assembleDebug
:app:lintDebug :app:lintRelease :app:detekt`.

- JVM: 369 methods, 349 passed, 20 historical archive skips, zero failures/errors.
- Existing 60 suites unchanged: 348 methods, 328 passed, 20 archive skips.
- Existing rules/read/candidate/calendar subset: 17 suites, 109 methods, 91 passed,
  18 archive skips, zero failures/errors. This includes the existing V17 validator suites.
- New V18: 21 methods across three suites, all passed; individual methods contain tables.
- Debug build passed. Debug and release lint: zero errors/warnings, 13 dependency-version
  hints each. Detekt: zero findings.
- `git diff --check` passed. Exactly two existing files modified; every other changed
  source/test file is newly added and carries the V18 suffix. No historical grammar,
  validator, existing test or protected/storage/policy/permission file changed.

The final safety review additionally rejects historical memory-topic exceptions (such as
`not swimming after 9pm`) as graph names; a graph request must not treat negation or a date
constraint as part of a project name. Rejection is tested both at validation and at the agent
with a zero-read counter.

## Deferred

- Device UI verification, including tapping graph-name choices, is deferred. JVM tests exercise
  the real ViewModel and existing pending/reference state; they are not emulator evidence.
- No PR or CI run is requested for this branch yet. No merge is performed.
- No fuzzy matching, arbitrary graph traversal, additional filters or inferred text/contact edges.
- The existing repositories enumerate local rows to compute exact counts; result presentation
  is capped at five. A metadata-only storage optimization would require a separately scoped change.
