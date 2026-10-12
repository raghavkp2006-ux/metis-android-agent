# Review of the 50 submitted requests

All requests are separate, with no conversation history. Text is verbatim. Labels and outcomes below are assistant drafts; no approvals have been recorded. Semantic intent does not grant read authority. Please correct any intended meaning, scope or desired constraints.

| # | Request | Draft intent | Draft disposition under current capabilities | Actual status | Scope / retained constraint |
| --- | --- | --- | --- | --- | --- |
| 1 | Have I told Riya I would send her the notes? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | promised-action content filter: send her the notes |
| 2 | What I said I will do with Arjun last week? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | time filter: last week; ambiguous with Arjun |
| 3 | Have I already got a task to turn in the lab report? | CHECK_TASKS | clarify | FOLLOW_UP | task content/existence filter: turn in the lab report |
| 4 | Please remind me what I said I was going to help Kabir with his project. | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | promised-action/project filter |
| 5 | Did I ever agree to call Meera post-class? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | promised-action and post-class time filter |
| 6 | If not, create a task for me to purchase the parts. | UNKNOWN | clarify | DENIED | conditional depends on missing context; action creation |
| 7 | What tasks do I have related to the antenna project? | CHECK_TASKS | clarify | FOLLOW_UP | task topic filter: antenna project |
| 8 | I hope I didn't tell Nisha that I would get the presentation done today. | UNKNOWN | clarify | FOLLOW_UP | belief/negative statement and today filter |
| 9 | Can you remember what I told you in the robotics competition plan? | CHECK_MEMORY | read | ANSWER | robotics competition plan |
| 10 | Ever heard of a guy called Aarav in relation to the report? | CHECK_MEMORY | clarify | FOLLOW_UP | identity/world-knowledge ambiguity; clarify saved-record scope |
| 11 | Is there anything saved in my mind for Priya? | CHECK_MEMORY | clarify | FOLLOW_UP | ambiguous mind/for-person scope |
| 12 | Did I promise anyone that I'd post the source code? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | recipient anyone is unresolved; action content filter |
| 13 | Identify and locate the word in which I indicated that I need to reach Rahul. | UNKNOWN | clarify | FOLLOW_UP | ambiguous word-location request |
| 14 | Should I be done with the assignment by Friday? | UNKNOWN | clarify | FOLLOW_UP | advice question with Friday constraint |
| 15 | About the prototype deadline?I said about the deadline for the prototype? | CHECK_MEMORY | clarify | FOLLOW_UP | fragmented/repeated deadline question |
| 16 | I said I didn't want to go to the meeting? | CHECK_MEMORY | clarify | FOLLOW_UP | negative statement without explicit recall request |
| 17 | Have I ever told Sameer that I don't have to help him out? | WHAT_DID_I_PROMISE | clarify | DENIED | negated obligation is not an affirmative promise |
| 18 | I did say I wouldn't submit it this week didn't I? | CHECK_MEMORY | clarify | FOLLOW_UP | negative content and this-week filter; uncertain reference |
| 19 | Look for anything I saved on Neha and database project. | CHECK_MEMORY | read | ANSWER | Neha and database project |
| 20 | So, what can you recall from what I said to Vikram? | CHECK_MEMORY | read | ANSWER | what I said to Vikram |
| 21 | Have I created a task already to send the document to Ananya? | CHECK_TASKS | clarify | FOLLOW_UP | task content/existence filter |
| 22 | Make a note to email the document to Ananya tomorrow. | UNKNOWN | clarify | FOLLOW_UP | note/email/scheduling action combination |
| 23 | Do I already have some plans for tomorrow afternoon? | CHECK_CALENDAR | unsupported | UNSUPPORTED | calendar unavailable |
| 24 | Which meetings are coming up next? | CHECK_CALENDAR | unsupported | UNSUPPORTED | calendar unavailable |
| 25 | Have any meetings with Rohit this week? | CHECK_CALENDAR | unsupported | UNSUPPORTED | calendar unavailable |
| 26 | When do I have to meet again concerning the research project? | CHECK_CALENDAR | unsupported | UNSUPPORTED | calendar unavailable |
| 27 | Did I leave any notes behind for a meeting with Ishita? | CHECK_MEMORY | read | ANSWER | meeting with Ishita |
| 28 | After meeting Karan, what would I do? | UNKNOWN | clarify | FOLLOW_UP | predictive action question after a meeting |
| 29 | Am I right that I am supposed to have it done by Monday? | UNKNOWN | clarify | FOLLOW_UP | unresolved it and Monday constraint |
| 30 | What guarantees have I given for the website project? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | project filter without a unique named promise recipient |
| 31 | Have I let anyone know I would deploy the app? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | unnamed recipient and deployment-content filter |
| 32 | The commitment here was that I will look at the document. | UNKNOWN | clarify | FOLLOW_UP | declarative commitment without a recall request |
| 33 | Did I say that I would send the file to someone or am I mis-remembering that? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | unresolved someone and action-content constraint |
| 34 | Oh! I remember I need to do this for him. | UNKNOWN | clarify | FOLLOW_UP | unresolved this and him |
| 35 | Have I already done that for her? | CHECK_TASKS | clarify | FOLLOW_UP | unresolved that/her and completion query |
| 36 | Go back to the task that I mentioned earlier regarding the presentation. | CHECK_TASKS | clarify | FOLLOW_UP | prior-conversation reference plus task topic filter |
| 37 | So what was I supposed to submit? | UNKNOWN | clarify | FOLLOW_UP | unspecified submission obligation |
| 38 | Have I made any notes about going there next week? | CHECK_MEMORY | clarify | FOLLOW_UP | unresolved there plus next-week constraint |
| 39 | Did Aditya have a task? | CHECK_TASKS | clarify | FOLLOW_UP | other-person task ownership not implemented |
| 40 | Have I offered Aditya anything to do with the demo? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | demo/action content filter |
| 41 | Locate uncompleted work on the project. | CHECK_TASKS | clarify | FOLLOW_UP | unresolved project topic filter |
| 42 | What did you get done yesterday? | UNKNOWN | clarify | FOLLOW_UP | assistant activity history is unavailable |
| 43 | Are there any tasks on Friday that I need to finish before? | CHECK_TASKS | clarify | FOLLOW_UP | Friday filter and incomplete before clause |
| 44 | Find tasks related to the report that are marked completed. | CHECK_TASKS | clarify | FOLLOW_UP | topic and COMPLETED status filters |
| 45 | Have I planned a task to meet Sana in Chennai? | CHECK_TASKS | clarify | FOLLOW_UP | meeting/person/location task content filters |
| 46 | What did I promise myself when I left for Delhi? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | myself unresolved as saved person; departure/location constraint |
| 47 | Did I say that I will send you only 3 files, Dev? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | send/only-3-files promise-content constraint |
| 48 | Identify the task that I was supposed to get in touch with Rohan and let me know the due date. | CHECK_TASKS | clarify | FOLLOW_UP | task content filter and requested due-date field |
| 49 | Did I say I'd send the slides to Maya and did I give her a job to do? | WHAT_DID_I_PROMISE | clarify | FOLLOW_UP | two requests: promise recall and assigned-job recall |
| 50 | See if I can remember anything about the workshop and if I did make a task for it. | UNKNOWN | clarify | FOLLOW_UP | compound memory and task-existence requests |

Calendar-unavailable requests retain their original person/topic/date wording. Clarification/rejection should preserve the full request; it must not run a broader unfiltered lookup. Queries 9, 19, 20 and 27 perform literal memory retrieval, not contact resolution or factual identity inference.

Request 49 contains two requested outcomes, and request 33 has an unresolved recipient and a rhetorical alternative; their provisional promise-intent labels are particularly important to review. Request 50 is a compound memory/task request and is provisionally UNKNOWN. All three remain blocked from reads.

User review pending. A change to any draft is development feedback, not retroactive approval of the original frozen benchmark.
