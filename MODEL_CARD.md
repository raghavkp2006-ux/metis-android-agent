# Model card

## Current status

Production uses the separately versioned [v15 deterministic candidate](docs/evaluation/phase15/candidate_v15/README.md), including bounded typed task/memory/promise/calendar reads. No ML artifact is bundled or enabled. All 785 retained admissions and 23/23 approved exposed benchmark reads are preserved; the 14 original forbidden reads remain blocked. These are exposed regressions, not fresh independent accuracy evidence.

V15 has not fitted a new classifier. All 365 draft semantic meanings have user approval; 40 complete annotations are approved and 325 retain field/disposition gaps. Annotation/family freeze, 100 calibration plus 50 selection requests, and 150 fresh evaluation requests after freeze remain prerequisites. Current deterministic validation passed 136 JVM tests, 47 retained plus seven new Python tests (122 including baseline/v3/v5/v6 suites), lint/Detekt/APK compilation and 18 distinct API-36 phone tests. Full API 26/36 instrumentation and build/evaluator jobs passed remotely. The latest local API-26 fixtures also exercise scoped storage and navigation; earlier UI timing failures are retained in the v15 report. Offline ML quality, calibration, independent evaluation and Android ML parity/latency/PSS/battery gates remain open.

### Historical v9 checkpoint

No ML model is bundled or enabled in the Android app. Phase 15's separately versioned v9 development classifier remains `NO_GO`; the app uses deterministic v8 read validation, original-text extraction, request-time date resolution and local structured retrieval. The [v9 report](docs/evaluation/phase15/classifier_v9/README.md), [v2 report](docs/PHASE_15_VERIFICATION.md) and [v3 report](docs/PHASE_15_READ_QUERIES.md) distinguish current and historical evidence. Saved preferences and memories are local context, not model weights or automatically collected training examples.

| Item | Status |
| --- | --- |
| Model name/version | Development candidate: phase15-classifier-v9; no app model |
| Training/evaluation data | 1,699 assistant-authored development rows in four disjoint family partitions; earlier exposed examples excluded from fitting; frozen human benchmark replayed as regressions only |
| Intended present use | Offline development evaluation only; NO_GO |
| Supported inference languages/intents | No learned inference enabled; v9 targets four English semantic read labels plus UNKNOWN; calendar remains unavailable and creation stays on rules |
| Accuracy/F1/entity metrics | Selected synthetic holdout F1 0.4390, zero safe-read gain, no memory/promise recognition at cutoff. Exposed native reads remain 8/23 with zero forbidden reads. No independent accuracy claim. |
| Latency, model size, RAM/battery | Not measured |
| On-device/offline inference | Not implemented |

## Planned evaluation requirements

### Current development experiment: v9 (2026-10-11)

[V9](docs/evaluation/phase15/classifier_v9/README.md) is a separately versioned word/character TF-IDF plus logistic-regression experiment on 1,699 assistant-authored development rows, with family-isolated train, calibration, selection and holdout partitions. Raw holdout macro F1 is 0.7498 with 12/135 negative label accepts. The selected calibrated cutoff reduces F1 to 0.4390 with zero negative label accepts, misses all memory/promise intents in that holdout, and adds zero safe reads over the fixed v8 gate. Original exposed coverage stays 8/23 with zero forbidden reads. Decision: NO_GO. Python numeric export parity is development-only; no Android model, representative calibration, fresh independent score or device result is claimed. The following entries record historical candidates.

### Latest development candidate: v5 (2026-10-10)

[V5](docs/PHASE_15_READ_QUERIES_V5.md) adds explicit intent/disposition separation, structured entity/filter parsing, English plus limited Roman Hinglish, lexical features and bounded synthetic calibration. Generated evaluation F1 is 0.9133 with six unknown false accepts (calendar labels, no simulated reads), 114/120 end-to-end draft entity matches and 47/66 end-to-end annotated filter matches. Isolated extraction receives the expected intent and is scored separately. The exposed user set improves to 80/100 correct positive intents and zero simulated reads in 25 reject/clarify cases; this is not fresh independent evidence.

The disabled development export and Python numeric parity are not Android inference. The first [human-approved v5 evaluation](docs/evaluation/phase15/human_review_003/first_evaluation/README.md), frozen before scoring on 2026-10-10, contains 145 user-attested fresh human queries. Unchanged v5 achieves macro F1 **0.4328**, 31/102 correct positive intents and **14 forbidden simulated reads**. End-to-end approved entity matches are 3/37 and annotated filter-field matches 1/62; richer approved constraints expose unsupported parsing/capability gaps. The candidate parameters and export hash match the pre-existing reference. This gate failed; these queries are now exposed regressions. Representative calibration, actual repository/policy behavior and representative-device costs remain pending. Decision: NO_GO. V4 and its first standalone benchmark remain unchanged as historical evidence; the older review packages remain unapproved.

### Latest development candidate: v4 (2026-10-10)

**Subsequent standalone evaluation:** unchanged v4 recognizes 56/100 positive intents across [125 fresh contributed queries](docs/evaluation/phase15/independent_125/README.md), macro F1 0.6116. Strict reject/clarify routing abstention is 18/25; simulated follow-up is 22/25, with three incorrect simulated reads. This limits the generated results below. No candidate tuning used this set before the first score; it is now exposed. Authorship/independence are user-attested; exact entity spans and full response behavior are not validated. Adoption remains NO_GO.

[V4 results](docs/PHASE_15_READ_QUERIES_V4.md) supersede v3 as the current development experiment. Word/character TF-IDF with logistic regression, eligibility filtering and entity masking achieves hybrid macro F1 0.9911, 164/168 positive intent coverage and 0/1,602 negative false accepts on generated evaluation families. Generated entity spans match 78/78 annotations; read outcomes are simulated. The 25 exposed user regressions produce 20/20 positive intents and 5/5 negative declines, without an independent accuracy claim. Only 848 eligible training rows and 55 eligible calibration rows reach the classifier after filtering; the full corpus contains 11,168 rows.

The disabled development JSON export is 537,675 bytes. Serialized Python feature/weight math matches sklearn across 1,819 evaluation/regression rows (zero argmax differences; maximum logit error about 1.33e-14), sharing preprocessing. Android inference, parity, runtime size, latency, RAM and battery remain unmeasured. Independent reviewed evaluation, representative calibration and real entity/outcome/policy integration remain unmet. Decision: NO_GO; deterministic rules remain deployed.

Begin with deterministic rules and structured retrieval. TF-IDF plus logistic regression or linear SVM are candidate baselines; a larger model is justified only by measured gains. Evaluation requires independent held-out utterance families, per-intent and entity metrics, unsupported/negation rejection, calibrated confidence, representative-device latency/RAM/battery measurements, and explicit failure limitations.

The product targets independently evaluated macro F1 >= 0.90 and on-device intent inference under 100 ms. The synthetic experiment does not establish those deployment targets. High classifier confidence never grants permission for an action. Every enabled model remains replaceable and subject to typed policy/confirmation validation. Emotional support is a later bounded flow and must not claim medical diagnosis or professional care.

Update this card when a model is actually introduced, recording provenance, licenses, version, dataset splits, evaluation methodology/results, scope, privacy, deployment conditions, and known risks. See the [dataset contract](docs/contracts/DATASET.md).

Phase 8 extends closed English commands for confirmed task rename, priority, deletion and postponement. Dedicated regression/storage tests cover these commands; the original 45-case development corpus remains a historical baseline, not a held-out accuracy measurement. Reminders and task actions use deterministic typed policy and platform/storage checks, not learned inference.

Phase 10 adds TASK_DECISION_V1, a fixed explainable weighted task-ranking rule. It uses recorded deadlines, priority, active goals, known effort and explicit focus preference, filters prerequisites and supplied duration budgets, and stores exact contributions. Calendar availability, behavioral success and context-switch signals remain unknown. This is not a trained or calibrated recommendation model. Its synthetic regression scenarios do not establish general recommendation quality or learned personalization. See [Phase 10 verification](docs/PHASE_10_VERIFICATION.md).

The original Phase 15 v2 experiment compares its candidate with the actual JVM rules, using a separate calibration subset and threshold-selection subset from validation families. The easy generated test score is high, but high-confidence acceptance of quoted, hypothetical, compound, and unsupported requests fails the safety gate. Independent evaluation, candidate entity/outcome/policy validation, Android parity, and representative-device latency/RAM/battery gates remain unmet. No model is shipped. See [Phase 15 verification](docs/PHASE_15_VERIFICATION.md).

V3 adds a conservative read eligibility filter and class-balanced logistic regression selected only on validation families. It rejects all 24 historical strict probes within its narrower read-only scope; this is not a like-for-like seven-intent accuracy comparison. The larger development suite exposes 10 compound memory queries accepted by preserved rule matches. The hybrid therefore still fails accuracy and rejection gates. The [review guide](docs/evaluation/phase15/read_queries_v3/REVIEW_GUIDE.md) requires human-authored challenges and completed annotations before benchmark freezing. Android integration is deferred until reviewed offline gates pass.

A separate [25-prompt user contribution](docs/PHASE_15_USER_CHALLENGE.md), excluded from training/tuning, scores macro F1 0.5052 and 40% read-intent coverage with zero false accepts on five negatives. User category labels and assistant-drafted entity/outcome annotations support only an intent diagnostic; the full package remains unapproved. These exposed prompts cannot provide a new blind claim after further tuning. NO_GO remains unchanged.
