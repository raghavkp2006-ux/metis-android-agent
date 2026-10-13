# Calibration intake 006: partial collection, reviewed labels

Received 2026-10-13. The user explicitly confirmed calibration purpose and requested draft labels, then approved the complete annotations and proposed shared Phase-15-process family. Every request remains independent. Items 51–151 are preserved verbatim: 101 submissions, 99 normalized unique requests. Items 51/115 and 88/146 are exact duplicate pairs. There are no exact normalized overlaps with the 365 frozen development requests; that comparison does not prove semantic independence.

Reviewed unique category counts: 73 memory, 19 task, three promise and four other/unsupported. No request searches calendar events: notes about calendar support retain memory intent. Promise requests without named recipients clarify before access. Standalone instructions and pronouns retain missing references rather than using neighboring rows.

The reviewed calibration pool is incomplete against the previously frozen quotas. It needs at least 30 calendar-event, 12 promise and six other/unsupported additions. The user confirmed they will send these 48 additions. The exact 100-row calibration subset must be proposed and approved after additions are reviewed. Excess rows stay unused; they are not silently recategorized into development, selection or independent evaluation. Related scenarios remain within calibration and must not leak into selection.

`intake.json` and `review.md` preserve the assistant draft that the user approved. `review-approval.json` binds that draft; `reviewed.json` records the approved projection separately. Original numbering, source wording and constraints remain unchanged. The same user supplied and reviewed this batch; authorship is not independently verified and no independent-review claim is made.

No parser/classifier predictions, temperature fitting, cutoff selection, personal-record reads or phone tests were run for this data-only intake. No F1 or fresh evaluation is claimed. Production ML remains disabled. Historical candidates and frozen approvals/results are unchanged.

Validate with:

```powershell
.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/calibration_intake_006 -p test_intake.py -v
```

`draft.py` authored the original proposal and now refuses to overwrite approved artifacts. Future additions belong in a separate intake.
