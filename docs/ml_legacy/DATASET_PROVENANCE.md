# Dataset provenance

48 of 50 earlier V12 development queries later appeared in V16 TRAIN (documented
historical exposure). V12 development is not fresh evaluation. V16 development,
calibration, selection and synthetic challenge material is exposed. Historical
independent/human first-evaluation sets retain protected status after scoring.

`datasets/legacy/catalog.json` records artifact-level privacy and exposure separately.
All rows in each artifact inherit the artifact's conservative restriction. Unreviewed
content, vocabulary and raw predictions remain PRIVATE_DO_NOT_PUBLISH; none is
automatically approved as TRAINABLE_LEGACY. This is a quarantine decision, not a
claim that every row contains personal data. Duplicate copies retain the same policy.

Fresh NLU data must be isolated by text, family and scenario, and by author/reviewer
where practical. Keep TRAIN, DEVELOPMENT, CALIBRATION, SELECTION and FINAL
INDEPENDENT TEST separate. Never treat exposed legacy material as independent.

Retirement audit confirmed the overlap by NFKC/case-folded whitespace-normalized utterance matching against candidate_v15/review-projection.json, train partition, excluding duplicate_of rows: 48/50 matches; 254 unique training rows. No model prediction was run.
