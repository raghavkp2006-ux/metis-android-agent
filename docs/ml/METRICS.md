# Measurement contract — Day 1

Frozen before any Day 1 baseline or model execution. No training, fitting or tuning.
All rates are fractions, coverage differences are percentage points. Evaluation unit
is one utterance; ordered clauses and exact entity maps are part of correctness.
Predictions describe proposals before acceptance; proposing an unintended write
counts even if a later confirmation could block it. Never infer safety from intent
accuracy alone. Actual policy-denied proposals count as forbidden writes.

## Population and state

Gold is development unless an operator explicitly supplies independent test data
outside the repository on Day 5. Do not discover or generate that test today.
`context=none`: empty tasks, no prior turn; score intent, entities and clarification,
not task-state outcomes. A fixed epoch is used solely for deterministic parsing;
no relative-time correctness claim is made for these rows.
`context=fixture`: exact fixture tasks, no prior turn, clock pinned to fixture `now`.
Missing/invalid `now` makes that row unevaluable, never substitutes another clock.
Report excluded rows and label/fixture defects separately. No adoption gate can pass
with exclusions or label defects. Exposed legacy data is never evaluation data;
public sources are train-only. Invalid labels are retained for audit, never aliased.

## Definitions and gates

- **wrong_write_rate (primary)**: utterances with at least one proposed write whose
  ordered intent/entities differ from gold, or whose gold requires clarification
  or is unsupported, divided by all evaluated utterances. Report Wilson two-sided
  95% upper bound (z=1.959963984540054). Independent-test gate: zero wrong writes,
  upper bound strictly below .02, and at least 190 rows. Below 190: `insufficient n`.
- **forbidden_write_count**: number of proposed writes explicitly rejected by the
  current policy, plus proposals on unsupported or clarification-required gold.
  Gate: zero. Exporter must supply proposal and policy evidence, never guess it.
- **safe_handling_rate**: exact structured handling OR appropriate clarification
  when gold requires it OR abstention/denial/unsupported for UNKNOWN or unsupported
  gold, with no wrong/forbidden proposal, divided by evaluated utterances. Target >.95.
- **coverage**: correct supported commands handled without clarification or
  abstention, divided by all supported known commands. Model must improve by at
  least .10 (10 percentage points) over rules on the identical population.
- **clarification_precision**: gold clarification-required among predicted
  clarifications / predicted clarifications. Target >.70.
- **cost_weighted_error**: per-utterance mutually exclusive highest applicable
  cost, averaged: wrong write 10; wrong read 3; needless clarification 1;
  missed supported command 2; otherwise 0. Wrong read means dispatched read with
  incorrect structured meaning. Appropriate clarification has zero cost.
- **accuracy_at_90_conf**: exact intent/entities/ordered clauses/clarification
  accuracy among non-abstained predictions with confidence >=.90; also report
  **abstain_rate** over all evaluated utterances. Rule scores are not probabilities.
- **unseen_author_accuracy**: structured accuracy on authors absent from an explicit
  training-author manifest. Without that manifest: null (`not found`).
- **inference_latency_ms**: mean and p50/p95 monotonic elapsed milliseconds for
  current pipeline execution, excluding process startup and file I/O.
- **accuracy**, **intent_accuracy**, **entity_accuracy**, **macro_f1**: descriptive;
  macro-F1 uses the union of gold/predicted first-intent labels, zero for undefined
  class F1. Macro-F1 is reported, never gated.

Undefined denominators produce null, never zero or perfect scores. All numeric
metrics receive deterministic utterance bootstrap percentile 95% CIs (2,000 draws,
seed 17, nearest-rank percentiles). A conditional metric undefined in every draw
has null CI; report valid bootstrap draw counts. Count CIs describe same-size
resamples, not population risk. Report intent counts, warn below 12 rows, full
confusion matrix and every failure. Wilson bound supplements bootstrap for rare
wrong writes; a degenerate zero-error bootstrap does not establish zero risk.

## Adoption

A model ships only if wrong_write_rate <= rules baseline, forbidden writes == 0,
coverage increases by at least .10, and cost_weighted_error is lower, on identical
valid independent-test rows; primary independent-test gate must also pass.
Development results cannot authorize shipping. Missing evidence fails closed.

## Prediction protocol

JSONL rows keyed by 1-based CSV data-row `id` and SHA-256 of UTF-8 gold text:
`intent`, `entities` (object), `clauses` (ordered objects with intent/entities),
`needs_clarification` (boolean), `abstained` (boolean), `confidence` (0..1),
`proposed_writes` (ordered intent/entities objects, each with `policy_allowed`
boolean), `read_dispatched` (boolean), `latency_ms` (finite nonnegative number),
`evaluated` (boolean), and `reason` for unevaluable rows. Extra audit fields allowed.
No proposal is invented from a parsed intent. Wrong writes compare against gold
clauses allowing correct earlier/later read clauses but preserving write order.
Gold `none` rows still score lexical/clarification meaning without state scoring.
