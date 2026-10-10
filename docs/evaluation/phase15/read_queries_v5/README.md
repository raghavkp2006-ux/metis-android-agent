# Phase 15 v5: intent and read behavior

Development only. English plus limited Roman-script Hinglish. No Android model, read adapter, repository integration or mutations are enabled. The original v4 candidate and first standalone 125-query evaluation remain unchanged. See [the v5 report](../../../PHASE_15_READ_QUERIES_V5.md).

## Reproduce

Use Python 3.12 and install this directory's pinned `requirements.txt` (includes parent requirements plus timezone data for Windows). From the repository root:

```powershell
python -m pip install -r docs/evaluation/phase15/read_queries_v5/requirements.txt
python -m unittest discover -s docs/evaluation/phase15/read_queries_v5 -p 'test_*.py' -v
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_queries_v5/rules-input-v5.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-v5.tsv"
python docs/evaluation/phase15/read_queries_v5/evaluate.py
```

Use `./gradlew` on Linux/macOS; omit `--offline` if dependencies are not cached. Normal evaluation writes only ignored build reports. `--record-reference` explicitly updates the compact checked-in reference. Source or corpus changes require explicit `python docs/evaluation/phase15/read_queries_v5/data.py`, manifest review and re-evaluation. Regenerate the native snapshot when native input changes. CI verifies the same pipeline without refreshing tracked references or approving deployment.

## Data and selection

18,570 fictional generated rows: 13,068 train, 870 calibration, 1,884 threshold/model selection, 2,748 development evaluation. Positive templates and ten negative wrappers stay together in heuristic family groups. Topic/person slot pools are disjoint across partitions, including unknown examples with these slots. Shared general vocabulary and heuristic grouping still limit independence. Unknown families are stratified by guard eligibility so each partition includes eligible unknown examples. Exact normalized exposed prompts are excluded from generated data. All 150 contributed prompts remain separate **exposed regressions**; their patterns informed this revision.

Eligibility examines original text before fitting and prediction. It handles some negation, quotes, hypothetical/compound requests and out-of-scope actions, with bounded exceptions for task-state negation, numeric ranges and past promises. It is not complete language detection or an injection defense. Unsupported scripts are rejected. Roman Hinglish support covers authored wording and ambiguous relative dates such as `kal` receive clarification.

Classifier features use normalized request text, original-span topic/person masking and explicit lexical cues for memory/calendar/commitment/unfinished wording. These cues are features, not hard intent assignments; logistic regression still predicts labels and can abstain. Both word TF-IDF 1–2 grams and word/character 3–5 grams are compared at C=1/4/16. Vocabulary, IDF and weights fit eligible train only. Temperature fits eligible calibration only, bounded to [0.25,4] to prevent extreme sharpening from a small perfect synthetic sample. This bound is a development constraint, not deployment calibration evidence. Cutoffs 0.90–0.99 and model choice use threshold families only, requiring zero sampled unknown accepts and zero forbidden simulated reads. If no candidate qualifies, ML abstains.

All v5 partitions were inspected during development; their results are development evidence. Different corpora and stricter abstention mean generated scores should not be compared directly with v4's easier generated score. Calibration ECE/Brier, per-class scores, coverage, isolated extraction, end-to-end entities, filter fields and dispositions are reported separately. Generated annotations are assistant drafts, not human-reviewed truth. Isolated extraction receives the expected intent and must not be presented as end-to-end accuracy.

## Internal interface and behavior

`ReadParse` carries `intent`, `disposition` (`read`, `clarify`, `reject`, `unsupported`), `reason`, original UTF-16 `entities`, `filters`, and simulated read/proposal/completion counts. Filters preserve task status, overdue/category constraints, raw time expressions, bounded day ranges, promise status and vague age constraints. Snapshot date uses each fixture timestamp and captured zone. Day ranges do not resolve precise evening/lunchtime meaning; unsupported filtering always clarifies.

Unresolved references and incomplete topics cannot become a read solely because the classifier chose the wrong intent. Existing non-read native matches are preserved for comparison without granting ML action authority. Actual product rule handling is unchanged. Calendar access stays unsupported; incomplete requests clarify. Every outcome is simulated; no repository or executor is called.

The current Kotlin read port exposes unfiltered tasks, a memory query/time and a person-only promise query. A future adapter would require typed read queries carrying the validated filters, supported-filter capability checks, deterministic date/zone resolution and repository results from the same parse. That API extension is deferred until reviewed accuracy/rejection gates pass; no Kotlin interface or database migration is introduced now.

The export contains feature vocabularies/IDF/weights, labels, coefficients/intercepts, temperature/cutoff and source/manifest hashes, and remains disabled. Serialized Python feature/weight math is compared with sklearn using shared v5 preprocessing; this is not Android tokenizer or numeric parity.

## Review and fresh evaluation

`annotation-review-exposed.csv` contains 125 **unapproved assistant drafts**, retaining user response summaries and blank reviewed/approval fields. It is not independent evaluation or a completed review of the older package. Correct the reviewed intent, UTF-16 entities, filters and disposition, then explicitly fill reviewer/approval. The generator refuses to overwrite a review file:

```powershell
python docs/evaluation/phase15/read_queries_v5/v5_review.py prepare-exposed --output <new-review.csv>
```

After the candidate is fixed, collect at least 25 genuinely new examples per label in the same columns, including approved entities, filters and disposition; attest `human_authored=yes`. Avoid fictional data that merely substitutes names into these templates. Freeze before any scoring:

For fresh review, label a clear memory/promise intent separately from its missing-topic/person clarification outcome. Use UNKNOWN for genuinely out-of-scope/negated/hypothetical/compound requests. The old contributed reject/clarify category remains an UNKNOWN routing projection for historical comparison; reviewers may correct its draft semantic labels. Include exact expected filters, including an empty dictionary when none is intended.

```powershell
python docs/evaluation/phase15/read_queries_v5/v5_review.py freeze-fresh --input <fresh-reviewed.csv> --output <fresh-frozen.json> --reviewer <reviewer-name>
```

This rejects unapproved rows, exact exposed/generated duplicates, invalid spans, insufficient class coverage and invalid read annotations. It emits a bound native `.tsv`. Run the native snapshot with that input and a separate output, then evaluate:

```powershell
python docs/evaluation/phase15/read_queries_v5/evaluate.py --reviewed-benchmark <fresh-frozen.json> --reviewed-baseline <fresh-native.tsv> --output <first-reviewed-result.json>
```

The candidate manifest must match. A consumption receipt prevents accidental repeated exposure; deleting it cannot restore independence. Preserve the first result. Fresh review may satisfy only the reviewed simulated-evaluation gate when macro F1 is >=0.90, no forbidden reads occur, and all reviewed entity/filter/disposition checks match. Representative calibration, actual repository/policy behavior and Android/device gates remain separate. Authorship and near-paraphrase independence are not externally verified.
