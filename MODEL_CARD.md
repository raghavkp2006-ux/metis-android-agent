# Model card

## Current status

No ML model is bundled or enabled in the Android app. Phase 15 trained a development-only intent candidate on synthetic text and rejected it after high-confidence failures on rejection probes. The production app uses deterministic rules, original-text extraction, request-time date resolution, and local structured retrieval. The [Phase 15 report](docs/PHASE_15_VERIFICATION.md) records the experiment and its limitations. Saved preferences and memories are local context, not model weights or automatically collected training examples.

| Item | Status |
| --- | --- |
| Model name/version | Development candidate: metis-p15-tfidf-logreg-v2; no app model |
| Training/evaluation data | 1,600 single-author synthetic intent examples with separated families, plus 50 native safety/resolution probes; prior development exposure prevents an independent blind accuracy claim |
| Intended present use | Offline development evaluation only; candidate rejected |
| Supported inference languages/intents | No learned inference enabled; experiment scoped to seven English intents and UNKNOWN |
| Accuracy/F1/entity metrics | Generated test macro F1 0.9958 after validation-only temperature calibration and a 0.90 cutoff; 13/24 strict rejection probes falsely accepted. Native rule entity/outcome checks are separate from candidate validation. |
| Latency, model size, RAM/battery | Not measured |
| On-device/offline inference | Not implemented |

## Planned evaluation requirements

Begin with deterministic rules and structured retrieval. TF-IDF plus logistic regression or linear SVM are candidate baselines; a larger model is justified only by measured gains. Evaluation requires independent held-out utterance families, per-intent and entity metrics, unsupported/negation rejection, calibrated confidence, representative-device latency/RAM/battery measurements, and explicit failure limitations.

The product targets independently evaluated macro F1 >= 0.90 and on-device intent inference under 100 ms. The synthetic experiment does not establish those deployment targets. High classifier confidence never grants permission for an action. Every enabled model remains replaceable and subject to typed policy/confirmation validation. Emotional support is a later bounded flow and must not claim medical diagnosis or professional care.

Update this card when a model is actually introduced, recording provenance, licenses, version, dataset splits, evaluation methodology/results, scope, privacy, deployment conditions, and known risks. See the [dataset contract](docs/contracts/DATASET.md).

Phase 8 extends closed English commands for confirmed task rename, priority, deletion and postponement. Dedicated regression/storage tests cover these commands; the original 45-case development corpus remains a historical baseline, not a held-out accuracy measurement. Reminders and task actions use deterministic typed policy and platform/storage checks, not learned inference.

Phase 10 adds TASK_DECISION_V1, a fixed explainable weighted task-ranking rule. It uses recorded deadlines, priority, active goals, known effort and explicit focus preference, filters prerequisites and supplied duration budgets, and stores exact contributions. Calendar availability, behavioral success and context-switch signals remain unknown. This is not a trained or calibrated recommendation model. Its synthetic regression scenarios do not establish general recommendation quality or learned personalization. See [Phase 10 verification](docs/PHASE_10_VERIFICATION.md).

Phase 15 compares the candidate with the actual JVM rules, using a separate calibration subset and threshold-selection subset from validation families. The easy generated test score is high, but high-confidence acceptance of quoted, hypothetical, compound, and unsupported requests fails the safety gate. Independent evaluation, candidate entity/outcome/policy validation, Android parity, and representative-device latency/RAM/battery gates remain unmet. No model is shipped. See [Phase 15 verification](docs/PHASE_15_VERIFICATION.md).
