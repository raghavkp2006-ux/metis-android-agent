# METIS NLU v1 — Offline Intent Smoke Pipeline

**SMOKE TEST - NOT VALID FOR ADOPTION.** This Python pipeline is for offline
experimentation only. It does not change Android code and must not authorize
actions. Rules run first; the classifier is only a suggestion when rules return
UNKNOWN, and the existing rules and policy remain authoritative.

## Run

Install the single direct dependency and execute from this directory:

```powershell
python -m pip install -r requirements.txt
python run_all.py
```

`data.csv` must contain `text`, `intent`, `author`, and `supported_now` columns.
Only supported rows (`supported_now == yes`) are used. Extra input columns are
ignored. The checked-in `data.csv` is the attached pilot seed.

The JSON-formatted `config.yaml` is also valid YAML and keeps configuration
dependency-free. It defines the intent set, exclusions, thin-class thresholds,
seed, model settings, and the forbidden-write intents. A single-author dataset
uses repeated stratified 3-fold CV (5 repeats, threshold 3). Multiple-author
datasets use an author-disjoint train/validation split and a `test.csv` frozen
on first creation (threshold 30); the frozen test file is never overwritten.
Class labels below the applicable configured threshold are omitted.

## Outputs

- `model.json`: multinomial logistic-regression parameters and the dependency-free
  tokenizer specification.
- `predict.py`: standard-library inference; accepts text with `--text` and emits
  probabilities plus a rejected-or-predicted label.
- `reference_tests.json`: 50 input/probability pairs. Pipeline execution checks
  the exported scorer against scikit-learn within `1e-6`, then verifies exact
  reproduction of the JSON probability values through `predict.py`.
- `REPORT.md`: macro-F1, accuracy, and forbidden-write false-accept count only.
- `test.csv`: created only for multi-author runs; its author groups are held out
  from training and validation.

All generated outputs are marked **SMOKE TEST - NOT VALID FOR ADOPTION**.
