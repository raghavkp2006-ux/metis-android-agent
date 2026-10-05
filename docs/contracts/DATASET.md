# Language dataset contract v0.1

No trained model or accuracy claim exists in Phase 0. The initial language engine is deterministic rules/regex, date/time parsing, permitted entity lookup, and confidence checks. ML is added only after evaluation shows a meaningful improvement over that baseline.

## Record schema

UTF-8 CSV uses a header and correctly quoted text/JSON. Required source columns from the plan: id, intent, utterance, language, style, source, noise_type, entities, difficulty, split. Add family_id to enforce leakage-free splitting, and expected_outcome to evaluate safety rather than intent accuracy alone.

| Field | Contract |
| --- | --- |
| id | Unique synthetic UUID or stable dataset ID |
| intent | Enabled catalog label; UNKNOWN for unsupported input |
| utterance | Synthetic/consented text; no real identifiers or private content by default |
| language | BCP-47 tag, e.g. en-IN, hi-Latn-IN |
| style | formal, normal, short, voice_like, indian_english, hinglish, reordered |
| source | synthetic, curated, consented_redacted |
| noise_type | none, typo, speech_noise, punctuation, mixed |
| entities | JSON array of type, startOffset, endOffset, rawValue, optional normalizedValue; UTF-16 [start, end) indexes |
| difficulty | easy, medium, hard |
| split | train, validation, test |
| family_id | Group covering paraphrases/templates and near duplicates |
| expected_outcome | execute_eligible, propose, clarify, reject; policy still checks actual execution |

Example annotation (synthetic): utterance “Remind me tomorrow at 8 to call Rahul”; intent CREATE_REMINDER; PERSON span [32,37), DATE [10,18), TIME [22,23); expected_outcome clarify because AM/PM is unspecified. TASK/title “call Rahul” can be separately annotated if the extractor's schema supports nested spans. Fixed clock/zone/context fixtures are required for date normalization tests; “tomorrow” has no context-free absolute timestamp.

Entity catalog: PERSON, DATE, TIME, DURATION, LOCATION, TASK, APP, PRIORITY, RELATIONSHIP, QUANTITY, PROJECT, GOAL. Contact entity resolution uses fictional fixtures and tests zero/one/multiple matches independently from extraction.

## Collection and splits

Prototype target from the plan: 100–300 examples per enabled intent, initially 20–30 intents (3,000–9,000 examples). Expand high-traffic intents to 500+ examples when justified. Maintain a manually curated entity evaluation set targeting 2,000–4,000 commands; generated examples do not substitute for independent evaluation.

Split 70% training, 15% validation, 15% test by family_id, not individual rows. Cluster near duplicates before splitting; all related templates/paraphrases belong to one split. Keep test data frozen and exclude it from threshold tuning. Record seed, dataset version, provenance, license, and label guidelines.

Include formal, casual, short, voice-like, Indian English, Hinglish, misspelled, reordered, ambiguous, negated, and out-of-scope commands. Hinglish evaluation does not imply advertised support before coverage passes. Negative utterances that name a valid intent still have reject/clarify expected outcomes; never count them as permission to act.

No private messages, microphone audio, notification contents, or personal timelines enter datasets by default. Contribution requires explicit opt-in, identifier replacement, redaction, and review. Behavioral learning uses synthetic timelines, internal dogfooding, then consenting pilots; no generic public productivity dataset is treated as representative of a user.

## Evaluation gates

- Compare every model with the deterministic baseline on the same held-out families.
- Report macro F1 (target >= 0.90), per-intent precision/recall, confusion matrix, entity precision/recall, resolved date/person accuracy, unsupported/negation rejection, and clarification rate.
- Wrong external action cases block enablement even when aggregate F1 is high. Test ambiguity, permission denial, and policy separately from classifier accuracy.
- Measure inference latency (target under 100 ms on representative devices), model size, RAM, and battery impact. Do not extrapolate host timings to phones.
- TF-IDF + logistic regression or linear SVM are the first classifier baselines. Larger models need demonstrated gains and a replaceable interface.
- Confidence thresholds are >=0.90 high, [0.60,0.90) confirmation/clarification, <0.60 follow-up. Calibrate on validation data; retain policy validation for every action.

Phase 1 provides DATASETS.md and MODEL_CARD.md with honest untrained/not-evaluated status. Phase 7 adds language evaluation; Phase 15 adds ML only where justified.
