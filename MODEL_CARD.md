# Model card

## Current status

No ML model is implemented, trained, bundled, or evaluated. The current app has Android infrastructure, navigation, encrypted local persistence and bounded memory word search. Saved preferences are explicit user data, not learned model parameters. There is no intent classifier, language model, emotional classifier, recommendation model, or personalized habit model. The [agent training plan](docs/AGENT_TRAINING_PLAN.md) describes future specialist evaluation and training gates.

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
