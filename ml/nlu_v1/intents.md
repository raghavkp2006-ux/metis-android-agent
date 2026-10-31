# METIS Intent Definitions

**Status: DRAFT — label set expanded from 16 → 21 on 2026-10-29.** Definitions are
provisional and derived from pilot utterances. The five new labels
(`CHECK_MEMORY`, `CREATE_TIMER`, `CREATE_ALARM`, `CALL_PERSON`, `SEND_MESSAGE`)
have zero rows in data.csv and test.csv and must be populated before any model
is re-trained.

Pipeline policy: rules run first; ML runs only when rules return UNKNOWN and only
suggests. Rules and policy decide whether to act. The validation-only reject
threshold must yield zero false accepts on forbidden-write intents. No threshold
or hyperparameter is tuned against test data.

---

## Rules Mapping

The following table shows how the fine NLU data labels map to coarser rules-engine
intents and graph-read sources. The data labels are the ground truth for ML training;
the rules-engine column is informational.

| Data label (NLU) | Rules-engine intent | Notes |
|---|---|---|
| RENAME_TASK | UPDATE_TASK | EnglishRules: `rename task: X to: Y` |
| PRIORITIZE_TASK | UPDATE_TASK | EnglishRules: `prioritize task: X to: Y` |
| PERSON_COMMITMENTS | WHAT_DID_I_PROMISE | EnglishRules: `what did i promise <person>` |
| PROJECT_STATUS | graph read (GraphReadGrammarV18) | Graph node query, not a rule pattern |
| PERSON_COMMITMENTS | graph read (GraphReadGrammarV18) | Also reachable via graph reads |
| CALL_PERSON | CallAction (ExternalActions) | Dialer handoff; ML routes to rules for execution |
| SEND_MESSAGE | MessageAction (ExternalActions) | SMS/Composer handoff; ML routes to rules |
| CREATE_TIMER | CREATE_TIMER (ExternalActions) | Clock app handoff |
| CREATE_ALARM | CREATE_ALARM (ExternalActions) | Clock app handoff |

---

## Label Reference (21 labels)

### READ intents

## CHECK_CALENDAR — READ; low risk (4 pilot rows)
Definition: Retrieve the user's calendar schedule or identify conflicts.

- "what does my schedule look like this friday afternoon"
- "chck my calendar for any clashes on wednsday morning"
- "kal ka mera poora schedule kya hai ek baar batao"

## CHECK_TASKS — READ; low risk (3 pilot rows)
Definition: Show or count the user's open tasks, optionally filtered by time.

- "wht tasks do i hve left to finish tonite"
- "aaj shaam ko mere kitne tasks bache hain zara dikhana"
- "um let me see what tasks i still have sitting open for the rest of the week"

## CHECK_MEMORY — READ; low risk (0 rows — NEW)
Definition: Search the user's stored notes or memory graph for information they
previously saved, distinct from querying tasks or calendar.

- *(no pilot rows yet — needs collection)*

Rules mapping: `CHECK_MEMORY` in EnglishRules (`what do you know about`,
`search my memory for`, `what did i write about`).

## PERSON_COMMITMENTS — READ; low risk (4 pilot rows)
Definition: Retrieve commitments the user owes to or made with a specified person.

Rules mapping: `WHAT_DID_I_PROMISE` in EnglishRules; also reachable via graph reads.

- "what commitments do i still owe prof iyer for the robotics grant"
- "wht did i promise rohan i wuld finish this week"
- "arjun ke saath mere kaunse commitments pending pade hain"

## PLAN_DAY — READ; low risk (3 pilot rows)
Definition: Help arrange the user's day around a specified event or schedule constraint.

- "pln my afternoon around the 4 30 pm placement talk"
- "subah 10 baje wale viva ke hisaab se mera din plan kar de"
- "so i have a dentist appointment at 11 am tomorrow um help me plan the rest of my day around it"

## PROJECT_STATUS — READ; low risk (5 pilot rows)
Definition: Retrieve the progress or remaining work for a specified project.

Rules mapping: graph read (GraphReadGrammarV18).

- "how is the autonomous drone project looking right now"
- "whats left pending on the solar inverter projct"
- "smart grid project mein abhi kya kya kaam baaki hai"

## WHAT_SHOULD_I_DO — READ; low risk (41 rows)
Definition: Ask METIS to recommend which task to work on next, typically using available time or priorities.

- "abhi 45 minutes free hoon bata kaunsa task pehle nipatun"

---

### WRITE — medium risk (proposal-then-accept or reversible)

## CREATE_TASK — WRITE; medium risk (9 pilot rows)
Definition: Add a new task to the user's task list, optionally with a deadline.

- "add a new task to submit the vlsi assignment by thursday evening"
- "add passport renewal application to my tasks for this weekend"
- "ad task finish pcb routng layout befre tuesday mrning"

## CREATE_REMINDER — WRITE; medium risk (8 pilot rows)
Definition: Schedule a notification for the user at a specified or implied time, distinct from adding a task. Uses notification system, not clock/alarm app.

- "remind me at 6 30 pm to pick up laundry from the dry cleaner"
- "ping me at 11 am tomorrow to register for the hackathon"
- "remnd me at 4pm to email sneha abt the seminar hall keys"

## CREATE_TIMER — WRITE; medium risk (0 rows — NEW)
Definition: Start a countdown timer for a specified duration. Routes to the platform clock / timer app. Distinct from CREATE_ALARM (which sets a fixed wake time) and CREATE_REMINDER (which uses the notification system).

- *(no pilot rows yet — needs collection)*

Rules mapping: `CREATE_TIMER` in EnglishRules (`set/start a timer for N seconds/minutes`); ExternalActions clock handoff.

## CREATE_ALARM — WRITE; medium risk (0 rows — NEW)
Definition: Set an alarm to fire at a specified wall-clock time (wake-up or nap alarm). Routes to the platform clock app. Distinct from CREATE_REMINDER (notification) and CREATE_TIMER (countdown).

- *(no pilot rows yet — needs collection)*

Rules mapping: ExternalActions clock handoff; no dedicated EnglishRules rule yet (currently handled by context heuristics).

## CREATE_PROMISE — WRITE; medium risk (7 pilot rows)
Definition: Record a commitment the user made to another person, distinct from a task the user intends to do.

- "i promised ananya i would send the sensor calibration csv by 9 pm"
- "i promsed vikram id lend him my oscilloscope probes tmrw morning"
- "maine siddharth ko bola hai ki friday tak backend api deploy kar dunga"

## CALL_PERSON — WRITE; medium risk (0 rows — NEW)
Definition: Initiate a phone call to a named contact. The app opens the platform dialer (DIAL handoff) — it does not place the call directly. Proposal-then-accept: user confirms before dialer opens.

- *(no pilot rows yet — needs collection)*

Rules mapping: `CallAction` in ExternalActions (mode = DIAL).

## SEND_MESSAGE — WRITE; medium risk (0 rows — NEW)
Definition: Compose and send a text message (SMS or messaging app) to a named contact. The app opens the platform composer (COMPOSER handoff) — it does not send the message directly.

- *(no pilot rows yet — needs collection)*

Rules mapping: `MessageAction` in ExternalActions (mode = COMPOSER, scheme = sms/smsto/mailto).

## REFLECTION — WRITE; medium risk (3 pilot rows)
Definition: Record a reflection about the user's productivity, activities, or day.

- "got through all three coding modules today feeling super productive"
- "wasted the whle afternoon scrolling reels didnt touch the compiler code"
- "honestly today was super chaotic i kept getting interrupted and barely touched my thesis chapters"

---

### WRITE — high risk / FORBIDDEN-WRITE (require extra confirmation gate)

## COMPLETE_TASK — WRITE; FORBIDDEN-WRITE (9 pilot rows)
Definition: Mark a specified existing task as completed.

- "i finished the microcontroller datasheet reading so mark it completed"
- "done with the linear algebra problem set finally"
- "mrk the embedded c debugging assignment as finsihed"

## DELETE_TASK — WRITE; FORBIDDEN-WRITE (6 pilot rows)
Definition: Remove a specified existing task from the user's task list.

- "scrap the Goa trip packing task we cancelled the plan"
- "dlete the duplicate badminton court booking task"
- "purana hackathon registration wala task hata de list se"

## POSTPONE_TASK — WRITE; FORBIDDEN-WRITE (7 pilot rows)
Definition: Reschedule an existing task by changing its scheduled date or time.

Rules mapping: `POSTPONE_TASK` in EnglishRules.

- "move the dentist cleanup task from wednesday to next monday"
- "psh the library book return to thursdy afternoon"
- "gym renewal wala task agle hafte monday pe shift kar do"

## PRIORITIZE_TASK — WRITE; FORBIDDEN-WRITE (5 pilot rows)
Definition: Set or change the priority of an existing task.

Rules mapping: `UPDATE_TASK` in EnglishRules (`prioritize task: X to: Y`).

- "make the placement resume review my highest priority for tonight"
- "set priorty high on the gate mock test analysis"
- "tax filing wale task ko sabse top priority pe daal de aaj"

## RENAME_TASK — WRITE; FORBIDDEN-WRITE (6 pilot rows)
Definition: Change the name or title of an existing task.

Rules mapping: `UPDATE_TASK` in EnglishRules (`rename task: X to: Y`).

- "rename the thesis draft task to final ieee paper submission"
- "chnge the name of dsp project task to audio filter design"
- "capstone presentation task ka naam badal ke final demo deck rakh do"

---

### Reject class

## UNKNOWN — negative/reject class (18 pilot rows)
Definition: Requests outside the supported METIS intents or not classifiable as one of them. Used as the OOD / abstain class.

- "book an uber auto to guindy railway station right now"
- "what is the current stock price of tata motors on nse"
- "check if it is going to rain in chennai this evening"
