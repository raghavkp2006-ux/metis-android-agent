# Phase 15 human development intake 004

This intake preserves 120 requests from the user's attached text file and 94 requests from the accompanying message, submitted after v13 physical-device validation on 2026-10-12. There are **214 cases, 213 normalized unique requests**: “Did I do it already?” occurs twice in the message. Four clarification/no-guessing instructions remain attached to their preceding questions instead of becoming standalone examples. Message IDs retain the original sentence ordinals, including gaps for those attached instructions. The attachment is copied byte-for-byte; the message text retains its wording and punctuation.

All requests use the previously agreed **separate-request, no-history** interpretation. Neighbouring requests do not establish what “it,” “him,” “that task,” or “the same application” means. Pronouns with explicit same-request antecedents, such as Neha/her code, are distinguished from unresolved standalone references. Full original wording is retained as the constraint checklist; no requested date, status, quantity, condition, field, location, attribution or negative content is discarded.

The [review table](user-review.md) contains **assistant-draft semantic intents**, unresolved-reference flags and review notes. These are not approved labels. Entity annotations, full retrieval queries and desired dispositions remain explicitly null pending review; an empty list must not imply there are no entities. UNKNOWN drafts include advice, statements, mixed requests and uncertain semantic intent. Intent does not authorize a repository read. Nothing in this batch is fitted, calibrated, used for model selection or scored as independent evaluation. ML remains disabled. This is a new data intake, not a v14 implementation or an ML adoption candidate.

The normalized duplicate audit uses Unicode NFKC, case-folding and whitespace normalization over earlier Phase 15 JSON utterances and CSV/TSV text fields. It found no matches to earlier structured inputs; the one within-batch repeat is retained and flagged, and must not cross future fitting/calibration splits or be counted as a second independent observation. Novel wording does not make exposed data an unseen holdout. Keep any remaining fresh queries unseen until candidate and thresholds are frozen.

## Current-behavior diagnostic

The native JVM replay calls the unchanged v13 production rules and orchestrator with local calendar enabled, a fixed 2026-10-12 clock/Asia-Kolkata zone, and synthetic read/context ports. It does not query the user's saved records or answer the sample questions factually. The same process records entities, queries, dispositions and context/read counts in ignored build output.

| Observation | Count |
| --- | ---: |
| Requests replayed | 214 |
| Memory reads admitted | 1 |
| Follow-up responses before context/read | 201 |
| Negation denials before context/read | 12 |
| Total context calls / total read calls | 1 / 1 |
| Forced read-label trials | 856 |
| Forged extraction trials | 856 |
| Gate bypasses observed in these trials | 0 |

The admitted request is `human-dev-004-message-030`, “What did I save about my electronics project?” Its complete literal query is `my electronics project`. None of the assistant-flagged unresolved references was admitted. All follow-up responses preserve the full submitted request. Negative recall content is still rejected in several forms; these denials do not establish that the user's intended read is invalid. Current admitted coverage is narrow; 213 blocked cases must not be called 213 correct predictions. Without reviewed gold labels there is **no macro-F1, entity accuracy, constraint accuracy or forbidden-read rate** for this batch. The forced-label checks measure consistency with the existing full-request gate, not the correctness of every gate decision.

Two new native test methods passed, including the complete 214-case replay and adversarial trials; Detekt passed. No app runtime sources, APK behavior, classifier weights, calibration thresholds or frozen evaluation artifacts changed. This data-only/JVM follow-up was not rerun on the phone; the earlier six v13 phone tests retain their separate scope.

## Reproduction and review

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/human_development_004/validate.py
.\gradlew.bat :app:testDebugUnitTest --tests dev.metis.agent.HumanDevelopment004Test :app:detekt
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/human_development_004/diagnostics.py
```

`prepare.py` regenerates the development projection from the two preserved sources and explicit assistant annotations; it is not an approval tool. `validate.py` checks provenance hashes, exact projections, no-training/no-approval flags, duplicate flags, and the existing v13 source/frozen-artifact certificate. Native logs, predictions and the generated diagnostic report stay in ignored `app/build/reports/phase15/human-development-004/`.

Next, review ambiguous meanings and complete intent/entity/query/disposition annotations. Only after explicit review should eligible, deduplicated development families enter separately versioned classifier work. Keep thematic groups together (GitHub/deployment, electronics/lab, internship, portfolio, etc.) so neighboring paraphrases do not leak between fitting and calibration. A later unseen human-reviewed evaluation and Android model measurements remain separate adoption gates.
