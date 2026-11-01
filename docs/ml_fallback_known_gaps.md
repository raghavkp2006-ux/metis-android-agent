# ML Fallback Known Gaps & Future Human-Test Candidates

## Overview
This document records Category (b) utterances identified during the regression enumeration of candidate device-test corpora (specifically from `docs/evaluation/phase15/read_coverage_v10/`).

In historical evaluation suites such as Candidate V10, only read queries were within grammar scope; any command outside of read candidate coverage was labeled `UNKNOWN` (expectation `clarify`). When evaluated under the statistical NLU model fallback, these utterances are correctly recognized as valid human write/action commands.

Per project constraints, historical corpus expectations are left unmodified. These rows are documented here as candidates for future human-evaluated benchmark suites.

---

## Category (b) Candidate Rows

| Row ID | Utterance | Historical Expectation | Model Predicted Intent | Confidence | Semantic Analysis & Candidacy Rationale |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `p15v5-fresh-0010` | `add: buy tickets for the concert on 14th` | `UNKNOWN` (`clarify`) | `CREATE_TASK` | 0.7130 | Imperative task creation with explicit date slot. Natural write command mislabeled as negative by read-only grammar. |
| `p15v5-fresh-0022` | `delete the task about calling the plumber. he fixed it already` | `UNKNOWN` (`clarify`) | `DELETE_TASK` | 0.9841 | High-confidence imperative task deletion with justification clause. Valid destructive command. |
| `p15v5-fresh-0024` | `note down: need to book flights for the trip to goa` | `UNKNOWN` (`clarify`) | `CREATE_PROMISE` | 0.3529 | Personal commitment / promise note request with destination. Valid commitment note. |
| `p15v5-fresh-0055` | `remind me i owe devika a call about the wedding` | `UNKNOWN` (`clarify`) | `CREATE_REMINDER` | 0.8768 | Imperative reminder containing an embedded obligation clause ("I owe"). Natural reminder request. |
| `p15v5-fresh-0070` | `remind me i promised to take my sister to the zoo` | `UNKNOWN` (`clarify`) | `CREATE_REMINDER` | 0.5934 | Imperative reminder containing a commitment reference. Natural reminder request. |
| `p15v5-fresh-0090` | `put a reminder for the school pickup at 3:30` | `UNKNOWN` (`clarify`) | `CREATE_REMINDER` | 0.9219 | Clear imperative reminder request with explicit time slot (`3:30`). High-confidence valid command. |
| `p15v5-fresh-0098` | `add a 30 minute buffer before the interview` | `UNKNOWN` (`clarify`) | `CREATE_TASK` | 0.4001 | Calendar/task buffer allocation request. Natural task scheduling command. |

---

## Evaluation Guidance
When developing new general-agent evaluation suites (spanning both read queries and write mutations), these rows should be classified as their true action intents rather than `UNKNOWN`.

