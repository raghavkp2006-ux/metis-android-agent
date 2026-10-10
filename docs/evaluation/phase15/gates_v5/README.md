# Remaining v5 gates

The candidate is frozen. These tools live outside its source directory so collecting review data does not change the candidate manifest. ML remains disabled; Phase 15 is not complete.

## Your next input: fresh reviewed queries

Copy `fresh-review-template.csv` to a new file and fill its 125 blank rows (25 per semantic intent). The template has **no generated prompts or approvals**. Write natural queries you would actually use, including varied wording and difficult cases. Avoid reusing the previous 150 prompts or merely replacing their names/topics. Use fictional personal details; real private notes are unnecessary.

| Column | What to enter |
| --- | --- |
| `intent` | CHECK_TASKS, CHECK_MEMORY, WHAT_DID_I_PROMISE, CHECK_CALENDAR or UNKNOWN; suggested slots may be corrected, retaining at least 25 of each |
| `utterance` | A genuinely new query |
| `entity_text` | Exact topic text for memory, or exact person name for promises, copied from the query; blank when unresolved/absent |
| `entity_start_utf16` | Normally blank; required only if the entity occurs more than once. Zero is valid. Tool derives offsets otherwise |
| `filters_json` | Expected filters as a JSON object; `{}` if none. Do not copy model predictions as truth |
| `disposition` | `read`, `clarify`, `reject` or `unsupported` |
| `expected_response` | In your words, what METIS should do or ask |
| `reviewer` | Name/identifier of the person approving this row |
| `approved` | `yes` only after the person has checked the annotation |
| `human_authored` | `yes` only for queries authored by a human |

Label semantic intent separately from the response: a clear memory query missing its topic can be CHECK_MEMORY + clarify. UNKNOWN covers unsupported actions, negation, hypothetical/quoted commands and compound requests. Calendar access is unavailable: complete calendar queries use unsupported; incomplete ones clarify. Only tasks/memory/promises can read. A memory/promise read needs one resolved entity. No external actions or deletion are executed.

### Filter annotation contract

Review filters against the intended meaning, using this schema for comparison:

- Pending tasks: `{"status":"incomplete"}`; add `"overdue":true` or `"category":"assignment"` when requested.
- Unresolved promises: `{"status":"unresolved"}` when explicitly requested; otherwise `{}`.
- Ordinary memory retrieval: `{}`. Explicit older notes use `{"age":"older_unspecified"}`.
- Time constraints: put the normalized lowercase requested phrase in `time_expression`. Resolved single-day dates also use `date_range` with `start` and `end_exclusive` as ISO dates. Fixtures use local reference date **2026-10-09, Asia/Kolkata**. Ambiguous Hinglish dates use `"ambiguous_relative_date":true`.

If the meaning cannot be expressed clearly, mark the row for discussion before approval. Do not guess. Current v5 simulates only unfiltered memory/promise reads and task status filtering; additional filters lead to clarification. The production adapter still needs later work. This is the current comparison schema, not proof that the candidate correctly extracts every field.

## Maintainer commands

From the repo root, with the v5 Python environment:

```powershell
python docs/evaluation/phase15/gates_v5/collect.py prepare --output <new-blank.csv>
python docs/evaluation/phase15/gates_v5/collect.py convert --input <filled-template.csv> --output <reviewed.csv> --reviewer "Reviewer name"
python docs/evaluation/phase15/gates_v5/collect.py freeze --input <reviewed.csv> --output <fresh-frozen.json> --reviewer "Reviewer name"
python -m unittest discover -s docs/evaluation/phase15/gates_v5 -p 'test_*.py' -v
python docs/evaluation/phase15/gates_v5/audit.py --output app/build/reports/phase15/gates-v5.json
```

Conversion preserves explicit human approval and calculates original UTF-16 entity spans. Freeze delegates to the existing v5 validator: exact exposed/generated duplicates, insufficient label coverage, invalid spans and unapproved rows are rejected. All collection/review/freeze commands refuse overwrites. Authorship and near-paraphrase freshness are attestations, not externally verified facts.

Then follow the [v5 native snapshot and first-score instructions](../read_queries_v5/README.md#review-and-fresh-evaluation). Freeze before inference. Keep the first evaluation result and consumption receipt; repeated scoring or deleting a receipt cannot restore independence. Do not use this set for calibration or tuning. After observing failures, improvements require a new candidate and a new independent benchmark.

## Gate status, 2026-10-10

| Evidence / gate | State and next work |
| --- | --- |
| Frozen source/data/regression integrity | Passed local hash audit |
| Disabled development export integrity | Passed local hash audit |
| Python export math | Previously recorded 2,898-row parity; not Android parity |
| Development quality | Needs improvement: 6 unknown label accepts, end-to-end entities 114/120, filters 47/66; zero forbidden simulated reads |
| Independent reviewed evaluation | Pending fresh human data and first scoring |
| Representative calibration | Pending separate representative calibration data |
| Repository/policy outcomes | Pending prerequisite gates, typed adapter and actual repository evidence |
| Android parity and devices | Pending prerequisite gates and device evidence; ADB inventory on this date had no connected device |

Audit output is an ignored local report. Exit zero means audited integrity checks passed; it **does not** approve adoption. Recorded Python parity is not rerun by the audit. Missing or mismatched export fails the audit. The original legacy language-scope exception remains visible in `results-v5.json`. Overall decision: **NO_GO**.
