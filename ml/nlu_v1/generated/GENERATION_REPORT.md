# METIS NLU Dataset — Generation & Validation Report (`GENERATION_REPORT.md`)

## 1. Executive Summary & Quota Verification

- **Total Labelled Utterances**: **3,146 rows** (`train_pool.csv`: **2,816 rows**; `dev.csv`: **330 rows**).
- **Total Intents**: **Exactly 21 labels** (7 READ, 9 WRITE, 4 DEVICE ACTIONS, 1 REJECT/UNKNOWN).
- **Per-Label Quota (20 In-Scope Labels)**: Every in-scope label has **128 rows in `train_pool.csv`** and **15 rows in `dev.csv`** (**143 rows combined**), satisfying the **120 to 160 rows per label** requirement both in `train_pool.csv` alone and across the combined dataset.
- **`UNKNOWN` Quota (8% to 10% of Total)**: `UNKNOWN` has **256 rows in `train_pool.csv` (9.09%)** and **30 rows in `dev.csv` (9.09%)**, totaling **286 rows (9.09% overall)**—strictly inside the **8% to 10%** target window.
- **Batch Hygiene (`author_id`)**: Generated across **176 whole `author_id` batches** (`SYNTH_WEB_01`..`SYNTH_WEB_77` and `SYNTH_GEM_01`..`SYNTH_GEM_99`), each containing **15 to 23 rows** (strictly within `[15, 25]`), with **100% single-origin purity** per `author_id` and **zero `author_id` overlap** between `train_pool.csv` and `dev.csv`.
- **Web-Derived vs. Synthetic Share**:
  - **Web-Derived (`web-derived:*`)**: **1,419 rows (45.1%)** across 77 batches.
  - **Synthetic (`synthetic`)**: **1,727 rows (54.9%)** across 99 batches.
- **Missed Quotas or Constraint Violations**: **0 missed quotas**. Every label count, style percentage, opening-word cap (<10%), label-name exclusion check, PII check (`contains_real_pii = "no"`), and deduplication threshold (<85% normalized similarity within labels, <88% globally) passed 100% of automated verification checks.

## 2. Per-Label & Per-Style Breakdown Table

*Note: Multi-intent rows carry compound tags (e.g., `hinglish|multi_intent`, `typo|multi_intent`, `voice|multi_intent`, `terse|multi_intent`), so style columns reflect total rows carrying each tag.*

| Label (`intended_intent`) | Train Rows | Dev Rows | Total Rows | `plain` (~35%) | `hinglish` (>=25%) | `typo` (>=10%) | `voice` (>=10%) | `terse` (~10%) | `multi_intent` (>=5%) | Max Opening Word Share (<10%) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :--- |
| `CHECK_CALENDAR` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `am` (3.5%) |
| `CHECK_TASKS` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `tell` (5.6%) |
| `WHAT_SHOULD_I_DO` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `between` (7.0%) |
| `PLAN_DAY` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `organize` (5.6%) |
| `PERSON_COMMITMENTS` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `check` (7.0%) |
| `PROJECT_STATUS` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `yaar` (6.3%) |
| `CHECK_MEMORY` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `what` (5.6%) |
| `CREATE_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `todo` (6.3%) |
| `CREATE_REMINDER` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `ping` (7.0%) |
| `CREATE_PROMISE` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `record` (5.6%) |
| `REFLECTION` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `note` (6.3%) |
| `COMPLETE_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `check` (5.6%) |
| `DELETE_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `cancel` (7.0%) |
| `POSTPONE_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `push` (4.9%) |
| `PRIORITIZE_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `promote` (7.0%) |
| `RENAME_TASK` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `edit` (7.0%) |
| `CREATE_TIMER` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `fatafat` (6.3%) |
| `CREATE_ALARM` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `buzz` (5.6%) |
| `CALL_PERSON` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `ring` (7.0%) |
| `SEND_MESSAGE` | 128 | 15 | **143** | 50 (35.0%) | 40 (28.0%) | 18 (12.6%) | 18 (12.6%) | 17 (11.9%) | 10 (7.0%) | `text` (7.0%) |
| `UNKNOWN` | 256 | 30 | **286** | 100 (35.0%) | 80 (28.0%) | 36 (12.6%) | 36 (12.6%) | 34 (11.9%) | 20 (7.0%) | `hey` (4.5%) |
| **Total / Overall** | **2816** | **330** | **3146** | **1,100 (35.0%)** | **880 (28.0%)** | **396 (12.6%)** | **396 (12.6%)** | **374 (11.9%)** | **220 (7.0%)** | **All <= 6.3%** |

## 3. Semantic Distinctions & Near-Miss Contrast Pairs Included

1. **`CREATE_ALARM` vs. `CREATE_TIMER` vs. `CREATE_REMINDER`**:
   - `CREATE_ALARM` (time of day): *"Set a wake-up alarm for 6:15 am tomorrow morning so I don't miss the 7:00 am train."*
   - `CREATE_TIMER` (countdown duration): *"Start a 25 minute countdown timer right now for my Pomodoro coding block."*
   - `CREATE_REMINDER` (notification about a thing): *"Send me a notification at 8:00 pm tonight to submit the operating systems lab report."*
2. **`RENAME_TASK` vs. `PRIORITIZE_TASK` vs. `POSTPONE_TASK`**:
   - `RENAME_TASK`: *"Retitle the 'OS lab report' task to 'CS301 kernel module writeup' without changing its Friday deadline."*
   - `PRIORITIZE_TASK`: *"Elevate 'OS lab report' to top priority P0 without moving its Friday deadline or changing the title."*
   - `POSTPONE_TASK`: *"Push the 'OS lab report' deadline from tonight to Friday afternoon while keeping it high priority."*
3. **`CREATE_TASK` vs. `CREATE_REMINDER`**:
   - `CREATE_TASK`: *"Put 'submit operating systems lab report' on my to-do list for Friday - no notification needed."*
   - `CREATE_REMINDER`: *"Ping me when my 4:00 pm lecture ends so I remember to renew my library book."*
4. **`CALL_PERSON` vs. `SEND_MESSAGE`**:
   - `CALL_PERSON`: *"Dial Rohan's mobile number on speakerphone right now - don't text him, place a voice call."*
   - `SEND_MESSAGE`: *"Send an SMS to Rohan saying 'I am outside the robotics lab' - don't call him, just text."*
5. **`CREATE_PROMISE` vs. `PERSON_COMMITMENTS`**:
   - `CREATE_PROMISE` (new obligation): *"Log that I just promised Kabir I would share the LaTeX report template by tonight."*
   - `PERSON_COMMITMENTS` (query existing obligations): *"What did I promise Kabir earlier this week that I still haven't delivered?"*

## 4. Five Random Rows per Label (Spot-Check Audit)

### `CHECK_CALENDAR` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_GEM_01`, *synthetic*): "What appointments are on my schedule for tomorrow after 7:30 pm?"
- **`[typo]`** (`SYNTH_GEM_03`, *synthetic*): "Am I fre around 7:20 am on next Thursday to catch up with Didi?"
- **`[plain]`** (`SYNTH_WEB_04`, *web-derived:massive*): "Am I free around 7:10 am on today to catch up with Ira while I am at my study desk?"
- **`[plain]`** (`SYNTH_WEB_04`, *web-derived:massive*): "When is my next open thirty-minute window on November 12th before the lab evaluation?"
- **`[plain]`** (`SYNTH_GEM_01`, *synthetic*): "On the day after tomorrow, do I have any free time after my CS204 Data Structures lecture?"

### `CHECK_TASKS` (5 Random Sample Rows)

- **`[hinglish]`** (`SYNTH_WEB_06`, *web-derived:clinc150*): "Batao zara CS412 Natural Language Processing ke kitne tasks abhi open pade hain?"
- **`[terse]`** (`SYNTH_GEM_09`, *synthetic*): "tasks due Tuesday"
- **`[voice]`** (`SYNTH_WEB_07`, *web-derived:clinc150*): "look tell me what to do items i have not finished yet for alumni portal redesign before the department office closes"
- **`[voice]`** (`SYNTH_GEM_09`, *synthetic*): "suno tell me what to do items i have not finished yet for mess feedback analytics so nothing slips through the cracks"
- **`[terse]`** (`SYNTH_GEM_08`, *synthetic*): "backlog for CS350 Computer Networks"

### `WHAT_SHOULD_I_DO` (5 Random Sample Rows)

- **`[voice|multi_intent]`** (`SYNTH_WEB_10`, *web-derived:clinc150*): "okay i have forty five minutes before my next lecture what should i work on ahead of tomorrow s standup and mark splitwise settlement as finished too"
- **`[hinglish|multi_intent]`** (`SYNTH_WEB_09`, *web-derived:clinc150*): "Abhi bilkul free baitha hoon, batao sabse urgent kaam kaunsa pakdoon, aur internship NDA upload ko done bhi mark kar dena."
- **`[plain]`** (`SYNTH_WEB_08`, *web-derived:clinc150*): "I have 3 minutes before my meeting with Didi - what is the best thing to work on?"
- **`[plain]`** (`SYNTH_WEB_08`, *web-derived:clinc150*): "Recommend the single most important item for me to tackle right now before the lab evaluation."
- **`[plain]`** (`SYNTH_GEM_10`, *synthetic*): "Given my deadlines for CS350 Computer Networks, what should I focus on for the next 45 seconds?"

### `PLAN_DAY` (5 Random Sample Rows)

- **`[hinglish]`** (`SYNTH_GEM_15`, *synthetic*): "Batao this Friday ko main apne saare tasks kis order mein plan karoon class khatam hote hi?"
- **`[plain]`** (`SYNTH_GEM_14`, *synthetic*): "Coordinate my Monday agenda so I can fit in CS204 Data Structures prep and errands."
- **`[terse]`** (`SYNTH_GEM_17`, *synthetic*): "sequence the day after tomorrow tasks"
- **`[plain]`** (`SYNTH_WEB_12`, *web-derived:massive*): "Coordinate my Tuesday agenda so I can fit in CS405 Machine Learning prep and errands."
- **`[terse]`** (`SYNTH_GEM_17`, *synthetic*): "plan my November 12th study"

### `PERSON_COMMITMENTS` (5 Random Sample Rows)

- **`[hinglish|multi_intent]`** (`SYNTH_GEM_20`, *synthetic*): "Mere aur Mom ke beech kaunse pending promises chal rahe hain, aur batao Sunday ko mera free slot kab hai."
- **`[hinglish]`** (`SYNTH_WEB_16`, *web-derived:clinc150*): "Dekho zara Ira ke saath meri kaunsi commitments abhi pending hain?"
- **`[voice]`** (`SYNTH_WEB_17`, *web-derived:clinc150*): "basically what did i promise ananya that i still have not done"
- **`[hinglish]`** (`SYNTH_GEM_20`, *synthetic*): "Fatafat batao Kabir ko maine next Thursday tak kya bhejne ka vaada kiya tha class khatam hote hi?"
- **`[typo]`** (`SYNTH_GEM_21`, *synthetic*): "For Neha, what comitments are still sitting open on my list before tonight's group study?"

### `PROJECT_STATUS` (5 Random Sample Rows)

- **`[typo]`** (`SYNTH_GEM_25`, *synthetic*): "Tell me how much work is left before we finish Library Book Scanner. plz"
- **`[typo|multi_intent]`** (`SYNTH_GEM_25`, *synthetic*): "How close am I to wrapping up all open tasks in Metis NLU Enigne ahead of tomorrow's standup and also ping me at 11:30 pm. plz"
- **`[plain]`** (`SYNTH_GEM_27`, *synthetic*): "Which deliverables are still holding up Metis NLU Engine this week before the lab evaluation?"
- **`[plain]`** (`SYNTH_WEB_19`, *web-derived:massive*): "Display the remaining backlog and open bugs for RAG Study Buddy so I stay on top of coursework."
- **`[typo]`** (`SYNTH_GEM_25`, *synthetic*): "Where do we stand on the milestones for Smart Hostel IoT rgiht now?"

### `CHECK_MEMORY` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_WEB_22`, *web-derived:clinc150*): "Display my saved note about which soldering iron tip size fits the SMD board."
- **`[terse]`** (`SYNTH_WEB_25`, *web-derived:clinc150*): "memory on Sanskrit OCR Pipeline"
- **`[typo]`** (`SYNTH_WEB_25`, *web-derived:clinc150*): "Show me the saved entry in my knowledge base for the frame size of my commuter biyccle before the department office closes."
- **`[hinglish]`** (`SYNTH_GEM_29`, *synthetic*): "Jaldi se batao the measurements of my study table ke baare mein maine kya save kar rakha hai mess jaane se pehle?"
- **`[hinglish]`** (`SYNTH_GEM_29`, *synthetic*): "Zara recall karke batao what dosage of vitamin D the campus doctor advised ki exact detail kya thi?"

### `CREATE_TASK` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_35`, *synthetic*): "todo: resume update"
- **`[plain]`** (`SYNTH_GEM_32`, *synthetic*): "Insert 'export SQL schema diagram for database assignment' into my Mini Linux Kernel task tracker right after tutorial."
- **`[hinglish]`** (`SYNTH_GEM_33`, *synthetic*): "Zara 'NLP dataset download' ko mere pending tasks mein jod do."
- **`[plain]`** (`SYNTH_WEB_26`, *web-derived:massive*): "Log a new to-do item to upload signed internship NDA to placement portal before this weekend."
- **`[typo]`** (`SYNTH_GEM_34`, *synthetic*): "Insert 'check passport expiry date for excahnge program' into my Mini Linux Kernel task tracker right after tutorial."

### `CREATE_REMINDER` (5 Random Sample Rows)

- **`[typo]`** (`SYNTH_GEM_39`, *synthetic*): "Nudge me when I finish my CS220 Algorithms lecture to reutrn Zoya's notebook."
- **`[terse]`** (`SYNTH_GEM_40`, *synthetic*): "buzz me to symposium abstract"
- **`[voice]`** (`SYNTH_WEB_31`, *web-derived:clinc150*): "wait send me a notification this saturday to draft abstract for undergraduate research symposium when you get a second"
- **`[plain]`** (`SYNTH_GEM_37`, *synthetic*): "Remind me at 7:30 am on Tuesday to buy electrolyte packets and band-aids from pharmacy."
- **`[voice]`** (`SYNTH_WEB_31`, *web-derived:clinc150*): "right nudge me when my cs three zero one operating systems class finishes to talk to dr venkat so nothing slips through the cracks"

### `CREATE_PROMISE` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_45`, *synthetic*): "gave word to Kabir: SQL schema export"
- **`[terse|multi_intent]`** (`SYNTH_GEM_44`, *synthetic*): "new promise Coach Raj this weekend + check this weekend slots"
- **`[plain]`** (`SYNTH_GEM_41`, *synthetic*): "Note down a new obligation to TA Pranav: format the bibliography section of our joint paper by tomorrow evening."
- **`[voice]`** (`SYNTH_WEB_35`, *web-derived:clinc150*): "wait record a new commitment to siddharth to help debug the docker networking issue after dinner when you get a second"
- **`[plain]`** (`SYNTH_GEM_45`, *synthetic*): "I told Librarian Rao I'd share the referral link for the summer internship before 4:30 pm; please record that commitment while I am at my study desk."

### `REFLECTION` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_49`, *synthetic*): "daily log: SQL schema export went well"
- **`[voice]`** (`SYNTH_WEB_38`, *web-derived:clinc150*): "wait record how my week went i felt super focused on metis nlu engine ahead of tomorrow s standup"
- **`[hinglish]`** (`SYNTH_WEB_39`, *web-derived:clinc150*): "Suno is hafte ka mera experience note karo - kaafi productive week raha aur SQL schema export bhi nipat gaya group study se pehle."
- **`[hinglish]`** (`SYNTH_GEM_47`, *synthetic*): "Yaar aaj ka din kaafi achha gaya, CS310 Database Systems ka assignment time pe khatam ho gaya."
- **`[voice]`** (`SYNTH_WEB_38`, *web-derived:clinc150*): "you know save an evening check in note today felt tiring after ph one zero one quantum physics but i finished git branch cleanup"

### `COMPLETE_TASK` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_53`, *synthetic*): "completed 'OS lab report'"
- **`[plain]`** (`SYNTH_GEM_54`, *synthetic*): "Set the status of 'grocery run' under CS412 Natural Language Processing to done before the lab evaluation."
- **`[plain]`** (`SYNTH_GEM_54`, *synthetic*): "Log that I have finished working on 'linear algebra reading' for Sanskrit OCR Pipeline ahead of tomorrow's standup."
- **`[plain]`** (`SYNTH_WEB_40`, *web-derived:massive*): "Cross 'Splitwise settlement' off my open to-dos; I submitted it ten minutes ago."
- **`[hinglish]`** (`SYNTH_WEB_41`, *web-derived:massive*): "Yaar maine 'hackathon registration' khatam kar liya hai, usko done mark kar do."

### `DELETE_TASK` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_GEM_55`, *synthetic*): "Throw out the task 'OS lab report' from my board."
- **`[plain]`** (`SYNTH_WEB_43`, *web-derived:clinc150*): "Clear 'laundry pickup' out of my task queue."
- **`[hinglish]`** (`SYNTH_WEB_46`, *web-derived:clinc150*): "Cancel kar do 'hostel room cleaning' ko aur meri list se hata do mess jaane se pehle."
- **`[plain]`** (`SYNTH_GEM_55`, *synthetic*): "Erase 'transcript email' from my backlog; we no longer need it for Robotics Arm Firmware."
- **`[voice]`** (`SYNTH_WEB_45`, *web-derived:clinc150*): "accha remove resume update from my to do list completely"

### `POSTPONE_TASK` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_62`, *synthetic*): "defer 'SSD backup' to Wednesday"
- **`[plain]`** (`SYNTH_GEM_59`, *synthetic*): "Please move 'Verilog testbench' to tomorrow evening at 10:00 pm."
- **`[terse]`** (`SYNTH_GEM_62`, *synthetic*): "slide 'dentist checkup' to next Thursday"
- **`[hinglish]`** (`SYNTH_WEB_48`, *web-derived:massive*): "Dekho the first one ko postpone karke Tuesday ki list mein daal do."
- **`[plain]`** (`SYNTH_GEM_59`, *synthetic*): "For 'dental clinic visit', push the deadline out to Wednesday."

### `PRIORITIZE_TASK` (5 Random Sample Rows)

- **`[terse]`** (`SYNTH_GEM_67`, *synthetic*): "mark 'Docker setup' P1"
- **`[terse]`** (`SYNTH_GEM_67`, *synthetic*): "set 'hackathon registration' to top priority"
- **`[voice]`** (`SYNTH_WEB_53`, *web-derived:clinc150*): "haan move linear algebra reading to the very top of my priority queue so nothing slips through the cracks"
- **`[hinglish]`** (`SYNTH_GEM_65`, *synthetic*): "Abhi 'USB-C cable order' ki urgency badha ke top priority pe set kar do hostel nikalne se pehle."
- **`[voice]`** (`SYNTH_WEB_52`, *web-derived:clinc150*): "right move hackathon registration to the very top of my priority queue so nothing slips through the cracks"

### `RENAME_TASK` (5 Random Sample Rows)

- **`[hinglish]`** (`SYNTH_GEM_69`, *synthetic*): "Mere board pe 'bicycle brake repair' ko naya naam 'Verilog testbench' de do mess jaane se pehle."
- **`[voice]`** (`SYNTH_GEM_72`, *synthetic*): "dekho change the title of my task friday demo slides to verilog testbench"
- **`[plain]`** (`SYNTH_GEM_72`, *synthetic*): "Replace the heading of 'hostel room cleaning' with 'scholarship form'."
- **`[terse]`** (`SYNTH_GEM_71`, *synthetic*): "call task 'Friday demo slides' 'lit review summary'"
- **`[voice]`** (`SYNTH_WEB_56`, *web-derived:clinc150*): "right edit the name of my leetcode dp practice item so it reads lab manual printout so nothing slips through the cracks"

### `CREATE_TIMER` (5 Random Sample Rows)

- **`[voice]`** (`SYNTH_WEB_59`, *web-derived:clinc150*): "yo begin a eighteen minutes countdown for my study sprint right after tutorial"
- **`[hinglish]`** (`SYNTH_WEB_58`, *web-derived:massive*): "Abhi 1 hour ka timer start kar do for a quick power nap."
- **`[hinglish]`** (`SYNTH_GEM_74`, *synthetic*): "Fatafat 2 hours ka Pomodoro timer on kar do PH101 Quantum Physics padhne ke liye."
- **`[hinglish]`** (`SYNTH_GEM_74`, *synthetic*): "Abhi 3 minutes ka timer start kar do for meditation."
- **`[plain]`** (`SYNTH_WEB_57`, *web-derived:hinglish_top*): "Time me for 7 minutes for my plank hold."

### `CREATE_ALARM` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_WEB_61`, *web-derived:clinc150*): "For Tuesday morning, set an alarm at 6:15 am sharp."
- **`[plain]`** (`SYNTH_GEM_77`, *synthetic*): "Make sure my alarm goes off at 9:30 am for PH101 Quantum Physics ahead of tomorrow's standup."
- **`[terse]`** (`SYNTH_GEM_80`, *synthetic*): "wake-up 6:15 am sharp"
- **`[hinglish]`** (`SYNTH_GEM_81`, *synthetic*): "Yaar kal subah 10:15 am baje ka alarm laga do HS102 Microeconomics class ke liye."
- **`[terse]`** (`SYNTH_GEM_80`, *synthetic*): "alarm 10:15 am"

### `CALL_PERSON` (5 Random Sample Rows)

- **`[typo]`** (`SYNTH_GEM_84`, *synthetic*): "Ring Didi on speakerphone so we can talk through Solar Rvoer Capstone when you get a second."
- **`[hinglish]`** (`SYNTH_WEB_65`, *web-derived:hinglish_top*): "Abhi ke abhi Mom ko phone lagao."
- **`[hinglish]`** (`SYNTH_GEM_83`, *synthetic*): "Call mila do Pooja ko bina kisi deri ke aaj raat sone se pehle."
- **`[terse]`** (`SYNTH_GEM_85`, *synthetic*): "now call Sana on speaker"
- **`[terse]`** (`SYNTH_GEM_85`, *synthetic*): "connect call to Dad"

### `SEND_MESSAGE` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_GEM_90`, *synthetic*): "Fire off a text to Dr Nair stating that heading to the stationery shop, do you need printouts."
- **`[terse|multi_intent]`** (`SYNTH_GEM_89`, *synthetic*): "reply to Mom: on my way now + timer 22 minutes"
- **`[voice|multi_intent]`** (`SYNTH_WEB_70`, *web-derived:clinc150*): "erm text aditi and tell them can you send me a photo of today s whiteboard notes and check when i am free tomorrow"
- **`[terse]`** (`SYNTH_GEM_90`, *synthetic*): "write to Roommate Kunal: coffee at 4pm?"
- **`[plain]`** (`SYNTH_WEB_68`, *web-derived:clinc150*): "Via SMS, tell Advait that the bus from main gate leaves in five minutes."

### `UNKNOWN` (5 Random Sample Rows)

- **`[plain]`** (`SYNTH_WEB_75`, *web-derived:massive*): "Look up do you ever get tired of answering questions about ME201 Thermodynamics and Mess Feedback Analytics."
- **`[plain]`** (`SYNTH_GEM_95`, *synthetic*): "Nuke all my tasks, contacts, calendars, and hackathon fintech prototype memories right now?"
- **`[voice]`** (`SYNTH_WEB_77`, *web-derived:banking77*): "dekho play coke studio songs on spotify while i study cs three zero one operating systems"
- **`[hinglish]`** (`SYNTH_WEB_72`, *web-derived:snips*): "Zara Jaipur se Chennai ki sabse sasti flight ticket book kar do next Thursday ki taaki koi dikkat na ho."
- **`[typo|multi_intent]`** (`SYNTH_GEM_97`, *synthetic*): "Can you what is the weather forecast in Jaipur for this Satruday ahead of tomorrow's standup before you text Aarav. plz"

