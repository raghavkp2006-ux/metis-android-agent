# Calibration intake 006: label draft for review

User-confirmed calibration purpose. Items 51–151 are 101 independent submissions; no earlier-row context is supplied. Labels are assistant drafts and require explicit review. No parser/classifier predictions, personal-record queries, model fitting or temperature selection were run.

Normalized unique requests: 99; duplicate rows: 2. Draft unique-label counts: {'CHECK_TASKS': 19, 'CHECK_MEMORY': 73, 'WHAT_DID_I_PROMISE': 3, 'UNKNOWN': 4}. Draft quota shortfalls: {'CHECK_MEMORY': 0, 'CHECK_CALENDAR': 30, 'CHECK_TASKS': 0, 'WHAT_DID_I_PROMISE': 12, 'UNKNOWN': 6}.

Questions about saved notes on calendar support are CHECK_MEMORY, not calendar-event requests. A mentioned promise without a named recipient clarifies before access. Instructions such as “If not, report unavailable” remain standalone independent requests; they cannot supply context to adjacent rows. All constraints remain verbatim in JSON.

Family proposal: Phase 15 process/policy/status. Related paraphrases must remain together, and this family cannot appear in selection. Semantic overlap with earlier data still needs review; exact text comparison is insufficient to establish independence.

| # | Request | Draft category | Complete subject | Draft safe outcome / explanation | Duplicate / earlier exact match |
| --- | --- | --- | --- | --- | --- |
| 51 | Did I mark it complete? | CHECK_TASKS | it | clarify_reference: it unresolved; requested completion status | none |
| 52 | What did I say about Phase 15? | CHECK_MEMORY | Phase 15 | read:  | none |
| 53 | Did I save any task related to Phase 15? | CHECK_TASKS | Phase 15 | read: existence lookup across ANY status | none |
| 54 | Did I say Phase 15 was complete? | CHECK_MEMORY | Phase 15 was complete | read: literal saved statement; no inferred completion | none |
| 55 | Did I explicitly say it was still open? | CHECK_MEMORY | still open | clarify_reference: it unresolved; preserve open statement | none |
| 56 | What is the latest saved status? | CHECK_MEMORY | status | unsupported_capability: subject/store missing; latest-record filter unsupported | none |
| 57 | Did I promise to collect more queries? | WHAT_DID_I_PROMISE | collect more queries | clarify_reference: named promise recipient missing | none |
| 58 | Did I create a task for collecting queries? | CHECK_TASKS | collecting queries | read: task existence across ANY status | none |
| 59 | Did I specify how many? | CHECK_MEMORY | how many | clarify_reference: quantity subject missing; requested stored count | none |
| 60 | Did I mention 50 or a different number? | CHECK_MEMORY | 50 or a different number | clarify_reference: quantity subject missing; preserve 50 without assuming it is stored | none |
| 61 | Was that for development or evaluation? | CHECK_MEMORY | development or evaluation | clarify_reference: that unresolved; requested stored batch purpose | none |
| 62 | Did I say those queries could be reused later? | CHECK_MEMORY | those queries could be reused later | clarify_reference: those queries unresolved; preserve reuse and later restrictions | none |
| 63 | Did I explicitly say they could not be reused? | CHECK_MEMORY | could not be reused | clarify_reference: they unresolved; retain negative saved content | none |
| 64 | Find any saved rule about exposed data. | CHECK_MEMORY | exposed data | read: requested saved rule; no new policy advice | none |
| 65 | Did I save anything about calibration? | CHECK_MEMORY | calibration | read:  | none |
| 66 | Did I create a task to review calibration coverage? | CHECK_TASKS | review calibration coverage | read: task existence across ANY status | none |
| 67 | Did I specify who would review it? | CHECK_MEMORY | who would review it | clarify_reference: it unresolved; requested stored reviewer | none |
| 68 | Is the reviewer saved? | CHECK_MEMORY | the reviewer | clarify_reference: reviewer of what is missing; requested stored reviewer | none |
| 69 | Did I promise to freeze the candidate afterward? | WHAT_DID_I_PROMISE | freeze the candidate afterward | clarify_reference: named recipient and afterward reference missing | none |
| 70 | Did I create a task for candidate freeze? | CHECK_TASKS | candidate freeze | read: task existence across ANY status | none |
| 71 | Is that task complete? | CHECK_TASKS | that task | clarify_reference: that task unresolved; requested completion status | none |
| 72 | Did I save any parser issues? | CHECK_MEMORY | parser issues | read:  | none |
| 73 | What parser gap did I mention? | CHECK_MEMORY | parser gap | read: requested literal saved parser issue | none |
| 74 | Did I create a task to fix parser scoping? | CHECK_TASKS | fix parser scoping | read: task existence across ANY status | none |
| 75 | Did I mark the parser fix complete? | CHECK_TASKS | the parser fix | read: requested COMPLETED task status; no identity or history inference | none |
| 76 | Did I say the adoption target was met? | CHECK_MEMORY | the adoption target was met | read: literal statement recall; do not infer acceptance | none |
| 77 | Did I say it was still being missed? | CHECK_MEMORY | still being missed | clarify_reference: it unresolved; preserve saved status content | none |
| 78 | Find the latest model-status note. | CHECK_MEMORY | model-status | unsupported_capability: latest-record filter unsupported | none |
| 79 | Did I create a task to rerun evaluation? | CHECK_TASKS | rerun evaluation | read: task existence across ANY status | none |
| 80 | Was that supposed to happen before or after freeze? | CHECK_MEMORY | before or after freeze | clarify_reference: that unresolved; preserve ordering constraint | none |
| 81 | Is the ordering explicitly saved? | CHECK_MEMORY | the ordering | clarify_reference: ordering/subject missing; do not infer sequence | none |
| 82 | Did I mention independent evaluation? | CHECK_MEMORY | independent evaluation | read:  | none |
| 83 | Did I say it was premature? | CHECK_MEMORY | premature | clarify_reference: it unresolved; literal statement recall only | none |
| 84 | Did I create any task to collect fresh evaluation data? | CHECK_TASKS | collect fresh evaluation data | read: task existence across ANY status | none |
| 85 | Did I specify that it must happen after candidate freeze? | CHECK_MEMORY | after candidate freeze | clarify_reference: it unresolved; retain after restriction | none |
| 86 | Find all constraints attached to fresh evaluation. | CHECK_MEMORY | fresh evaluation | read: requested all stored constraints; bounded results must report incomplete search honestly | none |
| 87 | Did I mention reviewer independence? | CHECK_MEMORY | reviewer independence | read:  | none |
| 88 | Did I save an acceptance policy? | CHECK_MEMORY | an acceptance policy | read:  | none |
| 89 | If not, report that it is unavailable. | UNKNOWN | (missing) | clarify_reference: standalone conditional missing-information instruction; no subject/antecedent; report unavailable rather than guess | none |
| 90 | Did I mention preserving the first result? | CHECK_MEMORY | preserving the first result | read:  | none |
| 91 | Did I say retries could replace the first result? | CHECK_MEMORY | retries could replace the first result | read: literal saved rule; do not authorize replacement | none |
| 92 | Find the exact saved rule about retries without quoting more than necessary. | CHECK_MEMORY | retries | read: requested exact saved rule; quote only necessary stored words; do not synthesize policy | none |
| 93 | Did I create a task to freeze hashes? | CHECK_TASKS | freeze hashes | read: task existence across ANY status | none |
| 94 | Which hashes did I mention? | CHECK_MEMORY | hashes | clarify_reference: missing record subject; requested stored hash names | none |
| 95 | Did I mention model and parser hashes? | CHECK_MEMORY | model and parser hashes | read: one literal notes subject; no cross-store join | none |
| 96 | Did I specify IDs and annotations too? | CHECK_MEMORY | IDs and annotations | clarify_reference: too refers to missing record; preserve both fields | none |
| 97 | Do I have any task covering all of those? | CHECK_TASKS | all of those | clarify_reference: those unresolved; preserve all requested constraints | none |
| 98 | Did I save anything about Android integration? | CHECK_MEMORY | Android integration | read:  | none |
| 99 | Is physical-device testing enough for that gate? | CHECK_MEMORY | physical-device testing | clarify_reference: that gate unresolved; stored policy versus new advice needs clarification | none |
| 100 | Did I explicitly say it was not enough? | CHECK_MEMORY | not enough | clarify_reference: it unresolved; retain explicit negative statement | none |
| 101 | What still remains for the Android gate? | CHECK_MEMORY | the Android gate | clarify_reference: remaining versus which saved gate/record unclear; do not infer completion | none |
| 102 | Did I promise to measure resources? | WHAT_DID_I_PROMISE | measure resources | clarify_reference: named promise recipient missing | none |
| 103 | Did I create a task to measure memory usage? | CHECK_TASKS | measure memory usage | read: task existence across ANY status | none |
| 104 | Did I create one for latency too? | CHECK_TASKS | latency | clarify_reference: one and too refer to missing task; do not infer task to measure | none |
| 105 | Are those separate tasks? | CHECK_TASKS | separate tasks | unsupported_capability: those unresolved; cross-record relationship inference unsupported | none |
| 106 | Did I specify a device for measurements? | CHECK_MEMORY | a device for measurements | clarify_reference: which measurements missing; requested stored device | none |
| 107 | Did I specify an acceptance threshold? | CHECK_MEMORY | an acceptance threshold | clarify_reference: which acceptance gate missing; requested stored threshold | none |
| 108 | If no threshold is saved, say so. | UNKNOWN | threshold | clarify_reference: standalone missing-information instruction; threshold subject missing; report missing rather than guess | none |
| 109 | Did I save anything about repository checks? | CHECK_MEMORY | repository checks | read:  | none |
| 110 | What repository check was mentioned? | CHECK_MEMORY | repository check | read: literal stored check lookup; no claim checks passed | none |
| 111 | Did I mention unique saved-person resolution? | CHECK_MEMORY | unique saved-person resolution | read: literal saved topic; do not resolve a person for this query | none |
| 112 | Was that part of the model gate or repository gate? | CHECK_MEMORY | the model gate or repository gate | clarify_reference: that unresolved; requested stored gate assignment | none |
| 113 | Is that distinction explicitly saved? | CHECK_MEMORY | that distinction | clarify_reference: distinction/record unresolved; do not infer | none |
| 114 | Did I create a task to test saved-person resolution? | CHECK_TASKS | test saved-person resolution | read: task existence across ANY status | none |
| 115 | Did I mark it complete? | CHECK_TASKS | it | clarify_reference: it unresolved; requested completion status | calibration-intake-006-051 |
| 116 | Did I mention calendar support? | CHECK_MEMORY | calendar support | read: notes about capability, not an event lookup | none |
| 117 | Is calendar currently available according to my saved notes? | CHECK_MEMORY | calendar | unsupported_capability: current availability cannot be inferred from potentially historical notes; clarify a concrete saved record or explicit statement | none |
| 118 | Did I create any calendar-related development queries? | CHECK_MEMORY | calendar-related development queries | clarify_reference: query creation history is not a calendar-event lookup; clarify whether explicit saved notes about those queries are intended | none |
| 119 | Did I say calendar questions should report unavailable? | CHECK_MEMORY | calendar questions should report unavailable | read: literal stored policy; do not impose it on current capability | none |
| 120 | Is that always true or dependent on context? | CHECK_MEMORY | always true or dependent on context | clarify_reference: that rule unresolved; do not infer policy universality | none |
| 121 | If the saved rule is insufficient, report ambiguity. | UNKNOWN | the saved rule | clarify_reference: standalone insufficient-information instruction; rule unresolved; preserve ambiguity response | none |
| 122 | Did I mention facts that may return no saved match? | CHECK_MEMORY | facts that may return no saved match | read: literal topic; honest no-match behavior | none |
| 123 | Is a no-match result considered valid? | CHECK_MEMORY | a no-match result | clarify_reference: stored policy versus new validity advice unclear | none |
| 124 | Did I create a task to test no-match behavior? | CHECK_TASKS | test no-match behavior | read: task existence across ANY status | none |
| 125 | Did I save examples of no-match questions? | CHECK_MEMORY | examples of no-match questions | read: literal saved examples; no implicit authorization to fit them | none |
| 126 | Are those examples now exposed? | CHECK_MEMORY | those examples | clarify_reference: those examples unresolved; no inferred exposure status | none |
| 127 | Did I say exposed queries could be used for fitting? | CHECK_MEMORY | exposed queries could be used for fitting | read: literal stored policy, not fitting authorization | none |
| 128 | Under what condition? | CHECK_MEMORY | condition | clarify_reference: condition subject missing | none |
| 129 | Did I mention explicit provenance? | CHECK_MEMORY | explicit provenance | read:  | none |
| 130 | Did I create a task to record provenance? | CHECK_TASKS | record provenance | read: task existence across ANY status | none |
| 131 | What fields need to be recorded? | CHECK_MEMORY | fields | clarify_reference: missing record/subject; stored field requirements versus advice unclear | none |
| 132 | Did I mention speaker grouping? | CHECK_MEMORY | speaker grouping | read:  | none |
| 133 | Did I say related paraphrases should stay together? | CHECK_MEMORY | related paraphrases should stay together | read: literal saved grouping policy | none |
| 134 | Did I create a task for grouping them? | CHECK_TASKS | grouping them | clarify_reference: them unresolved; preserve grouping task | none |
| 135 | Did I save anything about batch purpose? | CHECK_MEMORY | batch purpose | read:  | none |
| 136 | Did I say purpose must be decided before access? | CHECK_MEMORY | purpose must be decided before access | read: literal saved restriction; retain before access | none |
| 137 | Can a seen development batch later become unseen evaluation? | UNKNOWN | a seen development batch | unsupported_capability: general policy/advice question without explicit saved-record scope; never recategorize seen data as unseen evaluation | none |
| 138 | Did I explicitly say no? | CHECK_MEMORY | no | clarify_reference: missing subject; no alone does not identify negative content | none |
| 139 | Find any rule about recategorizing seen data. | CHECK_MEMORY | recategorizing seen data | read: requested stored rule, no recategorization action | none |
| 140 | Did I save anything about author and reviewer roles? | CHECK_MEMORY | author and reviewer roles | read:  | none |
| 141 | Were those roles finalized? | CHECK_MEMORY | those roles | clarify_reference: those roles unresolved; do not infer finalization | none |
| 142 | Did I mention independence limits? | CHECK_MEMORY | independence limits | read:  | none |
| 143 | Were the limits agreed? | CHECK_MEMORY | the limits | clarify_reference: which limits/agreement missing | none |
| 144 | Did I save category coverage requirements? | CHECK_MEMORY | category coverage requirements | read:  | none |
| 145 | Were those requirements fixed? | CHECK_MEMORY | those requirements | clarify_reference: those requirements unresolved; do not infer freeze | none |
| 146 | Did I save an acceptance policy? | CHECK_MEMORY | an acceptance policy | read:  | calibration-intake-006-088 |
| 147 | Is it finalized or still pending? | CHECK_MEMORY | finalized or still pending | clarify_reference: it unresolved; do not infer final status | none |
| 148 | Did I say a final independent evaluation could start now? | CHECK_MEMORY | a final independent evaluation could start now | read: literal saved statement; not current permission to start evaluation | none |
| 149 | Did I say it should wait? | CHECK_MEMORY | should wait | clarify_reference: it unresolved; preserve wait restriction | none |
| 150 | What event must happen before it starts? | CHECK_MEMORY | before it starts | clarify_reference: it unresolved; requested stored prerequisite | none |
| 151 | Is the candidate currently frozen? | CHECK_MEMORY | the candidate | clarify_reference: which candidate missing; do not infer freeze status | none |
