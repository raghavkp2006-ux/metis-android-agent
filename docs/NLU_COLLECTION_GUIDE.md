# NLU Collection Guide

> **Status**: Active — collection sprint in progress.
> Branch: `feat/nlu-collection-kit` | Target: ≥ 3,000 rows, ≥ 8 authors

---

## Part 1 — Contributor Guide (teammates)

### What this is

METIS needs real human phrases to learn from. You are helping it understand the
many ways people naturally ask for the same thing — formal, casual, Hinglish,
with typos, by voice, and everything in between.

**Time needed**: 45 – 90 minutes for a full set (8 – 10 utterances × 25 intents ≈ 200 – 250 rows).
You can do it in multiple sessions.

---

### Step-by-step

1. **Get the scenario cards.** Open `ml/nlu_v1/collection/prompts.md`. Each card
   describes a real situation in plain words. Read the scenario — do **not** copy
   the phrasing from the card.

2. **Open the CSV template.** Duplicate `ml/nlu_v1/collection/collection_template.csv`
   and rename it `collection_<your_author_id>.csv` (e.g. `collection_AK.csv`).

3. **Fill one row per utterance.** The columns are:

   | Column | What to write |
   |---|---|
   | `author_id` | Your initials or agreed ID (e.g. `AK`, `PR`). Keep it consistent. |
   | `utterance` | The phrase you would actually type or say to METIS in this situation. |
   | `intended_intent` | The intent label from the list below. Copy it exactly. |
   | `style_tags` | A pipe-separated list from: `plain`, `hinglish`, `typo`, `voice`, `multi_intent`. A row can have multiple tags, e.g. `hinglish\|typo`. |
   | `contains_real_pii` | `no` (almost always). Write `yes` only if you accidentally used a real contact or number — but please avoid it entirely. |
   | `notes` | Optional. Explain anything unusual about the utterance. |

4. **Quotas to hit per intent (across all your rows for that intent):**

   - 8 – 10 utterances per intent you cover
   - ≥ 25 % tagged `hinglish` (Hinglish / code-mixed, Roman script only)
   - ≥ 10 % tagged `typo` (realistic misspellings — not random garbling)
   - ≥ 10 % tagged `voice` (no punctuation, filler words like "um" or "so" are fine)
   - ≥ 5 % tagged `multi_intent` (one message that covers two intents at once)

5. **Valid intent labels** (copy these exactly into `intended_intent`):

   ```
   CHECK_CALENDAR       CHECK_TASKS          COMPLETE_TASK
   CREATE_PROMISE       CREATE_REMINDER      CREATE_TASK
   DELETE_TASK          PERSON_COMMITMENTS   PLAN_DAY
   POSTPONE_TASK        PRIORITIZE_TASK      PROJECT_STATUS
   REFLECTION           RENAME_TASK          UNKNOWN
   WHAT_SHOULD_I_DO     CHECK_MEMORY         UPDATE_TASK
   ```

   Also supported (external actions routed by the app):
   ```
   CREATE_TIMER
   ```
   For dial / call and message / SMS utterances, use `UNKNOWN` with a note
   explaining they are `CREATE_CALL` or `COMPOSE_MESSAGE` — these will be
   reclassified once those labels are frozen.

6. **Run the validator** before submitting:

   ```bash
   python ml/nlu_v1/collection/validate_collection.py collection_AK.csv \
       --intents-md ml/nlu_v1/intents.md
   ```

   Fix any ✗ errors; review ⚠ warnings. Zero errors = ready to merge.

---

### Privacy rules (read carefully)

> [!CAUTION]
> **Never enter real personal information.** The CSV will be stored in the
> repository and visible to the whole team.

- Use only **fake names**: Aarav, Priya, Dev, Riya, Sam, Arjun, Sneha, Vikram, Ananya, Rohan, Iyer.
- Use only **fake phone numbers**: `9876500000`, `9123456789`, or any obviously fake 10-digit number.
- Use only **fake email addresses**: `test@example.com` or `fake@nowhere.test`.
- **Do not** enter your real contacts, your phone number, your email, or anyone else's.
- If you discover you entered real PII, set `contains_real_pii` to `yes` in that
  row and flag it immediately so it can be removed before the file is committed.

---

### Style tips

**Plain**
> "Add a task to submit the VLSI assignment by Thursday evening."

**Hinglish** (mix naturally; Roman script only, no Devanagari)
> "Aaj shaam ko vlsi assignment submit karne ka task add kar do."

**Typo** (realistic phone-keyboard errors — wrong adjacent keys, missing letters)
> "ad taask to submti vlsi assinment by thurdsay evning"

**Voice** (no punctuation, filler words allowed)
> "um yeah so add a task to uh finish the vlsi thing by thursday"

**Multi-intent** (two goals in one message — label the *primary* intent,
tag as `multi_intent`, and add a note about the secondary intent)
> "remind me at 6 pm to pick up laundry and also add it as a task"

---

## Part 2 — Maintainer / ML Guide

### Target dataset size

| Metric | Target |
|---|---|
| Total rows (all intents) | ≥ 3,000 |
| Distinct authors | ≥ 8 |
| Rows per intent (non-UNKNOWN) | ≥ 120 (≈ 15 per author × 8 authors) |
| UNKNOWN / reject-class rows | ≥ 10 % of total |
| Hinglish rows | ≥ 25 % of non-UNKNOWN total |
| Typo rows | ≥ 10 % |
| Voice rows | ≥ 10 % |
| Multi-intent rows | ≥ 5 % |

### Author assignment policy

Authors must be assigned to **exactly one** split (train, dev, or test). Test
authors are frozen at first split time and never appear in training data.

Minimum split composition:

| Split | Authors | Purpose |
|---|---|---|
| Train | ≥ 5 distinct | Model fitting |
| Dev | ≥ 2 distinct | Threshold tuning, early stopping |
| Test | ≥ 2 distinct | **Held out — never tuned against** |

Use `split_by_author.py` to generate splits. The frozen test manifest
(`test_manifest.json`) prevents accidental overwrite.

### How to merge contributor sheets

1. Collect `collection_<author_id>.csv` files from each contributor.
2. Validate each file individually:
   ```bash
   for f in collection_*.csv; do
       python ml/nlu_v1/collection/validate_collection.py "$f" \
           --intents-md ml/nlu_v1/intents.md
   done
   ```
3. Concatenate (preserving one header):
   ```bash
   python - <<'EOF'
   import csv, glob, pathlib
   files = sorted(glob.glob("collection_*.csv"))
   rows = []
   header = None
   for f in files:
       with open(f, newline="", encoding="utf-8") as fh:
           reader = csv.DictReader(fh)
           if header is None:
               header = reader.fieldnames
           rows.extend(reader)
   with open("merged.csv", "w", newline="", encoding="utf-8") as fh:
       writer = csv.DictWriter(fh, fieldnames=header)
       writer.writeheader()
       writer.writerows(rows)
   print(f"Merged {len(rows)} rows from {len(files)} files.")
   EOF
   ```
4. Validate the merged file (catches cross-author duplicates):
   ```bash
   python ml/nlu_v1/collection/validate_collection.py merged.csv \
       --intents-md ml/nlu_v1/intents.md
   ```
5. Run the split:
   ```bash
   python ml/nlu_v1/collection/split_by_author.py merged.csv \
       --out-dir ml/nlu_v1/collection/splits \
       --test-authors SM,VN \
       --seed 42
   ```
6. Commit `splits/train.csv`, `splits/dev.csv`, `splits/test.csv`, and
   `splits/test_manifest.json` — **do not** commit raw `collection_*.csv` files
   if they contain any PII.

### Re-splitting after adding more authors

```bash
python ml/nlu_v1/collection/split_by_author.py merged_v2.csv \
    --out-dir ml/nlu_v1/collection/splits \
    --force-retrain \
    --seed 42
```

`--force-retrain` preserves the frozen test authors from the manifest and only
regenerates train/dev. To change test authors, also pass `--unlock-test-authors`
(requires interactive confirmation and invalidates prior evaluation results).

### Label schema reference

Labels follow a `VERB_NOUN` convention.  The access tier is embedded in `intents.md`:

| Tier | Labels | Risk level |
|---|---|---|
| READ | CHECK_CALENDAR, CHECK_TASKS, PERSON_COMMITMENTS, PLAN_DAY, PROJECT_STATUS, WHAT_SHOULD_I_DO, CHECK_MEMORY | Low — no state change |
| WRITE safe | CREATE_TASK, CREATE_REMINDER, CREATE_PROMISE, REFLECTION | Medium |
| WRITE forbidden (require extra confirmation gate) | COMPLETE_TASK, DELETE_TASK, POSTPONE_TASK, PRIORITIZE_TASK, RENAME_TASK, UPDATE_TASK | High — irreversible or destructive |
| External actions | CREATE_TIMER, CREATE_CALL\*, COMPOSE_MESSAGE\* | Handled by platform rules, not ML |
| Reject class | UNKNOWN | OOD / unsupported |

\* Not yet frozen NLU labels; use UNKNOWN + note until labels are approved.

---

*Last updated: 2026-10-29 | Branch: feat/nlu-collection-kit*

