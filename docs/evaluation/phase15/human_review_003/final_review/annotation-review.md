# Final pre-score annotation review

**Pending human approval. No model inference has occurred.**

Scope: current v5 read-only evaluation, with broader product expectations preserved separately.

Calendar access is unavailable. Actions are outside this read classifier; supported native actions remain separate. Topic/age/subject restrictions that cannot be applied safely require clarification.

Reference: 2026-10-09, Asia/Kolkata. Week starts Monday. Unqualified weekdays mean the next occurrence on or after the reference date; next Friday is 2026-10-16. Day-of-month references use the current reference month. Next two weeks is [2026-10-09, 2026-10-23). June has no inferred year. No conversation/repository identity fixture is supplied.

Extended filter keys preserve intended constraints even where v5 lacks extraction support. These can cause exact-filter failures. They do not authorize broader reads or future product actions. Original expectations below are retained as product requirements, separately from current safe dispositions.

## irs003-1-01

> did i say i need to renew my passport or not

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "renew my passport"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches pending tasks for passport renewal and reports whether it exists, what details were noted, and when it was set.

## irs003-1-02

> remind me to call mom back. she rang twice yesterday

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs a pending task to call mom, notes the context that she rang twice yesterday, and confirms.

## irs003-1-03

> what am i supposed to do this week

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this week", "date_range": {"start": "2026-10-05", "end_exclusive": "2026-10-12"}}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS retrieves all pending tasks with deadlines or dates falling within this week and lists them.

## irs003-1-04

> i think i still owe someone a book. who was it

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches tasks for anything about owing or lending a book and reports back the person and any details.

## irs003-1-05

> kya yaad hai mujhe kya karna tha uss office ka paper ke liye

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "office ka paper"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches pending tasks for anything about an office form or paper and reports what was noted.

## irs003-1-06

> the thing with the gym membership

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "gym membership"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the task, states what it is, and if details are sparse, says so plainly.

## irs003-1-07

> cancel the dentist appointment thing from my list. i already went

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the task, marks it done or removes it, and confirms.

## irs003-1-08

> i need to pay the electricity bill. was it due in march or april

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks if the task exists, notes the uncertainty about the month, and either confirms the date it has or asks the user to clarify.

## irs003-1-09

> what did i write down about the car service

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "car service", "startOffset": 32, "endOffset": 43}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches pending tasks and notes for car service information.

## irs003-1-10

> add: buy tickets for the concert on 14th

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs the task with the date and confirms.

## irs003-1-11

> i keep forgetting to water the plants when i travel

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs this as a pending task or standing note tied to travel, acknowledges the pattern.

## irs003-1-12

> remind me not to forget the gift for tanvi

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs the task, notes the recipient, and confirms.

## irs003-1-13

> is there anything i need to do before friday

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "deadline_before": "friday", "date_resolution": "clarify_current_or_next_friday"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters pending tasks by deadline and lists those falling before Friday.

## irs003-1-14

> the insurance renewal. did i set a reminder for that

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches and reports yes or no, with any details stored.

## irs003-1-15

> i said i would fix the leaky tap. did i do it or not

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "any", "topic": "leaky tap"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks the status of that task and reports.

## irs003-1-16

> put it down somewhere: need to change the oil in the bike

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs it as a pending task and confirms.

## irs003-1-17

> what tasks are older than a month now

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "creation_age_days_gt": 30}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters by creation date and lists stale tasks.

## irs003-1-18

> i have to submit the tax return. was it july 31 or august 15

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS notes the task, flags the date uncertainty, and asks which is correct or notes both.

## irs003-1-19

> remind me to thank whoever sent the flowers

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs it, notes the sender is unspecified, and confirms.

## irs003-1-20

> the thing about the landlord and the deposit

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "landlord and the deposit"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the task and states what was recorded.

## irs003-1-21

> i need to renew my library card. has that been sitting there

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "renew my library card"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS reports the task and its age.

## irs003-1-22

> delete the task about calling the plumber. he fixed it already

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS removes or marks done and confirms.

## irs003-1-23

> what do i still owe people

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{"status": "unresolved"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters tasks by obligation to others and lists them.

## irs003-1-24

> note down: need to book flights for the trip to goa

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs the task and confirms.

## irs003-1-25

> i think i have something pending about the visa application

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "visa application"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches and reports what it finds, including any uncertainty in the user's own memory.

## irs003-2-01

> what did i write about the trip to munnar last year

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "trip to munnar", "startOffset": 27, "endOffset": 41}]`
- Filters: `{"time_expression": "last year"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches memories/notes for Munnar trip content and returns what was saved.

## irs003-2-02

> remind me of the wifi password for the cafe

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "wifi password for the cafe", "startOffset": 17, "endOffset": 43}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for wifi password and returns it if stored.

## irs003-2-03

> i saved something about the recipe for kadhi. can you pull it up

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "recipe for kadhi", "startOffset": 28, "endOffset": 44}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds and displays the recipe note.

## irs003-2-04

> who was the guy at the conference who worked at that AI startup

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "conference who worked at that AI startup", "startOffset": 23, "endOffset": 63}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for conference contacts and reports what it has.

## irs003-2-05

> what did i note about the house i looked at on fridays road

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "house i looked at on fridays road", "startOffset": 26, "endOffset": 59}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches for property notes and returns details.

## irs003-2-06

> the book i was reading. what page was i on

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "book i was reading", "startOffset": 4, "endOffset": 22}]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks if it has a note about the book and page number.

## irs003-2-07

> did i write down what my friend said about her divorce

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "my friend said about her divorce", "startOffset": 22, "endOffset": 54}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns it, handling it with care since it is personal.

## irs003-2-08

> what is the name of the street where my aunty lives

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "my aunty", "startOffset": 37, "endOffset": 45}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for the aunty's address.

## irs003-2-09

> pull up the notes from that meeting with the client

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "meeting with the client", "startOffset": 28, "endOffset": 51}]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds and displays the meeting notes.

## irs003-2-10

> i wrote something about not eating after 9pm. why did i write that

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "not eating after 9pm", "startOffset": 24, "endOffset": 44}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns any surrounding context or reason stored.

## irs003-2-11

> what did i save about the dog's vet

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "dog's vet", "startOffset": 26, "endOffset": 35}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches for vet information and returns it.

## irs003-2-12

> the number for the mechanic. do you have it

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "mechanic", "startOffset": 19, "endOffset": 27}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for the mechanic's number.

## irs003-2-13

> i noted down something about the exam syllabus. can i see it

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "exam syllabus", "startOffset": 33, "endOffset": 46}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds and displays the syllabus notes.

## irs003-2-14

> what did i write about the argument with rohan

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "argument with rohan", "startOffset": 27, "endOffset": 46}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns it, being mindful it may be emotionally loaded.

## irs003-2-15

> the name of the restaurant we liked in bangalore

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "restaurant we liked in bangalore", "startOffset": 16, "endOffset": 48}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for Bangalore restaurant mentions.

## irs003-2-16

> i saved a link somewhere about that course on coursera

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "course on coursera", "startOffset": 36, "endOffset": 54}]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for the link or course name.

## irs003-2-17

> what did i note about the weather in shillong in october

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "weather in shillong in october", "startOffset": 26, "endOffset": 56}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns it.

## irs003-2-18

> the name of the teacher at my niece's school

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "teacher at my niece's school", "startOffset": 16, "endOffset": 44}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for the teacher's name.

## irs003-2-19

> did i write down what the doctor said about the scan results

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "scan results", "startOffset": 48, "endOffset": 60}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns it, noting this is medical information the user should verify with their doctor.

## irs003-2-20

> what did i save about the concert setlist

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "concert setlist", "startOffset": 26, "endOffset": 41}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds and displays the setlist note.

## irs003-2-21

> the password for the cloud drive

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "password for the cloud drive", "startOffset": 4, "endOffset": 32}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for the cloud drive password and returns it if stored.

## irs003-2-22

> what did i write about why i quit the running club

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "why i quit the running club", "startOffset": 23, "endOffset": 50}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds the note and returns the reasoning stored.

## irs003-2-23

> i saved something about the land registry office hours

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "land registry office hours", "startOffset": 28, "endOffset": 54}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds and displays the hours.

## irs003-2-24

> the name of the hotel in manali where we stayed

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "hotel in manali", "startOffset": 16, "endOffset": 31}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS searches notes for Manali hotel information.

## irs003-2-25

> what did i note about the warranty on the washing machine

- Intent: `CHECK_MEMORY`
- Entities: `[{"type": "MEMORY", "rawValue": "warranty on the washing machine", "startOffset": 26, "endOffset": 57}]`
- Filters: `{}`
- Current disposition: `read`
- Broader supplied expectation: METIS finds and returns the warranty note.

## irs003-3-01

> did i promise kavita i would send her the photos

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "kavita", "startOffset": 14, "endOffset": 20}]`
- Filters: `{"subject": "photos"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches commitments and reports whether this promise exists.

## irs003-3-02

> i told arjun i would cover his shift. when was that

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "arjun", "startOffset": 7, "endOffset": 12}]`
- Filters: `{"subject": "shift"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and states when it was made.

## irs003-3-03

> who am i still owing a favour to

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{"status": "unresolved"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters commitments that are not yet fulfilled and lists them.

## irs003-3-04

> i said i would help priya move on sunday. did i follow through

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "priya", "startOffset": 20, "endOffset": 25}]`
- Filters: `{"subject": "move", "status": "any", "time_expression": "sunday"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports whether it is marked done or still open.

## irs003-3-05

> remind me i owe devika a call about the wedding

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs the commitment and confirms.

## irs003-3-06

> what did i promise meera about the birthday party

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "meera", "startOffset": 19, "endOffset": 24}]`
- Filters: `{"subject": "birthday party"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and states what was agreed.

## irs003-3-07

> i told sanjay i would review his manuscript. has that come due

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "sanjay", "startOffset": 7, "endOffset": 13}]`
- Filters: `{"subject": "manuscript"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise, notes any deadline, and reports status.

## irs003-3-08

> who did i say i would buy dinner for

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches commitments for dinner-related promises.

## irs003-3-09

> i promised to teach rohan how to cook. when did i say that

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "rohan", "startOffset": 20, "endOffset": 25}]`
- Filters: `{"subject": "cook"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and states the date or timeframe.

## irs003-3-10

> did i commit to being at the temple on tuesday

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches commitments for the temple visit.

## irs003-3-11

> i said i would lend nikhil my camera. did i give it back

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "nikhil", "startOffset": 20, "endOffset": 26}]`
- Filters: `{"subject": "camera", "status": "any"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the commitment and reports whether the item was returned.

## irs003-3-12

> what did i tell ananya about the job referral

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "ananya", "startOffset": 16, "endOffset": 22}]`
- Filters: `{"subject": "job referral"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and states what was said.

## irs003-3-13

> i owe someone a ride to the airport

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for the commitment, notes the person is unspecified, and asks who it is or logs it vaguely.

## irs003-3-14

> did i promise to be on time for the parent teacher meeting

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports.

## irs003-3-15

> i told vikram i would help him with his resume

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs or confirms the commitment.

## irs003-3-16

> who did i say i would pick up from the station

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches commitments for a station pickup.

## irs003-3-17

> i promised to write a review of the restaurant. did i do it

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports status.

## irs003-3-18

> what did i agree to for the neighbourhood group chat

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for commitments related to a neighbourhood group.

## irs003-3-19

> i said i would proofread kavita's thesis. is that still on

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "kavita", "startOffset": 25, "endOffset": 31}]`
- Filters: `{"subject": "thesis", "status": "any"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports whether it is still open.

## irs003-3-20

> remind me i promised to take my sister to the zoo

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS logs the commitment and confirms.

## irs003-3-21

> did i tell rohan i would pay him back by end of month

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "rohan", "startOffset": 11, "endOffset": 16}]`
- Filters: `{"subject": "pay him back", "time_expression": "end of month"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports the terms.

## irs003-3-22

> i committed to doing one workout per day for a month. am i tracking that

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the commitment and reports progress or lack thereof.

## irs003-3-23

> what did i promise the kids about the weekend

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for promises to kids and lists them.

## irs003-3-24

> i said i would send the contract to legal. did i

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the promise and reports status.

## irs003-3-25

> who am i supposed to thank for the gift

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches commitments for gift-related obligations.

## irs003-4-01

> am i free tomorrow evening

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "tomorrow", "time_of_day": "evening", "date_range": {"start": "2026-10-10", "end_exclusive": "2026-10-11"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS checks the calendar and reports free or booked slots.

## irs003-4-02

> what time is the thing with the doctor

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"topic": "doctor"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS searches calendar for a doctor appointment and states the time.

## irs003-4-03

> can i squeeze in a haircut between meetings thursday

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "thursday", "topic": "meetings", "date_range": {"start": "2026-10-15", "end_exclusive": "2026-10-16"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews Thursday schedule and identifies free windows.

## irs003-4-04

> when is the next time i am completely free

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"availability": "next_open_slot"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS scans the calendar forward and reports the next open slot.

## irs003-4-05

> i have something on the 12th. what is it

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "12th", "date_range": {"start": "2026-10-12", "end_exclusive": "2026-10-13"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS finds the event and states what it is.

## irs003-4-06

> move the standup to 10 instead of 9

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS updates the calendar entry and confirms.

## irs003-4-07

> is saturday clear for a long drive

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "saturday", "date_range": {"start": "2026-10-10", "end_exclusive": "2026-10-11"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reports whether Saturday is free or has events.

## irs003-4-08

> what am i doing this afternoon

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "today", "time_of_day": "afternoon", "date_range": {"start": "2026-10-09", "end_exclusive": "2026-10-10"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS retrieves afternoon appointments and lists them.

## irs003-4-09

> block two hours on wednesday for the project

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS adds a two-hour block and confirms the time chosen.

## irs003-4-10

> did i already book the flight for the 20th

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "20th", "topic": "flight", "date_range": {"start": "2026-10-20", "end_exclusive": "2026-10-21"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS searches calendar for the 20th and reports.

## irs003-4-11

> what is the meeting with the vendor about

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"topic": "meeting with the vendor"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS finds the event and states the subject or notes attached.

## irs003-4-12

> take me off the 9am call. i cannot make it

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS updates the calendar entry and confirms.

## irs003-4-13

> how much free time do i have this week

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "this week", "date_range": {"start": "2026-10-05", "end_exclusive": "2026-10-12"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews the week's calendar and estimates free hours.

## irs003-4-14

> is the yoga class still on for monday

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "monday", "topic": "yoga class", "date_range": {"start": "2026-10-12", "end_exclusive": "2026-10-13"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS finds the event and confirms or reports cancellation.

## irs003-4-15

> put a reminder for the school pickup at 3:30

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS adds the reminder and confirms.

## irs003-4-16

> what did i schedule for next friday

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "next friday", "date_range": {"start": "2026-10-16", "end_exclusive": "2026-10-17"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS retrieves and lists the events.

## irs003-4-17

> i need to be free on the 15th. is there anything clashing

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "15th", "date_range": {"start": "2026-10-15", "end_exclusive": "2026-10-16"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews the 15th and reports any existing events.

## irs003-4-18

> shift the dinner with priya to 8 instead of 7

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS updates the time and confirms.

## irs003-4-19

> am i double booked on tuesday

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "tuesday", "date_range": {"start": "2026-10-13", "end_exclusive": "2026-10-14"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews Tuesday and reports any conflicts.

## irs003-4-20

> what is the event after the lunch with sanjay

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"after_event": "lunch with sanjay"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS locates the lunch and reports what follows.

## irs003-4-21

> clear my calendar for saturday. i am going to the village

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS clears or cancels Saturday entries and confirms.

## irs003-4-22

> is there a gap between the two meetings on thursday

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "thursday", "between_events": "two meetings", "date_range": {"start": "2026-10-15", "end_exclusive": "2026-10-16"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS identifies both meetings and reports the interval.

## irs003-4-23

> add a 30 minute buffer before the interview

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the interview and adds the buffer.

## irs003-4-24

> what is on my calendar for the whole month of june

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "june", "unresolved_year": true}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS retrieves and lists June's events.

## irs003-5-01

> did i NOT promise to call devika

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[{"type": "PERSON", "rawValue": "devika", "startOffset": 26, "endOffset": 32}]`
- Filters: `{"subject": "call devika", "negation_interpretation": "clarify_existence_question_or_denial"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should clarify whether the user is asking if a promise exists or confirming one does not.

## irs003-5-02

> what if i said i would go to the concert

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `reject`
- Broader supplied expectation: METIS should note this is hypothetical and not log it as a commitment unless the user confirms it is real.

## irs003-5-03

> remind me to call mom AND book the flights AND cancel the gym

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should parse all three, confirm each individually, and not merge or drop any.

## irs003-5-04

> i need to fly to mars

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `reject`
- Broader supplied expectation: METIS should decline or flag that it cannot help with interplanetary travel.

## irs003-5-05

> remind me to do the thing

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask what the thing is before logging anything.

## irs003-5-06

> what did i say about the thing on tuesday

- Intent: `CHECK_MEMORY`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask what topic and which tuesday.

## irs003-5-07

> i am free on monday AND i am not free on monday

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should point out the contradiction and ask which is correct.

## irs003-5-08

> someone told me to remind you that i said i would never use this app

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `reject`
- Broader supplied expectation: METIS should clarify who said what and whether this is a real instruction or just conversation.

## irs003-5-09

> cancel everything except what i cancelled

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask what specifically to cancel since the instruction is circular.

## irs003-5-10

> what is my schedule for the day after the day after tomorrow

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "day after the day after tomorrow", "date_range": {"start": "2026-10-12", "end_exclusive": "2026-10-13"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS should interpret this as three days from now and check the calendar, or ask for confirmation.

## irs003-5-11

> i want to remember to forget about the trip

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask whether they want to delete the trip note or keep it.

## irs003-5-12

> remind me about the thing kavita mentioned at the place where we met the person who was talking about the movie

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask for clarification on what specifically Kavita mentioned.

## irs003-5-13

> not the dentist. the other one. the teeth one

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask what "the teeth one" refers to specifically.

## irs003-5-14

> i said i would NOT do the homework

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should clarify whether they want to record that they declined or whether they are retracting a previous promise.

## irs003-5-15

> what is on my calendar for a date that has not happened yet

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask which future date they mean.

## irs003-5-16

> remind me to do what i said i would not do

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask what action they are referring to.

## irs003-5-17

> i am available every day except the days i am not

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `reject`
- Broader supplied expectation: METIS should note this gives no actionable availability and ask for specific days.

## irs003-5-18

> someone quoted me saying i would help. was that me

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should search for the commitment and report what it has, noting the user's own uncertainty.

## irs003-5-19

> delete my notes but keep the ones about deleting my notes

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should clarify what they actually want to delete.

## irs003-5-20

> is it possible that i am free on a day that does not exist

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should gently note that no such day exists and ask what they actually need.

## irs003-5-21

> i want to be reminded of something i never told you

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `reject`
- Broader supplied expectation: METIS should explain it only remembers what was told to it and ask if they want to save something new.

## irs003-5-22

> what did i promise to do on the day after i stopped promising things

- Intent: `WHAT_DID_I_PROMISE`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask for a specific date or context.

## irs003-5-23

> remind me to not remind me about the thing

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should ask whether they want to suppress a reminder or are joking.

## irs003-5-24

> i have a meeting at noon AND i have a meeting at midnight on the same day

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should confirm both times or note the unusual spread and ask if both are correct.

## irs003-5-25

> what is my availability on the day between yesterday and tomorrow

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "today", "date_range": {"start": "2026-10-09", "end_exclusive": "2026-10-10"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS should interpret as today and check the calendar, or confirm the interpretation.

## irs003-5-26

> someone said i owe them. someone else said i do not. who is right

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS should note it has no way to adjudicate and ask for names or specifics to check against stored commitments.

## irs003-add-tasks-01

> is the thing about the gas cylinder still on my list

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "gas cylinder"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches pending tasks for gas cylinder and reports whether it is there and what was noted.

## irs003-add-tasks-02

> how many stuffs am i supposed to do this month

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this month", "date_range": {"start": "2026-10-01", "end_exclusive": "2026-11-01"}, "aggregation": "count_and_list"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters tasks by current month and gives a count plus a quick list.

## irs003-add-tasks-03

> i think i crossed off the visa thing but i cant find it

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "any", "topic": "visa"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks the task list and completed items for a visa entry and reports its status.

## irs003-add-tasks-04

> what is the most urgent thing on my list right now

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "order_by": "deadline_urgency"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS reviews pending tasks, considers deadlines, and names the most time-sensitive one.

## irs003-add-tasks-05

> did i ever write down that i need to get the bike insured

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "bike insured"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches and reports whether it exists, without assuming.

## irs003-add-tasks-06

> the renewal for the driving licence. is that still pending or did i do it

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "any", "topic": "driving licence"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS finds the task and reports whether it is open or marked done.

## irs003-add-tasks-07

> pull up whatever i have saved about the flat rent

- Intent: `UNKNOWN`
- Entities: `[]`
- Filters: `{}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches both pending tasks and saved notes for rent-related entries and shows what it finds.

## irs003-add-tasks-08

> is there anything about the school fee on my to do

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "school fee"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for school fee tasks and reports.

## irs003-add-tasks-09

> i have like seventeen things to do and i cant remember any of them

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete"}`
- Current disposition: `read`
- Broader supplied expectation: METIS lists all pending tasks without editorialising.

## irs003-add-tasks-10

> what was that thing about the water purifier filter

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "water purifier filter"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for purifier or filter tasks and states what was noted.

## irs003-add-tasks-11

> check if the gym payment task is still there. i think i paid already

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "any", "topic": "gym payment"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks and reports, noting the user believes it is done.

## irs003-add-tasks-12

> kya kuch pending hai mere paas is mahine ke liye

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this month", "date_range": {"start": "2026-10-01", "end_exclusive": "2026-11-01"}}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS interprets the Hindi, filters tasks by month, and lists them.

## irs003-add-tasks-13

> the thing with the electricity connection. was it a new meter or the old one

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "electricity connection"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS searches for electricity-related tasks and reports what it has, flagging the ambiguity if both exist.

## irs003-add-tasks-14

> remind me what i need to do before the trip to kerala

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "trip to kerala"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS filters tasks by trip context and lists them.

## irs003-add-tasks-15

> is the passport thing still there or did i already do it and forget

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "any", "topic": "passport"}`
- Current disposition: `clarify`
- Broader supplied expectation: METIS checks status and reports plainly.

## irs003-add-calendar-01

> is anything on for this weekend

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "this weekend", "date_range": {"start": "2026-10-10", "end_exclusive": "2026-10-12"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews both days and lists anything scheduled or says they are clear.

## irs003-add-calendar-02

> what time does the thing with the school start

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"topic": "school"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS searches the calendar for a school event and states the time.

## irs003-add-calendar-03

> am i booked up on wednesday morning

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "wednesday morning", "date_range": {"start": "2026-10-14", "end_exclusive": "2026-10-15"}, "time_of_day": "morning"}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS reviews Wednesday morning slots and reports free or occupied.

## irs003-add-calendar-04

> what is coming up in the next two weeks

- Intent: `CHECK_CALENDAR`
- Entities: `[]`
- Filters: `{"time_expression": "next two weeks", "date_range": {"start": "2026-10-09", "end_exclusive": "2026-10-23"}}`
- Current disposition: `unsupported`
- Broader supplied expectation: METIS lists all events in that window, grouped by day.

## irs003-add-tasks-16

> is the thing about cancelling the newspaper still on my list

- Intent: `CHECK_TASKS`
- Entities: `[]`
- Filters: `{"status": "incomplete", "topic": "cancelling the newspaper"}`
- Current disposition: `clarify`
- Broader supplied expectation: Check whether the pending newspaper-cancellation task exists; do not cancel a subscription
