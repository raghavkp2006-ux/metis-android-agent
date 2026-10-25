# Development and teammate CSV schema

UTF-8 CSV with this exact header and order:

```text
author,text,style,intent,entities,clauses,needs_clarification,supported_now,context
```

`author` is the actual human author, required for grouping; never split one author
across train/development/test. `text` is the unchanged utterance, `style` is a
nonempty descriptive tag. `intent` must exist in `AgentIntent` in
`app/src/main/java/dev/metis/agent/domain/agent/AgentRequests.kt`; the importer
extracts that enum each time. No label aliases are silently applied.

`entities`: semicolon-separated `key=value` pairs. Allowed keys: `task`, `new_name`,
`person`, `project`, `time`, `from_time`, `to_time`, `priority`, `duration`, `anchor`.
No duplicate/empty keys or values. Values must appear **verbatim, case-sensitive**
in the utterance. A value may contain `=` but not an unescaped semicolon; values
containing semicolons cannot be represented by this v1 schema. UNKNOWN entities
must be empty. Postpone targets use `to_time`, never `time` (an explicit source
may use `from_time`). Do not normalize values into new text.

`clauses`: empty for single requests; otherwise ordered
`INTENT:key=value;... || INTENT:...`. A clause may have no entities. The first
clause must equal the primary intent/entities columns; every clause intent must
exist in code and every value must appear verbatim. The evaluator scores the full
ordered clause list even though today's grammar parses one whole request.

`needs_clarification` and `supported_now`: exactly `yes` or `no`.
`supported_now` describes current implementation, not desired future behavior.
UNKNOWN describes out-of-scope meaning, while UNSUPPORTED is a recognized code
label for unavailable behavior; do not relabel without review.

`context`: exactly `none` or `fixture`. None means empty tasks and no prior turn;
only intent/entities/clarification are scored, with epoch used for deterministic
lexical parsing and no relative-time normalization claim. Fixture means exactly
`fixtures/default_state.json`, no prior turn, pinned to its `now`. Missing `now`
makes the row unevaluable. Timezone is Asia/Kolkata, explicitly recorded by the
exporter. A fixture timestamp accepts an ISO instant or an ISO local datetime
in that zone. No fixture rows run under substitute state or a host clock.

The provided sheet is preserved byte-for-byte as `ml/data/dev_own.csv`, a visible
DEVELOPMENT set. Its audit may reject rows; copying a supplied sheet for audit is
not a successful strict import. `import_sheet` refuses output for invalid labels,
value mismatches, exact/normalized/typed-skeleton or pinned MiniLM duplicates.
Nothing silently repairs or drops gold rows. Reports identify every row by its
1-based data-row ID (header excluded).

The public JSONL pool is TRAIN-only intent candidate data. It retains original
source/split/locale/mapping/provenance, empty entities, and review status. It is
not teammate gold and cannot be passed into the CSV evaluator or author splitter.
Official public development/validation/test splits are excluded. Exposed V12/V16
and V17 quarantine material is never independent evaluation.

## Prediction entity bridge

Production raw TITLE -> `task` (UPDATE_TASK -> `new_name`), TASK -> `task`, TIME
-> `time` (POSTPONE_TASK -> `to_time`), PERSON -> `person`, DURATION -> `duration`,
MEMORY/GOAL/LOCATION -> `anchor`. Raw spans are kept; normalized time/reference
objects are audit evidence rather than replacement gold strings. UPDATE_TASK is
the code intent for both rename and prioritize grammar; CSV labels are not aliased.
Reference selection uses the current exact case-insensitive unique open-task rule;
there is no prior-turn reference resolver or multi-intent splitter in the current path.
