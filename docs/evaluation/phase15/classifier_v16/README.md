# V16 classifier preparation: human data gate remains open

This separately versioned offline pipeline implements the frozen v15 five-label word/character TF-IDF logistic-regression plan. Production ML is disabled and no model is packaged. No v16 candidate has been trained, calibrated or scored; there is no new F1 result.

The software path is implemented: reviewed-data loading, exact final-subset approval, fixed grid/seed, family/text/ID separation, training, temperature fitting, confidence selection, numeric JSON export/parity, calibration diagnostics and immutable first-result output. The retained v11 flat engine is loaded in isolation with seed 1515, four predeclared configurations and a global cutoff floor of 0.90. No annotations, IDs, families, parser labels or expected entities become model features. Numerical threads are restricted to one before loading the engine and during fitting.

Frozen development contains 365 submitted rows. Its one exact duplicate is excluded: 364 unique rows, including 254 training rows and 55 each for development validation/test. Development validation/test never fit features, weights, temperature or cutoffs. The test has only two calendar requests. Same-user development author/reviewer and exposed-data limitations remain disclosed.

The requesting user approved calibration intake 006's 99 unique annotations, but the final collection is incomplete. It still needs 30 calendar-event, 12 promise and six other requests, then exact 100-row subset approval. A separate 50-row selection collection is also needed. Excess pool rows stay unused; they never silently move to another purpose. `prepare_packet.py` proposes an exact quota-balanced subset without predictions; it does not approve it. `pipeline.py` rejects incomplete, unreviewed, purpose-mismatched, duplicate, overlapping, exposed-before-freeze or source-modified packets before importing the model.

Preflight:

```powershell
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/classifier_v16/pipeline.py
```

Fitting is available only after both source-bound packets are reviewed and frozen:

```powershell
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/classifier_v16/pipeline.py --calibration <approved-calibration.json> --selection <approved-selection.json> --fit --attempt first
```

Temperature fits calibration only; configuration and global cutoff use selection only. Development test scoring happens after selection. A started scored attempt locks the candidate version; failures and NO_GO results cannot be overwritten or retried under another attempt name. Further tuning needs another candidate version. All raw reports/models stay in ignored `app/build/reports/phase15/v16/`. The predeclared 313-row assistant-authored synthetic challenge is diagnostic only and never fitting/calibration/selection or independent evaluation data.

Read outcome gains are attributed separately: the new deterministic v16 parser closes the 52 v15 read-form gaps. An ML label still cannot widen read authority. Reports distinguish parser gain, combined gain against unchanged v15 rules and incremental classifier read gain. A high F1 or deterministic parsing gain alone does not establish useful ML adoption. Full native scope/result checks, fresh 150-request independent author/reviewer evaluation and Android model/resource measurements remain later gates. No automatic enablement occurs.

Eight preparation tests cover eligibility rejection, reviewed deduplication, leakage, collection purpose, pre-fit cutoff declaration, gain attribution, selection/test separation and first-failure preservation. Lifecycle tests use a stub engine in temporary output, never a fitted candidate or human calibration predictions.
