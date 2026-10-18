# Local privacy terminal reviewer

Run from the repository root:

```powershell
python -m ml.nlu_v1.dataset_v0.privacy_review review
```

The same command resumes the saved cursor, source filter, PENDING filter and undo
history. To include only unresolved rows:

```powershell
python -m ml.nlu_v1.dataset_v0.privacy_review review --pending-only
```

Use `--source massive` or `--source clinc` for a source filter, `--source all` to
clear it, and `--all-targets` to include previously decided rows again.

| Key | Action |
|---|---|
| 1 / 2 / 3 / 4 | Record PUBLIC / PRIVATE / RESTRICTED / KEEP_UNKNOWN |
| N / P | Next / previous without changing a choice |
| U | Undo the last recorded choice, including across restarts |
| S / Q | Save / save and quit |
| F | Choose all, massive or clinc |
| G | Go to a global row number or exact target ID within the active filters |
| R / H | Remaining PENDING count / help |

Keys are immediate in an interactive Windows terminal. F/G prompt for a value
followed by Enter. A key on a decided target explicitly replaces that proposed
choice; navigation never changes it. Decisions advance to the next visible row.
Completing a PENDING-only filter leaves no target selected; Q exits safely.

Only proposed-classification cells in the existing gitignored
`ml/nlu_v1/datasets/quarantine/plan07-privacy-review-001/PRIVACY_DECISIONS.md`
are changed. `review_session.json` in that same local folder stores cursor,
filters, undo metadata and recoverable write-ahead state, without utterances.
Each interaction saves state atomically. Each decision uses a write-ahead save,
atomic Markdown replacement and completion save; a restart recovers interrupted
writes or refuses conflicting external edits. An OS lock prevents simultaneous
terminal reviewers and synchronization. Edit Markdown externally only after
quitting the reviewer; conflicting edits are rejected rather than overwritten.

The original preparation engine is preserved as local
`prepared_privacy_review.py`, verified against the existing immutable packet's
original engine hash. The packet, member hashes and non-engine evidence remain
unchanged; the frontend does not rewrite them or permit other stale inputs.

The tool has no networking, model inference, recommendations, bulk approval,
telemetry, publication, intake, training or classification-application operation.
Raw text appears only in the local terminal and existing gitignored review area.
Avoid terminal recording or sharing the local review files publicly. Source text
control characters are escaped so they cannot execute terminal commands.

After human review, the existing draft synchronization command remains:

```powershell
python -m ml.nlu_v1.dataset_v0.privacy_review sync
```

Synchronization records unsigned drafts. It does not apply privacy classifications,
sign review evidence, resolve intake or publication, or release rows. The 60
existing PUBLIC rows and every synthetic row are outside this packet.
