# Datasets

No language or behavioral dataset has been collected or shipped in Phase 1. There are no real names, messages, audio recordings, notification contents, contact exports, or user timelines in test fixtures.

The [dataset contract](docs/contracts/DATASET.md) defines CSV fields, UTF-16 entity spans, family-based 70/15/15 splits, required negative/ambiguous/out-of-scope cases, provenance, privacy, and evaluation. The [intent catalog](docs/PRODUCT_CONTRACT.md) contains 32 planned intent labels; enabled support is incremental and requires evaluation, not merely a catalog entry.

Phase 7 establishes deterministic parsing and held-out evaluation fixtures. ML improvements are deferred to Phase 15 and must beat the same baseline on independent utterance families. Dataset contributions require explicit opt-in and redaction; no private content is collected automatically.

Every future dataset must include a version, generation/collection method, source/license records, annotation guidance, split seed and family IDs, deduplication results, evaluation scope, and known limitations. Dataset files and trained artifacts must not imply accuracy before evaluation. See [MODEL_CARD.md](MODEL_CARD.md) for current model status.
