# Source-level restricted-training privacy policy 003

`nlu_v1_privacy_policy_003` is a prospective owner-authorized policy. Policies002
and all earlier rules/evidence remain unchanged. It applies only to the exact
MASSIVE1.1 en-US and CLINC150 full artifacts and eligible train/oos_train splits
pinned in `ml/nlu_v1/configs/privacy_policy_003.yaml`. It excludes synthetic data,
future sources, public benchmark test/validation inputs and protected finals.

The source-level finding is `SOURCE_LEVEL_RESTRICTED_TRAINING`, represented by the
existing canonical row privacy enum `RESTRICTED`. It is not PUBLIC clearance,
individual consent, individual row privacy review, or a claim that no real-world
name/identifier can occur. Publication and raw-row redistribution remain false.

## Authoritative evidence

The official papers are saved as exact PDF/HTML bytes with title, full authors,
publication, canonical/resolved URLs, actual UTC retrieval time, SHA-256 and source
relationship in the append-only local evidence manifest:

`ml/nlu_v1/datasets/raw/plan07-collection-evidence-003/manifest.json`.

- [Larson et al., EMNLP-IJCNLP2019](https://aclanthology.org/D19-1131/), sections2.1–2.3: CLINC queries were elicited by crowdsourcing, including scoping/rephrase/scenario tasks and OOS collection. In-scope worker grouping and OOS prompt grouping are distinct.
- [FitzGerald et al., ACL2023](https://aclanthology.org/2023.acl-long.235/), abstract/data creation: MASSIVE localizes the English SLURP source using professional translators.
- [Bastianelli et al., EMNLP2020](https://aclanthology.org/2020.emnlp-main.588/), section3.1: SLURP textual commands were elicited from AMT workers using predefined assistant/robot prompts. Separate audio/demographic collection is outside this text-only authorization.

Exact short evidence spans, PDF page numbers, normalized-page hashes and offsets
are bound in the restricted run's `collection_findings.json`. Complete local paper
text is retained for offline inspection. Verification never substitutes a live
URL for the pinned bytes. These papers establish deliberate benchmark elicitation;
they do not establish individual consent or blanket privacy suitability.

## Scope and gates

All ten checks are required: an externally published benchmark; exact source,
version and artifact pin; an existing verified license allowing the scoped
development use; authoritative collection methodology; benchmark elicitation
rather than METIS private account/connector/device content; no protected inputs;
no raw republication; publication false; recorded source-level authority; and
separate explicit operator training authority.

Existing native source verification checks archive/member/license/acquisition
hashes and exact mapped/native row identity. The original registry's CC-BY4.0
MASSIVE and CC-BY3.0 CLINC license evidence remains the license basis; no broader
license interpretation or new public redistribution permission is introduced.

## Operator-bound records and native integration

The current attached project-owner request is copied and SHA-bound in the ignored
run directory. Its asserted PROJECT_OWNER role is recorded as
`project-owner-current-request`, with the request hash as identity provenance.
This is not an invented enrolled signer, a cryptographic signature, or an assumed
legal identity. The timestamp is the actual UTC recording time, not a fabricated
historical review time. The existing intake boundary trusts operator JSON
configuration and does not require Ed25519 signing; no keys/signatures are created.

Separate source privacy, native intake and training authorization records bind
source/version/revision, archive/member/acquisition/license hashes, policy/config
hashes, exact eligible splits and original row membership/content hashes. The
privacy finding enables the genuine native `privacy_reviewed` prerequisite under
this new restricted policy; it does not imply PUBLIC status or publication.

The native `ingestion.ingest.approve_intake` validates the scoped intake record.
The policy003 wrapper limits application to its exact original candidate members
and training splits. Do not use the source record to ingest every split from a
mixed upstream artifact. Authorizing an artifact does not authorize its protected
test/validation rows.

Training records cover the owner's stated Dataset-v0/development purposes only,
conditional on all remaining eligibility gates. They are not model execution,
canonical approval, source-wide future-row approval, production collection,
publication or protected-final authority.

`records.classify_privacy` applies RESTRICTED only to a new local derivative
snapshot, with fact-bound evidence and source-level authority metadata. Original
rows, previous PUBLIC evidence, histories and semantic signatures are preserved.
The derivative retains the original history prefix and quarantine reasons and
gets new source-authority/privacy events. It creates no HUMAN_REVIEWED/APPROVED
events and does not replay old signatures onto changed facts.

## Scientific selection and completion

The real embedding receipt/checkpoint and original full-corpus text binding are
verified. Exact unchanged vectors are subset-derived for the public pool, with a
parent-artifact/receipt derivation record; the existing Plan04 cosine audit then
runs again over that complete public pool. All implicated rows, unresolved groups
and synthetic backlog are excluded from v0 selection and preserved.

Existing grouped planning produces a readiness preview only. A successful preview
is not a frozen dataset or canonical approval. Capability coverage must pass;
source mappings are inspected across the verified source inventory before any
coverage addition. No intent is force-mapped into missing MEMORY/PROMISE domains.

The legacy public-output builder remains unchanged: it requires PUBLIC/privacy
publication permission and its final-independent gates. It cannot be used for a
RESTRICTED, publication-disabled dataset. If source coverage becomes sufficient,
a separately validated private build/release path must satisfy the new scoped
authority and canonical requirements before a private dataset freeze can occur.
No current policy003 report claims that the legacy public builder passed.

Run the evidence acquisition explicitly once; subsequent evidence checks are
offline:

```powershell
python -m ml.nlu_v1.dataset_v0.collection_evidence
python -m ml.nlu_v1.dataset_v0.restricted_training
```

The restricted run is append-only and refuses an existing destination. It never
trains a model, starts Plan08, publishes source utterances, or freezes a dataset
when coverage/approval/build gates remain unresolved.
