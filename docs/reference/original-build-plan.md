# Local Autonomous Android Personal Agent
## Complete Build Plan — From Zero to Production

> **Product goal:** Build a private, local-first Android agent that maintains a structured model of the user's digital life, understands supported natural-language commands, monitors permitted device events, plans around goals and constraints, executes authorized Android actions, explains its decisions, and learns from behavioral feedback.

---

# 1. Core Product Principles

The product must follow these rules throughout development:

1. **Local-first by default**
   - Personal data stays on the device unless the user explicitly opts into a future synchronization feature.

2. **Offline-first**
   - Core intelligence must work without internet whenever the underlying Android capability itself can work offline.

3. **No mandatory LLM**
   - Core functionality must work with:
     - deterministic rules
     - classical ML
     - lightweight on-device ML
     - structured storage
     - optimization algorithms

4. **Android-native**
   - Kotlin
   - Jetpack Compose
   - Room / SQLite
   - WorkManager
   - Android platform APIs

5. **Deterministic actions**
   - Every real-world action must pass through typed action objects and policy validation.

6. **Explicit permissions**
   - Permissions must be requested only when needed.
   - The app must never assume a permission exists.

7. **Explainable behavior**
   - Every recommendation and autonomous action must have a reason.

8. **Safe autonomy**
   - Risk level determines whether the system may act automatically or must ask for confirmation.

9. **Replaceable intelligence**
   - ML models, repositories, planners, and integrations must sit behind interfaces so they can be replaced later.

10. **User-controlled autonomy**
    - The user must always be able to reduce or disable proactive behavior.

---

# 2. Unified Interaction Model

The entire product should behave like one agent, not a collection of unrelated tools.

The user should learn one rule:

> **Tell the agent what you need.**

All user interaction enters the same pipeline.

```text
                    USER
                     │
          ┌──────────┴──────────┐
          │                     │
        TEXT                  VOICE
          │                     │
          └──────────┬──────────┘
                     ↓
              UNIVERSAL COMPOSER
                     ↓
                AgentRequest
                     ↓
        ┌───────────────────────────┐
        │     AGENT ORCHESTRATOR    │
        └─────────────┬─────────────┘
                      ↓
              Context Resolution
                      ↓
              Intent Detection
                      ↓
              Entity Extraction
                      ↓
                Policy Engine
                      ↓
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
      Query         Decide         Plan
        │             │             │
        └─────────────┼─────────────┘
                      ↓
                 ActionProposal
                      ↓
              Confirmation?
                /           \
              YES            NO
               ↓              ↓
             User        Action Engine
               ↓              ↓
          Action Engine ←──────┘
               ↓
            VERIFY
               ↓
           ActionResult
               ↓
        Event + Memory Update
               ↓
        Human-readable Response
```

---

# 3. Universal Agent Composer

A universal composer should be available throughout the app.

Example:

```text
┌───────────────────────────────────────┐
│ What do you want to do?          🎙   │
└───────────────────────────────────────┘
```

The same composer should support commands such as:

```text
Remind me at 8.

Call Rahul.

What did I miss?

Plan my evening.

Move my revision session to 7.

What did I promise Rahul?

I have an exam tomorrow.

Show tasks related to DSP.

Why are you recommending this?

I'm overwhelmed with everything today.
```

Voice is only another input mechanism.

```text
Speech
  ↓
Text
  ↓
AgentRequest
```

Do not build a separate voice-agent architecture.

---

# 4. Standard Agent Protocol

Create one internal contract shared by every feature.

## 4.1 AgentRequest

```kotlin
data class AgentRequest(
    val id: String,
    val text: String,
    val source: InputSource,
    val timestamp: Instant,
    val screenContext: ScreenContext?,
    val conversationId: String?
)
```

## 4.2 ParsedRequest

```kotlin
data class ParsedRequest(
    val requestId: String,
    val intent: IntentType,
    val intentConfidence: Float,
    val entities: List<ExtractedEntity>,
    val context: ResolvedContext
)
```

## 4.3 ActionProposal

```kotlin
data class ActionProposal(
    val id: String,
    val type: ActionType,
    val parameters: Map<String, Any>,
    val risk: RiskLevel,
    val reason: String,
    val evidence: List<Evidence>,
    val requiresConfirmation: Boolean
)
```

## 4.4 AgentResult

```kotlin
data class AgentResult(
    val message: String,
    val proposals: List<ActionProposal>,
    val completedActions: List<ActionReceipt>,
    val followUp: FollowUpQuestion?,
    val explanation: Explanation?
)
```

This same protocol should support:

```text
Text UI
Voice UI
Home recommendations
Notifications
Planner
Timeline
Quick actions
Widgets
Future Wear OS
Future Android Auto
```

---

# 5. High-Level Production Architecture

```text
                         ANDROID APP
                             │
                  ┌──────────┴───────────┐
                  │                      │
              PRESENTATION           SYSTEM UI
                  │
             Agent Composer
                  │
                  ▼
            AGENT ORCHESTRATOR
                  │
   ┌──────────────┼────────────────┐
   │              │                │
   ▼              ▼                ▼
LANGUAGE        MEMORY          CONTEXT
ENGINE          ENGINE          ENGINE
   │              │                │
   └──────────────┼────────────────┘
                  ↓
             POLICY ENGINE
                  ↓
            DECISION ENGINE
                  ↓
            PLANNING ENGINE
                  ↓
              ACTION BUS
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
    Android    Internal    Future
     APIs       Actions     APIs
       │
       ▼
 EVENT NORMALIZATION LAYER
       │
       ▼
     EVENT STORE
       │
       ├────→ Memory
       ├────→ Habit Engine
       ├────→ Insights
       └────→ Recommendation Engine

-----------------------------------

LOCAL DATA PLATFORM

Room / SQLite
FTS
DataStore
Encrypted sensitive fields
Keystore
Model files
Audit log
```

---

# 6. Recommended Module Structure

For the first vertical slice, a single app module is acceptable.

For the complete application, move toward:

```text
:app

:core:model
:core:common
:core:database
:core:security
:core:testing
:core:designsystem

:agent:orchestrator
:agent:language
:agent:context
:agent:memory
:agent:policy
:agent:decision
:agent:planning
:agent:autonomy

:actions:core
:actions:alarm
:actions:calendar
:actions:call
:actions:message
:actions:task
:actions:navigation

:integration:contacts
:integration:calendar
:integration:notifications
:integration:voice
:integration:health

:feature:today
:feature:agent
:feature:timeline
:feature:planner
:feature:memory
:feature:insights
:feature:settings

:ml:intent
:ml:emotion
:ml:ranking
:ml:habit
```

Dependency direction:

```text
features
   ↓
agent/domain
   ↓
repositories/interfaces
   ↓
data/integrations
```

UI must never directly depend on Android platform implementation details.

---

# 7. Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| UI foundation | Material 3 with custom design tokens |
| Architecture | Clean Architecture + UDF/MVI-style state |
| State | StateFlow |
| Dependency Injection | Hilt |
| Database | Room |
| Search | SQLite FTS |
| Preferences | DataStore |
| Async | Kotlin Coroutines + Flow |
| Background work | WorkManager |
| Exact alarms | AlarmManager where justified |
| Secure keys | Android Keystore |
| ML | LiteRT/TFLite or ONNX Runtime Mobile |
| Voice | Android SpeechRecognizer |
| TTS | Android TextToSpeech |
| Testing | JUnit + AndroidX + Compose UI Test |
| Build | Gradle Kotlin DSL |
| CI | GitHub Actions |

---

# 8. Core Data Architecture

The local database acts as the user's private personal knowledge platform.

Recommended tables:

| Table | Purpose |
|---|---|
| `user_profile` | Local user configuration |
| `persons` | Known people |
| `relationships` | Relationship metadata |
| `projects` | Collections of work |
| `goals` | Long-term objectives |
| `tasks` | Executable work |
| `events` | Normalized event log |
| `promises` | Commitments to people |
| `habits` | Repeated behavioral patterns |
| `preferences` | Explicit user preferences |
| `memories` | Structured memories |
| `schedule_blocks` | Planned work blocks |
| `routines` | Repeated schedules |
| `action_runs` | Executed actions |
| `action_audit` | Why an action happened |
| `agent_sessions` | Conversation/session metadata |
| `recommendations` | Generated recommendations |
| `experiments` | Personal behavioral experiments |
| `derived_insights` | Recomputable analytics |

Keep factual and derived information separate.

Example:

```text
FACT

User selected:
"I prefer studying in the morning."
```

versus:

```text
DERIVED INSIGHT

Morning study completion rate = 81%
```

---

# 9. Event Store

Everything important should become a normalized event.

```kotlin
data class AgentEvent(
    val id: String,
    val type: EventType,
    val source: EventSource,
    val timestamp: Instant,
    val entityType: EntityType?,
    val entityId: String?,
    val importance: Float,
    val metadata: String
)
```

Example events:

```text
TASK_CREATED
TASK_COMPLETED
TASK_MISSED
TASK_POSTPONED

REMINDER_TRIGGERED

CALENDAR_EVENT_STARTED
CALENDAR_EVENT_ENDED

CALL_STARTED
CALL_COMPLETED

GOAL_CREATED
GOAL_UPDATED

FOCUS_STARTED
FOCUS_COMPLETED

RECOMMENDATION_SHOWN
RECOMMENDATION_ACCEPTED
RECOMMENDATION_REJECTED
```

The event store powers:

```text
Timeline
Analytics
Habit detection
Daily review
Weekly review
Recommendations
Behavior adaptation
```

---

# 10. Memory Architecture

Use multiple logical memory types:

```text
Working Memory
Episodic Memory
Semantic Memory
Procedural Memory
Goal Memory
Relationship Memory
```

All memory access should go through one repository.

```kotlin
interface MemoryRepository {

    suspend fun store(memory: MemoryItem)

    suspend fun search(
        query: MemoryQuery
    ): List<MemoryItem>

    suspend fun update(memory: MemoryItem)

    suspend fun delete(id: String)

    suspend fun memoriesForEntity(
        entityId: String
    ): List<MemoryItem>
}
```

Example initial ranking formula:

```text
Memory Score =

0.30 × relevance
+
0.25 × importance
+
0.20 × recency
+
0.15 × relationship relevance
+
0.10 × confidence
```

Weights must remain configurable.

---

# 11. Search Architecture

Start with:

```text
Room queries
+
Metadata filtering
+
SQLite FTS
+
Recency
+
Importance
+
Relationships
```

Do not start with a vector database.

Add local embeddings only if real evaluation shows that structured retrieval is insufficient.

---

# 12. Language Engine

Pipeline:

```text
Input
 ↓
Normalization
 ↓
Grammar Rules
 ↓
Intent Classifier
 ↓
Entity Extraction
 ↓
Entity Resolution
 ↓
Context Resolution
 ↓
Confidence Evaluation
```

Use a hybrid strategy:

```text
RULES
   +
REGEX
   +
ML
```

ML should not replace deterministic logic that is already reliable.

---

# 13. Initial Intent Taxonomy

## Communication

```text
CALL_PERSON
SEND_MESSAGE
```

## Tasks

```text
CREATE_TASK
COMPLETE_TASK
DELETE_TASK
POSTPONE_TASK
CHECK_TASKS
```

## Time

```text
CREATE_REMINDER
CREATE_ALARM
CREATE_TIMER
```

## Calendar

```text
CREATE_CALENDAR_EVENT
CHECK_CALENDAR
```

## Memory

```text
CHECK_MEMORY
CHECK_PERSON
WHAT_DID_I_PROMISE
```

## Goals

```text
CREATE_GOAL
CHECK_GOALS
CHECK_PROGRESS
```

## Planning

```text
PLAN_DAY
PLAN_WEEK
WHAT_SHOULD_I_DO
```

## Review

```text
DAILY_REVIEW
WEEKLY_REVIEW
WHAT_DID_I_MISS
CATCH_UP
```

## Focus

```text
START_FOCUS
STOP_FOCUS
```

## Navigation

```text
START_NAVIGATION
```

## Habits

```text
CHECK_HABITS
```

## Settings

```text
SET_PREFERENCE
```

## Conversation

```text
START_REFLECTION
EMOTIONAL_SUPPORT
```

Initial target:

```text
25–30 intents
```

---

# 14. Intent Dataset

## Target Size

Prototype:

```text
20–30 intents
100–300 examples per intent
3,000–9,000 examples
```

Later:

```text
500+ examples for high-traffic intents
```

Dataset columns:

```csv
id,
intent,
utterance,
language,
style,
source,
noise_type,
entities,
difficulty,
split
```

Example:

```csv
001,CREATE_REMINDER,
"remind me to call Rahul tomorrow",
en-IN,casual,synthetic,none,
"[PERSON:Rahul][DATE:tomorrow]",
easy,train
```

---

# 15. Dataset Variation Strategy

For every intent, generate:

| Variation | Example |
|---|---|
| Formal | Please remind me to call Rahul tomorrow |
| Normal | Remind me to call Rahul tomorrow |
| Short | Rahul reminder tomorrow |
| Voice-like | Uh remind me tomorrow to call Rahul |
| Indian English | Put one reminder tomorrow for Rahul |
| Hinglish | Kal Rahul ko call karne ka reminder laga |
| Typo | remind me tomorow to call rahul |
| Reordered | Tomorrow remind me about calling Rahul |
| Ambiguous | remind me about Rahul |
| Negative | don't remind me about Rahul |

Negative and ambiguous examples are critical.

---

# 16. Dataset Split

Use:

```text
70% training
15% validation
15% test
```

Split by utterance family.

Do not allow near-duplicate utterances to appear across train and test.

Bad split example:

```text
Train:
remind me to call Rahul tomorrow

Test:
remind me tomorrow to call Rahul
```

This would produce unrealistic evaluation results.

---

# 17. Entity Extraction Dataset

Entities:

```text
PERSON
DATE
TIME
DURATION
LOCATION
TASK
APP
PRIORITY
RELATIONSHIP
QUANTITY
PROJECT
GOAL
```

Recommended manually curated evaluation set:

```text
2,000–4,000 annotated commands
```

Initial implementation should use:

```text
Regex
Date/time parser
Contact lookup
Dictionary matching
Fuzzy matching
```

Train an entity model only if deterministic parsing becomes insufficient.

---

# 18. Intent Classifier

Baseline:

```text
TF-IDF
    ↓
Logistic Regression
```

Alternative:

```text
TF-IDF
    ↓
Linear SVM
```

Only move to:

```text
MobileBERT
MiniLM
Small transformer
```

if evaluation shows a meaningful improvement.

Target:

```text
Macro F1 >= 0.90
```

On-device inference target:

```text
< 100 ms on representative devices
```

---

# 19. Confidence System

Initial thresholds:

```text
>= 0.90
High confidence

0.60–0.90
Confirm or clarify

< 0.60
Ask the user
```

Important rule:

> High intent confidence does not equal permission to perform the action.

Example:

```text
Intent confidence = 99.9%
Intent = SEND_MESSAGE
```

The Policy Engine must still decide whether the action can execute.

---

# 20. Context Engine

The Context Engine should combine:

```text
Current time
Current screen
Current conversation
Recent user actions
Tasks
Calendar
Goals
Preferences
Recent memories
People
Available time
Current location where permitted
Device state where permitted
```

It should output a structured object, not free text.

---

# 21. Decision Engine

Inputs:

```text
Intent
Context
Tasks
Calendar
Goals
Deadlines
User preferences
Behavioral statistics
Current time
Available duration
Action history
```

Output:

```kotlin
data class Decision(
    val action: DecisionType,
    val score: Float,
    val reason: String,
    val evidence: List<Evidence>
)
```

---

# 22. Recommendation Engine

Start with an explainable weighted formula.

```text
score =

deadlineWeight
+ priorityWeight
+ goalWeight
+ overdueWeight
+ importanceWeight
+ preferenceWeight
+ habitSuccessWeight

- effortPenalty
- contextSwitchPenalty
```

Example explanation:

```text
Why this?

• Assignment due tomorrow
• Estimated time: 40 min
• You have 55 min available
• Related to a high-priority goal
```

Every recommendation must store the components used to create it.

---

# 23. Planner

Inputs:

```text
Tasks
Deadlines
Duration
Calendar
Availability
Preferences
Quiet hours
Routines
Priority
Energy preference
Dependencies
```

Output:

```text
08:00–09:00 DSP revision
09:00–09:30 Breakfast
10:00–11:00 Class
11:30–12:15 Assignment
```

Algorithm progression:

```text
V1
Earliest Deadline First

↓

V2
Weighted priority scheduling

↓

V3
Constraint satisfaction

↓

V4
Optimization library if justified
```

Do not start with a heavy optimizer.

---

# 24. Habit Engine

Start with statistical analysis.

Track:

```text
completion_by_hour
completion_by_day
completion_by_duration
postponement_rate
focus_success
streak
average_delay
```

Example:

```text
Study completion

08–11 → 82%
12–15 → 47%
16–19 → 54%
20–23 → 34%
```

Valid recommendation:

> Important study sessions are usually completed more often in the morning.

Avoid unsupported personality claims.

---

# 25. Habit Dataset

Do not use a generic public productivity dataset as a representation of users.

Use:

```text
Synthetic timelines
        ↓
Internal dogfooding
        ↓
Consenting pilot users
        ↓
Anonymized derived statistics
```

Collect behavioral data only with explicit consent.

---

# 26. Emotional Support System

This should be implemented late in development.

Possible research/training sources:

```text
GoEmotions
EmotionLines
DailyDialog
```

Then create a smaller domain-specific validation set.

Possible output:

```text
possible_signal = OVERWHELMED
confidence = 0.67
```

Never treat emotion classification as a diagnosis.

Use a state-machine-based support flow:

```text
EMOTIONAL_ENTRY
       ↓
CLASSIFY_SIGNAL
       ↓
ASK_PREFERENCE
  ┌─────┼─────┐
  ↓     ↓     ↓
TALK  PLAN   BREAK
  ↓     ↓      ↓
SUPPORT ACTION ROUTINE
```

For severe distress, switch to a dedicated safety flow and encourage immediate human support.

---

# 27. Action Engine

All actions should implement a common interface.

```kotlin
interface ActionExecutor<T : Action> {

    suspend fun validate(
        action: T
    ): ValidationResult

    suspend fun execute(
        action: T
    ): ActionResult

    suspend fun undo(
        action: T
    ): UndoResult?
}
```

Example actions:

```text
CreateTaskAction
CompleteTaskAction
CreateReminderAction
CreateAlarmAction
CalendarAction
CallAction
MessageAction
NavigationAction
FocusAction
```

---

# 28. Risk System

## R0 — Read Only

```text
Search
Check tasks
Check calendar
Check memory
```

## R1 — Reversible Local

```text
Create local task
Create note
Create reminder
```

## R2 — External / Communication

```text
Call
Message
Calendar modification
Navigation
```

## R3 — Sensitive / Irreversible

```text
Deleting important information
Public communication
Sensitive data sharing
Financial actions
```

Rule:

```text
R3 should generally never be autonomously executed.
```

Autonomy level never overrides safety risk.

---

# 29. Android Integration Strategy

## Calling

Preferred default:

```text
ACTION_DIAL
```

This lets the user make the final call action.

Use direct calling only when the required permission and product role are justified.

## Messaging

Default:

```text
Agent prepares message
        ↓
Opens messaging composer
        ↓
User sends
```

Do not design v1 around unrestricted direct SMS permissions.

## Contacts

Prefer:

```text
Android Contact Picker
```

when broad contact access is unnecessary.

## Calendar

Use two modes:

```text
Safe / Simple
→ Calendar Intent

Integrated
→ Calendar Provider permission
```

## Background Work

Use WorkManager for:

```text
Daily review
Weekly review
Cleanup
Habit analysis
Non-exact reminders
Model maintenance
```

## Exact Alarms

Only use exact alarms for genuinely exact user-facing timing.

---

# 30. Frontend Design Philosophy

The interface should feel:

> **Calm, intelligent, precise, minimal, human.**

Avoid:

```text
❌ glowing AI orb
❌ purple gradients everywhere
❌ glassmorphism everywhere
❌ cards inside cards inside cards
❌ giant AI labels
❌ unnecessary animation
❌ oversized chat bubbles
❌ decorative analytics
❌ crowded dashboards
```

The design should use:

```text
minimal chrome
clear typography
strong hierarchy
restrained color
system fonts
consistent spacing
accessible contrast
purposeful cards
```

Do not imitate OpenAI branding, logos, or imply that the product is an OpenAI product.

---

# 31. Visual System

## Light Mode

```text
Background       near-white
Surface          white
Primary text     near-black
Secondary text   neutral gray
Divider          soft neutral
```

## Dark Mode

```text
Background       near-black
Surface          elevated charcoal
Primary text     near-white
Secondary text   medium gray
```

Use one brand accent.

Use accent for:

```text
Primary CTA
Selected state
Voice listening
Progress
Important positive state
```

Do not color every surface.

---

# 32. Typography

Use Android/platform-native sans typography.

Suggested hierarchy:

```text
Display        28sp / Medium
Page title     24sp / Medium
Section        18sp / Medium
Body           16sp / Regular
Body small     14sp / Regular
Caption        12sp / Regular
```

Avoid unnecessary custom font stacks.

---

# 33. Spacing System

Base spacing unit:

```text
4dp
```

Use:

```text
4
8
12
16
20
24
32
40
48
```

Suggested screen horizontal padding:

```text
16–20dp
```

Major section gap:

```text
24–32dp
```

---

# 34. Shape System

Suggested corner radii:

```text
Small controls       8dp
Interactive items   12dp
Sheets              20dp
Composer            24dp
```

Cards should represent real interactive objects.

Do not make every section a card.

---

# 35. Motion System

Animations should communicate state.

Use motion for:

```text
Composer expansion
Confirmation completion
Task completion
Voice listening
Planner movement
Undo
```

Avoid decorative looping animations.

---

# 36. Edge-to-Edge

Design for edge-to-edge from the start.

All Compose screens must correctly handle:

```text
status bar
navigation bar
IME
gesture insets
display cutouts
```

Do not patch system insets later.

---

# 37. Main Navigation

Recommended five destinations:

```text
TODAY

PLAN

AGENT

TIMELINE

YOU
```

Meaning:

```text
Today
→ Current day and next important action

Plan
→ Tasks, calendar, goals

Agent
→ Full conversation experience

Timeline
→ Meaningful historical events

You
→ Memory, preferences, insights, settings
```

The universal composer should remain accessible throughout.

---

# 38. Today Screen

Keep it lightweight.

```text
Good morning
Thursday, 1 October

────────────────────────────

Next

DSP revision
45 min
Due tomorrow

[Start]

────────────────────────────

Today

09:00  Class
11:30  Assignment
17:00  Project

────────────────────────────

Needs attention

Assignment due tomorrow

────────────────────────────

Ask the agent...             🎙
```

---

# 39. Agent Screen

Do not make it look like a standard messaging app.

Example:

```text
You
Plan my evening.

Agent

You have 3 hours free after 6 PM.

18:00–18:45
DSP revision

19:00–19:40
Assignment

20:00–20:30
Project review

[Use this plan]    [Adjust]

Why this plan?
```

Interactive objects should appear as structured UI, not only text.

---

# 40. Confirmation Pattern

For meaningful external actions:

```text
Send message

To
Rahul

Message
"I'll reach around 7:30."

────────────────────

[Cancel]              [Continue]
```

Do not hide confirmation inside conversational text.

---

# 41. Action Receipt

After execution:

```text
Reminder created

Tomorrow
8:00 AM

Call Rahul

✓ Saved locally

Undo
```

Build one standard reusable ActionReceipt component.

---

# 42. Explainability UI

Every recommendation should expose:

```text
Why?
```

Example:

```text
Why this task?

Due tomorrow

40-minute estimated duration

You have 55 minutes free

Related to DSP exam goal

Your completion rate is higher before 11 AM
```

---

# 43. Plan Screen

Modes:

```text
DAY
WEEK
GOALS
```

Example day:

```text
08:00
┌───────────────┐
│ DSP revision  │
│ 45 min        │
└───────────────┘

09:00
Class

10:30
Assignment
```

Where appropriate, allow drag-and-drop adjustments.

After a manual move, recalculate affected schedule blocks.

---

# 44. Timeline Screen

Chronological and searchable.

```text
TODAY

18:30
Completed
DSP revision

17:10
Reminder
Assignment due tomorrow

15:00
Postponed
Project meeting prep

12:32
Called Rahul

09:03
Class started
```

Do not expose low-value technical events.

---

# 45. Memory Screen

Sections:

```text
People
Goals
Projects
Preferences
Promises
Habits
```

Search:

```text
Search your memory...
```

Example person page:

```text
Rahul

Project partner

Last interaction
Yesterday

Open promises
Send prototype screenshots

Recent
Called yesterday
Meeting Monday
Shared project task
```

---

# 46. Insights Screen

Only show useful and actionable insights.

Example:

```text
Your best study window

8 AM – 11 AM

Tasks scheduled here were completed
81% of the time.

[Use for important tasks]
```

Avoid dashboard overload.

---

# 47. Voice Mode

Voice must use the same agent pipeline.

```text
Tap microphone
      ↓
Listening
      ↓
Live partial text
      ↓
Final transcription
      ↓
AgentRequest
```

UI:

```text
──────────────────

Listening...

"remind me tomorrow at eight..."

──────────────────

Cancel
```

Raw audio should not be stored by default.

---

# 48. Permissions UX

Never request all permissions during onboarding.

Bad:

```text
Allow contacts
Allow calendar
Allow notifications
Allow microphone
Allow location
Allow calls
```

Correct approach:

```text
User taps microphone
        ↓
Explain microphone purpose
        ↓
Request microphone permission
```

```text
User opens calendar integration
        ↓
Explain calendar purpose
        ↓
Request calendar access
```

```text
User asks to contact Rahul
        ↓
Open contact picker
```

---

# 49. Onboarding

Keep onboarding short.

## Screen 1

```text
Your day.
Organized privately.

A personal agent that plans,
remembers and helps you act.

[Continue]
```

## Screen 2

```text
Your data stays on this device.

No account required.
No cloud required.
You control what the agent can access.

[Continue]
```

## Screen 3

```text
How much should the agent do?

○ Answer only
○ Suggest actions
● Perform simple actions
○ Proactively manage routines

[Continue]
```

## Screen 4

```text
Start with notifications?

[Enable notifications]
[Not now]
```

No unnecessary tutorial sequence.

---

# 50. Autonomy Levels

```text
LEVEL 0
Answer only

LEVEL 1
Suggest actions

LEVEL 2
Execute safe local actions

LEVEL 3
Proactively manage routines

LEVEL 4
Advanced daily management
```

Risk always overrides autonomy.

Example:

```text
Autonomy Level 4
+
Sensitive action
=
Still requires confirmation
```

---

# 51. Security Architecture

Use:

```text
Android Keystore
      ↓
Master key

Database
      ↓
Encrypted sensitive fields

DataStore
      ↓
Protected preferences

Audit Log
      ↓
Every meaningful agent action
```

Never:

```text
Hard-code encryption keys
Log personal data
Store microphone recordings unnecessarily
Store unnecessary message content
Upload private telemetry
```

---

# 52. Privacy Model

Separate:

```text
RAW PERSONAL DATA

DERIVED PERSONAL DATA

ANONYMOUS PRODUCT METRICS
```

Default policy:

```text
RAW
→ Never leaves device

DERIVED
→ Never leaves device

ANONYMOUS METRICS
→ Opt-in
```

Possible dataset contribution flow:

```text
Explicit user opt-in
          ↓
Remove direct identifiers
          ↓
Replace names/entities
          ↓
Strip exact content where unnecessary
          ↓
Aggregate
          ↓
Training / Evaluation
```

---

# 53. Testing Strategy

## Unit Tests

```text
Intent parser
Entity parser
Date parser
Priority score
Memory ranking
Planning
Policy
Risk
```

## Dataset Evaluation

```text
Intent F1
Entity precision
Entity recall
Confusion matrix
Latency
Model size
RAM usage
```

## Integration Tests

```text
Room
WorkManager
AlarmManager
Calendar
Contacts
Notifications
```

## End-to-End Tests

```text
User command
→ Parse
→ Decide
→ Action
→ Verify
→ Event
→ UI
```

## UI Tests

```text
Navigation
Composer
Confirmation
Undo
Permissions
Adaptive layout
Dark mode
Font scaling
```

## Device Tests

At minimum:

```text
Low-memory phone
Mid-range phone
Flagship phone
Small screen
Large screen/tablet
Recent Android version
Oldest supported Android version
```

---

# 54. Performance Budgets

Recommended starting targets:

```text
Cold launch
< ~2 seconds on representative mid-range hardware

Simple local command parse
< 150 ms perceived

Intent model
< 100 ms

DB task query
< 50 ms typical

Memory search
< 150 ms typical

Recommendation generation
< 250 ms typical
```

Never block the UI thread for long-running operations.

---

# 55. Battery Strategy

Avoid:

```text
Continuous polling
Continuous GPS
Permanent foreground service
Always-running microphone
Constant ML inference
```

Prefer:

```text
Events
WorkManager
AlarmManager where justified
System callbacks
Scheduled aggregation
```

---

# 56. Complete Development Sequence

| Phase | Build | Exit Condition |
|---|---|---|
| 0 | Product contract | Requirements frozen |
| 1 | Repository + CI | App builds automatically |
| 2 | Design system | Core UI components established |
| 3 | Navigation shell | Main destinations exist |
| 4 | Database | Schema + migrations work |
| 5 | Memory engine | Local structured memory works |
| 6 | Agent protocol | Unified request/result pipeline works |
| 7 | Language engine | Commands become structured requests |
| 8 | Action engine | Real Android actions work |
| 9 | Event engine | Actions/events create timeline |
| 10 | Decision engine | Agent prioritizes correctly |
| 11 | Planner | “Plan my day” works |
| 12 | Proactive system | Deadline/missed-task detection works |
| 13 | Voice | Speech uses same agent pipeline |
| 14 | Habits/insights | Behavioral feedback works |
| 15 | Advanced ML | Replace rules where justified |
| 16 | Emotional support | Structured support flows |
| 17 | Hardening | Security/performance/battery tests |
| 18 | Pilot | Real user testing |
| 19 | Production | Store-ready build |

---

# 57. Phase 0 — Product Contract

Freeze before major coding begins:

```text
Supported intents
Supported actions
Risk matrix
Permission matrix
Database schema v1
Design tokens
Navigation
AgentRequest contract
Action contract
Event schema
Dataset schema
Definition of Done
```

---

# 58. Phase 1 — Repository and CI

Create:

```text
README.md
ARCHITECTURE.md
PRIVACY.md
SECURITY.md
CONTRIBUTING.md
DATASETS.md
MODEL_CARD.md
```

CI should run:

```text
Compile
Lint
Unit tests
Static analysis
Android tests where possible
```

Use a Gradle version catalog.

---

# 59. Phase 2 — Design System

Build first:

```text
AgentTheme
Typography
Spacing
Shapes
Colors

PrimaryButton
SecondaryButton
IconButton

AgentComposer
AgentMessage
ActionProposal
ActionReceipt
ConfirmationSheet

TaskRow
ScheduleBlock
MemoryRow
TimelineRow

EmptyState
LoadingState
ErrorState
PermissionExplainer
```

Only then build large screens.

---

# 60. Phase 3 — Database

Implement:

```text
Entities
DAOs
Repositories
Migrations
Indexes
FTS
Database tests
Backup/export strategy
```

Create development seed data.

Add a developer-only database inspection interface.

---

# 61. Phase 4 — First Vertical Slice

First major milestone:

```text
"Remind me tomorrow at 8 to call Rahul"

                 ↓
Text input
                 ↓
Intent detection
                 ↓
Entity extraction
                 ↓
Person resolution
                 ↓
Policy
                 ↓
Action proposal
                 ↓
Confirmation if required
                 ↓
Reminder created
                 ↓
Database record
                 ↓
Event
                 ↓
Timeline
                 ↓
Action receipt
```

Do not move forward until this flow is reliable.

---

# 62. Phase 5 — Task Vertical Slice

Implement:

```text
Create
Update
Complete
Delete
Postpone
Search
Recurring tasks
Priority
Deadlines
Projects
Goals
```

Then this must work:

```text
"What tasks do I have?"
```

through the Agent Composer.

---

# 63. Phase 6 — Calendar

Implement:

```text
Check calendar
Create event
Conflict detection
Availability calculation
```

Then support:

```text
"When am I free tomorrow?"
```

---

# 64. Phase 7 — Memory

Support:

```text
"What did I promise Rahul?"

"What do you know about my DSP exam?"

"What project am I doing with Rahul?"
```

All locally.

---

# 65. Phase 8 — Decision Engine

Implement:

```text
"What should I do?"
```

Evaluate:

```text
Urgency
Importance
Available time
Goal relevance
Dependencies
Preferences
Behavioral success
```

---

# 66. Phase 9 — Planner

Implement:

```text
"Plan my evening."
```

Pipeline:

```text
Read calendar
        ↓
Read tasks
        ↓
Read deadlines
        ↓
Calculate free time
        ↓
Score tasks
        ↓
Generate schedule
        ↓
Show proposal
        ↓
User accepts
        ↓
Store plan
        ↓
Create required reminders
```

---

# 67. Phase 10 — Proactive Engine

Triggers:

```text
Deadline approaching
Missed task
Repeated postponement
Schedule conflict
Important promise due
```

Notification priority:

```text
LOW
MEDIUM
IMPORTANT
URGENT
```

Implement rate limiting and suppression logic.

---

# 68. Phase 11 — Reflection

## Daily

```text
Completed
Missed
Postponed
Focus
Progress
Tomorrow
```

## Weekly

```text
What went well
What slipped
Patterns
Suggested adjustment
Next priorities
```

---

# 69. Phase 12 — ML Improvement

Compare every ML solution with a deterministic baseline.

Keep ML only if it meaningfully improves:

```text
Accuracy
Latency
Memory usage
Battery impact
Failure safety
```

Do not add ML for appearance.

---

# 70. Phase 13 — Adaptive Behavior

Feedback signals:

```text
Accepted recommendation
Rejected recommendation
Changed scheduled time
Postponed task
Completed task
Ignored notification
Manually reordered task
```

Do not change behavior after one event.

Require repeated evidence.

---

# 71. Phase 14 — Polished Experience

Refine:

```text
Animation
Empty states
Microcopy
Dark mode
Accessibility
Font scaling
Landscape
Tablet
Loading states
Error recovery
Permission denial
Offline states
```

A polished app must handle failure states well.

---

# 72. Phase 15 — Security Review

Test scenarios:

```text
Ambiguous contact
Incorrect person match
Incorrect date
Duplicate action
Rapid repeated commands
App killed during action
Device restart
Database corruption
Permission revoked
Alarm permission removed
Calendar unavailable
Model fails to load
Malicious notification content
```

Every case must fail safely.

---

# 73. Phase 16 — Pilot

Track:

```text
Command success rate
Clarification rate
Action failure rate
Recommendation acceptance
Planner edits
Notification dismissal rate
Battery consumption
Crash-free sessions
Intent confusion
Device compatibility
```

Never collect private raw content without explicit consent.

---

# 74. Product Metrics

Do not optimize for:

```text
Number of chats
```

Optimize for:

```text
Successful useful outcomes
```

Examples:

```text
User wanted reminder
→ Reminder successfully created

User asked what to do
→ Recommendation selected/completed

User wanted a plan
→ Plan accepted and used
```

---

# 75. Agent Quality Metrics

Track:

```text
Task success rate
Wrong-action rate
Clarification rate
Undo rate
Recommendation acceptance rate
Planning acceptance rate
Missed-action rate
Latency
Battery impact
Crash rate
```

One of the most important metrics is:

```text
WRONG EXTERNAL ACTION RATE
```

Incorrect calls, messages, or calendar modifications are more serious than incorrect text replies.

---

# 76. Complete End-to-End Acceptance Scenario

Primary acceptance scenario:

> **“I have an exam tomorrow. Help me prepare.”**

The system should:

```text
Recognize exam-planning intent
        ↓
Create goal
        ↓
Ask subject/scope
        ↓
Read calendar availability
        ↓
Create study tasks
        ↓
Estimate durations
        ↓
Prioritize
        ↓
Create schedule
        ↓
Show proposal
        ↓
User accepts
        ↓
Create reminders
        ↓
Track sessions
        ↓
Detect missed sessions
        ↓
Reschedule
        ↓
Provide evening review
        ↓
Provide morning brief
        ↓
Answer:

"What have I finished?"
"What am I missing?"
"What should I do now?"
```

If this works reliably, most of the architecture has been proven.

---

# 77. Definition of Complete

The application is complete when this loop works reliably:

```text
             OBSERVE
                ↓
             REMEMBER
                ↓
            UNDERSTAND
                ↓
              DECIDE
                ↓
               PLAN
                ↓
             ASK / ACT
                ↓
              VERIFY
                ↓
              LEARN
                ↓
             OBSERVE
```

Completion is not defined by the number of features.

---

# 78. Features Not to Build Initially

Do not add early:

```text
Firebase dependency
Node backend
Microservices
Kubernetes
Cloud database
Vector database
Huge transformer
Custom speech model
Multi-device sync
Web dashboard
iOS client
Wearable client
Automatic purchases
Financial actions
Unrestricted app control
```

These can distract from proving the core architecture.

---

# 79. Long-Term Architecture

```text
                    PERSONAL AGENT CORE
                           │
           ┌───────────────┼──────────────┐
           │               │              │
        LANGUAGE         MEMORY        AUTONOMY
           │               │              │
           └───────────────┼──────────────┘
                           ↓
                     DECISION
                           ↓
                       PLANNER
                           ↓
                     ACTION BUS
                           │
       ┌───────────────────┼───────────────────┐
       ↓                   ↓                   ↓
     Android            Wear OS            Future API
       │
       ↓
    Local DB
```

Critical rule:

> **The agent core must not know which screen is displaying it.**

This allows the same intelligence layer to support future interfaces.

---

# 80. Final Engineering Rulebook

```text
1. Build Android-native using Kotlin + Compose.

2. Local-first is mandatory.

3. No mandatory backend.

4. No mandatory LLM.

5. UI must never contain business logic.

6. All user interaction enters AgentRequest.

7. Voice and text use the same pipeline.

8. Every action goes through PolicyEngine.

9. Every external action produces an ActionReceipt.

10. Every meaningful action creates an Event.

11. Every autonomous decision must be explainable.

12. Personal data stays on-device by default.

13. Request permissions progressively.

14. Prefer minimum-scope Android APIs.

15. Never assume a permission is available.

16. Never silently guess an ambiguous person, date, or time.

17. High classification confidence does not equal permission to act.

18. ML must beat its deterministic baseline.

19. No unnecessary vector database.

20. No unnecessary cloud infrastructure.

21. UI uses system typography and restrained color.

22. No decorative AI gradients or orbs.

23. Cards are only for real interactive objects.

24. Every screen supports loading, empty, error, and permission-denied states.

25. Every core feature works without network whenever the underlying
    Android capability itself works without network.

26. Database migrations must be tested.

27. Every critical action gets automated tests.

28. Raw microphone audio is not stored by default.

29. Behavioral conclusions require repeated evidence.

30. The user always remains in control of autonomy.
```

---

# 81. Architecture to Freeze Before Development

```text
                         USER
                           │
                  Text / Voice / Tap
                           │
                           ▼
                 UNIVERSAL COMPOSER
                           │
                           ▼
                     AgentRequest
                           │
                           ▼
                  AGENT ORCHESTRATOR
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
       LANGUAGE         CONTEXT          MEMORY
          │                │                │
          └────────────────┼────────────────┘
                           ▼
                     POLICY ENGINE
                           │
                           ▼
                    DECISION ENGINE
                           │
                           ▼
                    PLANNING ENGINE
                           │
                           ▼
                    ACTION PROPOSAL
                           │
                    Confirmation?
                      /         \
                    Yes          No
                     │            │
                     └─────┬──────┘
                           ▼
                      ACTION BUS
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
       Android API      Internal DB      Future APIs
           │
           ▼
                       VERIFY
                           │
                           ▼
                     ACTION RESULT
                           │
                 ┌─────────┼──────────┐
                 ▼         ▼          ▼
               EVENT     MEMORY     RECEIPT
                 │
                 ▼
              TIMELINE
                 │
                 ▼
          HABIT / INSIGHT ENGINE
                 │
                 ▼
            FUTURE DECISIONS
```

---

# 82. Final Development Target

The finished product should not be:

> A chatbot that can set alarms.

It should be:

> **A local autonomous software agent that maintains a structured model of the user's life, observes permitted contextual events, makes explainable decisions, plans around goals and constraints, executes authorized Android actions, learns from behavioral feedback, and proactively helps the user manage their day.**

Core stack:

```text
Kotlin
+
Jetpack Compose
+
Room / SQLite
+
Android APIs
+
WorkManager
+
Rule Engine
+
Classical ML
+
On-device inference
+
Constraint optimization
+
Structured memory
+
Event-driven architecture
+
Secure local storage
```

No mandatory:

```text
Web app
Cloud database
Backend
LLM
Cloud AI
```

The first engineering milestone remains a vertical slice:

```text
"Remind me tomorrow at 8 to call Rahul"
             ↓
Text / Speech
             ↓
Intent Classifier
             ↓
Entity Extractor
             ↓
Contact Resolver
             ↓
Action Validator
             ↓
Local Database
             ↓
Android Reminder
             ↓
Event Recorded
             ↓
Timeline Updated
             ↓
Daily Review
```

Once that pipeline is solid, other capabilities should attach to the same architecture rather than creating new independent systems.
