"""Development-only read classifier. No Android adapter or executable action output."""

from __future__ import annotations

import re
import unicodedata

from scipy.special import softmax
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from dataset import READ_LABELS, SEED

# Conservative eligibility, independent of learned confidence. Reject rather than
# interpret complex syntax. This is a bounded development filter, not a general
# injection detector or an assertion of natural-language safety.
BLOCKED = re.compile(
    r"\b(?:not|never|no|don['’]t|do\s+not|cancel|stop|avoid|without|"
    r"if|suppose|imagine|hypothetical|pretend|example|quote|quoted|translate|"
    r"and|then|also|or|ignore|instead|system|developer|instructions|execute|"
    r"create|add|delete|complete|rename|postpone|send|call|timer|reminder|"
    r"notify|alert|book|buy|transfer|kripya|dikhao|kal|mere)\b", re.IGNORECASE)


def eligibility(text, source="TEXT"):
    if source not in {"TEXT", "VOICE"}:
        return "unsupported_source"
    if not text.strip() or len(text.encode("utf-16-le")) // 2 > 512:
        return "length"
    if any(unicodedata.category(char).startswith("C") for char in text):
        return "control_character"
    if any(char in text for char in ('"', "'", "“", "”", "‘", "’", ";", "`", "\\", "|", "&", ":", "=", "<", ">")):
        return "complex_syntax"
    if any(char.isalpha() and "LATIN" not in unicodedata.name(char, "") for char in text):
        return "unsupported_script"
    if BLOCKED.search(text):
        return "rejection_term"
    return None


def train(rows, regularization=1.0):
    if any(row["split"] != "train" for row in rows):
        raise ValueError("Only train rows may fit vocabulary, IDF, and weights")
    model = Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 2), lowercase=True, strip_accents="unicode", sublinear_tf=True)),
        ("logreg", LogisticRegression(C=regularization, class_weight="balanced", max_iter=2000, random_state=SEED)),
    ])
    model.fit([row["utterance"] for row in rows], [row["intent"] for row in rows])
    return model


def predict(model, rows, temperature, threshold):
    probabilities = softmax(model.decision_function([row["utterance"] for row in rows]) / temperature, axis=1)
    predictions, reasons = [], []
    for row, probability in zip(rows, probabilities):
        reason = eligibility(row["utterance"], row["input_source"])
        label = str(model.classes_[probability.argmax()])
        if reason or label not in READ_LABELS or threshold is None or probability.max() < threshold:
            predictions.append("UNKNOWN")
            reasons.append(reason or "unknown_or_low_confidence")
        else:
            predictions.append(label)
            reasons.append("accepted")
    return predictions, probabilities, reasons


def hybrid(rows, predictions, baseline):
    """Preserve the actual rule path; learned fallback is restricted to read labels."""
    return [baseline[row["id"]]["intent"] if baseline[row["id"]]["negated"] == "true" or
            baseline[row["id"]]["intent"] != "UNKNOWN" else label
            for row, label in zip(rows, predictions)]
