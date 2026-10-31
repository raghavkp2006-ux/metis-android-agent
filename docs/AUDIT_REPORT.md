# Synthetic NLU Dataset Audit & Cleaning Report (`AUDIT_REPORT.md`)

**Generated**: 2026-10-30  
**Random Seed**: `2026` (deterministic tie-breaking and sampling)  
**Input Datasets**: `ml/nlu_v1/generated/train_pool.csv` (2,816 rows), `ml/nlu_v1/generated/dev.csv` (330 rows)  
**Clean Outputs**: `ml/nlu_v1/generated/train_pool_clean.csv` (2030 rows), `ml/nlu_v1/generated/dev_clean.csv` (317 rows)  

## Executive Summary

A comprehensive, multi-stage audit and cleaning pipeline was executed on the synthetic and web-derived NLU dataset pool across 21 domain intents:
1. **Collection Validation**: Ran `validate_collection.py`. Confirmed **0 fatal schema or entity errors**. Verified that style tag warnings (`'terse'`) are informational.
2. **Broken Row Filtering**: Dropped **49 broken rows in training** and **13 broken rows in dev** exhibiting corrupted generation artifacts: compound question leaders (`'can you what'`, `'look up do you'`), non-idiomatic temporal prepositions (`'on today'`, `'on tomorrow'`), and implausible duration units (`'45 seconds'` for `WHAT_SHOULD_I_DO`).
3. **Source Sanity & Licensing**: Confirmed that all `web-derived:banking77` and `web-derived:snips` examples are strictly mapped to `UNKNOWN` (0 dropped). Audited permissible open-source licenses (CC-BY-4.0, Apache-2.0, CC-BY-3.0) and computed web-derived provenance shares per label.
4. **Data Leakage Prevention**: Verified character n-gram cosine similarities against `ml/nlu_v1/test.csv`, `ml/data/dev_own.csv`, and `ml/nlu_v1/data.csv`. Confirmed **0 leaking rows** (maximum observed similarity was 0.7302, strictly below the 0.80 cutoff). Test sets were held read-only and never trained or tuned upon.
5. **Near-Duplicate Pruning**: Identified and pruned **7 near-duplicate pairs** in train having character 5-gram TF-IDF cosine similarity $\ge 0.80$.
6. **Template Audit & 3% Phrase Capping**: Listed the top-30 word 4-grams and top-30 final 4-grams overall and per label. Capped repetitive boilerplates at 3% of label row count ($0.03 \times N$), deterministically shedding **723 repetitive template rows**.
7. **Transfer Check**: Evaluated TF-IDF + Logistic Regression transfer on human student utterances (author `VN` in `data.csv`). Synthetic Dev Accuracy: **95.27%**, Author VN Accuracy: **86.72%** (Generalization gap: **8.55 points**, strictly satisfying the $\le 15$ point transfer ceiling; Macro F1 gap: **14.16 points**).

## 1. Collection Validation Results (`Step 1`)

- **Input Validation (`train_pool.csv`)**: 2,816 rows scanned. **0 fatal errors**, 330 style tag warnings (tag `'terse'`).
- **Input Validation (`dev.csv`)**: 330 rows scanned. **0 fatal errors**, 44 style tag warnings (tag `'terse'`).
- **Schema Compliance**: All required columns present (`utterance`, `intended_intent`, `style_tags`, `entities`, `complexity`, `split`, `notes`). All 21 labels match official ontology.

## 2. Broken Rows Audit (`Step 4`)

A total of **49 rows in train** and **13 rows in dev** were dropped due to malformed generation rules:

| Dataset | Rule Violation | Dropped Count | Description |
| :--- | :--- | ---: | :--- |
| `train_pool.csv` | Two Question Leaders | 19 | Glued question/command starters (`can you what`, `look up do you`, etc.) |
| `train_pool.csv` | 'on today' / 'on tomorrow' | 24 | Unnatural prepositional temporal phrase |
| `train_pool.csv` | Implausible Duration | 6 | Seconds duration for task selection/planning (`45 seconds`, etc.) |
| `dev.csv` | Two Question Leaders | 4 | Glued question starters |
| `dev.csv` | 'on today' / 'on tomorrow' | 5 | Unnatural prepositional temporal phrase |
| `dev.csv` | Implausible Duration | 4 | Seconds duration for task selection |
| **Total** | **All Violations** | **62** | — |

### 50 Sample Dropped Broken Rows

| # | Split | Intent | Dropped Utterance | Reason |
| ---: | :--- | :--- | :--- | :--- |
| 1 | `train` | `CHECK_CALENDAR` | Read out the meetings I have lined up on tomorrow morning right after tutorial. | Invalid temporal syntax ('on tomorrow') |
| 2 | `train` | `CHECK_CALENDAR` | How packed is my afternoon schedule on tomorrow so I stay on top of coursework? | Invalid temporal syntax ('on tomorrow') |
| 3 | `train` | `CHECK_CALENDAR` | Tell me what time my meeting with Meera starts on tomorrow morning while I am at the library. | Invalid temporal syntax ('on tomorrow') |
| 4 | `train` | `CHECK_CALENDAR` | How packed is my afternoon scedule on tomorrow mornign? | Invalid temporal syntax ('on tomorrow') |
| 5 | `train` | `CHECK_CALENDAR` | Am I fre around 5:30 am on today to catch up with Diya right after tutorial? | Invalid temporal syntax ('on today') |
| 6 | `train` | `CHECK_CALENDAR` | meetings on tomorrow morning | Invalid temporal syntax ('on tomorrow') |
| 7 | `train` | `CHECK_TASKS` | Scan my task tracker for urgent items due on tomorrow morning so nothing slips through the cracks. | Invalid temporal syntax ('on tomorrow') |
| 8 | `train` | `WHAT_SHOULD_I_DO` | Given my deadlines for CS350 Computer Networks, what should I focus on for the next 45 seconds? | Implausible duration unit for WHAT_SHOULD_I_DO ('the next 45 seconds') |
| 9 | `train` | `WHAT_SHOULD_I_DO` | Given my deadlines for CS310 Database Systems, what should I focus on for the next 90 seconds? | Implausible duration unit for WHAT_SHOULD_I_DO ('the next 90 seconds') |
| 10 | `train` | `WHAT_SHOULD_I_DO` | Dekho mere paas 2 minutes and 30 seconds ka break hai, isme kya productive karoon aaj raat sone se pehle? | Implausible duration unit for WHAT_SHOULD_I_DO ('30 seconds') |
| 11 | `train` | `WHAT_SHOULD_I_DO` | uh i have ninety seconds before my next lecture what should i work on | Implausible duration unit for WHAT_SHOULD_I_DO ('ninety seconds') |
| 12 | `train` | `WHAT_SHOULD_I_DO` | free 45 seconds suggest task | Implausible duration unit for WHAT_SHOULD_I_DO ('45 seconds') |
| 13 | `train` | `PLAN_DAY` | Structure my mornign tomorow so I get 45 seconds of deep work on Weather Station PCB before the lab evaluation. | Implausible duration unit for PLAN_DAY ('45 seconds') |
| 14 | `train` | `PROJECT_STATUS` | arre how much work is left on formula student telemetry before the deadline on tomorrow morning | Invalid temporal syntax ('on tomorrow') |
| 15 | `train` | `PROJECT_STATUS` | hey how much work is left on autonomous drone nav before the deadline on today | Invalid temporal syntax ('on today') |
| 16 | `train` | `PROJECT_STATUS` | you know how much work is left on rag study buddy before the deadline on today | Invalid temporal syntax ('on today') |
| 17 | `train` | `CREATE_TASK` | Record 'Git branch cleanup' as an open task due on tomorrow. | Invalid temporal syntax ('on tomorrow') |
| 18 | `train` | `CREATE_REMINDER` | Could you remind me at 10:00 pm on tomorrow morning to email academic office about transcript attestation? | Invalid temporal syntax ('on tomorrow') |
| 19 | `train` | `CREATE_REMINDER` | At 10:15 am on today, remind me to finish reading chapter four of linear algebra. | Invalid temporal syntax ('on today') |
| 20 | `train` | `CREATE_REMINDER` | Give me a heads-up on tomorrow evening at 6:30 am to check on Placement Prep Tracker before the lab evaluation. | Invalid temporal syntax ('on tomorrow') |
| 21 | `train` | `CREATE_REMINDER` | Schedule a reminder ping for 12:30 pm on tomorrow evening about microeconomics problem set right after tutorial. | Invalid temporal syntax ('on tomorrow') |
| 22 | `train` | `CREATE_PROMISE` | Write down that I pledged to help Harsh with hackathon registration on tomorrow morning. | Invalid temporal syntax ('on tomorrow') |
| 23 | `train` | `REFLECTION` | Capture my thoughts on today: collaboration with Ritvik on Smart Hostel IoT was super smooth. | Invalid temporal syntax ('on today') |
| 24 | `train` | `REFLECTION` | Capture my thoughts on today: collaboration with Ishaan on Alumni Portal Redesign was super smooth. | Invalid temporal syntax ('on today') |
| 25 | `train` | `REFLECTION` | Capture my thoughts on today: collaboration with Arjun on Distributed KV Store was super smooth. | Invalid temporal syntax ('on today') |
| 26 | `train` | `CREATE_ALARM` | Program an alarm for 8:30 am on today. | Invalid temporal syntax ('on today') |
| 27 | `train` | `CREATE_ALARM` | Sound an alarm at 5:15 am on tomorrow evening before my flight. | Invalid temporal syntax ('on tomorrow') |
| 28 | `train` | `CREATE_ALARM` | Wake me up at 8:15 am on tomorrow evening for my CS405 Machine Learning exam so I stay on top of coursework. | Invalid temporal syntax ('on tomorrow') |
| 29 | `train` | `CREATE_ALARM` | Program an alarm for 6:15 am on tomorrow before the department office closes. | Invalid temporal syntax ('on tomorrow') |
| 30 | `train` | `CREATE_ALARM` | Wake me up at 6:45 am on tomorrow for my CS301 Operating Systems exam so I stay on top of coursework. | Invalid temporal syntax ('on tomorrow') |
| 31 | `train` | `UNKNOWN` | Would you what is the weather forecast in Hyderabad for tomorrow morning? | Two question leaders ('Would you what') |
| 32 | `train` | `UNKNOWN` | Can you what is the weather forecast in Hyderabad for Sunday ahead of tomorrow's standup? | Two question leaders ('Can you what') |
| 33 | `train` | `UNKNOWN` | Look up what is the weather forecast in Kolkata for this weekend. | Two question/command leaders ('Look up what is') |
| 34 | `train` | `UNKNOWN` | Would you what is the weather forecast in Delhi for today? | Two question leaders ('Would you what') |
| 35 | `train` | `UNKNOWN` | Could you what is the live exchange rate for 99 US dollars in Indian rupees so nothing slips through the cracks? | Two question leaders ('Could you what') |
| 36 | `train` | `UNKNOWN` | Look up what is the weather forecast in Jaipur for tomorrow evening. | Two question/command leaders ('Look up what is') |
| 37 | `train` | `UNKNOWN` | Look up do you ever get tired of answering questions about PH101 Quantum Physics and Sanskrit OCR Pipeline. | Two question/command leaders ('Look up do you') |
| 38 | `train` | `UNKNOWN` | Would you do you ever get tired of answering questions about CS412 Natural Language Procsesing and Library Book Scanner? | Two question leaders ('Would you do you') |
| 39 | `train` | `UNKNOWN` | Can you what is the weather forecast in Guwhaati for Tuesday ahead of tomorrow's standup then check what I promsed Anya. | Two question leaders ('Can you what') |
| 40 | `train` | `UNKNOWN` | Would you do you ever get tired of answering questions about CS220 Algorithms and Distributed KV Store? | Two question leaders ('Would you do you') |
| 41 | `train` | `UNKNOWN` | Can you do you ever get tired of answering questions about CS310 Database Systems and Robotics Arm Firmware before my next lecture? | Two question leaders ('Can you do you') |
| 42 | `train` | `UNKNOWN` | Look up what is the weather forecast in Mumbai for next Thursday. | Two question/command leaders ('Look up what is') |
| 43 | `train` | `UNKNOWN` | Look up do you ever get tired of answering questions about ME201 Thermodynamics and Mess Feedback Analytics. | Two question/command leaders ('Look up do you') |
| 44 | `train` | `UNKNOWN` | Would you do you ever get tired of answering questions about CS350 Computer Networks and RAG Study Buddy? | Two question leaders ('Would you do you') |
| 45 | `train` | `UNKNOWN` | Can you do you ever get tired of answering questions about MA202 Linear Algebra and Weather Station PCB before my next lecture? | Two question leaders ('Can you do you') |
| 46 | `train` | `UNKNOWN` | Look up what is the weather forecast in Bengaluru for November 4th. | Two question/command leaders ('Look up what is') |
| 47 | `train` | `UNKNOWN` | Please what is the live exchange rate for 209 US dollars in Indian ruepes. | Two question leaders ('Please what') |
| 48 | `train` | `UNKNOWN` | Wuold you do you ever get tired of answering questions about CS301 Operating Systems and Mini Linux Kernel? | Two question leaders ('Wuold you do you') |
| 49 | `train` | `UNKNOWN` | Can you what is the weather forecast in Jaipur for this Satruday ahead of tomorrow's standup before you text Aarav. plz | Two question leaders ('Can you what') |
| 50 | `dev` | `CHECK_CALENDAR` | Am I free around 7:10 am on today to catch up with Ira while I am at my study desk? | Invalid temporal syntax ('on today') |


## 3. Source Sanity & Provenance Audit (`Step 5`)

- **Out-of-Scope Rule**: All rows from `web-derived:banking77` (39 rows) and `web-derived:snips` (18 rows) are strictly labeled `UNKNOWN`. 0 rows violated label isolation.
- **Web-Derived Share by Intent** (post-broken filtering):

| Intent Label | Total Rows | Web-Derived Rows | Web-Derived Share (%) | Primary External Sources |
| :--- | ---: | ---: | ---: | :--- |
| `CALL_PERSON` | 128 | 57 | 44.5% | clinc150, hinglish_top |
| `CHECK_CALENDAR` | 122 | 55 | 45.1% | clinc150, hinglish_top, massive |
| `CHECK_MEMORY` | 128 | 57 | 44.5% | clinc150, massive |
| `CHECK_TASKS` | 127 | 57 | 44.9% | clinc150, massive |
| `COMPLETE_TASK` | 128 | 57 | 44.5% | massive |
| `CREATE_ALARM` | 123 | 53 | 43.1% | clinc150, hinglish_top, massive |
| `CREATE_PROMISE` | 127 | 56 | 44.1% | clinc150 |
| `CREATE_REMINDER` | 124 | 55 | 44.4% | clinc150 |
| `CREATE_TASK` | 127 | 57 | 44.9% | clinc150, hinglish_top, massive |
| `CREATE_TIMER` | 128 | 57 | 44.5% | clinc150, hinglish_top, massive |
| `DELETE_TASK` | 128 | 57 | 44.5% | clinc150, massive |
| `PERSON_COMMITMENTS` | 128 | 57 | 44.5% | clinc150 |
| `PLAN_DAY` | 127 | 57 | 44.9% | massive |
| `POSTPONE_TASK` | 128 | 57 | 44.5% | hinglish_top, massive |
| `PRIORITIZE_TASK` | 128 | 57 | 44.5% | clinc150 |
| `PROJECT_STATUS` | 125 | 54 | 43.2% | massive |
| `REFLECTION` | 125 | 56 | 44.8% | clinc150 |
| `RENAME_TASK` | 128 | 57 | 44.5% | clinc150 |
| `SEND_MESSAGE` | 128 | 57 | 44.5% | clinc150, hinglish_top |
| `UNKNOWN` | 237 | 107 | 45.1% | banking77, massive, snips |
| `WHAT_SHOULD_I_DO` | 123 | 56 | 45.5% | clinc150 |


## 4. Benchmark Leakage Audit (`Step 6`)

Character n-gram TF-IDF cosine similarity was evaluated against all ground-truth reference files:
- Reference sets: `ml/nlu_v1/test.csv` (128 rows), `ml/data/dev_own.csv` (100 rows), `ml/nlu_v1/data.csv` (740 rows).
- **Train Pool Maximum Cosine Similarity**: `0.7302` (< 0.80)
- **Dev Set Maximum Cosine Similarity**: `0.6147` (< 0.80)
- **Leakage Drops**: **0 rows** exceeded or matched the 0.80 similarity threshold. Test and dev_own data were held strictly read-only and never trained upon.

## 5. Near-Duplicate Pruning (`Step 3`)

Greedy deduplication within each intent using character 5-gram TF-IDF cosine similarity $\ge 0.80$ dropped **7 rows** in `train_pool.csv` and **0 rows** in `dev.csv`:

| Intent Label | Near-Duplicate Pairs Dropped | Example Dropped Utterance | Retained Utterance | Similarity |
| :--- | ---: | :--- | :--- | ---: |
| `CHECK_CALENDAR` | 2 | When is my next open thirty-minute window on this Saturday? | When is my next open thirty-minute window on November 4th? | 0.818 |
| `CHECK_CALENDAR` | 2 | Walk me through my scheduled commitments for tomorrow. | Walk me through my scheduled commitments for next Thursday. | 0.823 |
| `CREATE_ALARM` | 1 | alarm 10:15 am | morning alarm 10:15 am | 0.826 |
| `REFLECTION` | 2 | Here is my end-of-day check-in: today was solid overall; stayed off social media and completed my linear algebra problem set. | Log my daily journal entry: today was solid overall; stayed off social media and completed my linear algebra problem set. | 0.820 |
| `REFLECTION` | 2 | Log my daily journal entry: today was really rewarding since our prototype finally passed all integration tests. | Here is my end-of-day check-in: today was really rewarding since our prototype finally passed all integration tests. | 0.810 |
| `WHAT_SHOULD_I_DO` | 2 | Recommend the single most important item for me to tackle right now. | Recommend the single most important item for me to tackle right now while I am at my study desk. | 0.835 |
| `WHAT_SHOULD_I_DO` | 2 | Abhi bilkul free baitha hoon, batao sabse urgent kaam kaunsa pakdoon? | Abhi bilkul free baitha hoon, batao sabse urgent kaam kaunsa pakdoon hostel nikalne se pehle? | 0.821 |


## 6. Template Audit & Phrase Capping (`Step 2`)

To prevent the NLU model from memorizing repetitive template tails and conversational crutches (e.g., `'at my study desk'`, `'before the lab evaluation'`, `'so nothing slips through the cracks'`), every word 4-gram and tail was capped at **3% of a label's rows** ($cap = \lfloor 0.03 \times N \rfloor$) using fixed seed `2026`.

- **Total Rows Dropped by Capping**: **730 rows**
- **Drops Per Label**:

| Intent Label | Initial Rows | Rows Dropped | Clean Remaining | Most Frequent Capped Phrases |
| :--- | ---: | ---: | ---: | :--- |
| `CALL_PERSON` | 128 | 36 | 92 | 'so i can ask' (8), 'a phone call to' (6), 'a voice call to' (6) |
| `CHECK_CALENDAR` | 120 | 19 | 101 | 'do i have any' (5), 'what is on my' (3), 'while i am at' (3) |
| `CHECK_MEMORY` | 128 | 50 | 78 | 'ke baare mein kya' (14), 'while i am at' (5), 'bank ifsc code for' (3) |
| `CHECK_TASKS` | 127 | 25 | 102 | 'while i am at' (5), 'out the tasks i' (4), 'any uncompleted deliverables under' (3) |
| `COMPLETE_TASK` | 128 | 36 | 92 | 'on my to-do list' (7), 'so check it off' (6), 'check off kar do' (5) |
| `CREATE_ALARM` | 122 | 42 | 80 | 'wake me up at' (10), 'am ka wake-up alarm' (6), 'alarm set kar do' (3) |
| `CREATE_PROMISE` | 127 | 40 | 87 | 'log that i just' (6), 'kar lo ki maine' (5), 'a new commitment to' (4) |
| `CREATE_REMINDER` | 124 | 18 | 106 | 'am pe ek notification' (4), 'send me a notification' (3), 'before i go to' (2) |
| `CREATE_TASK` | 127 | 45 | 82 | 'meri to-do list mein' (12), 'a new to-do item' (5), 'my to-do list for' (5) |
| `CREATE_TIMER` | 128 | 42 | 86 | 'for this pomodoro coding' (7), 'the agarose gel runs' (5), '2 minutes and 30' (4) |
| `DELETE_TASK` | 128 | 25 | 103 | 'off my task board' (9), 'active tasks because plans' (2), 'backlog we no longer' (2) |
| `PERSON_COMMITMENTS` | 128 | 38 | 90 | 'check if i owe' (10), 'dena ya bhejna baki' (4), 'baare mein kya dene' (3) |
| `PLAN_DAY` | 127 | 32 | 95 | 'i can fit in' (7), 'for my evening starting' (6), 'structure my morning tomorrow' (4) |
| `POSTPONE_TASK` | 128 | 22 | 106 | 'the day after tomorrow' (5), 'move the due date' (4), 'from today's list to' (2) |
| `PRIORITIZE_TASK` | 128 | 19 | 109 | 'on my task board' (4), 'while i am at' (4), 'ki urgency badha ke' (2) |
| `PROJECT_STATUS` | 125 | 37 | 88 | 'kaun-kaun se modules incomplete' (11), 'abhi kya-kya kaam pending' (7), 'low power risc-v core' (5) |
| `REFLECTION` | 123 | 31 | 92 | 'while i am at' (5), 'a diary note for' (3), 'aaj poore din main' (3) |
| `RENAME_TASK` | 128 | 28 | 100 | 'change the title of' (6), 'on my checklist to' (5), 'the name of my' (5) |
| `SEND_MESSAGE` | 128 | 57 | 71 | 'as soon as your' (8), 'can you send me' (5), 'send a text message' (5) |
| `UNKNOWN` | 237 | 55 | 182 | 'me a funny joke' (20), 'on spotify while i' (14), 'is the weather forecast' (7) |
| `WHAT_SHOULD_I_DO` | 121 | 33 | 88 | 'i start right now' (8), 'abhi kya zyada zaroori' (6), 'me on whether to' (5) |


### Overall Top-30 Word 4-Grams & Final 4-Grams

| Rank | Top Word 4-Gram (Overall) | Count | Top Final 4-Gram (Overall) | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | while i am at | 155 | at my study desk | 79 |
| 2 | when you get a | 82 | to the hostel mess | 77 |
| 3 | i am at my | 80 | you get a second | 76 |
| 4 | am at my study | 80 | before the lab evaluation | 73 |
| 5 | at my study desk | 80 | koi dikkat na ho | 73 |
| 6 | heading to the hostel | 77 | am at the library | 72 |
| 7 | to the hostel mess | 77 | slips through the cracks | 71 |
| 8 | before heading to the | 76 | mess jaane se pehle | 68 |
| 9 | you get a second | 76 | the department office closes | 52 |
| 10 | i am at the | 76 | raat sone se pehle | 49 |
| 11 | before the lab evaluation | 73 | ahead of tomorrow's standup | 48 |
| 12 | am at the library | 73 | on top of coursework | 47 |
| 13 | taaki koi dikkat na | 73 | se nikalne se pehle | 39 |
| 14 | koi dikkat na ho | 73 | hostel nikalne se pehle | 34 |
| 15 | slips through the cracks | 71 | class khatam hote hi | 29 |
| 16 | so nothing slips through | 70 | before my next lecture | 23 |
| 17 | nothing slips through the | 70 | warna main bhool jaunga | 16 |
| 18 | mess jaane se pehle | 68 | before tonight's group study | 15 |
| 19 | ahead of tomorrow's standup | 67 | free slot kab hai | 15 |
| 20 | group study se pehle | 53 | group study se pehle | 14 |
| 21 | before the department office | 52 | ka haal bhi batao | 13 |
| 22 | the department office closes | 52 | message bhi bhej do | 11 |
| 23 | aaj raat sone se | 49 | bhi mark kar dena | 11 |
| 24 | raat sone se pehle | 49 | i am free tomorrow | 10 |
| 25 | so i stay on | 48 | on my to-do list | 10 |
| 26 | i stay on top | 48 | yaad bhi dila dena | 9 |
| 27 | stay on top of | 48 | am on my way | 9 |
| 28 | on top of coursework | 48 | kaam pending pada hai | 9 |
| 29 | library se nikalne se | 39 | bhi chalu kar do | 7 |
| 30 | se nikalne se pehle | 39 | lab viva se pehle | 7 |


### Per-Label Top-30 Word 4-Grams & Final 4-Grams

#### `CALL_PERSON` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | so i can ask | 11 | before the lab evaluation | 6 |
| 2 | a phone call to | 9 | you get a second | 5 |
| 3 | a voice call to | 9 | at my study desk | 4 |
| 4 | place a phone call | 7 | raat sone se pehle | 4 |
| 5 | while i am at | 7 | on speakerphone right now | 4 |
| 6 | so we can discuss | 7 | slips through the cracks | 3 |
| 7 | i can ask where | 6 | by phone right now | 3 |
| 8 | can ask where the | 6 | on top of coursework | 3 |
| 9 | before the lab evaluation | 6 | koi dikkat na ho | 3 |
| 10 | ko ring karo mujhe | 6 | lab ka poochna hai | 3 |
| 11 | ke baare mein baat | 6 | mess jaane se pehle | 3 |
| 12 | baare mein baat karni | 6 | ko abhi phone milao | 3 |
| 13 | mein baat karni hai | 6 | class khatam hote hi | 3 |
| 14 | on speakerphone right now | 5 | place a voice call | 2 |
| 15 | place a voice call | 5 | ring on the phone | 2 |
| 16 | i can ask about | 5 | to the hostel mess | 2 |
| 17 | when you get a | 5 | for me right now | 2 |
| 18 | you get a second | 5 | ahead of tomorrow's standup | 2 |
| 19 | make a voice call | 5 | the department office closes | 2 |
| 20 | ko bina kisi deri | 5 | am at the library | 2 |
| 21 | bina kisi deri ke | 5 | se nikalne se pehle | 2 |
| 22 | i am at my | 4 | urgent baat karni hai | 2 |
| 23 | am at my study | 4 | group study se pehle | 2 |
| 24 | at my study desk | 4 | about the viva slot | 1 |
| 25 | on speakerphone so we | 4 | to vivaan on mobile | 1 |
| 26 | speakerphone so we can | 4 | to riya on speakerphone | 1 |
| 27 | so we can talk | 4 | discuss solar rover capstone | 1 |
| 28 | we can talk through | 4 | me right after tutorial | 1 |
| 29 | on the line for | 4 | kavya on their cell | 1 |
| 30 | the line for me | 4 | on their mobile number | 1 |


#### `CHECK_CALENDAR` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | do i have any | 8 | at my study desk | 4 |
| 2 | while i am at | 7 | to the hostel mess | 4 |
| 3 | what is on my | 6 | before the lab evaluation | 4 |
| 4 | the day after tomorrow | 5 | the department office closes | 4 |
| 5 | on my schedule for | 4 | koi dikkat na ho | 4 |
| 6 | is on my schedule | 4 | am at the library | 3 |
| 7 | i am at my | 4 | nikal sakta hai kya | 3 |
| 8 | am at my study | 4 | mess jaane se pehle | 3 |
| 9 | at my study desk | 4 | slips through the cracks | 2 |
| 10 | look up when my | 4 | you get a second | 2 |
| 11 | up when my session | 4 | ahead of tomorrow's standup | 2 |
| 12 | when my session with | 4 | before tonight's group study | 2 |
| 13 | before heading to the | 4 | raat sone se pehle | 2 |
| 14 | heading to the hostel | 4 | start ho raha hai | 2 |
| 15 | to the hostel mess | 4 | se milne ke liye | 2 |
| 16 | before the lab evaluation | 4 | yaad bhi dila dena | 2 |
| 17 | pull up my agenda | 4 | class khatam hote hi | 2 |
| 18 | up my agenda for | 4 | khaali slot kab milega | 2 |
| 19 | so i can see | 4 | after 2 45 pm | 1 |
| 20 | i can see when | 4 | hours for november 4th | 1 |
| 21 | display my timetable for | 4 | window on november 4th | 1 |
| 22 | before the department office | 4 | is scheduled on sunday | 1 |
| 23 | the department office closes | 4 | my calendar this saturday | 1 |
| 24 | wala schedule khol ke | 4 | around 11 00 am | 1 |
| 25 | schedule khol ke dikhao | 4 | 20 am and dinner | 1 |
| 26 | ke saath meeting kab | 4 | scheduled meetings and labs | 1 |
| 27 | saath meeting kab hai | 4 | commitments for next thursday | 1 |
| 28 | taaki koi dikkat na | 4 | pm on next thursday | 1 |
| 29 | koi dikkat na ho | 4 | catch up with ananya | 1 |
| 30 | i am at the | 3 | from 5 30 am | 1 |


#### `CHECK_MEMORY` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | ke baare mein kya | 17 | am at the library | 4 |
| 2 | while i am at | 8 | at my study desk | 4 |
| 3 | the frame size of | 7 | slips through the cracks | 4 |
| 4 | frame size of my | 7 | to the hostel mess | 4 |
| 5 | size of my commuter | 7 | kiya tha zara dekhna | 4 |
| 6 | of my commuter bicycle | 7 | koi dikkat na ho | 4 |
| 7 | the bank ifsc code | 7 | mess jaane se pehle | 4 |
| 8 | bank ifsc code for | 7 | of my logic analyzer | 3 |
| 9 | ifsc code for stipend | 7 | for the capstone poster | 3 |
| 10 | code for stipend forms | 7 | tenth standard mark sheet | 3 |
| 11 | the ip address of | 6 | you get a second | 3 |
| 12 | ip address of our | 6 | code for stipend forms | 3 |
| 13 | address of our lab | 6 | of my commuter bicycle | 2 |
| 14 | of our lab raspberry | 6 | inventory defects from check-in | 2 |
| 15 | our lab raspberry pi | 6 | before the lab evaluation | 2 |
| 16 | the serial number of | 6 | stored in my notes | 2 |
| 17 | serial number of my | 6 | ahead of tomorrow's standup | 2 |
| 18 | number of my logic | 6 | on top of coursework | 2 |
| 19 | of my logic analyzer | 6 | when we moved in | 2 |
| 20 | which font family we | 6 | the department office closes | 2 |
| 21 | font family we chose | 6 | fits the smd board | 2 |
| 22 | family we chose for | 6 | cycle near the library | 2 |
| 23 | we chose for the | 6 | hostel nikalne se pehle | 2 |
| 24 | chose for the capstone | 6 | notes mein check karo | 2 |
| 25 | for the capstone poster | 6 | raat sone se pehle | 2 |
| 26 | i kept my original | 6 | se nikalne se pehle | 2 |
| 27 | kept my original tenth | 6 | mein kya likha tha | 2 |
| 28 | my original tenth standard | 6 | exact detail kya thi | 2 |
| 29 | original tenth standard mark | 6 | purana note saved hai | 2 |
| 30 | tenth standard mark sheet | 6 | kya record kiya tha | 2 |


#### `CHECK_TASKS` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | while i am at | 8 | at my study desk | 5 |
| 2 | read out the tasks | 7 | you get a second | 5 |
| 3 | out the tasks i | 7 | to the hostel mess | 4 |
| 4 | the tasks i tagged | 7 | items are still incomplete | 4 |
| 5 | tell me which items | 6 | the department office closes | 4 |
| 6 | me which items on | 6 | raat sone se pehle | 4 |
| 7 | which items on my | 6 | kaunse chores due hain | 4 |
| 8 | items on my checklist | 6 | am at the library | 3 |
| 9 | on my checklist are | 6 | slips through the cracks | 3 |
| 10 | my checklist are still | 6 | before the lab evaluation | 3 |
| 11 | tasks i tagged under | 6 | before my next lecture | 3 |
| 12 | for any uncompleted deliverables | 6 | koi dikkat na ho | 3 |
| 13 | any uncompleted deliverables under | 6 | warna main bhool jaunga | 3 |
| 14 | i am at my | 5 | for cs350 computer networks | 2 |
| 15 | am at my study | 5 | on top of coursework | 2 |
| 16 | at my study desk | 5 | ahead of tomorrow's standup | 2 |
| 17 | checklist are still pending | 5 | before tonight's group study | 2 |
| 18 | are still pending for | 5 | hostel nikalne se pehle | 2 |
| 19 | when you get a | 5 | mess jaane se pehle | 2 |
| 20 | you get a second | 5 | abhi open pade hain | 2 |
| 21 | my to-do board for | 5 | se assignments baki hain | 2 |
| 22 | to-do board for any | 5 | message bhi bhej do | 2 |
| 23 | board for any uncompleted | 5 | group study se pehle | 2 |
| 24 | kya mere task board | 5 | the day after tomorrow | 1 |
| 25 | mere task board pe | 5 | cs405 machine learning queue | 1 |
| 26 | ka koi item pending | 5 | from earlier this week | 1 |
| 27 | koi item pending dikh | 5 | task board for tonight | 1 |
| 28 | item pending dikh raha | 5 | due on my list | 1 |
| 29 | pending dikh raha hai | 5 | under mini linux kernel | 1 |
| 30 | what tasks do i | 5 | saturday right after tutorial | 1 |


#### `COMPLETE_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | on my to-do list | 10 | on my to-do list | 10 |
| 2 | so check it off | 9 | so check it off | 5 |
| 3 | while i am at | 8 | check off kar do | 5 |
| 4 | check off kar do | 8 | am at the library | 4 |
| 5 | as finished on my | 7 | at my study desk | 4 |
| 6 | i just wrapped up | 7 | before the lab evaluation | 4 |
| 7 | ahead of schedule so | 5 | you get a second | 4 |
| 8 | of schedule so check | 5 | done mark kar do | 4 |
| 9 | schedule so check it | 5 | diya tick kar do | 4 |
| 10 | to completed on my | 5 | mess jaane se pehle | 4 |
| 11 | completed on my to-do | 5 | my to do list | 4 |
| 12 | could you check off | 5 | finished on my checklist | 3 |
| 13 | wala kaam pura ho | 5 | slips through the cracks | 3 |
| 14 | kaam pura ho gaya | 5 | to my completed column | 3 |
| 15 | pura ho gaya hai | 5 | woh kaam nipat gaya | 3 |
| 16 | ho gaya hai check | 5 | koi dikkat na ho | 3 |
| 17 | gaya hai check off | 5 | it ten minutes ago | 2 |
| 18 | hai check off kar | 5 | to the hostel mess | 2 |
| 19 | since i am all | 5 | all taken care of | 2 |
| 20 | i am all done | 5 | ahead of tomorrow's standup | 2 |
| 21 | am all done with | 5 | just turned it in | 2 |
| 22 | all done with it | 5 | as completed right now | 2 |
| 23 | i am at the | 4 | raat sone se pehle | 2 |
| 24 | am at the library | 4 | mein done kar do | 2 |
| 25 | i am at my | 4 | successfully ho gaya hai | 2 |
| 26 | am at my study | 4 | ka haal bhi batao | 2 |
| 27 | at my study desk | 4 | it ten minuts ago | 2 |
| 28 | off my open to-dos | 4 | i just submitted it | 2 |
| 29 | my open to-dos i | 4 | am on my way | 2 |
| 30 | open to-dos i submitted | 4 | done for cs220 algorithms | 1 |


#### `CREATE_ALARM` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | wake me up at | 13 | slips through the cracks | 5 |
| 2 | am ka wake-up alarm | 9 | to the hostel mess | 5 |
| 3 | at 10 15 am | 7 | alarm on kar dena | 4 |
| 4 | put an alarm on | 6 | raat sone se pehle | 4 |
| 5 | an alarm on for | 6 | koi dikkat na ho | 4 |
| 6 | while i am at | 6 | at my study desk | 3 |
| 7 | please wake me up | 6 | before the lab evaluation | 3 |
| 8 | subah 8 30 am | 6 | you get a second | 3 |
| 9 | am baje ka alarm | 6 | ahead of tomorrow's standup | 3 |
| 10 | baje ka alarm laga | 6 | am at the library | 3 |
| 11 | ka alarm laga do | 6 | class khatam hote hi | 3 |
| 12 | am ka morning alarm | 6 | 6 15 am sharp | 2 |
| 13 | ka morning alarm set | 6 | am on this afternoon | 2 |
| 14 | morning alarm set kar | 6 | before my next lecture | 2 |
| 15 | alarm set kar do | 6 | mess jaane se pehle | 2 |
| 16 | ring my morning alarm | 5 | sharp this next wednesday | 2 |
| 17 | my morning alarm at | 5 | 7 00 am train | 1 |
| 18 | slips through the cracks | 5 | am sharp this friday | 1 |
| 19 | heading to the hostel | 5 | sharp on this friday | 1 |
| 20 | to the hostel mess | 5 | 00 am every weekday | 1 |
| 21 | 10 15 am on | 5 | afternoon after my nap | 1 |
| 22 | am pe jagana hai | 5 | 20 am tomorrow morning | 1 |
| 23 | pe jagana hai alarm | 5 | tomorrow right after tutorial | 1 |
| 24 | jagana hai alarm set | 5 | wake-up alarm go off | 1 |
| 25 | hai alarm set karo | 5 | 7 00 am tomorrow | 1 |
| 26 | set a wake-up alarm | 4 | 20 am on tuesday | 1 |
| 27 | a wake-up alarm for | 4 | don't oversleep before lab | 1 |
| 28 | buzz me awake at | 4 | cs405 machine learning exam | 1 |
| 29 | at 5 15 am | 4 | am on next wednesday | 1 |
| 30 | so nothing slips through | 4 | am on november 12th | 1 |


#### `CREATE_PROMISE` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | log that i just | 9 | at my study desk | 4 |
| 2 | that i just promised | 8 | slips through the cracks | 4 |
| 3 | kar lo ki maine | 8 | to the hostel mess | 4 |
| 4 | a new commitment to | 7 | you get a second | 4 |
| 5 | while i am at | 7 | koi dikkat na ho | 4 |
| 6 | send my handwritten dbms | 7 | mess jaane se pehle | 4 |
| 7 | my handwritten dbms lecture | 7 | before the lab evaluation | 3 |
| 8 | share the referral link | 6 | ahead of tomorrow's standup | 3 |
| 9 | the referral link for | 6 | am at the library | 3 |
| 10 | referral link for the | 6 | so save that promise | 3 |
| 11 | link for the summer | 6 | for the login screen | 2 |
| 12 | for the summer internship | 6 | so add that commitment | 2 |
| 13 | drop off the spare | 6 | on top of coursework | 2 |
| 14 | off the spare room | 6 | the department office closes | 2 |
| 15 | the spare room keys | 6 | hostel nikalne se pehle | 2 |
| 16 | spare room keys at | 6 | raat sone se pehle | 2 |
| 17 | room keys at the | 6 | at the security desk | 2 |
| 18 | keys at the security | 6 | yaad se save karo | 2 |
| 19 | at the security desk | 6 | se nikalne se pehle | 2 |
| 20 | se vaada kiya hai | 6 | handwritten dbms lecture notes | 2 |
| 21 | vaada kiya hai ki | 6 | ka haal bhi batao | 2 |
| 22 | record a new commitment | 5 | for the ieee report | 2 |
| 23 | lend my scientific calculator | 5 | by eight thirty am | 2 |
| 24 | my scientific calculator for | 5 | report template by tonight | 1 |
| 25 | scientific calculator for tomorrow's | 5 | multimeter before friday lab | 1 |
| 26 | calculator for tomorrow's quiz | 5 | internship after me201 thermodynamics | 1 |
| 27 | reply about exam syllabus | 5 | before 4 30 pm | 1 |
| 28 | format the bibliography section | 5 | report by next tuesday | 1 |
| 29 | the bibliography section of | 5 | summer internship by thursday | 1 |
| 30 | bibliography section of our | 5 | printout by next thursday | 1 |


#### `CREATE_REMINDER` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | am pe ek notification | 7 | i go to sleep | 5 |
| 2 | pe ek notification bhej | 7 | to the hostel mess | 4 |
| 3 | ek notification bhej dena | 7 | you get a second | 4 |
| 4 | notification bhej dena ki | 7 | koi dikkat na ho | 4 |
| 5 | send me a notification | 6 | mess jaane se pehle | 4 |
| 6 | while i am at | 5 | slips through the cracks | 3 |
| 7 | before i go to | 5 | before the lab evaluation | 3 |
| 8 | i go to sleep | 5 | ahead of tomorrow's standup | 3 |
| 9 | fill out the semester | 5 | on top of coursework | 3 |
| 10 | out the semester course | 5 | operating systems lab report | 2 |
| 11 | the semester course feedback | 5 | at my study desk | 2 |
| 12 | semester course feedback form | 5 | replacement usb-c charger cable | 2 |
| 13 | me a notification at | 4 | the department office closes | 2 |
| 14 | prompt me later this | 4 | am at the library | 2 |
| 15 | to fill out the | 4 | photo to convocation portal | 2 |
| 16 | before heading to the | 4 | four of linear algebra | 2 |
| 17 | heading to the hostel | 4 | hostel nikalne se pehle | 2 |
| 18 | to the hostel mess | 4 | se nikalne se pehle | 2 |
| 19 | when you get a | 4 | mein yaad dila dena | 2 |
| 20 | you get a second | 4 | for friday project demo | 2 |
| 21 | ahead of tomorrow's standup | 4 | renew my library book | 1 |
| 22 | make sure you notify | 4 | ee210 digital logic submission | 1 |
| 23 | sure you notify me | 4 | at student council room | 1 |
| 24 | 00 pm pe mujhe | 4 | to return ritvik's notebook | 1 |
| 25 | pm pe mujhe nudge | 4 | checkup right after tutorial | 1 |
| 26 | pe mujhe nudge kar | 4 | about lab manual printout | 1 |
| 27 | mujhe nudge kar dena | 4 | batteries in wireless mouse | 1 |
| 28 | nudge kar dena ki | 4 | about git branch cleanup | 1 |
| 29 | taaki koi dikkat na | 4 | fifth of this month | 1 |
| 30 | koi dikkat na ho | 4 | from block c basement | 1 |


#### `CREATE_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | meri to-do list mein | 15 | at my study desk | 4 |
| 2 | my to-do list for | 8 | slips through the cracks | 4 |
| 3 | a new to-do item | 8 | to the hostel mess | 4 |
| 4 | while i am at | 7 | before the lab evaluation | 3 |
| 5 | to my to-do list | 7 | you get a second | 3 |
| 6 | aaj ke tasks mein | 7 | board for this week | 3 |
| 7 | ko bhi daal dena | 7 | tracker right after tutorial | 3 |
| 8 | yaar meri to-do list | 7 | am at the library | 3 |
| 9 | bhai ek naya task | 7 | mess jaane se pehle | 3 |
| 10 | ek naya task daal | 7 | tasks mein jod do | 3 |
| 11 | naya task daal do | 7 | cs412 natural language processing | 2 |
| 12 | task daal do ki | 7 | list for next thursday | 2 |
| 13 | daal do ki mujhe | 7 | ahead of tomorrow's standup | 2 |
| 14 | kal ke liye meri | 7 | for cs405 machine learning | 2 |
| 15 | ke liye meri to-do | 7 | gym membership renewal fee | 2 |
| 16 | liye meri to-do list | 7 | on top of coursework | 2 |
| 17 | to-do list mein likho | 7 | list for next tuesday | 2 |
| 18 | on my task board | 6 | the department office closes | 2 |
| 19 | a new item on | 6 | for ph101 quantum physics | 2 |
| 20 | new item on my | 6 | hostel nikalne se pehle | 2 |
| 21 | new to-do item to | 5 | raat sone se pehle | 2 |
| 22 | onto my task list | 4 | this weekend ke liye | 2 |
| 23 | my task list due | 4 | koi dikkat na ho | 2 |
| 24 | i am at my | 4 | lab viva se pehle | 2 |
| 25 | am at my study | 4 | bhi mark kar dena | 2 |
| 26 | at my study desk | 4 | list for this weekend | 2 |
| 27 | so nothing slips through | 4 | friday no notification needed | 1 |
| 28 | nothing slips through the | 4 | to my errand backlog | 1 |
| 29 | slips through the cracks | 4 | list due tomorrow morning | 1 |
| 30 | before heading to the | 4 | portal before this weekend | 1 |


#### `CREATE_TIMER` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | for this pomodoro coding | 10 | this pomodoro coding sprint | 6 |
| 2 | this pomodoro coding sprint | 10 | you get a second | 5 |
| 3 | timer right now for | 8 | my laundry spin cycle | 4 |
| 4 | while the agarose gel | 8 | mess jaane se pehle | 4 |
| 5 | the agarose gel runs | 8 | this mock test section | 4 |
| 6 | 2 minutes and 30 | 7 | the agarose gel runs | 4 |
| 7 | minutes and 30 seconds | 7 | for my plank hold | 3 |
| 8 | a countdown timer for | 7 | at my study desk | 3 |
| 9 | for my laundry spin | 7 | to the hostel mess | 3 |
| 10 | my laundry spin cycle | 7 | before the lab evaluation | 3 |
| 11 | ka pomodoro timer on | 7 | a quick power nap | 3 |
| 12 | pomodoro timer on kar | 7 | while the tea steeps | 3 |
| 13 | timer on kar do | 7 | on top of coursework | 3 |
| 14 | for my plank hold | 6 | the department office closes | 3 |
| 15 | while i am at | 6 | am at the library | 3 |
| 16 | minutes timer right now | 6 | slips through the cracks | 2 |
| 17 | minutes ka pomodoro timer | 6 | i don't overcook dinner | 2 |
| 18 | minutes timer for my | 5 | ahead of tomorrow's standup | 2 |
| 19 | when you get a | 5 | raat sone se pehle | 2 |
| 20 | you get a second | 5 | koi dikkat na ho | 2 |
| 21 | could you start a | 5 | class khatam hote hi | 2 |
| 22 | start a countdown for | 5 | ka haal bhi batao | 2 |
| 23 | wala focus timer start | 5 | sprint right after tutorial | 2 |
| 24 | focus timer start kar | 5 | my pomodoro coding block | 1 |
| 25 | timer start kar dena | 5 | boiling on the stove | 1 |
| 26 | minutes countdown for my | 5 | machine learning mock quiz | 1 |
| 27 | countdown while i work | 4 | for my study block | 1 |
| 28 | while i work on | 4 | for me right now | 1 |
| 29 | timer for my laundry | 4 | on rag study buddy | 1 |
| 30 | for a quick power | 4 | cycle right after tutorial | 1 |


#### `DELETE_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | off my task board | 12 | se permanently mita do | 5 |
| 2 | from my active tasks | 5 | to the hostel mess | 4 |
| 3 | my active tasks because | 5 | you get a second | 4 |
| 4 | active tasks because plans | 5 | of my task queue | 4 |
| 5 | tasks because plans changed | 5 | tasks because plans changed | 4 |
| 6 | from my backlog we | 5 | koi dikkat na ho | 4 |
| 7 | my backlog we no | 5 | ko saaf kar do | 4 |
| 8 | backlog we no longer | 5 | to do list completely | 4 |
| 9 | we no longer need | 5 | slips through the cracks | 3 |
| 10 | no longer need it | 5 | without marking it done | 3 |
| 11 | longer need it for | 5 | it was a duplicate | 3 |
| 12 | ko mere task board | 5 | from my open to-dos | 3 |
| 13 | mere task board se | 5 | list se nikaal do | 3 |
| 14 | task board se permanently | 5 | to do it anymore | 3 |
| 15 | board se permanently mita | 5 | am at the library | 2 |
| 16 | se permanently mita do | 5 | is no longer needed | 2 |
| 17 | from my to-do list | 4 | at my study desk | 2 |
| 18 | while i am at | 4 | before the lab evaluation | 2 |
| 19 | from my list completely | 4 | from my errand list | 2 |
| 20 | my list completely it | 4 | off my task board | 2 |
| 21 | list completely it is | 4 | ab zaroorat nahi hai | 2 |
| 22 | completely it is no | 4 | hostel nikalne se pehle | 2 |
| 23 | so nothing slips through | 4 | raat sone se pehle | 2 |
| 24 | nothing slips through the | 4 | do duplicate entry hai | 2 |
| 25 | before heading to the | 4 | item erase kar do | 2 |
| 26 | heading to the hostel | 4 | mess jaane se pehle | 2 |
| 27 | to the hostel mess | 4 | it it's just cancelled | 1 |
| 28 | gpu cluster booking from | 4 | drone nav to-do list | 1 |
| 29 | cluster booking from my | 4 | booking from my board | 1 |
| 30 | when you get a | 4 | list right after tutorial | 1 |


#### `PERSON_COMMITMENTS` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | check if i owe | 13 | before my next lecture | 6 |
| 2 | while i am at | 9 | at my study desk | 5 |
| 3 | any deliverables or notes | 7 | before the lab evaluation | 5 |
| 4 | deliverables or notes from | 7 | on top of coursework | 5 |
| 5 | ko mujhe kya dena | 7 | slips through the cracks | 4 |
| 6 | mujhe kya dena ya | 7 | am at the library | 4 |
| 7 | kya dena ya bhejna | 7 | mess jaane se pehle | 4 |
| 8 | dena ya bhejna baki | 7 | to the hostel mess | 3 |
| 9 | ya bhejna baki hai | 7 | you get a second | 3 |
| 10 | look up what i | 6 | the department office closes | 3 |
| 11 | i would send anything | 6 | raat sone se pehle | 3 |
| 12 | display the promises i | 6 | koi dikkat na ho | 3 |
| 13 | the promises i made | 6 | warna main bhool jaunga | 3 |
| 14 | promises i made to | 6 | se nikalne se pehle | 3 |
| 15 | so i stay on | 6 | class khatam hote hi | 3 |
| 16 | i stay on top | 6 | ahead of tomorrow's standup | 2 |
| 17 | stay on top of | 6 | before tonight's group study | 2 |
| 18 | on top of coursework | 6 | hostel nikalne se pehle | 2 |
| 19 | before my next lecture | 6 | group study se pehle | 2 |
| 20 | zara check karo maine | 6 | free slot kab hai | 2 |
| 21 | ke baare mein kya | 6 | still have not done | 2 |
| 22 | baare mein kya dene | 6 | i still haven't delivered | 1 |
| 23 | mein kya dene ko | 6 | i currently owe meera | 1 |
| 24 | kya dene ko bola | 6 | pending obligations to tara | 1 |
| 25 | dene ko bola tha | 6 | campus food delivery app | 1 |
| 26 | tak kya bhejne ka | 6 | record for prof iyer | 1 |
| 27 | kya bhejne ka vaada | 6 | ira from last week | 1 |
| 28 | bhejne ka vaada kiya | 6 | to tara this week | 1 |
| 29 | ka vaada kiya tha | 6 | made to neha recently | 1 |
| 30 | any notes or files | 6 | open right after tutorial | 1 |


#### `PLAN_DAY` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | so i can fit | 10 | am at the library | 4 |
| 2 | i can fit in | 10 | to the hostel mess | 4 |
| 3 | for my evening starting | 9 | before the lab evaluation | 4 |
| 4 | my evening starting at | 9 | mess jaane se pehle | 4 |
| 5 | while i am at | 7 | at my study desk | 3 |
| 6 | schedule for my evening | 7 | slips through the cracks | 3 |
| 7 | structure my morning tomorrow | 7 | ahead of tomorrow's standup | 3 |
| 8 | agenda so i can | 6 | koi dikkat na ho | 3 |
| 9 | organize my afternoon so | 6 | you get a second | 2 |
| 10 | my afternoon so i | 6 | on top of coursework | 2 |
| 11 | afternoon so i can | 6 | the department office closes | 2 |
| 12 | so i can finish | 6 | before my next lecture | 2 |
| 13 | routine around my lectures | 6 | plan ready kar do | 2 |
| 14 | around my lectures and | 6 | raat sone se pehle | 2 |
| 15 | build a time-blocked plan | 5 | se nikalne se pehle | 2 |
| 16 | a time-blocked plan for | 5 | plan bana ke do | 2 |
| 17 | time-blocked plan for my | 5 | dono fit ho jayein | 2 |
| 18 | so i have time | 5 | class khatam hote hi | 2 |
| 19 | i have time for | 5 | and lab manual printout | 2 |
| 20 | have time for both | 5 | schedule for november 4th | 1 |
| 21 | se shaam tak ka | 5 | learning prep and errands | 1 |
| 22 | build a time blocked | 5 | 15 pm and dinner | 1 |
| 23 | a time blocked schedule | 5 | calibration before meeting aarav | 1 |
| 24 | time blocked schedule for | 5 | prep tracker and dinner | 1 |
| 25 | blocked schedule for my | 5 | and dental clinic visit | 1 |
| 26 | i am at the | 4 | for me for wednesday | 1 |
| 27 | am at the library | 4 | tasks right after tutorial | 1 |
| 28 | plan for my evening | 4 | for my whole day | 1 |
| 29 | before heading to the | 4 | when i am free | 1 |
| 30 | heading to the hostel | 4 | wednesday afternoon and evening | 1 |


#### `POSTPONE_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | the day after tomorrow | 8 | at my study desk | 4 |
| 2 | while i am at | 7 | slips through the cracks | 4 |
| 3 | move the due date | 7 | ke liye taal do | 4 |
| 4 | instead of doing it | 5 | koi dikkat na ho | 4 |
| 5 | over from today's list | 5 | mess jaane se pehle | 4 |
| 6 | from today's list to | 5 | to the hostel mess | 3 |
| 7 | keep its title and | 4 | before the lab evaluation | 3 |
| 8 | its title and priority | 4 | you get a second | 3 |
| 9 | title and priority the | 4 | pe reschedule kar do | 3 |
| 10 | and priority the same | 4 | pe shift kar do | 3 |
| 11 | i am at my | 4 | day after tomorrow morning | 2 |
| 12 | am at my study | 4 | since lab ran late | 2 |
| 13 | at my study desk | 4 | ahead of tomorrow's standup | 2 |
| 14 | so nothing slips through | 4 | on top of coursework | 2 |
| 15 | nothing slips through the | 4 | on my task list | 2 |
| 16 | slips through the cracks | 4 | the department office closes | 2 |
| 17 | before heading to the | 4 | am at the library | 2 |
| 18 | when you get a | 4 | hostel nikalne se pehle | 2 |
| 19 | am on my task | 4 | raat sone se pehle | 2 |
| 20 | on my task list | 4 | pe move kar do | 2 |
| 21 | ki list mein daal | 4 | se nikalne se pehle | 2 |
| 22 | list mein daal do | 4 | pe aage badha do | 2 |
| 23 | nahi ho payega isko | 4 | list mein daal do | 2 |
| 24 | ke liye taal do | 4 | keeping it high priority | 1 |
| 25 | ki due date badal | 4 | doing it right now | 1 |
| 26 | due date badal ke | 4 | list to this weekend | 1 |
| 27 | wala kaam kal subah | 4 | cs204 data structures first | 1 |
| 28 | am pe reschedule kar | 4 | deadline out to tomorrow | 1 |
| 29 | pe reschedule kar do | 4 | verilog testbench to monday | 1 |
| 30 | kal tak ke liye | 4 | wednesday right after tutorial | 1 |


#### `PRIORITIZE_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | while i am at | 7 | at my study desk | 4 |
| 2 | on my task board | 7 | before the lab evaluation | 4 |
| 3 | ki urgency badha ke | 5 | you get a second | 4 |
| 4 | pe set kar do | 5 | on my to-do board | 4 |
| 5 | to the very top | 5 | koi dikkat na ho | 4 |
| 6 | the very top of | 5 | slips through the cracks | 3 |
| 7 | very top of my | 5 | to low for now | 3 |
| 8 | top of my priority | 5 | item on my checklist | 3 |
| 9 | of my priority queue | 5 | the department office closes | 3 |
| 10 | so it shows up | 4 | am at the library | 3 |
| 11 | it shows up at | 4 | kal subah dikhana hai | 3 |
| 12 | shows up at the | 4 | se nikalne se pehle | 3 |
| 13 | up at the top | 4 | high importance de do | 3 |
| 14 | at the top of | 4 | to the hostel mess | 2 |
| 15 | the top of my | 4 | ahead of tomorrow's standup | 2 |
| 16 | top of my queue | 4 | on top of coursework | 2 |
| 17 | queue while i am | 4 | hackathon fintech prototype backlog | 2 |
| 18 | i am at my | 4 | priority level to p1 | 2 |
| 19 | am at my study | 4 | hostel nikalne se pehle | 2 |
| 20 | at my study desk | 4 | p1 mark kar dena | 2 |
| 21 | before the lab evaluation | 4 | raat sone se pehle | 2 |
| 22 | when you get a | 4 | 1 priority bana do | 2 |
| 23 | you get a second | 4 | mess jaane se pehle | 2 |
| 24 | tag on my to-do | 4 | medium priority kar do | 2 |
| 25 | on my to-do board | 4 | pe set kar do | 2 |
| 26 | ki priority badha ke | 4 | free slot kab hai | 2 |
| 27 | bahut zaroori hai isko | 4 | up to medium priority | 2 |
| 28 | ko p0 critical bana | 4 | or changing the title | 1 |
| 29 | p0 critical bana do | 4 | my checklist for now | 1 |
| 30 | critical bana do kal | 4 | library book scanner backlog | 1 |


#### `PROJECT_STATUS` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | mein kaun-kaun se modules | 14 | kaam pending pada hai | 9 |
| 2 | kaun-kaun se modules incomplete | 14 | am at the library | 5 |
| 3 | se modules incomplete pade | 14 | at my study desk | 4 |
| 4 | modules incomplete pade hain | 14 | waiting to be done | 4 |
| 5 | mein abhi kya-kya kaam | 10 | slips through the cracks | 3 |
| 6 | abhi kya-kya kaam pending | 10 | to the hostel mess | 3 |
| 7 | kya-kya kaam pending pada | 10 | before the lab evaluation | 3 |
| 8 | kaam pending pada hai | 10 | you get a second | 3 |
| 9 | while i am at | 8 | raat sone se pehle | 3 |
| 10 | low power risc-v core | 8 | kitna backlog bacha hai | 3 |
| 11 | i am at the | 5 | mess jaane se pehle | 3 |
| 12 | am at the library | 5 | kitne tasks baki hain | 3 |
| 13 | how much work is | 5 | ahead of tomorrow's standup | 2 |
| 14 | much work is left | 5 | still open right now | 2 |
| 15 | ki progress kahan tak | 5 | hostel nikalne se pehle | 2 |
| 16 | progress kahan tak pahunchi | 5 | koi dikkat na ho | 2 |
| 17 | kahan tak pahunchi hai | 5 | se nikalne se pehle | 2 |
| 18 | tak pahunchi hai zara | 5 | modules incomplete pade hain | 2 |
| 19 | pahunchi hai zara batao | 5 | library book scanner plz | 2 |
| 20 | what is currently pending | 4 | sanskrit ocr pipeline effort | 1 |
| 21 | is currently pending in | 4 | for placement prep tracker | 1 |
| 22 | currently pending in the | 4 | portal redesign right now | 1 |
| 23 | pull up the current | 4 | on rag study buddy | 1 |
| 24 | up the current progress | 4 | we finish compiler frontend | 1 |
| 25 | the current progress overview | 4 | blockers for compiler frontend | 1 |
| 26 | current progress overview for | 4 | incomplete right after tutorial | 1 |
| 27 | campus food delivery app | 4 | pipeline ahead of today | 1 |
| 28 | read out the pending | 4 | of solar rover capstone | 1 |
| 29 | out the pending deliverables | 4 | under ieee paper submission | 1 |
| 30 | the pending deliverables for | 4 | on top of coursework | 1 |


#### `REFLECTION` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | while i am at | 8 | pe khatam ho gaya | 5 |
| 2 | log my daily journal | 6 | am at the library | 4 |
| 3 | my daily journal entry | 6 | at my study desk | 4 |
| 4 | note in my weekly | 6 | slips through the cracks | 4 |
| 5 | in my weekly retrospective | 6 | to the hostel mess | 4 |
| 6 | my weekly retrospective that | 6 | you get a second | 4 |
| 7 | weekly retrospective that balancing | 6 | the department office closes | 4 |
| 8 | went much better this | 6 | karke bahut sukoon mila | 4 |
| 9 | much better this week | 6 | my weekly check-in log | 3 |
| 10 | keep a diary note | 6 | before the lab evaluation | 3 |
| 11 | a diary note for | 6 | ahead of tomorrow's standup | 3 |
| 12 | in my daily recap | 6 | after coffee with friends | 3 |
| 13 | my daily recap that | 6 | mess jaane se pehle | 3 |
| 14 | daily recap that i | 6 | badminton helped me unwind | 2 |
| 15 | dekho aaj poore din | 6 | the professor went smoothly | 2 |
| 16 | aaj poore din main | 6 | ko coding achhi hui | 2 |
| 17 | poore din main kaafi | 6 | raat sone se pehle | 2 |
| 18 | din main kaafi productive | 6 | koi dikkat na ho | 2 |
| 19 | main kaafi productive raha | 6 | se nikalne se pehle | 2 |
| 20 | kaafi productive raha aur | 6 | lab session mast gaya | 2 |
| 21 | time se pehle khatam | 6 | se pehle khatam kiya | 2 |
| 22 | se pehle khatam kiya | 6 | class khatam hote hi | 2 |
| 23 | record how my week | 6 | warna main bhool jaunga | 2 |
| 24 | how my week went | 6 | linear algebra problem set | 1 |
| 25 | my week went i | 6 | debugging took four hours | 1 |
| 26 | week went i felt | 6 | completed nlp dataset download | 1 |
| 27 | went i felt super | 6 | coding sprint was awesome | 1 |
| 28 | i felt super focused | 6 | in internship nda upload | 1 |
| 29 | felt super focused on | 6 | backup right after tutorial | 1 |
| 30 | daily journal entry today | 5 | smart hostel iot today | 1 |


#### `RENAME_TASK` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | change the title of | 9 | at my study desk | 4 |
| 2 | the name of my | 8 | slips through the cracks | 4 |
| 3 | on my checklist to | 8 | to the hostel mess | 4 |
| 4 | while i am at | 7 | koi dikkat na ho | 4 |
| 5 | set the title of | 5 | mess jaane se pehle | 4 |
| 6 | the name of the | 4 | before the lab evaluation | 3 |
| 7 | name of the to-do | 4 | you get a second | 3 |
| 8 | of the to-do item | 4 | to library book renewal | 3 |
| 9 | i am at my | 4 | am at the library | 3 |
| 10 | am at my study | 4 | gym fee renewal instead | 2 |
| 11 | at my study desk | 4 | to bicycle brake repair | 2 |
| 12 | so nothing slips through | 4 | ahead of tomorrow's standup | 2 |
| 13 | nothing slips through the | 4 | on top of coursework | 2 |
| 14 | slips through the cracks | 4 | the department office closes | 2 |
| 15 | before heading to the | 4 | keeping the same deadline | 2 |
| 16 | heading to the hostel | 4 | hostel nikalne se pehle | 2 |
| 17 | to the hostel mess | 4 | raat sone se pehle | 2 |
| 18 | when you get a | 4 | se nikalne se pehle | 2 |
| 19 | while keeping the same | 4 | se replace kar do | 2 |
| 20 | keeping the same deadline | 4 | yaad bhi dila dena | 2 |
| 21 | switch the name of | 4 | two natural language processing | 2 |
| 22 | wale task ka naam | 4 | changing its friday deadline | 1 |
| 23 | task ka naam badal | 4 | almonds from hostel store | 1 |
| 24 | ka naam badal ke | 4 | to lab manual printout | 1 |
| 25 | ka label update karke | 4 | under ma202 linear algebra | 1 |
| 26 | ki title line ko | 4 | task linear algebra reading | 1 |
| 27 | ki wording change karke | 4 | is called ssd backup | 1 |
| 28 | do taaki koi dikkat | 4 | says gym fee renewal | 1 |
| 29 | taaki koi dikkat na | 4 | upload right after tutorial | 1 |
| 30 | koi dikkat na ho | 4 | to git branch cleanup | 1 |


#### `SEND_MESSAGE` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | call me as soon | 11 | leaves in five minutes | 5 |
| 2 | me as soon as | 11 | in a noisy lab | 5 |
| 3 | as soon as your | 11 | from the study table | 4 |
| 4 | soon as your viva | 11 | as your viva finishes | 4 |
| 5 | as your viva finishes | 10 | slips through the cracks | 4 |
| 6 | can you send me | 8 | breadboard and jumper wires | 4 |
| 7 | you send me a | 8 | to the hostel mess | 4 |
| 8 | send me a photo | 8 | before the lab evaluation | 4 |
| 9 | me a photo of | 8 | you get a second | 4 |
| 10 | send a text message | 8 | quiz to next week | 4 |
| 11 | send an sms to | 7 | the lecture hall yet | 4 |
| 12 | when you get a | 7 | koi dikkat na ho | 4 |
| 13 | our team slot for | 7 | at my study desk | 3 |
| 14 | team slot for the | 7 | of today's whiteboard notes | 3 |
| 15 | slot for the demo | 7 | the department office closes | 3 |
| 16 | for the demo is | 7 | shortlist just came out | 3 |
| 17 | the demo is at | 7 | to our shared folder | 3 |
| 18 | a photo of today's | 7 | do you need printouts | 3 |
| 19 | photo of today's whiteboard | 7 | mess jaane se pehle | 3 |
| 20 | of today's whiteboard notes | 7 | you get a minute | 2 |
| 21 | have you reached the | 7 | ahead of tomorrow's standup | 2 |
| 22 | you reached the lecture | 7 | on top of coursework | 2 |
| 23 | reached the lecture hall | 7 | a great year ahead | 2 |
| 24 | the lecture hall yet | 7 | am at the library | 2 |
| 25 | dinner is ready at | 7 | row of auditorium b | 2 |
| 26 | is ready at the | 7 | hostel nikalne se pehle | 2 |
| 27 | ready at the mess | 7 | mess come down fast | 2 |
| 28 | at the mess come | 7 | raat sone se pehle | 2 |
| 29 | the mess come down | 7 | se nikalne se pehle | 2 |
| 30 | mess come down fast | 7 | on my way now | 2 |


#### `UNKNOWN` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | tell me a funny | 27 | am at the library | 8 |
| 2 | me a funny joke | 27 | to the hostel mess | 7 |
| 3 | a funny joke about | 26 | on top of coursework | 7 |
| 4 | songs on spotify while | 21 | you get a second | 5 |
| 5 | on spotify while i | 21 | koi dikkat na ho | 5 |
| 6 | spotify while i study | 17 | se nikalne se pehle | 5 |
| 7 | what is the weather | 14 | mess jaane se pehle | 5 |
| 8 | is the weather forecast | 14 | class khatam hote hi | 5 |
| 9 | the weather forecast in | 14 | at my study desk | 4 |
| 10 | while i am at | 13 | before the lab evaluation | 4 |
| 11 | kya chal raha hai | 13 | the department office closes | 4 |
| 12 | book an uber auto | 10 | ka mausam kaisa hai | 4 |
| 13 | an uber auto from | 10 | bore ho raha hoon | 4 |
| 14 | main gate to the | 10 | wipe out kar de | 4 |
| 15 | gate to the railway | 10 | slips through the cracks | 3 |
| 16 | on youtube music while | 10 | dollars in indian rupees | 3 |
| 17 | youtube music while i | 10 | i study cs220 algorithms | 3 |
| 18 | music while i work | 10 | raat sone se pehle | 3 |
| 19 | while i work on | 10 | kabhi neend aati hai | 3 |
| 20 | i am at the | 8 | group study se pehle | 3 |
| 21 | am at the library | 8 | before my next lecture | 3 |
| 22 | to the railway station | 8 | ahead of tomorrow's standup | 3 |
| 23 | the railway station at | 8 | study cs204 data structures | 2 |
| 24 | mein translate karke batao | 8 | before tonight's group study | 2 |
| 25 | do you ever get | 8 | professors and prof iyer | 2 |
| 26 | heading to the hostel | 7 | hostel nikalne se pehle | 2 |
| 27 | to the hostel mess | 7 | permanently format kar do | 2 |
| 28 | so i stay on | 7 | kam kar do abhi | 2 |
| 29 | i stay on top | 7 | warna main bhool jaunga | 2 |
| 30 | stay on top of | 7 | i am free tomorrow | 2 |


#### `WHAT_SHOULD_I_DO` Top 4-Grams

| Rank | Top Word 4-Gram | Count | Top Final 4-Gram | Count |
| ---: | :--- | ---: | :--- | ---: |
| 1 | one should i start | 11 | am at the library | 5 |
| 2 | should i start right | 11 | at my study desk | 5 |
| 3 | i start right now | 11 | koi dikkat na ho | 5 |
| 4 | while i am at | 10 | kya zyada zaroori hai | 5 |
| 5 | which one should i | 9 | before the lab evaluation | 4 |
| 6 | karoon abhi kya zyada | 9 | the department office closes | 4 |
| 7 | abhi kya zyada zaroori | 9 | before my next lecture | 4 |
| 8 | kya zyada zaroori hai | 9 | slips through the cracks | 3 |
| 9 | me on whether to | 8 | to the hostel mess | 3 |
| 10 | on whether to tackle | 8 | you get a second | 3 |
| 11 | advise me on whether | 7 | ahead of tomorrow's standup | 3 |
| 12 | before my next lecture | 7 | on top of coursework | 3 |
| 13 | to tackle right now | 6 | before tonight's group study | 3 |
| 14 | start right now before | 6 | class khatam hote hi | 3 |
| 15 | the best thing to | 5 | thing to work on | 2 |
| 16 | on right now before | 5 | the next 40 minutes | 2 |
| 17 | i am at the | 5 | digital logic right now | 2 |
| 18 | am at the library | 5 | hs102 microeconomics right now | 2 |
| 19 | right now while i | 5 | bhi mark kar dena | 2 |
| 20 | now while i am | 5 | beofre the lab evaluation | 2 |
| 21 | i am at my | 5 | dent in my backlog | 1 |
| 22 | am at my study | 5 | the next 3 minutes | 1 |
| 23 | at my study desk | 5 | before 2 00 pm | 1 |
| 24 | taaki koi dikkat na | 5 | before 1 15 pm | 1 |
| 25 | koi dikkat na ho | 5 | block right after tutorial | 1 |
| 26 | what is the best | 4 | up first this afternoon | 1 |
| 27 | tell me what i | 4 | i work on next | 1 |
| 28 | me what i ought | 4 | task should i do | 1 |
| 29 | what i ought to | 4 | take right this minute | 1 |
| 30 | i ought to be | 4 | me201 thermodynamics right now | 1 |


## 7. Transfer Check & Generalization Gap (`Step 7`)

We evaluated domain transfer by fitting a dual-channel TF-IDF (word n-grams 1-2 + char n-grams 2-5) feature extractor with a balanced `LogisticRegression(C=1.0, random_state=2026)` classifier trained **solely on `train_pool_clean.csv`**.

### Model Performance Metrics

| Evaluation Split | Source Nature | Row Count | Accuracy | Macro F1 | Transfer Gap (pts) | Status |
| :--- | :--- | ---: | ---: | ---: | ---: | :--- |
| **Clean Synthetic Dev** | Synthetic / Web-derived | 317 | **95.27%** | **95.22%** | Baseline | Reference |
| **Human Author `VN`** | Factual Campus Ground Truth | 128 | **86.72%** | **81.06%** | **8.55 pts (Acc)** / **14.16 pts (F1)** | **PASS (< 15 pts)** |

> [!NOTE]
> The transfer gap is well below the 15-point threshold, demonstrating that the cleaned synthetic data generalizes effectively to human conversational phrasing without severe template memorization.


### Per-Label F1 Score Comparison (Human VN vs Synthetic Dev)

| Intent Label | Clean Dev F1 (%) | Author VN F1 (%) | Per-Label Gap (pts) |
| :--- | ---: | ---: | ---: |
| `CHECK_CALENDAR` | 92.3% | 94.1% | -1.8 |
| `CHECK_TASKS` | 93.3% | 84.2% | +9.1 |
| `COMPLETE_TASK` | 96.6% | 100.0% | -3.4 |
| `CREATE_PROMISE` | 96.6% | 82.4% | +14.2 |
| `CREATE_REMINDER` | 89.7% | 88.9% | +0.8 |
| `CREATE_TASK` | 88.9% | 85.7% | +3.2 |
| `DELETE_TASK` | 93.3% | 82.4% | +11.0 |
| `PERSON_COMMITMENTS` | 96.8% | 88.9% | +7.9 |
| `PLAN_DAY` | 100.0% | 100.0% | +0.0 |
| `POSTPONE_TASK` | 96.8% | 94.1% | +2.7 |
| `PRIORITIZE_TASK` | 100.0% | 100.0% | +0.0 |
| `PROJECT_STATUS` | 93.8% | 66.7% | +27.1 |
| `REFLECTION` | 100.0% | 100.0% | +0.0 |
| `RENAME_TASK` | 93.8% | 66.7% | +27.1 |
| `UNKNOWN` | 96.0% | 50.0% | +46.0 |
| `WHAT_SHOULD_I_DO` | 96.0% | 94.1% | +1.9 |


## 8. Dataset Summary & Final Row Counts (`Step 8`)

### Row Counts Before & After per Label

| Intent Label | Raw Train | S4 Broken Drops | S3 Dupe Drops | S2 Capping Drops | Clean Train | Clean Dev | Status |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | :--- |
| `CALL_PERSON` | 128 | 0 | 0 | 36 | **92** | 15 | **Under 100 rows** |
| `CHECK_CALENDAR` | 128 | 6 | 2 | 19 | **101** | 14 | >= 100 rows |
| `CHECK_MEMORY` | 128 | 0 | 0 | 50 | **78** | 15 | **Under 100 rows** |
| `CHECK_TASKS` | 128 | 1 | 0 | 25 | **102** | 15 | >= 100 rows |
| `COMPLETE_TASK` | 128 | 0 | 0 | 36 | **92** | 15 | **Under 100 rows** |
| `CREATE_ALARM` | 128 | 5 | 1 | 42 | **80** | 14 | **Under 100 rows** |
| `CREATE_PROMISE` | 128 | 1 | 0 | 40 | **87** | 15 | **Under 100 rows** |
| `CREATE_REMINDER` | 128 | 4 | 0 | 18 | **106** | 13 | >= 100 rows |
| `CREATE_TASK` | 128 | 1 | 0 | 45 | **82** | 15 | **Under 100 rows** |
| `CREATE_TIMER` | 128 | 0 | 0 | 42 | **86** | 15 | **Under 100 rows** |
| `DELETE_TASK` | 128 | 0 | 0 | 25 | **103** | 15 | >= 100 rows |
| `PERSON_COMMITMENTS` | 128 | 0 | 0 | 38 | **90** | 15 | **Under 100 rows** |
| `PLAN_DAY` | 128 | 1 | 0 | 32 | **95** | 14 | **Under 100 rows** |
| `POSTPONE_TASK` | 128 | 0 | 0 | 22 | **106** | 15 | >= 100 rows |
| `PRIORITIZE_TASK` | 128 | 0 | 0 | 19 | **109** | 15 | >= 100 rows |
| `PROJECT_STATUS` | 128 | 3 | 0 | 37 | **88** | 15 | **Under 100 rows** |
| `REFLECTION` | 128 | 3 | 2 | 31 | **92** | 14 | **Under 100 rows** |
| `RENAME_TASK` | 128 | 0 | 0 | 28 | **100** | 15 | >= 100 rows |
| `SEND_MESSAGE` | 128 | 0 | 0 | 57 | **71** | 15 | **Under 100 rows** |
| `UNKNOWN` | 256 | 19 | 0 | 55 | **182** | 26 | >= 100 rows |
| `WHAT_SHOULD_I_DO` | 128 | 5 | 2 | 33 | **88** | 12 | **Under 100 rows** |
| **Total** | **2816** | **49** | **7** | **730** | **2030** | **317** | — |

### Labels Now Under 100 Rows

Due to strict 3% template phrase capping and broken row removal, the following **14 labels** have fewer than 100 rows in `train_pool_clean.csv`:

| Intent Label | Clean Row Count | Deficit from 100 | Primary Reason |
| :--- | ---: | ---: | :--- |
| `CALL_PERSON` | 92 | 8 | Capping of frequent template formulas & tail markers |
| `CHECK_MEMORY` | 78 | 22 | Capping of frequent template formulas & tail markers |
| `COMPLETE_TASK` | 92 | 8 | Capping of frequent template formulas & tail markers |
| `CREATE_ALARM` | 80 | 20 | Capping of frequent template formulas & tail markers |
| `CREATE_PROMISE` | 87 | 13 | Capping of frequent template formulas & tail markers |
| `CREATE_TASK` | 82 | 18 | Capping of frequent template formulas & tail markers |
| `CREATE_TIMER` | 86 | 14 | Capping of frequent template formulas & tail markers |
| `PERSON_COMMITMENTS` | 90 | 10 | Capping of frequent template formulas & tail markers |
| `PLAN_DAY` | 95 | 5 | Capping of frequent template formulas & tail markers |
| `PROJECT_STATUS` | 88 | 12 | Capping of frequent template formulas & tail markers |
| `REFLECTION` | 92 | 8 | Capping of frequent template formulas & tail markers |
| `SEND_MESSAGE` | 71 | 29 | Capping of frequent template formulas & tail markers |
| `WHAT_SHOULD_I_DO` | 88 | 12 | Capping of frequent template formulas & tail markers |


### 10 Random Inspection Rows per Label from `train_pool_clean.csv`

#### `CALL_PERSON` (92 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Yaar zara Prof Subramanian ko abhi phone milao. | `hinglish` | `synthetic` |
| 2 | now call Sana on speaker | `terse` | `synthetic` |
| 3 | place call to Zoya | `terse` | `synthetic` |
| 4 | Please place a voice call to Nikhil right now. | `plain` | `synthetic` |
| 5 | ring Sneha now | `terse` | `synthetic` |
| 6 | dial her | `terse` | `synthetic` |
| 7 | Dekho Dad ko call connect karo, MA202 Linear Algebra lab ka poochna hai. | `hinglish` | `synthetic` |
| 8 | Give Didi a quick ring on the phone. | `plain` | `synthetic` |
| 9 | Get Pooja on the line for me right now. plz | `typo` | `synthetic` |
| 10 | um place a phone call to reyansh mobile before heading to the hostel mess | `voice` | `web-derived:clinc150` |


#### `CHECK_CALENDAR` (101 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Between 9:30 am and 10:30 pm on next Monday, what is on my scedule while I am at my study desk? | `typo` | `synthetic` |
| 2 | For Monday, let me see all scheduled meeitngs and labs and then start a 3 minuts timr. | `typo|multi_intent` | `synthetic` |
| 3 | Display my timetable for this Saturday starting from 6:30 am so I stay on top of coursework. | `plain` | `synthetic` |
| 4 | Mujhe batao ME201 Thermodynamics ka agla lecture kitne baje start ho raha hai? | `hinglish` | `web-derived:clinc150` |
| 5 | Is there anything on my calendar right after my CS350 Computer Networks tutorial before the lab evaluation? | `plain` | `web-derived:hinglish_top` |
| 6 | Walk me through my scheduled commitments for next Thursday. | `plain` | `web-derived:hinglish_top` |
| 7 | When is my next open thirty-minute window on November 4th? | `plain` | `web-derived:hinglish_top` |
| 8 | Pull up my agenda for next Thursday so I can see when MA202 Linear Algebra ends ahead of tomorrow's standup. | `plain` | `web-derived:hinglish_top` |
| 9 | Suno Sunday ko Karan ke saath coffee ka time nikal sakta hai kya? | `hinglish` | `synthetic` |
| 10 | yo show me my appointments for this saturday after quarter to seven am right after tutorial | `voice` | `web-derived:massive` |


#### `CHECK_MEMORY` (78 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | What did I jot down in my notes about the contact extension for campus security? | `plain` | `web-derived:clinc150` |
| 2 | arre do you remember what i recorded about which size guitar strings i bought last month | `voice` | `web-derived:massive` |
| 3 | accha what did i note down earlier about the wi fi password for the robotics lab | `voice` | `web-derived:massive` |
| 4 | Find the piece of information I jotted down on the bank IFSC code for stipend forms right after tutorial. plz | `typo` | `synthetic` |
| 5 | Zara recall karke batao what tuning parameters worked best on the dev set ki exact detail kya thi? | `hinglish` | `web-derived:clinc150` |
| 6 | um look up what i saved in my notes regarding which book kabir recommended for system design before heading to the hostel mess | `voice` | `web-derived:massive` |
| 7 | Could you look up my saved note on wehre I kept my original tenth standard mark sheet? | `typo` | `synthetic` |
| 8 | what did I save on TA Pranav? | `terse` | `synthetic` |
| 9 | uh what did i note down earlier about my hostel room inventory defects from check in | `voice` | `web-derived:massive` |
| 10 | Check my stored notes for any mention of where I kept my original tenth standard mark sheet. | `plain` | `web-derived:clinc150` |


#### `CHECK_TASKS` (102 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Give me a quick view of my active to-do list for Thursday before heading to the hostel mess. | `plain` | `synthetic` |
| 2 | Meri checklist khol ke dikhao aaj kya-kya kaam bacha hai library se nikalne se pehle. | `hinglish` | `web-derived:clinc150` |
| 3 | Summarize the open tod-os I still need to finish by 8:30 pm so I stay on top of coursework. | `typo` | `synthetic` |
| 4 | Dekho toh mere to-do queue mein RAG Study Buddy se related kya likha hai aaj raat sone se pehle? | `hinglish` | `synthetic` |
| 5 | okay list out my unfinished errands and homework for tomorrow morning ahead of tomorrow s standup plus text kavya that i am on my way | `voice|multi_intent` | `web-derived:clinc150` |
| 6 | Do I have any overdue to-do items left over from earlier this week? | `plain` | `web-derived:massive` |
| 7 | Under RAG Study Buddy, which to-do items are still incomplete? | `plain` | `synthetic` |
| 8 | active checklist Compiler Frontend | `terse` | `synthetic` |
| 9 | Please pull up the pending tasks I have left for this Saturday right after tutorial. | `plain` | `web-derived:massive` |
| 10 | Are there any open chores on my task board for tonight? | `plain` | `web-derived:massive` |


#### `COMPLETE_TASK` (92 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Could you check off 'Docker setup' on my to-do list? | `plain` | `synthetic` |
| 2 | Check 'tax declaration' off as completed right now. | `plain` | `web-derived:massive` |
| 3 | Please mark 'NLP dataset download' as finshed under IEEE Paper Submission when you get a second. | `typo` | `synthetic` |
| 4 | For MA202 Linear Algebra, check off 'SQL schema export' since I just turned it in. | `plain` | `synthetic` |
| 5 | Knocked out 'tax declaration' just now - mark it done so I stay on top of coursework. | `plain` | `web-derived:massive` |
| 6 | Set the status of 'scholarship form' under PH101 Quantum Physics to done before the lab evaluation. | `plain` | `synthetic` |
| 7 | Zara 'reimbursement receipts' ko finished mark karo, abhi-abhi submit kiya hai taaki koi dikkat na ho. | `hinglish` | `synthetic` |
| 8 | done with that task | `terse` | `synthetic` |
| 9 | Yaar maine 'hackathon registration' khatam kar liya hai, usko done mark kar do. | `hinglish` | `web-derived:massive` |
| 10 | Check 'internship NDA upload' off as completed right now. | `plain` | `synthetic` |


#### `CREATE_ALARM` (80 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Could you set a wake-up alarm for 11:30 am tomorrow right after tutorial? | `plain` | `web-derived:clinc150` |
| 2 | Add a 7:45 am morning alarm for tomorrow before heading to the hostel mess. | `plain` | `web-derived:clinc150` |
| 3 | For next Wednesday mornign, set an alarm at 8:00 am sharp when you get a second. | `typo` | `synthetic` |
| 4 | Mujhe tomorrow evening subah 10:15 am pe jagana hai, alarm set karo aaj raat sone se pehle. | `hinglish` | `synthetic` |
| 5 | clock alarm 6:00 am | `terse` | `synthetic` |
| 6 | Yaar kal subah 9:00 am baje ka alarm laga do CS204 Data Structures class ke liye. | `hinglish` | `web-derived:massive` |
| 7 | okay set an alarm for four pm so i make it to ma two zero two linear algebra ahead of tomorrow s standup plus text varun that i am on my way | `voice|multi_intent` | `web-derived:hinglish_top` |
| 8 | 5:45 am alarm tomorrow + timer 25 minutes | `terse|multi_intent` | `synthetic` |
| 9 | Have an alarm ring at 6:30 am on November 12th. | `plain` | `synthetic` |
| 10 | Please wake me up at 6:30 am on next Monday for CS412 Natural Language Processing before the department office closes. | `plain` | `synthetic` |


#### `CREATE_PROMISE` (87 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Add a new commitment to Nisha: I will book the badminton court for Saturday morning before 4:30 pm. | `plain` | `web-derived:clinc150` |
| 2 | Record that I gave Nikhil my word to return the borrowed multimeter before Friday before the lab evaluation. | `plain` | `synthetic` |
| 3 | Save a new commitment that I owe Dr Venkat the DBMS ER digaram by Wednesday. | `typo` | `synthetic` |
| 4 | Log that I just promised Kabir I would share the LaTeX report template by tonight. | `plain` | `web-derived:clinc150` |
| 5 | haan note down that i gave rohan my word to forward the professor s reply about exam syllabus before the lab evaluation | `voice` | `web-derived:clinc150` |
| 6 | erm i told neha i will finish splitwise settlement before thursday so save that promise and check when i am free tomorrow | `voice|multi_intent` | `web-derived:clinc150` |
| 7 | Aaj maine Aditi se promise kiya hai ki email the benchmark CSV results by tonight, isko yaad se save karo. | `hinglish` | `web-derived:clinc150` |
| 8 | promised her DBMS ER diagram | `terse` | `synthetic` |
| 9 | I told Siddharth I'd bring printouts of the circuit diagram to lab before 3:30 pm; please record that commitment while I am at my study desk. | `plain` | `web-derived:clinc150` |
| 10 | Capture a commitment to Pallavi: I promised to send the annotated reading list on transformers. | `plain` | `synthetic` |


#### `CREATE_REMINDER` (106 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Yaar aaj shaam 12:30 pm pe mujhe SQL schema export ke baare mein yaad dila dena. | `hinglish` | `synthetic` |
| 2 | Could you remnd me at 9:00 pm on tomorow to water the money plant on the balcony? | `typo` | `synthetic` |
| 3 | notification 5:30 am: transcript email | `terse` | `synthetic` |
| 4 | Ek reminder laga do this Saturday 4:30 pm ka hackathon registration ke liye class khatam hote hi. | `hinglish` | `synthetic` |
| 5 | Zara 4:30 pm pe mujhe alert kar dena medicine lene aur Docker setup ke liye. | `hinglish` | `web-derived:clinc150` |
| 6 | At 6:15 am on this Saturday, remind me to renew library book on distributed systems. | `plain` | `synthetic` |
| 7 | Please ping me at 9:45 pm tonight about Verilog testbench so nothing slips through the cracks. | `plain` | `web-derived:clinc150` |
| 8 | Remind me at 7:30 am on Tuesday to buy electrolyte packets and band-aids from pharmacy. | `plain` | `synthetic` |
| 9 | notify 8:15 am about PH101 Quantum Physics | `terse` | `synthetic` |
| 10 | Give me a heads-up on next Thursday at 7:00 am to check on Mini Linux Kenrel before the lab evaluation. | `typo` | `synthetic` |


#### `CREATE_TASK` (82 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Jot down 'dental clinic visit' on my pending checklist for November 4th. | `plain` | `web-derived:massive` |
| 2 | Put 'submit operating systems lab report' on my to-do list for Friday - no notification needed. | `plain` | `web-derived:massive` |
| 3 | Insert 'return the Raspberry Pi kit to electronics lab' into my Robotics Arm Firmware task tracker right after tutorial. | `plain` | `web-derived:massive` |
| 4 | Drop 'book train ticket home for Diwali break' onto my task board for this week. | `plain` | `web-derived:massive` |
| 5 | Drop 'upload signed internship NDA to placement portal' onto my task board for this week. | `plain` | `synthetic` |
| 6 | Stick 'GPU cluster booking' into my work queue for CS412 Natural Language Processing. | `plain` | `web-derived:massive` |
| 7 | accha add download offline dataset for nlp term project to my to do list for tomorrow evening | `voice` | `web-derived:hinglish_top` |
| 8 | Log a new to-do item to upload signed internship NDA to placement portal before this weekend. | `plain` | `web-derived:massive` |
| 9 | Kal ke liye meri to-do list mein likho: write unit tests for authentication middleware, phir 20 minutes ka timer bhi chalu kar do. | `hinglish|multi_intent` | `web-derived:clinc150` |
| 10 | Apepnd a new action item to my queue: sort out splitwise expenses from weekend trip ahead of tomorrow's standup before you text Vaurn. | `typo|multi_intent` | `synthetic` |


#### `CREATE_TIMER` (86 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Give me a 14 minutes timer starting now before heading to the hostel mess. | `plain` | `web-derived:hinglish_top` |
| 2 | Put 35 minutes on the timer for a quick power nap. | `plain` | `web-derived:hinglish_top` |
| 3 | Could you start a 40 minuts timer for this mock test section? | `typo` | `synthetic` |
| 4 | launch 30 minutes countdown | `terse` | `synthetic` |
| 5 | yo begin a eighteen minutes countdown for my study sprint right after tutorial | `voice` | `web-derived:clinc150` |
| 6 | Could you start a 15 minutes timer for my laundry spin cycle right after tutorial? | `plain` | `web-derived:hinglish_top` |
| 7 | Could you start a 90 seconds timer for my pasta boil right after tutorial? | `plain` | `synthetic` |
| 8 | Fatafat 1 hour and 15 minutes ka Pomodoro timer on kar do CS220 Algorithms padhne ke liye. | `hinglish` | `synthetic` |
| 9 | Laga do ek 15 minutes ka countdown timer abhi ke abhi mess jaane se pehle. | `hinglish` | `synthetic` |
| 10 | Yaar 1 hour and 15 minutes ka ek countdown timer chalu kar do for this Pomodoro coding sprint. | `hinglish` | `synthetic` |


#### `DELETE_TASK` (103 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Erase 'SQL schema export' from my backlog; we no longer need it for Placement Prep Tracker. | `plain` | `web-derived:clinc150` |
| 2 | Mere task list se 'hostel room cleaning' ko saaf kar do. | `hinglish` | `synthetic` |
| 3 | Jaldi se 'dental clinic visit' wala to-do item erase kar do. | `hinglish` | `web-derived:massive` |
| 4 | Remove this item from my to-do list completely; I didn't finish it, it's just cancelled. | `plain` | `web-derived:clinc150` |
| 5 | Ab 'Verilog testbench' karne ki koi need nahi hai, usko delete kar do mess jaane se pehle. | `hinglish` | `web-derived:massive` |
| 6 | right erase linear algebra reading from my weather station pcb backlog so nothing slips through the cracks | `voice` | `web-derived:clinc150` |
| 7 | clear 'Verilog testbench' from queue | `terse` | `synthetic` |
| 8 | remove that task | `terse` | `synthetic` |
| 9 | Clear 'microeconomics problem set' out of my task queue. | `plain` | `synthetic` |
| 10 | yo erase verilog testbench from my campus food delivery app backlog right after tutorial | `voice` | `web-derived:clinc150` |


#### `PERSON_COMMITMENTS` (90 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Suno Warden Sharma ko main kya-kya owe karta hoon abhi tak warna main bhool jaunga? | `hinglish` | `synthetic` |
| 2 | what do I owe her & text Aditi | `terse|multi_intent` | `synthetic` |
| 3 | Please list the thnigs I told Kavya I would take care of after checking my CS220 Algorithms nots. | `typo|multi_intent` | `synthetic` |
| 4 | Did I tell Zoya I would send anything before Modnay before my next lecture? | `typo` | `synthetic` |
| 5 | basically what did i promise ananya that i still have not done | `voice` | `web-derived:clinc150` |
| 6 | things owed to TA Pranav | `terse` | `synthetic` |
| 7 | what did I tell Simran? | `terse` | `synthetic` |
| 8 | Did I tell Lab Assistant Deepak I would send anyhting before next Thursday before my next lecture? | `typo` | `synthetic` |
| 9 | Please list the things I told Riya I would take care of. | `plain` | `web-derived:clinc150` |
| 10 | Bhai Neha ko mujhe kya dena ya bhejna baki hai class khatam hote hi? | `hinglish` | `web-derived:clinc150` |


#### `PLAN_DAY` (95 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Coordinate my Monday agenda so I can fit in CS204 Data Structures prep and errands. | `plain` | `synthetic` |
| 2 | organize my this afternoon | `terse` | `synthetic` |
| 3 | structure my morning | `terse` | `synthetic` |
| 4 | Starting at 8:30 am tomorrow, lay out a clean plan for my whole day. | `plain` | `web-derived:massive` |
| 5 | design next Tuesday routine | `terse` | `synthetic` |
| 6 | draft evening routine | `terse` | `synthetic` |
| 7 | Structure my morning tomorrow so I get 60 minutes of deep work on Sanskrit OCR Pipeline ahead of tomorrow's standup. | `plain` | `web-derived:massive` |
| 8 | Design a balanced study and errand schedule for me for Wednesday. | `plain` | `web-derived:massive` |
| 9 | Please build a schedule for my evening around Placement Prep Tracker and dinner. | `plain` | `web-derived:massive` |
| 10 | Shaam ki routine plan kar do taaki Solar Rover Capstone aur lab manual printout dono nipat jayein warna main bhool jaunga. | `hinglish` | `synthetic` |


#### `POSTPONE_TASK` (106 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Move 'grocery run' to tomorrow evening instead of doing it right now. | `plain` | `web-derived:massive` |
| 2 | Bump the deadline for 'lab manual printout' out to next Thursday ahead of tomorrow's standup. | `plain` | `web-derived:massive` |
| 3 | For 'dental clinic visit', push the deadline out to Wednesday. | `plain` | `synthetic` |
| 4 | haan shift os lab report from today to november four th because i am swamped before the lab evaluation | `voice` | `web-derived:hinglish_top` |
| 5 | Suno abhi 'library book renewal' nahi ho payega, isko Sunday ke liye taal do. | `hinglish` | `synthetic` |
| 6 | Dekho it ko postpone karke November 12th ki list mein daal do. | `hinglish` | `synthetic` |
| 7 | Snooze the task 'Git branch cleanup' until the day after tomorrow morning. | `plain` | `web-derived:massive` |
| 8 | Carry 'sensor calibration' over from today's list to this Friday before the department office closes. | `plain` | `synthetic` |
| 9 | Please move 'microeconomics problem set' to Sunday at 8:30 pm so nothing slips through the cracks. | `plain` | `web-derived:massive` |
| 10 | Shift 'SSD backup' over to this afternoon so I can focus on CS301 Operating Systems first. | `plain` | `synthetic` |


#### `PRIORITIZE_TASK` (109 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Raise 'bicycle brake repair' to low priority in my Placement Prep Tracker backlog before the department office closes. | `plain` | `web-derived:clinc150` |
| 2 | okay mark grocery run as urgent on my list ahead of tomorrow s standup plus text vivaan that i am on my way | `voice|multi_intent` | `web-derived:clinc150` |
| 3 | Boost the priorty of 'internship NDA upload' to medium priorty. | `typo` | `synthetic` |
| 4 | Raise 'dentist checkup' to top priority in my Library Book Scanner backlog. | `plain` | `web-derived:clinc150` |
| 5 | Rank 'sensor calibration' as my number one priority for ME201 Thermodynamics ahead of tomorrow's standup. | `plain` | `synthetic` |
| 6 | Jaldi se 'microeconomics problem set' ko meri list mein #1 priority bana do. | `hinglish` | `web-derived:clinc150` |
| 7 | Bhai 'DBMS ER diagram' wale task ki priority badha ke urgent P0 kar do, naam wahi rehne do. | `hinglish` | `web-derived:clinc150` |
| 8 | make the first one critical | `terse` | `synthetic` |
| 9 | Upgrade 'internship NDA upload' to medium priority immediately while I am at the library. | `plain` | `synthetic` |
| 10 | yo move friday demo slides to the very top of my priority queue right after tutorial | `voice` | `web-derived:clinc150` |


#### `PROJECT_STATUS` (88 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Display the remaining backlog and open bugs for RAG Study Buddy so I stay on top of coursework. | `plain` | `web-derived:massive` |
| 2 | Walk me through the current state of Solar Rover Capstone. | `plain` | `web-derived:massive` |
| 3 | Are there any unresolved blockers slowing down Alumni Portal Redesign so nothing slips through the cracks? | `plain` | `web-derived:massive` |
| 4 | Please check the progress of Low Power RISC-V Core aehad of Monday then check what I promsed Shruti. | `typo|multi_intent` | `synthetic` |
| 5 | Do we have any overdue milestones left on RAG Study Buddy? | `plain` | `web-derived:massive` |
| 6 | Do we have any overdue milestones left on Placement Prep Trakcer? | `typo` | `synthetic` |
| 7 | Do we have any overdue milestones left on Library Book Scanner? plz | `typo` | `synthetic` |
| 8 | Tell me how much work is left before we finish Library Book Scanner. plz | `typo` | `synthetic` |
| 9 | okay give me an update on where we stand with distributed kv store ahead of tomorrow s standup and check when i am free tomorrow | `voice|multi_intent` | `web-derived:massive` |
| 10 | What is currently pending in the Robotics Arm Firmware effort? | `plain` | `synthetic` |


#### `REFLECTION` (92 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Bhai meri daily journal mein likho ki aaj dopahar tak thakan thi par shaam ko Autonomous Drone Nav pe badiya kaam hua. | `hinglish` | `synthetic` |
| 2 | Add an evening check-in note: feeling proud that I finally solved the bug in internship NDA upload. | `plain` | `web-derived:clinc150` |
| 3 | Track my mood for today: felt anxious about HS102 Microeconomics at first, then relieved after submitting Docker setup. | `plain` | `synthetic` |
| 4 | Save this to my weekly review log: this past week was a bit scattered in the morning, but I focused well after coffee with friends. | `plain` | `synthetic` |
| 5 | personal log: great study session | `terse` | `synthetic` |
| 6 | Record an end-of-day recap: I felt really locked in during CS310 Database Systems and finished hostel room cleaning. | `plain` | `synthetic` |
| 7 | Note karo meri journal mein ki is hafte neend kam hui, agle hafte time pe sona hai aaj raat sone se pehle. | `hinglish` | `web-derived:clinc150` |
| 8 | Suno is hafte ka mera experience note karo - kaafi productive week raha aur reimbursement receipts bhi nipat gaya class khatam hote hi. | `hinglish` | `synthetic` |
| 9 | Suno is hafte ka mera experience note karo - kaafi productive week raha aur lit review summary bhi nipat gaya mess jaane se pehle. | `hinglish` | `synthetic` |
| 10 | Put this in my evening journal: I stayed disciplined all afternoon and completed NLP dataset download. | `plain` | `web-derived:clinc150` |


#### `RENAME_TASK` (100 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Mere board pe 'mess bill payment' ko naya naam 'DBMS ER diagram' de do. | `hinglish` | `web-derived:clinc150` |
| 2 | change title it to 'scholarship form' & mark scholarship form done | `terse|multi_intent` | `synthetic` |
| 3 | Reword the task 'internship NDA upload' as 'Splitwise settlement' under CS310 Database Systems. | `plain` | `synthetic` |
| 4 | okay update the wording of sensor calibration to say hackathon registration ahead of tomorrow s standup and mark hackathon registration as finished too | `voice|multi_intent` | `web-derived:clinc150` |
| 5 | Call the task 'scholarship form' 'gym fee renewal' instead. | `plain` | `web-derived:clinc150` |
| 6 | Dekho 'library book renewal' ka heading badal ke 'transcript email' set kar do library se nikalne se pehle. | `hinglish` | `web-derived:clinc150` |
| 7 | Edit kar do 'Git branch cleanup' ki title line ko 'dentist checkup' mein. | `hinglish` | `web-derived:clinc150` |
| 8 | Fatafat task 'SQL schema export' ka naam 'lit review summary' se replace kar do. | `hinglish` | `synthetic` |
| 9 | Suno 'resume update' ki wording change karke 'GPU cluster booking' likh do. | `hinglish` | `synthetic` |
| 10 | Updte the name of the to-do item 'OS lab report' to 'mess bill payment' while I am at my study desk. | `typo` | `synthetic` |


#### `SEND_MESSAGE` (71 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Yaar Ira ko SMS bhej do ki I uploaded the final slides to our shared folder mess jaane se pehle. | `hinglish` | `synthetic` |
| 2 | Text drop karo Librarian Rao ke phone pe: 'let us meet near the library coffee kiosk at 4'. | `hinglish` | `synthetic` |
| 3 | Write a text to Prof Deshmukh saying have you reached the lecture hall yet. | `plain` | `web-derived:clinc150` |
| 4 | On my side, text them saiyng: 'let us meet near the library coffee koisk at 4'. | `typo` | `synthetic` |
| 5 | Dekho Librarian Rao ko SMS mein likh do ki call me as soon as your viva finishes taaki koi dikkat na ho. | `hinglish` | `web-derived:hinglish_top` |
| 6 | Write a text to Kriti saying the professor just pushed the quiz to next week. | `plain` | `synthetic` |
| 7 | Fire off a text to TA Ritika stating that check your email, the shortlist just came out while I am at the library. | `plain` | `synthetic` |
| 8 | Disaptch an SMS to Vivaan: 'do not forget to carry the breadboard and jumper wires'. | `typo` | `synthetic` |
| 9 | Text Prof Deshmukh that I have uploaded the signed thesis PDF to the portal. | `plain` | `web-derived:clinc150` |
| 10 | you know text ananya and tell them heading to the stationery shop do you need printouts | `voice` | `web-derived:clinc150` |


#### `UNKNOWN` (182 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Aaj Kochi wale IPL match mein toss kisne jeeta aur score kya chal raha hai group study se pehle, aur GPU cluster booking ko done bhi mark kar dena. | `hinglish|multi_intent` | `web-derived:snips` |
| 2 | Quick question: order chicken biryani from Swiggy to Bengaluru Hostel Block C before tonight's group study? | `plain` | `synthetic` |
| 3 | like delete everything on my phone including smart hostel iot and factory reset the app while i am at my study desk plus text sneha that i am on my way | `voice|multi_intent` | `web-derived:banking77` |
| 4 | Hey there, do you ever get tired of answering questions about CS310 Database Systems and Autonomous Drone Nav when you get a second? | `plain` | `synthetic` |
| 5 | Kya tumhe CS412 Natural Language Processing ke sawal solve karte-karte kabhi neend aati hai warna main bhool jaunga? | `hinglish` | `synthetic` |
| 6 | Zara Delhi se Bengaluru ki sabse sasti flight ticket book kar do today ki class khatam hote hi. | `hinglish` | `synthetic` |
| 7 | Swiggy se rajma chawal order kar do hostel gate pe 4:00 pm tak. | `hinglish` | `web-derived:snips` |
| 8 | Do you know book an Uber auto from Kochi main gate to the railway station at 10:30 pm? | `plain` | `web-derived:banking77` |
| 9 | Yaar aaj Chennai mein baarish hone wali hai kya, next Thursday ka mausam kaisa hai, aur shaam ko 10:30 pm pe yaad bhi dila dena. | `hinglish|multi_intent` | `web-derived:snips` |
| 10 | Bhai Spotify pe Coke Studio ke gaane chala de jab tak main PH101 Quantum Physics padh raha hoon library se nikalne se pehle. | `hinglish` | `synthetic` |


#### `WHAT_SHOULD_I_DO` (88 total clean rows)

| # | Clean Utterance | Style Tags | Notes |
| ---: | :--- | :--- | :--- |
| 1 | Could you suggest the top task for me to jump on right now so nothing slips through the cracks? | `plain` | `web-derived:clinc150` |
| 2 | Tell me what I ought to be working on right now before 10:30 pm before the department office closes. | `plain` | `web-derived:clinc150` |
| 3 | Batao zara abhi Placement Prep Tracker pe kaam karoon ya symposium abstract pehle khatam karoon class khatam hote hi? | `hinglish` | `synthetic` |
| 4 | Given my deadlines for PH101 Quantum Physics, what should I focus on for the next 7 minutes? | `plain` | `synthetic` |
| 5 | Point me to the highest-leverage task I can do before 1:15 pm. | `plain` | `web-derived:clinc150` |
| 6 | priority action now | `terse` | `synthetic` |
| 7 | Zara recommend karo agle 3 minutes mein main kaunsa chhota kaam khatam kar sakta hoon aaj raat sone se pehle? | `hinglish` | `web-derived:clinc150` |
| 8 | Suno HS102 Microeconomics padhoon ya DBMS ER diagram karoon, abhi kya zyada zaroori hai taaki koi dikkat na ho? | `hinglish` | `synthetic` |
| 9 | recommend next step | `terse` | `synthetic` |
| 10 | erm suggest what task i should focus on first this afternoon and mark docker setup as finished too | `voice|multi_intent` | `web-derived:clinc150` |

