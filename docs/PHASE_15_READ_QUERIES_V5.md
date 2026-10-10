# Phase 15: v5 ML and structured read interpretation

Recorded 2026-10-10. **Development implementation completed; deployment remains NO_GO.** No Android model, production rule change, database migration or actual read integration is introduced. Phase 16 has not started.

## Implemented

- Separate learned intent from read/clarify/reject/unsupported disposition. Resolve original UTF-16 topic/person spans and preserve structured retrieval constraints.
- Clarify vague/incomplete topics, unresolved people, confusing negation and contradictory requests. Missing-reference protection applies even when the classifier chooses the wrong read label.
- Expand short/slang/declarative English and limited Roman Hinglish. Distinguish past promise actions from action commands; retain unavailable-calendar responses and native non-read rule matches.
- Add 18,570 synthetic examples with family separation and disjoint topic/person pools, word versus word/character model selection, masking and explicit lexical features, bounded synthetic calibration and abstention.
- Add disabled serialized model export, numeric feature/weight parity, separate entity/filter/disposition metrics, unapproved annotation drafts and fresh-review freezing/scoring tools. CI runs the development checks.

## Recorded results

| Measurement | V5 |
| --- | --- |
| Generated evaluation macro F1 | 0.9133 |
| Generated positive intent coverage | 215/246; includes six wrong positive labels |
| Generated unknown false accepts | 6/2,502; all misclassified as calendar, with no simulated read |
| Forbidden simulated reads in generated evaluation | 0 |
| Isolated entity extraction with expected intent | 120/120 draft spans |
| End-to-end entity spans | 114/120 |
| Isolated annotated filter fields | 66/66 |
| End-to-end annotated filter fields | 47/66 |
| Exact generated dispositions | 2,639/2,748 |
| Exposed 125-query macro F1 | 0.8115; original v4 first score 0.6116 |
| Exposed positive intents correct | 80/100; v4 was 56/100 |
| Exposed reject/clarify cases | 25/25 receive clarify/reject, zero simulated reads |
| Earlier exposed 25 prompts | 20/20 positive intents; 5/5 negative routing abstentions |
| Serialized feature/weight parity | 2,898 rows; zero argmax differences |

On the exposed 125-query set: tasks 18/25, memory 21/25, promises 23/25, calendar 18/25 correct intents. One additional positive receives a wrong read label, so 81% read-label coverage is not 81% intent accuracy. Five reject/clarify cases retain a semantic read label but safely clarify; strict routing abstention is 20/25. Full response wording and actual retrieval are not verified. Entity/outcome annotations for these prompts remain unapproved.

The selected model uses word/character TF-IDF, C=4, temperature approximately 0.7432 and cutoff 0.90. Vocabulary/weights fit eligible train only, temperature fits eligible calibration only (84 rows), and model/cutoff selection uses threshold families only. ECE/Brier are recorded separately on eligible classifier inputs. Representative calibration remains unproven.

V5 partitions and all contributed prompts were exposed during development. The generated corpus is harder and differs from v4; its score cannot establish a direct independent gain. An intermediate run reached higher exposed coverage but was rejected after calibration/negative-request weaknesses were found. The final result preserves abstention and reports remaining errors instead of claiming the interim score.

## Safety and contract limits

No real read, proposal, completion or mutation is executed. Current task/person read interfaces cannot apply the requested date/status/category filters; v5 clarifies rather than silently ignoring them. Calendar queries return unsupported when recognized. Limited Hinglish does not mean general Hindi or multilingual support. All extraction patterns and lexical guards remain bounded heuristics.

The original native 50-case baseline still matches all recorded intents/entities/harness outcomes. Of the old 24 strict negatives, `mere tasks dikhao` is intentionally eligible under the user's newly approved Hinglish scope. Its original UNKNOWN expectation and raw legacy result remain preserved: one legacy mismatch. The other 23 current-scope strict cases have zero false accepts or simulated reads. This is an explicit scope change, not a rewritten historical fixture.

## Verification and remaining gates

- All 22 Python regression tests pass, covering original unsafe reads, incorrect-label bypass, negation/compounds, entities/UTF-16, filters/snapshot dates, limited Hinglish, calibration bounds, data separation and review freezing/non-overwrite. Manifest/source/data integrity and blank human-approval fields were also checked.
- Production JVM snapshot export succeeds for 18,770 requests; its existing unit-test dependency was up to date. No Android/device parity or runtime measurement is claimed.
- Serialized JSON feature/weight math matches sklearn to approximately 1.42e-14 maximum logit error. Shared preprocessing means this does not establish Android parity.
- Fresh human-reviewed evaluation, representative calibration, actual repository/policy outcomes and Android latency/RAM/battery remain pending. Generated unknown rejection and end-to-end filter/disposition results are not yet sufficient for adoption.

The [reproduction and review guide](evaluation/phase15/read_queries_v5/README.md), [recorded metrics](evaluation/phase15/read_queries_v5/results-v5.json) and [unapproved annotation drafts](evaluation/phase15/read_queries_v5/annotation-review-exposed.csv) make the work reviewable. Neither the older annotation package nor these drafts are automatically approved. A fresh benchmark must be frozen against the final candidate before another independent adoption attempt.

## First approved v5 benchmark, 2026-10-10

The separate [145-row human-approved benchmark](evaluation/phase15/human_review_003/first_evaluation/README.md) was frozen before scoring against unchanged v5. It yields macro F1 **0.4328**, 31/102 correct positive intents, 6/43 UNKNOWN read-label accepts and **14 forbidden simulated reads**. Exact dispositions match 86/145, nonempty expected entities 3/37, and annotated filter fields 1/62. Extended approved topic/subject/age/priority/date constraints expose capability gaps; not all strict annotation mismatches imply an unsafe read. No actual repository read or mutation occurred.

Candidate parameters and export bytes match the prior reference. The first result, native snapshot, approved annotations, approval/freeze hashes and exposure receipt are preserved separately; the development reference above is unchanged. The reviewed quality gate **failed**. Representative calibration, repository/policy outcomes and Android/device evidence remain pending, and ML remains disabled/unbundled. These 145 queries are now exposed regressions for future development, not a fresh evaluation set for another candidate.
