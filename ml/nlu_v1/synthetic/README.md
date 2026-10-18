# Controlled synthetic candidates (Plan 05)

Generation starts with a closed semantic structure and seed, then renders text.
No user query batches, network model, downloaded corpus, or evaluation examples
are generation inputs. This is a bounded template generator, not an open-ended
language model paraphraser or a semantic quality evaluation.

```powershell
python -m ml.nlu_v1.synthetic.generate --check
python -m ml.nlu_v1.synthetic.generate --batch-id plan05-seed17 --seed 17 --count 1
python -m ml.nlu_v1.synthetic.generate --verify-batch plan05-seed17
python -m unittest discover -s ml/nlu_v1/tests -v
```

Use a fresh batch ID for every run; existing files are never replaced. `--root`
can select a separate local batch directory. Both the ordinary batch manifest
and the generation manifest must verify before consuming a generation batch.
The generation commit marker is written last and binds the group registry,
generation report, original templates, local source registry, mix policy and code
snapshot. The ordinary manifest binds raw structures, canonical/quarantine rows
and the dedupe report. A partial run without both markers is incomplete.

## Structure and generators

[synthetic_families.yaml](../configs/synthetic_families.yaml) defines required
slots, labels, bindings and surface forms before any utterance is rendered:

```yaml
task_read_subject_date_status:
  domain: TASK
  action: READ
  disposition: SUPPORTED_COMPLETE
  required_slots: [SUBJECT, DATE, STATUS]
  forms:
    canonical: 'show {status} {subject} tasks due {date}'
```

The checked-in template freeze detects edits; changing semantics or allowed
forms requires a deliberate template version/review. This freeze authorizes
candidate generation only and is not human review of any resulting corpus.

Four initial positive families cover tasks, calendar, memory and promises. Each
has canonical, polite, fragment, slang, indirect and typo forms. The APIs
`paraphrase_structures`, `hard_negative_structures` and `ambiguity_structures`
return declared structures. `structures` assembles them; `make_rows` renders
closed seed-selected values and exact UTF-16 spans/BIO tags. Arbitrary external
bindings, input text or parent-family substitutions are rejected. Fictional
names and common subjects are generated independently of personal records.

Boundaries get their own labels: calendar DELETE and UPDATE are distinct from
READ; emailing attendees is COMMUNICATION/SEND with missing context. Hard
negatives describe the decision boundary, not an instruction to mark every
nearby request OOD. No generated confirmation policy overrides Safety.

Every variant and boundary points to a canonical positive parent. Their
semantic-parent IDs stay equal even when boundary families differ, preventing
later splits from separating positive/negative siblings. Scenario and family
definitions are versioned and stored in the batch-specific group snapshot.
Generation records store seed, parent IDs, template/style, role, configuration
and dependency hashes, source identity and exact text. Reports include Python,
dependency versions, platform and Git revision; code hashes capture uncommitted
changes. Model/tokenizer are explicitly null because neither is used here.

## Context and compounds

"show that task" and "call him tomorrow" retain REFERENCE slots. "move it to
Friday" has an unresolved action hint but no guessed domain. "what about the
other one?" likewise stays UNKNOWN/NONE/SUPPORTED_NEEDS_CONTEXT.

The [002 extension](../schema_versions/nlu_v1_schema_002/README.md) adds that
context combination without changing any frozen 001 bytes or label catalog.
Generated managed rows explicitly use `nlu_v1_data_002` and model schema 002.
Old adapters and records keep 001; no silent migration or Android adoption occurs.

Compounds use UNKNOWN/NONE/SUPPORTED_NEEDS_CONTEXT and preserve both component
domain/action/slot lists in `composition.requests`. Their routing hint is
ORCHESTRATOR_DECOMPOSE_OR_CLARIFY. Components are annotation metadata, never
executable plans. Context and deterministic date code perform resolution later;
validators and Safety retain authority. Agent integration remains Plan 13.

## Rejection and quarantine

`validate_generated` rebuilds the declared trusted form from repository templates,
even if a caller passes the candidate as its expected row. It checks required
slots, text, spans/BIO, labels, parentage, components, source and configuration
bindings. Changed action/domain, missing slots, appended clauses or unregistered
paraphrases fail; unconstrained paraphrases are not accepted on heuristics.

Exact, normalized, source-ID and source-skeleton collisions reject implicated
attempts. Nothing is silently deleted: attempted structures and rejected rows
remain in the batch, with GENERATION_CHECK_REJECTED and dedupe reasons. Optional
programmatic isolation rows can only veto candidates for exact/skeleton/group
collisions; they are never paraphrase parents or text sources. Complete
cross-source, cross-partition isolation is still Plan 06.

All rows pass through Plan 04 dedupe/quarantine. Missing real embedding artifacts
keep the similarity audit incomplete and every row quarantined. Repeated seeds
with the same source skeleton are conservatively rejected, even if slot values
vary; generating more seeds is not evidence of independent language coverage.

The separate [synthetic source registry](../configs/synthetic_sources.yaml)
permits local internal candidates only. Rights, privacy and training permissions
are unresolved; rows use UNKNOWN_PRIVACY, PENDING then QUARANTINED, UNASSIGNED
partition and no fabricated intake/human approvals. Generated data cannot be
assigned to TRAIN or final independent evaluation. No Dataset v0/v1 was built.

[Dataset v1 mix policy](../configs/dataset_v1_mix.yaml) records disjoint ranges:
65–70% real/public human, 20–25% controlled synthetic, 10–15% hard boundaries.
An example is 68/22/10. Synthetic boundaries count once in the boundary category;
ambiguities count as controlled synthetic. These are final build gates, not
claims about this entirely synthetic candidate batch.

## Evidence

The first local batch has 41 attempts: 24 positive forms, 7 hard negatives,
4 compounds and 6 ambiguities. See the tracked aggregate
[Plan 05 report](../../../../docs/nlu_v1/PLAN_05_GENERATION_REPORT.json).
Payloads and code snapshots remain ignored under `datasets/`; aggregate evidence
is safe to inspect in Git. Every candidate is held pending rights/privacy review,
embedding similarity and later split validation. Generator tests do not prove
semantic-model accuracy, Android performance or completion of METIS NLU v1.
