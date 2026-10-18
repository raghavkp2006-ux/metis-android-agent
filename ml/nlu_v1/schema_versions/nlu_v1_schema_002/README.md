# Context extension 002

Plan 05 explicitly requires context labels for unresolved utterances such as
"what about the other one?". Schema 001 cannot express those without guessing a
domain. This version adds UNKNOWN / NONE / SUPPORTED_NEEDS_CONTEXT. NONE always
requires UNKNOWN. Complete and confirmation dispositions still require a known
domain and action. The exact 001 label catalogs remain authoritative and unchanged.

Compound candidate rows use UNKNOWN / NONE / SUPPORTED_NEEDS_CONTEXT and preserve
their individual requests in managed-record composition metadata, with an
ORCHESTRATOR_DECOMPOSE_OR_CLARIFY hint. This means the single-request model cannot
complete this request; it does not mean the component actions are unsupported.
Component extraction is generation supervision, not an executable plan.

The immutable schema 001 still validates old rows. No automatic relabeling or
conversion occurs. Managed data 002 explicitly references model schema 002 and
the local synthetic source registry. Existing public-source adapters keep 001.
Use contract.validate with the explicit schema version; unknown versions fail.
Spans, BIO, OOD rules and safety authority are unchanged. The extension and this
document have their own freeze manifest. Training, runtime adoption and agent
integration remain later gates; no Android runtime changed in Plan 05.
