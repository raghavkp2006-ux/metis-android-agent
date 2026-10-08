# METIS agent training and evaluation plan

Status: design plan, 2026-10-08. No model is trained, bundled, or enabled. The current app implements navigation and local persistence. This plan follows the [roadmap](ROADMAP.md), [dataset contract](contracts/DATASET.md), [agent protocol](contracts/AGENT_PROTOCOL.md), and [model card](../MODEL_CARD.md).

## What an individual agent means

METIS's specialists are replaceable software engines coordinated through one typed request/proposal/action pipeline. They share local context and repository interfaces. A specialist does not need its own large language model. Learned outputs may suggest an intent, entity, or score; policy and executors retain authority over mutations and Android effects.

| Specialist / engine | Initial implementation and evaluation | Possible later training |
| --- | --- | --- |
| Language | Rules, entity extraction, fixed-clock date/time tests, ambiguity/negation/unknown rejection | A shared small intent classifier; an entity model only when extraction evaluation demonstrates a gap |
| Memory | Structured retrieval, explicit/derived separation, encrypted storage, bounded search; synthetic query/relevance fixtures | Ranking from curated relevance judgments, compared against the Phase 5 weighted baseline |
| Planner / decision | Constraint validation and explainable scoring over tasks, deadlines, dependencies, saved preferences and known availability | Ranking/estimation only after consented feedback and measurable improvement; hard constraints stay enforced in code |
| Action / policy | Typed deterministic validation, confirmation, permissions, idempotency, verification, crash/retry/undo tests | No learned permission grant or model-selected Android dispatch |
| Events / habits | Normalized factual events; opt-in, repeated-evidence statistics tested on synthetic timelines | Later consented local personalization with provenance, sample count, confidence, deletion, and recomputation |
| Emotional support | Bounded, reviewed support flows and escalation/unsafe-response cases | Later evaluation-led changes; no diagnosis or dependence claims |

Saved explicit preferences are user-provided facts, not training examples or inferred habits. Changes to autonomy and behavioral consent require separate policy-bound controls; planning preferences cannot grant that authority.

## First training workflow

1. **Define enabled scope.** Phase 6 establishes interfaces and policy-bound proposals; Phase 7 implements and evaluates the language rules. Begin with a small supported intent set, including unsupported and negated requests. Catalog entries alone do not enable actions.
2. **Create versioned examples.** Start with synthetic, manually reviewed utterances, fictional entity fixtures, and intent/entity/outcome labels. The source plan targets 100–300 examples per enabled intent; 3,000–9,000 examples applies only after expanding to about 20–30 intents. Include short, Indian English, typo, voice-like, ambiguous and out-of-scope inputs. Evaluate Hinglish separately before claiming support. Fix clock, zone, available capabilities and entity context in resolution tests.
3. **Split before fitting.** Deduplicate and group paraphrase/template families, then split families 70/15/15 into training/validation/test. Keep all related examples in one split. Fit vocabulary/IDF and classifier weights on training only; tune thresholds on validation only; freeze test families. Record licenses, provenance, annotation rules, seed and dataset version. Private messages, audio, notifications and timelines are excluded by default.
4. **Train the smallest useful candidate on a development machine.** The planned first learned classifier is TF-IDF plus logistic regression (linear SVM is an alternative). Compare it against the same deterministic baseline. This classifier predicts supported intent probabilities; it does not generate executable Android instructions. No training job runs in the app as part of current persistence work.
5. **Measure the complete outcome.** Report macro/per-intent F1, entity precision/recall, date/person resolution, unknown/negation rejection, confidence calibration and clarification behavior. Separately test policy denial, stale proposals, permission revocation, repeated acceptance, crash recovery and wrong external action cases. The project targets macro F1 ≥ 0.90 and intent inference <100 ms on representative devices; these remain unmeasured targets, not results. A wrong external action blocks enablement regardless of aggregate accuracy.
6. **Package only a verified model.** In Phase 15, introduce ML only if it improves held-out outcomes enough to justify latency, memory, size and battery costs. For a linear classifier, version vocabulary/IDF, coefficients, labels, preprocessing and calibrated thresholds together; an Android adapter must match development predictions on golden inputs before deployment. Tokenization, UTF-16 entity offsets, Unicode and unknown inputs require parity tests. A larger compatible on-device model/runtime is a later option with a separate measured gate. Keep rules/follow-up behavior available when model output is unavailable or uncertain.

## Personalization and privacy

Personal memory stays in the encrypted local store and is retrieved as context. Remembering a fact does not retrain model weights. Habit/behavior learning is a later opt-in feature; no background data collection, remote training upload, or dataset contribution is enabled by this plan. Any contribution requires explicit opt-in, redaction and review. Consent withdrawal and deletion must cover derived records and personalization artifacts before that capability ships.

Every enabled model needs a reproducible version, evaluation report, compatible inference adapter, privacy review, model card, and rollback path. An individual specialist receives its own test suite and evaluation fixtures; separate learned models are introduced only when its task benefits from them.

The candidate tools are documented by their maintainers: [TF-IDF vectorization](https://scikit-learn.org/stable/modules/generated/sklearn.feature_extraction.text.TfidfVectorizer.html) and [logistic regression](https://scikit-learn.org/stable/modules/generated/sklearn.linear_model.LogisticRegression.html). They are future training-tool candidates and are not app dependencies.
