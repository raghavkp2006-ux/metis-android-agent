# METIS NLU v1 privacy governance policy 002

Version: `nlu_v1_privacy_policy_002`. This prospective policy implements the project
owner's explicit request for source-level assessment. Previous Plan04/Plan07
rules, source observations, row histories, signed semantic decisions and local
privacy review proposals remain unchanged. No individual review is implied.

The policy applies only to the exact MASSIVE 1.1 en-US and CLINC150 full artifacts,
revisions and training splits pinned in
`ml/nlu_v1/configs/privacy_policy_002.yaml`. Synthetic sources, future sources,
protected benchmark/test partitions and independent-final data are excluded.

All fourteen required facts must be supported: exact identity; version/revision;
training split; artifact digest; provenance; license evidence; benchmark/research
identity; no METIS account-data collection; no connector/private-device content;
no protected inputs; source collection/privacy documentation establishing that
the scoped source is not confidential/private; exact source/version/hash/split
binding; separate publication authority; and separate training authority.

A public license alone cannot prove privacy suitability. A README hash without
its document bytes cannot establish the source's collection practices. Missing
or ambiguous evidence produces `INSUFFICIENT_EVIDENCE`, with affected rows
excluded from the current v0 selection and preserved in quarantine/backlog.
Verified source restrictions produce `SOURCE_LEVEL_RESTRICTED` when the other
required facts are established. No consent or collection facts are invented.

`SOURCE_LEVEL_PUBLIC` requires all fourteen checks plus a genuine operator-bound
or existing-trust-store-signed source approval for subsequent application. Such
an approval must bind the policy/config digest, exact source/version/revision,
archive/member/acquisition/license digests, exact splits, member row IDs/content
digests, real operator identity, actual UTC authorization time and reason. It
must record source-level authority rather than fictitious per-row human review.
Publication remains false; training, intake, canonical release and final
evaluation retain their independent gates. Changed facts invalidate old review
bindings; originals must be retained and signatures must not be transferred.

The new assessor is:

```powershell
python -m ml.nlu_v1.dataset_v0.source_privacy
```

It writes a new, append-only, local assessment under the gitignored quarantine
area. It does not issue source approvals or mutate privacy states. The present
implementation stops at assessment because neither source has sufficient local
collection/privacy evidence. A future application adapter must still use the
existing Plan04 evidence-bound classification/history contract and native intake
verifier; this policy does not itself set `privacy_reviewed=true`.

The full pool can be filtered without requiring every candidate to qualify.
All 425 synthetic records are excluded under the owner's current decision.
Unresolved families/scenarios and ambiguous duplicate/similarity cases are
excluded from v0 selection; their original rows and findings are retained.
Existing dedupe policy quarantines all cosine matches and never deletes them;
selection exclusion does not change that policy or claim duplicate equivalence.

The actual freeze implementation still requires its existing provenance,
coverage, rights, scientific partition and independent-final gates. This new
privacy policy neither fabricates those passes nor changes their validators.
No training or Plan08 work is performed by this policy.
