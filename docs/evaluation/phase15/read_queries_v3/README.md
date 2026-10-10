# Phase 15 read-query candidate v3

Development experiment only. No learned inference, model artifact, or runtime is included in the app. The original v2 dataset, evaluator, and rejected results remain unchanged. This experiment stays in Phase 15; Phase 16 has not begun.

## Scope and method

The candidate predicts `CHECK_TASKS`, `CHECK_MEMORY`, `WHAT_DID_I_PROMISE`, `CHECK_CALENDAR`, or `UNKNOWN`. Task/reminder/timer creation and every other action are outside learned scope. A read-intent prediction does not establish permission, entity resolution, or a successful read. In particular, calendar access remains unavailable.

There are 7,200 fictional development examples: 5,110 train, 370 calibration, 720 threshold/model-selection validation, and 1,000 test rows. Positive templates reuse exposed v2 development wording. Seven negative wrappers cover quotes, hypotheticals, compounds, injection-like text, negation, unsupported language, and creation requests. Each positive and all its wrapped variants share a family and partition. Other unrelated/creation templates are grouped before splitting. No exact normalized text crosses families. Shared fictional slot values and heuristic template grouping do not establish natural-language independence.

All five labels occur in each partition. The four partitions contain 110/8/16/23 families. Development rows are intent-only: their empty entity arrays are not extraction annotations and `expected_outcome` is not an executed outcome. The corpus is intentionally negative-heavy; coverage, accepted precision, per-class scores, and confusion matrices accompany macro F1.

Vocabulary, IDF, and balanced logistic-regression weights use train only. The model uses word TF-IDF 1–2 grams and a validation-selected `C` from 1/4/16/64. Scalar temperature fits only calibration families. Model and cutoff selection use only threshold families, requiring zero false accepts and at least one accepted query; ties prefer smaller `C` and lower cutoff. Cutoffs range from 0.90 to 0.99. If none qualifies, every learned query receives follow-up and zero coverage is reported.

A conservative eligibility filter rejects complex/quoted/hypothetical/compound/action-like inputs, non-Latin scripts, some unsupported-language markers, control characters, and untrusted sources before learned acceptance. It intentionally rejects some legitimate wording. It is not a complete language detector or injection defense. The hybrid preserves existing rule matches and negation handling; only eligible unnegated unknown requests can gain a learned read label. It has no executor dependency.

## Reproduce

Use the parent directory's pinned Python 3.12 [requirements](../requirements.txt). From the repository root:

```powershell
.\docs\evaluation\phase15\read_queries_v3\run_evaluation.ps1 -Python <path-to-python> -Offline
```

Equivalent commands on Linux/macOS:

```sh
python -m unittest discover -s docs/evaluation/phase15/read_queries_v3 -p 'test_*.py' -v
./gradlew --no-daemon :app:phase15ReadRulesSnapshot
python docs/evaluation/phase15/read_queries_v3/evaluate.py
```

The native task calls production `EnglishRules` and the existing read-only language harness for all 7,250 development and historical regression requests. Input/source hashes reject stale snapshots. This is not the current task action executor or an ML end-to-end pipeline. Reports go to `app/build/reports/phase15/`. [results-v3.json](results-v3.json) is the recorded local reference. Successful tool execution never enables a model; inspect `decision` and every gate.

Intentional corpus/source changes require `python docs/evaluation/phase15/read_queries_v3/dataset.py`, manifest inspection, and a new result. Tests regenerate into temporary directories, never overwriting user review. CI runs development evaluation only and cannot approve a benchmark or enable inference.

## User review and later integration

See [REVIEW_GUIDE.md](REVIEW_GUIDE.md) and [review-package.csv](review-package.csv). The package has **no approvals or completed human challenge prompts**. It has not been scored. Freezing requires complete review and five fresh human-authored examples per label. Synthetic draft review alone cannot satisfy the independent-evaluation gate. Human authorship is an attestation, not verified identity or a representative population sample.

After review, freeze before scoring. Candidate/dataset/production-source hashes bind the benchmark to this version; changes invalidate it. A local evaluation receipt prevents accidental reuse for another adoption attempt. This is workflow protection, not access control: deleting receipts or editing attestations cannot make repeated exposure independent. A new tuning attempt requires a new benchmark. Reports score human-authored challenges separately so draft examples cannot conceal failures.

Only after accuracy, rejection, and reviewed evaluation pass should an Android adapter be implemented. It must produce consistent predictions/entities from one parse, clarify missing or ambiguous entities before reads, preserve UTF-16 spans, retain unavailable-calendar responses and rules fallback, and never propose mutations. Preprocessing/vocabulary/IDF/coefficients/labels/calibration/cutoffs must be versioned together. Device parity, <100 ms inference, model size, RAM, battery, policy/outcome validation, and deployment calibration are still unmeasured adoption gates. No Android packaging is justified by the current result.
