# Phase 15 read-query candidate v4

Development only. Four read intents plus UNKNOWN; no model is packaged or enabled in Android. See [results and limitations](../../../PHASE_15_READ_QUERIES_V4.md).

## Reproduce

Use Python 3.12 and the parent [pinned requirements](../requirements.txt). From the repository root:

```powershell
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_queries_v4/rules-input-v4.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-v4.tsv"
python docs/evaluation/phase15/read_queries_v4/v4_evaluate.py
```

On Linux use `./gradlew` and omit `--offline` if dependencies are not cached. The native snapshot task uses the existing JVM rule harness. It does not exercise an Android ML adapter or actual repositories. Hashes reject stale data, native snapshots, and changed sources. Intentional corpus/source changes require `python docs/evaluation/phase15/read_queries_v4/v4_data.py`, inspection of the updated manifest, and re-evaluation; do not regenerate to conceal drift.

Full per-row results and serialized weights go to ignored `app/build/reports/phase15/`. The checked-in `results-v4.json` retains aggregate metrics and all 25 user regression details; generated development details are omitted there. Export includes vocabulary, IDF, weights, labels, temperature, cutoff, and preprocessing/manifest hashes. It remains disabled. Serialized feature/weight calculations are compared with sklearn on evaluation rows; preprocessing is shared, and this does not establish Android parity.

## Method and provenance

11,168 fictional generated rows: 7,746 train, 510 calibration, 1,142 threshold selection, 1,770 development evaluation. Family groups stay together, including nine negative wrappers around each positive template. Normalized duplicates and cross-partition families are rejected. Shared fictional entities and heuristic template clustering limit independence. Prior v2/v3 wording and user examples were exposed during development. Exact user strings are excluded from all generated partitions, but their wording informed v4: they are regressions, never blind evidence. No external data or new distribution license is introduced.

Eligibility is applied to original text before fitting or inference. This prevents guard-rejected quoted/negated prompts from teaching conflicting feature labels. Vocabulary, IDF and classifier weights use eligible training rows only. Eligible calibration families fit temperature; threshold families choose word versus word/character features, C=1/4/16, and cutoff 0.90–0.99 with zero sampled false accepts. Evaluation families do not select hyperparameters. All v4 results remain development evidence.

Classifier-only entity masking reduces dependence on fictional topic/person names. Span extraction retains original text and UTF-16 offsets. These are heuristic English patterns, not a trained entity model. Ambiguous topics, unusual names, multilingual inputs and broad real-world phrasing remain gaps. Calibration has only 55 eligible synthetic rows and is not deployment calibration.

The development hybrid guards unsafe native read matches, preserves non-read native rule matches, retains eligible read rules, and lets ML fill eligible UNKNOWN requests. It can distinguish task-state negation, contractions, and numeric range conjunctions from some unsafe commands. These are bounded heuristics, not complete semantic negation or injection defenses. Production rules and policy are unchanged.

Outcomes are simulations: missing entities and unsupported date filtering receive follow-up; unavailable calendar access stays unsupported. No actual read, mutation, proposal, or completion is executed. Existing user review remains pending. A fresh reviewed benchmark, real integration/outcome validation, representative-device measurements and Android parity are required before adoption. Phase 16 has not begun.
