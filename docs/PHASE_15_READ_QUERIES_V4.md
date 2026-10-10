# Phase 15: read-query ML improvements v4

Recorded 2026-10-10. **Decision: NO_GO for deployment; development improvements completed.** The app still uses deterministic rules. No model/runtime or changed command policy is bundled.

## Changes

- Expanded generated development data to 11,168 rows with family-separated partitions and nine negative wrappers.
- Added word/character TF-IDF model selection, original-text eligibility filtering, classifier-only topic/person masking, and calibrated abstention.
- Distinguished unfinished-task wording, contractions and time ranges from some negated, quoted, hypothetical and compound requests.
- Added original-string UTF-16 entity spans and conservative simulated read outcomes. The development wrapper blocks unsafe broad memory-rule matches.
- Exported complete disabled model parameters and compared serialized feature/weight math with sklearn. Source/data/native snapshot hashes preserve reproducibility.

## Recorded evaluation

**Later standalone benchmark:** unchanged v4 scores 0.6116 macro F1 and 56/100 positive intents on the user's fresh 125 queries. Three reject/clarify cases incorrectly reach simulated reads. See [the frozen first result](evaluation/phase15/independent_125/README.md). These failures limit the generated-data numbers below; no tuning was performed before scoring.

| Measurement | Result |
| --- | --- |
| Generated evaluation macro F1, hybrid | 0.9911 |
| Positive read-intent coverage | 164/168 (97.62%) |
| Accepted intent precision | 100% in this sample |
| Negative false accepts | 0/1,602 |
| Exact annotated entity spans | 78/78 generated rows |
| Negative simulated reads | 0 |
| User positive intent regressions | 20/20; v3 was 8/20 |
| User negative intent regressions | 5/5 declined |
| Historical strict rejection probes | 0/24 false accepts |
| Existing native historical regressions | 50/50 intent, entity and harness outcome matches |
| Serialized model feature/weight parity | 1,819 rows; zero argmax differences |

The selected model uses word 1–2 grams plus character 3–5 grams, C=16, temperature approximately 0.3251 and cutoff 0.90. Model/cutoff selection uses threshold families only. Four task examples remain abstentions. Comparisons with v3 use different generated corpora and do not establish an independent accuracy gain.

All 25 user prompts are exposed regressions. Their exact text is excluded from training and selection data, but development was informed by them. Generated examples reuse template patterns and slot values. Zero observed false accepts is not a population error guarantee. Entity and outcome annotations have not received human approval.

Intent recognition does not imply the request can execute. For example, “finish today” receives follow-up because the current reader cannot filter tasks by date; ambiguous promises receive follow-up; calendar queries remain unsupported. Simulated statuses are not actual repository or executor outcomes. The user’s incomplete reminder remains outside this read-only candidate’s scope.

## Remaining work

1. Freeze and score a fresh human-reviewed benchmark, including varied English, unseen entities, ambiguity, negation and general-information distractors. The previous review package is still unapproved.
2. Measure calibration on representative independent data and improve remaining abstentions without weakening rejection.
3. After the accuracy/rejection/review gates pass, integrate a read-only Android adapter with consistent intent/entities, real policy/outcome validation and fallback.
4. Measure Android numerical parity, latency (<100 ms target), artifact size, RAM and battery on representative devices before enabling.

The [reproduction guide](evaluation/phase15/read_queries_v4/README.md) and [recorded results](evaluation/phase15/read_queries_v4/results-v4.json) contain methodology, provenance, metrics and gates. V2/v3 records remain unchanged. No full independent benchmark or Phase 15 adoption gate is claimed complete.
