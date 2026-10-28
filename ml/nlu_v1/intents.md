# METIS Intent Definitions

**SMOKE TEST - NOT VALID FOR ADOPTION.** Definitions are provisional and derived from the `intent` labels and supported pilot utterances. Every example below is verbatim from a CSV row with `supported_now == yes`. `WHAT_SHOULD_I_DO` is excluded from the configured label set because it has one row. All figures are pilot, not valid for adoption.

Pipeline policy: rules run first; ML runs only when rules return UNKNOWN and only suggests. Rules and policy decide whether to act. The validation-only reject threshold must yield zero false accepts on forbidden-write intents. The approved smoke-test configuration includes labels with at least 3 rows. No threshold or hyperparameter is tuned against test data.

## CHECK_CALENDAR — READ (4 rows)
Definition: Retrieve the user's calendar schedule or identify conflicts.

- "what does my schedule look like this friday afternoon"
- "chck my calendar for any clashes on wednsday morning"
- "kal ka mera poora schedule kya hai ek baar batao"

## CHECK_TASKS — READ (3 rows)
Definition: Show or count the user's open tasks, optionally filtered by time.

- "wht tasks do i hve left to finish tonite"
- "aaj shaam ko mere kitne tasks bache hain zara dikhana"
- "um let me see what tasks i still have sitting open for the rest of the week"

## COMPLETE_TASK — WRITE; FORBIDDEN-WRITE (9 rows)
Definition: Mark a specified existing task as completed.

- "i finished the microcontroller datasheet reading so mark it completed"
- "done with the linear algebra problem set finally"
- "mrk the embedded c debugging assignment as finsihed"

## CREATE_PROMISE — WRITE (7 rows)
Definition: Record a commitment the user made to another person, distinct from a task the user intends to do.

- "i promised ananya i would send the sensor calibration csv by 9 pm"
- "i promsed vikram id lend him my oscilloscope probes tmrw morning"
- "maine siddharth ko bola hai ki friday tak backend api deploy kar dunga"

## CREATE_REMINDER — WRITE (8 rows)
Definition: Schedule a notification for the user at a specified or implied time, distinct from adding a task.

- "remind me at 6 30 pm to pick up laundry from the dry cleaner"
- "ping me at 11 am tomorrow to register for the hackathon"
- "remnd me at 4pm to email sneha abt the seminar hall keys"

## CREATE_TASK — WRITE (9 rows)
Definition: Add a new task to the user's task list, optionally with a deadline.

- "add a new task to submit the vlsi assignment by thursday evening"
- "add passport renewal application to my tasks for this weekend"
- "ad task finish pcb routng layout befre tuesday mrning"

## DELETE_TASK — WRITE; FORBIDDEN-WRITE (6 rows)
Definition: Remove a specified existing task from the user's task list.

- "scrap the Goa trip packing task we cancelled the plan"
- "dlete the duplicate badminton court booking task"
- "purana hackathon registration wala task hata de list se"

## PERSON_COMMITMENTS — READ (4 rows)
Definition: Retrieve commitments the user owes to or made with a specified person.

- "what commitments do i still owe prof iyer for the robotics grant"
- "wht did i promise rohan i wuld finish this week"
- "arjun ke saath mere kaunse commitments pending pade hain"

## PLAN_DAY — READ (3 rows)
Definition: Help arrange the user's day around a specified event or schedule constraint.

- "pln my afternoon around the 4 30 pm placement talk"
- "subah 10 baje wale viva ke hisaab se mera din plan kar de"
- "so i have a dentist appointment at 11 am tomorrow um help me plan the rest of my day around it"

## POSTPONE_TASK — WRITE; FORBIDDEN-WRITE (7 rows)
Definition: Reschedule an existing task by changing its scheduled date or time.

- "move the dentist cleanup task from wednesday to next monday"
- "psh the library book return to thursdy afternoon"
- "gym renewal wala task agle hafte monday pe shift kar do"

## PRIORITIZE_TASK — WRITE; FORBIDDEN-WRITE (5 rows)
Definition: Set or change the priority of an existing task.

- "make the placement resume review my highest priority for tonight"
- "set priorty high on the gate mock test analysis"
- "tax filing wale task ko sabse top priority pe daal de aaj"

## PROJECT_STATUS — READ (5 rows)
Definition: Retrieve the progress or remaining work for a specified project.

- "how is the autonomous drone project looking right now"
- "whats left pending on the solar inverter projct"
- "smart grid project mein abhi kya kya kaam baaki hai"

## REFLECTION — WRITE (3 rows)
Definition: Record a reflection about the user's productivity, activities, or day.

- "got through all three coding modules today feeling super productive"
- "wasted the whle afternoon scrolling reels didnt touch the compiler code"
- "honestly today was super chaotic i kept getting interrupted and barely touched my thesis chapters"

## RENAME_TASK — WRITE; FORBIDDEN-WRITE (6 rows)
Definition: Change the name or title of an existing task.

- "rename the thesis draft task to final ieee paper submission"
- "chnge the name of dsp project task to audio filter design"
- "capstone presentation task ka naam badal ke final demo deck rakh do"

## UNKNOWN — READ/WRITE MIXED; negative/reject class (18 rows)
Definition: Requests outside the supported METIS intents or not classifiable as one of them.

- "book an uber auto to guindy railway station right now"
- "what is the current stock price of tata motors on nse"
- "check if it is going to rain in chennai this evening"

## WHAT_SHOULD_I_DO — READ; excluded from label set (1 row)
Definition: Ask METIS to recommend which task to work on next, typically using available time or priorities.

- "abhi 45 minutes free hoon bata kaunsa task pehle nipatun"
