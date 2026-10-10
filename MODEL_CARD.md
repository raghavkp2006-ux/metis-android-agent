# Model card

## Current status

No ML model is bundled or enabled in the Android app. Phase 15 rejected v2 and continues with a development-only read-query v3 candidate, also currently `NO_GO`. The production app uses deterministic rules, original-text extraction, request-time date resolution, and local structured retrieval. The [v2 report](docs/PHASE_15_VERIFICATION.md) and [v3 report](docs/PHASE_15_READ_QUERIES.md) record scope and limitations. Saved preferences and memories are local context, not model weights or automatically collected training examples.

| Item | Status |
| --- | --- |
| Model name/version | Development candidate: metis-p15-read-queries-v3; no app model |
| Training/evaluation data | 7,200 synthetic development examples with four separate family partitions; exposed v2 positives reused; 50 historical native probes; human review pending |
| Intended present use | Offline development evaluation only; NO_GO |
| Supported inference languages/intents | No learned inference enabled; v3 targets four English read intents plus UNKNOWN; creation stays on rules |
| Accuracy/F1/entity metrics | Candidate development macro F1 0.8171, 65.45% read coverage, 0/890 negative false accepts; rules-first F1 0.7913 and 10 compound-memory false accepts. No ML entity/outcome validation or independent accuracy claim. |
| Latency, model size, RAM/battery | Not measured |
| On-device/offline inference | Not implemented |

## Planned evaluation requirements

Begin with deterministic rules and structured retrieval. TF-IDF plus logistic regression or linear SVM are candidate baselines; a larger model is justified only by measured gains. Evaluation requires independent held-out utterance families, per-intent and entity metrics, unsupported/negation rejection, calibrated confidence, representative-device latency/RAM/battery measurements, and explicit failure limitations.

The product targets independently evaluated macro F1 >= 0.90 and on-device intent inference under 100 ms. The synthetic experiment does not establish those deployment targets. High classifier confidence never grants permission for an action. Every enabled model remains replaceable and subject to typed policy/confirmation validation. Emotional support is a later bounded flow and must not claim medical diagnosis or professional care.

Update this card when a model is actually introduced, recording provenance, licenses, version, dataset splits, evaluation methodology/results, scope, privacy, deployment conditions, and known risks. See the [dataset contract](docs/contracts/DATASET.md).

Phase 8 extends closed English commands for confirmed task rename, priority, deletion and postponement. Dedicated regression/storage tests cover these commands; the original 45-case development corpus remains a historical baseline, not a held-out accuracy measurement. Reminders and task actions use deterministic typed policy and platform/storage checks, not learned inference.

Phase 10 adds TASK_DECISION_V1, a fixed explainable weighted task-ranking rule. It uses recorded deadlines, priority, active goals, known effort and explicit focus preference, filters prerequisites and supplied duration budgets, and stores exact contributions. Calendar availability, behavioral success and context-switch signals remain unknown. This is not a trained or calibrated recommendation model. Its synthetic regression scenarios do not establish general recommendation quality or learned personalization. See [Phase 10 verification](docs/PHASE_10_VERIFICATION.md).

The original Phase 15 v2 experiment compares its candidate with the actual JVM rules, using a separate calibration subset and threshold-selection subset from validation families. The easy generated test score is high, but high-confidence acceptance of quoted, hypothetical, compound, and unsupported requests fails the safety gate. Independent evaluation, candidate entity/outcome/policy validation, Android parity, and representative-device latency/RAM/battery gates remain unmet. No model is shipped. See [Phase 15 verification](docs/PHASE_15_VERIFICATION.md).

V3 adds a conservative read eligibility filter and class-balanced logistic regression selected only on validation families. It rejects all 24 historical strict probes within its narrower read-only scope; this is not a like-for-like seven-intent accuracy comparison. The larger development suite exposes 10 compound memory queries accepted by preserved rule matches. The hybrid therefore still fails accuracy and rejection gates. The [review guide](docs/evaluation/phase15/read_queries_v3/REVIEW_GUIDE.md) requires human-authored challenges and completed annotations before benchmark freezing. Android integration is deferred until reviewed offline gates pass.
