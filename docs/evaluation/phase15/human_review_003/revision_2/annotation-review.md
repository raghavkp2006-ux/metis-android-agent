# Annotation proposals for human review

**145 rows; draft annotations, not frozen truth.** Reference date: 2026-10-09 in Asia/Kolkata. Original expected responses remain separate from proposed current-capability dispositions.

## irs003-1-01

> did i say i need to renew my passport or not

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "renew my passport"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches pending tasks for passport renewal and reports whether it exists, what details were noted, and when it was set.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-02

> remind me to call mom back. she rang twice yesterday

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs a pending task to call mom, notes the context that she rang twice yesterday, and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-03

> what am i supposed to do this week

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this week", "date_range": {"start": "2026-10-05", "end_exclusive": "2026-10-12"}}`
- Proposed current disposition: `clarify`
- Original expectation: METIS retrieves all pending tasks with deadlines or dates falling within this week and lists them.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-04

> i think i still owe someone a book. who was it

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches tasks for anything about owing or lending a book and reports back the person and any details.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-1-05

> kya yaad hai mujhe kya karna tha uss office ka paper ke liye

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "office"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches pending tasks for anything about an office form or paper and reports what was noted.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-06

> the thing with the gym membership

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "gym membership"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the task, states what it is, and if details are sparse, says so plainly.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-07

> cancel the dentist appointment thing from my list. i already went

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the task, marks it done or removes it, and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-08

> i need to pay the electricity bill. was it due in march or april

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks if the task exists, notes the uncertainty about the month, and either confirms the date it has or asks the user to clarify.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-09

> what did i write down about the car service

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "car service", "startOffset": 32, "endOffset": 43}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches pending tasks and notes for car service information.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-1-10

> add: buy tickets for the concert on 14th

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs the task with the date and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-11

> i keep forgetting to water the plants when i travel

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs this as a pending task or standing note tied to travel, acknowledges the pattern.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-12

> remind me not to forget the gift for tanvi

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs the task, notes the recipient, and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-13

> is there anything i need to do before friday

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "deadline_before": "friday"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters pending tasks by deadline and lists those falling before Friday.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-14

> the insurance renewal. did i set a reminder for that

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches and reports yes or no, with any details stored.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-15

> i said i would fix the leaky tap. did i do it or not

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "any", "topic": "leaky tap"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks the status of that task and reports.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-16

> put it down somewhere: need to change the oil in the bike

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs it as a pending task and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-17

> what tasks are older than a month now

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "creation_age_days_gt": 30}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters by creation date and lists stale tasks.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-18

> i have to submit the tax return. was it july 31 or august 15

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS notes the task, flags the date uncertainty, and asks which is correct or notes both.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-19

> remind me to thank whoever sent the flowers

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs it, notes the sender is unspecified, and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-20

> the thing about the landlord and the deposit

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "landlord and the deposit"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the task and states what was recorded.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-21

> i need to renew my library card. has that been sitting there

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "renew my library card"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS reports the task and its age.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-1-22

> delete the task about calling the plumber. he fixed it already

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS removes or marks done and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-23

> what do i still owe people

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{"status": "unresolved"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters tasks by obligation to others and lists them.
- Review rationale: All-person unresolved commitments are outside the person-only read port

## irs003-1-24

> note down: need to book flights for the trip to goa

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs the task and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-1-25

> i think i have something pending about the visa application

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "visa application"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches and reports what it finds, including any uncertainty in the user's own memory.
- Review rationale: Requested topic/date/age/status lookup exceeds current task read port

## irs003-2-01

> what did i write about the trip to munnar last year

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "trip to munnar", "startOffset": 27, "endOffset": 41}]`
- Filters: `{"time_expression": "last year"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches memories/notes for Munnar trip content and returns what was saved.
- Review rationale: Historical time restriction is not supported by current read port

## irs003-2-02

> remind me of the wifi password for the cafe

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "wifi password for the cafe", "startOffset": 17, "endOffset": 43}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for wifi password and returns it if stored.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-03

> i saved something about the recipe for kadhi. can you pull it up

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "recipe for kadhi", "startOffset": 28, "endOffset": 44}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds and displays the recipe note.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-04

> who was the guy at the conference who worked at that AI startup

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "conference", "startOffset": 23, "endOffset": 33}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for conference contacts and reports what it has.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-05

> what did i note about the house i looked at on fridays road

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "house i looked at on fridays road", "startOffset": 26, "endOffset": 59}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches for property notes and returns details.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-06

> the book i was reading. what page was i on

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "book i was reading", "startOffset": 4, "endOffset": 22}]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks if it has a note about the book and page number.
- Review rationale: Specific book/meeting/course is unresolved without conversation context

## irs003-2-07

> did i write down what my friend said about her divorce

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "my friend said about her divorce", "startOffset": 22, "endOffset": 54}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns it, handling it with care since it is personal.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-08

> what is the name of the street where my aunty lives

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "my aunty", "startOffset": 37, "endOffset": 45}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for the aunty's address.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-09

> pull up the notes from that meeting with the client

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "meeting with the client", "startOffset": 28, "endOffset": 51}]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds and displays the meeting notes.
- Review rationale: Specific book/meeting/course is unresolved without conversation context

## irs003-2-10

> i wrote something about not eating after 9pm. why did i write that

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "not eating after 9pm", "startOffset": 24, "endOffset": 44}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns any surrounding context or reason stored.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-11

> what did i save about the dog's vet

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "dog's vet", "startOffset": 26, "endOffset": 35}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches for vet information and returns it.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-12

> the number for the mechanic. do you have it

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "mechanic", "startOffset": 19, "endOffset": 27}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for the mechanic's number.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-13

> i noted down something about the exam syllabus. can i see it

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "exam syllabus", "startOffset": 33, "endOffset": 46}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds and displays the syllabus notes.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-14

> what did i write about the argument with rohan

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "argument with rohan", "startOffset": 27, "endOffset": 46}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns it, being mindful it may be emotionally loaded.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-15

> the name of the restaurant we liked in bangalore

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "restaurant we liked in bangalore", "startOffset": 16, "endOffset": 48}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for Bangalore restaurant mentions.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-16

> i saved a link somewhere about that course on coursera

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "course on coursera", "startOffset": 36, "endOffset": 54}]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for the link or course name.
- Review rationale: Specific book/meeting/course is unresolved without conversation context

## irs003-2-17

> what did i note about the weather in shillong in october

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "weather in shillong in october", "startOffset": 26, "endOffset": 56}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns it.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-18

> the name of the teacher at my niece's school

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "teacher at my niece's school", "startOffset": 16, "endOffset": 44}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for the teacher's name.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-19

> did i write down what the doctor said about the scan results

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "scan results", "startOffset": 48, "endOffset": 60}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns it, noting this is medical information the user should verify with their doctor.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-20

> what did i save about the concert setlist

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "concert setlist", "startOffset": 26, "endOffset": 41}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds and displays the setlist note.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-21

> the password for the cloud drive

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "password for the cloud drive", "startOffset": 4, "endOffset": 32}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for the cloud drive password and returns it if stored.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-22

> what did i write about why i quit the running club

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "why i quit the running club", "startOffset": 23, "endOffset": 50}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds the note and returns the reasoning stored.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-23

> i saved something about the land registry office hours

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "land registry office hours", "startOffset": 28, "endOffset": 54}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds and displays the hours.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-24

> the name of the hotel in manali where we stayed

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "hotel in manali", "startOffset": 16, "endOffset": 31}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS searches notes for Manali hotel information.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-2-25

> what did i note about the warranty on the washing machine

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[{"type": "MEMORY", "rawValue": "warranty on the washing machine", "startOffset": 26, "endOffset": 57}]`
- Filters: `{}`
- Proposed current disposition: `read`
- Original expectation: METIS finds and returns the warranty note.
- Review rationale: Search stored notes by literal topic; do not infer unstored facts

## irs003-3-01

> did i promise kavita i would send her the photos

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "kavita", "startOffset": 14, "endOffset": 20}]`
- Filters: `{"subject": "photos"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches commitments and reports whether this promise exists.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-02

> i told arjun i would cover his shift. when was that

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "arjun", "startOffset": 7, "endOffset": 12}]`
- Filters: `{"subject": "shift"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and states when it was made.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-03

> who am i still owing a favour to

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{"status": "unresolved"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters commitments that are not yet fulfilled and lists them.
- Review rationale: All-person unresolved commitments are outside the person-only read port

## irs003-3-04

> i said i would help priya move on sunday. did i follow through

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "priya", "startOffset": 20, "endOffset": 25}]`
- Filters: `{"subject": "move", "status": "any", "time_expression": "sunday"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports whether it is marked done or still open.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-05

> remind me i owe devika a call about the wedding

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs the commitment and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-3-06

> what did i promise meera about the birthday party

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "meera", "startOffset": 19, "endOffset": 24}]`
- Filters: `{"subject": "birthday party"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and states what was agreed.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-07

> i told sanjay i would review his manuscript. has that come due

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "sanjay", "startOffset": 7, "endOffset": 13}]`
- Filters: `{"subject": "manuscript"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise, notes any deadline, and reports status.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-08

> who did i say i would buy dinner for

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches commitments for dinner-related promises.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-09

> i promised to teach rohan how to cook. when did i say that

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "rohan", "startOffset": 20, "endOffset": 25}]`
- Filters: `{"subject": "cook"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and states the date or timeframe.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-10

> did i commit to being at the temple on tuesday

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches commitments for the temple visit.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-11

> i said i would lend nikhil my camera. did i give it back

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "nikhil", "startOffset": 20, "endOffset": 26}]`
- Filters: `{"subject": "camera", "status": "any"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the commitment and reports whether the item was returned.
- Review rationale: Lender/returner role is ambiguous; approve intended meaning before status lookup

## irs003-3-12

> what did i tell ananya about the job referral

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "ananya", "startOffset": 16, "endOffset": 22}]`
- Filters: `{"subject": "job referral"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and states what was said.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-13

> i owe someone a ride to the airport

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for the commitment, notes the person is unspecified, and asks who it is or logs it vaguely.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-3-14

> did i promise to be on time for the parent teacher meeting

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-15

> i told vikram i would help him with his resume

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs or confirms the commitment.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-3-16

> who did i say i would pick up from the station

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches commitments for a station pickup.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-17

> i promised to write a review of the restaurant. did i do it

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports status.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-18

> what did i agree to for the neighbourhood group chat

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for commitments related to a neighbourhood group.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-19

> i said i would proofread kavita's thesis. is that still on

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "kavita", "startOffset": 25, "endOffset": 31}]`
- Filters: `{"subject": "thesis", "status": "any"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports whether it is still open.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-20

> remind me i promised to take my sister to the zoo

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS logs the commitment and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-3-21

> did i tell rohan i would pay him back by end of month

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "rohan", "startOffset": 11, "endOffset": 16}]`
- Filters: `{"subject": "pay him back", "time_expression": "end of month"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports the terms.
- Review rationale: Subject/status restriction requires a richer commitment read port

## irs003-3-22

> i committed to doing one workout per day for a month. am i tracking that

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the commitment and reports progress or lack thereof.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-23

> what did i promise the kids about the weekend

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for promises to kids and lists them.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-24

> i said i would send the contract to legal. did i

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the promise and reports status.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-3-25

> who am i supposed to thank for the gift

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches commitments for gift-related obligations.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-4-01

> am i free tomorrow evening

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS checks the calendar and reports free or booked slots.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-02

> what time is the thing with the doctor

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS searches calendar for a doctor appointment and states the time.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-03

> can i squeeze in a haircut between meetings thursday

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews Thursday schedule and identifies free windows.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-04

> when is the next time i am completely free

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS scans the calendar forward and reports the next open slot.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-05

> i have something on the 12th. what is it

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS finds the event and states what it is.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-06

> move the standup to 10 instead of 9

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS updates the calendar entry and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-07

> is saturday clear for a long drive

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reports whether Saturday is free or has events.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-08

> what am i doing this afternoon

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS retrieves afternoon appointments and lists them.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-09

> block two hours on wednesday for the project

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS adds a two-hour block and confirms the time chosen.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-10

> did i already book the flight for the 20th

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS searches calendar for the 20th and reports.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-11

> what is the meeting with the vendor about

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS finds the event and states the subject or notes attached.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-12

> take me off the 9am call. i cannot make it

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS updates the calendar entry and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-13

> how much free time do i have this week

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews the week's calendar and estimates free hours.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-14

> is the yoga class still on for monday

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS finds the event and confirms or reports cancellation.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-15

> put a reminder for the school pickup at 3:30

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS adds the reminder and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-16

> what did i schedule for next friday

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS retrieves and lists the events.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-17

> i need to be free on the 15th. is there anything clashing

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews the 15th and reports any existing events.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-18

> shift the dinner with priya to 8 instead of 7

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS updates the time and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-19

> am i double booked on tuesday

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews Tuesday and reports any conflicts.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-20

> what is the event after the lunch with sanjay

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS locates the lunch and reports what follows.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-21

> clear my calendar for saturday. i am going to the village

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS clears or cancels Saturday entries and confirms.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-22

> is there a gap between the two meetings on thursday

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS identifies both meetings and reports the interval.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-4-23

> add a 30 minute buffer before the interview

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the interview and adds the buffer.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-4-24

> what is on my calendar for the whole month of june

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS retrieves and lists June's events.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-5-01

> did i NOT promise to call devika

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[{"type": "PERSON", "rawValue": "devika", "startOffset": 26, "endOffset": 32}]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should clarify whether the user is asking if a promise exists or confirming one does not.
- Review rationale: Reviewer must resolve whether NOT is an ordinary existence question or intended clarification

## irs003-5-02

> what if i said i would go to the concert

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `reject`
- Original expectation: METIS should note this is hypothetical and not log it as a commitment unless the user confirms it is real.
- Review rationale: Hypothetical, unsupported request or non-actionable statement; no read or mutation

## irs003-5-03

> remind me to call mom AND book the flights AND cancel the gym

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should parse all three, confirm each individually, and not merge or drop any.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-04

> i need to fly to mars

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `reject`
- Original expectation: METIS should decline or flag that it cannot help with interplanetary travel.
- Review rationale: Hypothetical, unsupported request or non-actionable statement; no read or mutation

## irs003-5-05

> remind me to do the thing

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask what the thing is before logging anything.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-06

> what did i say about the thing on tuesday

- Draft intent: `CHECK_MEMORY`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask what topic and which tuesday.
- Review rationale: Missing topic/context

## irs003-5-07

> i am free on monday AND i am not free on monday

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should point out the contradiction and ask which is correct.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-08

> someone told me to remind you that i said i would never use this app

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `reject`
- Original expectation: METIS should clarify who said what and whether this is a real instruction or just conversation.
- Review rationale: Hypothetical, unsupported request or non-actionable statement; no read or mutation

## irs003-5-09

> cancel everything except what i cancelled

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask what specifically to cancel since the instruction is circular.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-10

> what is my schedule for the day after the day after tomorrow

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"time_expression": "day after the day after tomorrow", "date_range": {"start": "2026-10-12", "end_exclusive": "2026-10-13"}}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS should interpret this as three days from now and check the calendar, or ask for confirmation.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-5-11

> i want to remember to forget about the trip

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask whether they want to delete the trip note or keep it.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-12

> remind me about the thing kavita mentioned at the place where we met the person who was talking about the movie

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask for clarification on what specifically Kavita mentioned.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-13

> not the dentist. the other one. the teeth one

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask what "the teeth one" refers to specifically.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-14

> i said i would NOT do the homework

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should clarify whether they want to record that they declined or whether they are retracting a previous promise.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-15

> what is on my calendar for a date that has not happened yet

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask which future date they mean.
- Review rationale: Date is missing or invalid; exact time constraints still require reviewer annotation

## irs003-5-16

> remind me to do what i said i would not do

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask what action they are referring to.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-17

> i am available every day except the days i am not

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `reject`
- Original expectation: METIS should note this gives no actionable availability and ask for specific days.
- Review rationale: Hypothetical, unsupported request or non-actionable statement; no read or mutation

## irs003-5-18

> someone quoted me saying i would help. was that me

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should search for the commitment and report what it has, noting the user's own uncertainty.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-19

> delete my notes but keep the ones about deleting my notes

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should clarify what they actually want to delete.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-20

> is it possible that i am free on a day that does not exist

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should gently note that no such day exists and ask what they actually need.
- Review rationale: Date is missing or invalid; exact time constraints still require reviewer annotation

## irs003-5-21

> i want to be reminded of something i never told you

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `reject`
- Original expectation: METIS should explain it only remembers what was told to it and ask if they want to save something new.
- Review rationale: Hypothetical, unsupported request or non-actionable statement; no read or mutation

## irs003-5-22

> what did i promise to do on the day after i stopped promising things

- Draft intent: `WHAT_DID_I_PROMISE`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask for a specific date or context.
- Review rationale: Person, collective identity, subject or temporal context is unresolved

## irs003-5-23

> remind me to not remind me about the thing

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should ask whether they want to suppress a reminder or are joking.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-24

> i have a meeting at noon AND i have a meeting at midnight on the same day

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should confirm both times or note the unusual spread and ask if both are correct.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-5-25

> what is my availability on the day between yesterday and tomorrow

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"time_expression": "today", "date_range": {"start": "2026-10-09", "end_exclusive": "2026-10-10"}}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS should interpret as today and check the calendar, or confirm the interpretation.
- Review rationale: Calendar access is unavailable in current product; exact time constraints still require reviewer annotation

## irs003-5-26

> someone said i owe them. someone else said i do not. who is right

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS should note it has no way to adjudicate and ask for names or specifics to check against stored commitments.
- Review rationale: Outside read-classifier scope; preserve native action handling and resolve ambiguity separately

## irs003-add-tasks-01

> is the thing about the gas cylinder still on my list

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "gas cylinder"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches pending tasks for gas cylinder and reports whether it is there and what was noted.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-02

> how many stuffs am i supposed to do this month

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this month", "date_range": {"start": "2026-10-01", "end_exclusive": "2026-11-01"}}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters tasks by current month and gives a count plus a quick list.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-03

> i think i crossed off the visa thing but i cant find it

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "any", "topic": "visa"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks the task list and completed items for a visa entry and reports its status.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-04

> what is the most urgent thing on my list right now

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "order_by": "deadline_urgency"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS reviews pending tasks, considers deadlines, and names the most time-sensitive one.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-05

> did i ever write down that i need to get the bike insured

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "bike insured"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches and reports whether it exists, without assuming.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-06

> the renewal for the driving licence. is that still pending or did i do it

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "any", "topic": "driving licence"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS finds the task and reports whether it is open or marked done.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-07

> pull up whatever i have saved about the flat rent

- Draft intent: `UNKNOWN`
- Entity spans: `[]`
- Filters: `{}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches both pending tasks and saved notes for rent-related entries and shows what it finds.
- Review rationale: Explicit search of both tasks and notes; outside single-read-intent protocol

## irs003-add-tasks-08

> is there anything about the school fee on my to do

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "school fee"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for school fee tasks and reports.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-09

> i have like seventeen things to do and i cant remember any of them

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete"}`
- Proposed current disposition: `read`
- Original expectation: METIS lists all pending tasks without editorialising.
- Review rationale: Retrieve full pending list; do not assume it contains exactly seventeen items

## irs003-add-tasks-10

> what was that thing about the water purifier filter

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "water purifier filter"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for purifier or filter tasks and states what was noted.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-11

> check if the gym payment task is still there. i think i paid already

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "any", "topic": "gym payment"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks and reports, noting the user believes it is done.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-12

> kya kuch pending hai mere paas is mahine ke liye

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "time_expression": "this month", "date_range": {"start": "2026-10-01", "end_exclusive": "2026-11-01"}}`
- Proposed current disposition: `clarify`
- Original expectation: METIS interprets the Hindi, filters tasks by month, and lists them.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-13

> the thing with the electricity connection. was it a new meter or the old one

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "electricity connection"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS searches for electricity-related tasks and reports what it has, flagging the ambiguity if both exist.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-14

> remind me what i need to do before the trip to kerala

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "trip to kerala"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS filters tasks by trip context and lists them.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-tasks-15

> is the passport thing still there or did i already do it and forget

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "any", "topic": "passport"}`
- Proposed current disposition: `clarify`
- Original expectation: METIS checks status and reports plainly.
- Review rationale: Requested structured task lookup exceeds current unfiltered read port

## irs003-add-calendar-01

> is anything on for this weekend

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"time_expression": "this weekend", "date_range": {"start": "2026-10-10", "end_exclusive": "2026-10-12"}}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews both days and lists anything scheduled or says they are clear.
- Review rationale: Calendar access is unavailable; supplied event lookup remains desired future behavior

## irs003-add-calendar-02

> what time does the thing with the school start

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"topic": "school"}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS searches the calendar for a school event and states the time.
- Review rationale: Calendar access is unavailable; supplied event lookup remains desired future behavior

## irs003-add-calendar-03

> am i booked up on wednesday morning

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"time_expression": "wednesday morning", "date_range": {"start": "2026-10-14", "end_exclusive": "2026-10-15"}, "time_of_day": "morning"}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS reviews Wednesday morning slots and reports free or occupied.
- Review rationale: Calendar access is unavailable; supplied event lookup remains desired future behavior

## irs003-add-calendar-04

> what is coming up in the next two weeks

- Draft intent: `CHECK_CALENDAR`
- Entity spans: `[]`
- Filters: `{"time_expression": "next two weeks", "date_range": {"start": "2026-10-09", "end_exclusive": "2026-10-23"}}`
- Proposed current disposition: `unsupported`
- Original expectation: METIS lists all events in that window, grouped by day.
- Review rationale: Calendar access is unavailable; supplied event lookup remains desired future behavior; reviewer must approve whether 14-day window includes reference day

## irs003-add-tasks-16

> is the thing about cancelling the newspaper still on my list

- Draft intent: `CHECK_TASKS`
- Entity spans: `[]`
- Filters: `{"status": "incomplete", "topic": "cancelling the newspaper"}`
- Proposed current disposition: `clarify`
- Original expectation: Check whether the pending newspaper-cancellation task exists; do not cancel a subscription
- Review rationale: Read of a task about cancellation, not a cancellation command; topic-filter capability remains absent
