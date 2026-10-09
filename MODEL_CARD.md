# Model card

## Current status

No ML model is implemented, trained, bundled, or evaluated. Phase 7 adds a deterministic rule classifier, original-text extraction and request-time date resolution for a bounded English grammar, plus local read answers. Its 45-case synthetic regression corpus was authored with the implementation; it does not establish general language accuracy or calibrated confidence. See [Phase 7 verification](docs/PHASE_7_VERIFICATION.md). Saved preferences are explicit user data, not learned model parameters. There is no learned language, emotional, recommendation or personalized habit model. The [agent training plan](docs/AGENT_TRAINING_PLAN.md) describes future specialist evaluation and training gates. The table below describes ML status only.

| Item | Status |
| --- | --- |
| Model name/version | Not applicable |
| Training/evaluation data | None |
| Intended present use | No inference capability |
| Supported inference languages/intents | None enabled |
| Accuracy/F1/entity metrics | Not measured |
| Latency, model size, RAM/battery | Not measured |
| On-device/offline inference | Not implemented |

## Planned evaluation requirements

Begin with deterministic rules and structured retrieval. TF-IDF plus logistic regression or linear SVM are candidate baselines; a larger model is justified only by measured gains. Evaluation requires independent held-out utterance families, per-intent and entity metrics, unsupported/negation rejection, calibrated confidence, representative-device latency/RAM/battery measurements, and explicit failure limitations.

The product targets macro F1 >= 0.90 and on-device intent inference under 100 ms. Those are targets, not results. High classifier confidence never grants permission for an action. Every enabled model remains replaceable and subject to typed policy/confirmation validation. Emotional support is a later bounded flow and must not claim medical diagnosis or professional care.

Update this card when a model is actually introduced, recording provenance, licenses, version, dataset splits, evaluation methodology/results, scope, privacy, deployment conditions, and known risks. See the [dataset contract](docs/contracts/DATASET.md).

Phase 8 extends closed English commands for confirmed task rename, priority, deletion and postponement. Dedicated regression/storage tests cover these commands; the original 45-case development corpus remains a historical baseline, not a held-out accuracy measurement. Reminders and task actions use deterministic typed policy and platform/storage checks, not learned inference.
