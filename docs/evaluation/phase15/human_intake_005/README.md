# Phase 15 human intake 005: unscored requests

Received 2026-10-12 after the v14 increment. The user called this 100 fresh tasks; the submitted list contains **101 numbered items**, preserved verbatim in `requests.txt`. The user explicitly confirmed **independent requests**, without conversation history. Earlier items do not resolve pronouns or identify records in later items. Numbered no-guessing/missing-value instructions remain separate as submitted; no neighboring request is silently appended or used as context.

This is a data-only intake, not an evaluation or implementation increment. There are no classifier predictions, fitted weights, calibration changes, phone queries or reads of personal records. Intent, entity, complete-query and expected-disposition annotations remain null, pending human review. Full original wording is retained as the constraint checklist. Human authorship of a question does not constitute approval of its labels. There is no F1 or factual-answer score.

`intake.json` records source hashes, normalized within-batch duplicates and exact normalized overlaps with earlier structured JSON utterances. The audit found **101 normalized unique items and zero exact prior overlaps**. Normalization is Unicode NFKC, case-folding and whitespace folding; this audit does not establish semantic independence or detect paraphrases. Audit inputs are individually bound by the intake manifest. No request enters training, calibration, selection or regression replay. Evaluation eligibility is undecided: v14 is NO_GO, this batch has now been submitted/inspected, and there is no prospective frozen evaluation protocol or reviewed gold. Do not retrospectively call it a fresh independent evaluation. Later evaluation requires a protocol and labels frozen before predictions; any requests used for development must be excluded from the independent holdout.

The existing v14 candidate, model settings, approvals/results and all earlier artifacts remain unchanged. ML stays disabled. The connected phone is not needed for this intake.

Validation: intake projection/count/duplicate/hash checks passed, as did the existing v14 preservation certificate, 42 retained Python behavior tests and five v14 regressions. No Android code changed; no APK rebuild or device test was run for this data-only intake.

Reproduce the count, projection, duplicate audit, manifest and v14 preservation check:

```powershell
.\.venv-phase15\Scripts\python.exe docs/evaluation/phase15/human_intake_005/intake.py
```

The `--prepare` option writes the unannotated intake and its manifest; it does not label, fit, predict or approve anything. Keep these requests outside development until their intended evaluation role is decided. Continue implementation from previously exposed development data instead.
