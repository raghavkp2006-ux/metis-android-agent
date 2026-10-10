"""Read-only development language pipeline: learned intents, bounded resolution."""

import re
import unicodedata

import numpy as np
from scipy.special import softmax
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline

from v4_data import READ_LABELS, SEED

CONTRACTIONS = {"that's": "that is", "what's": "what is", "i've": "i have", "i'm": "i am", "i'd": "i would",
                "don't": "do not", "doesn't": "does not", "haven't": "have not", "hasn't": "has not",
                "isn't": "is not", "aren't": "are not", "can't": "cannot", "won't": "will not"}
STATE = re.compile(r"\bnot\s+(?:yet\s+)?(?:been\s+)?(?:completed|finished|done)\b", re.I)
RANGE = re.compile(r"\bbetween\s+(?:\d{1,2}(?::\d{2})?\s*(?:am|pm)?|noon)\s+and\s+"
                   r"(?:\d{1,2}(?::\d{2})?\s*(?:am|pm)?|noon)\b", re.I)
BLOCKED = re.compile(r"\b(?:suppose|imagine|hypothetical|pretend|example|quoted|quote|translate|ignore|"
                     r"system|developer|instructions|override|create|delete|rename|postpone|send|message|call|"
                     r"order|buy|transfer|notify|alert|execute|kripya|dikhao|kal|mere)\b", re.I)
NEGATION = re.compile(r"\b(?:do not|does not|never|no|stop|cancel|avoid|without|cannot|will not)\b", re.I)
REQUEST = re.compile(r"^(?:(?:please|bro|hey|okay)\s*[,!]?\s*)?(?:what|which|when|where|how|show|list|"
                     r"find|search|look|check|pull|bring|retrieve|recall|tell|give|display|open|read|"
                     r"remind me (?:what|of)|let me|can|could|do i|did i|have i|is |am i|will i|"
                     r"anything|i (?:want|would like))", re.I)


def normalize(text):
    text = unicodedata.normalize("NFKC", text).replace("’", "'").lower()
    for contraction, expanded in CONTRACTIONS.items():
        text = re.sub(r"(?<!\w)"+re.escape(contraction)+r"(?!\w)", expanded, text)
    return re.sub(r"\s+", " ", text).strip()


def feature_text(text):
    """Classify request wording rather than memorizing fictional topic/person slots.

    Eligibility always examines unmasked text. Masking never grants safety or
    supplies resolved entities, and original offsets remain separate.
    """
    if re.search(r"\b(?:saved|stored|memory|notes?|recorded|wrote|writing|facts|records|information|details|entry|know)\b", text, re.I):
        spans = extract(text, "CHECK_MEMORY")
        if spans:
            span = spans[0]
            encoded = text.encode("utf-16-le")
            before = encoded[:span["startOffset"]*2].decode("utf-16-le")
            after = encoded[span["endOffset"]*2:].decode("utf-16-le")
            return normalize(before+"topicvalue"+after)
    if re.search(r"\b(?:i|my)\b", text, re.I) and (re.search(r"\b(?:promis\w*|commit\w*|agreed?|pledged?|owe|undertak\w*|obligation)\b", text, re.I) or
            re.search(r"\b(?:say|said|tell|told)\b.+\b(?:would|i['’]d)\b", text, re.I)):
        spans = extract(text, "WHAT_DID_I_PROMISE")
        if spans:
            span = spans[0]
            encoded = text.encode("utf-16-le")
            return normalize(encoded[:span["startOffset"]*2].decode("utf-16-le")+"personvalue"+
                             encoded[span["endOffset"]*2:].decode("utf-16-le"))
    return normalize(text)


def eligibility(text, source="TEXT"):
    if source not in {"TEXT", "VOICE"}:
        return "unsupported_source"
    if not text.strip() or len(text.encode("utf-16-le"))//2 > 512:
        return "length"
    if any(unicodedata.category(char).startswith("C") for char in text):
        return "control_character"
    if any(char.isalpha() and "LATIN" not in unicodedata.name(char, "") for char in text):
        return "unsupported_script"
    # Intraword apostrophes are contractions/possessives, not quote delimiters.
    syntax = re.sub(r"(?<=[^\W\d_])['’](?=[^\W\d_])", "", text)
    if any(char in syntax for char in ('"', "'", "’", "‘", "“", "”", ";", "`", "\\", "|", "&", "=", "<", ">")):
        return "quoted_or_complex"
    value = normalize(text)
    if BLOCKED.search(value) or ":" in RANGE.sub("time range", value):
        return "unsafe_or_unsupported_request"
    if NEGATION.search(value):
        return "command_negation"
    # A task-state descriptor can contain not without negating the command.
    remainder = STATE.sub("unfinished", value) if re.search(r"\b(?:tasks?|work|jobs?|items?|to-dos?)\b", value) else value
    remainder = RANGE.sub("time range", remainder)
    remainder = re.sub(r"\s+if possible[?!.]*$", "", remainder)
    remainder = re.sub(r"^check if\b", "check whether", remainder)
    if re.search(r"\b(?:not|and|then|also|or|if)\b", remainder):
        return "negated_hypothetical_or_compound"
    if not REQUEST.search(value):
        return "no_direct_read_request"
    return None


def train(rows, regularization=1., characters=True):
    if not rows or any(row["split"] != "train" for row in rows):
        raise ValueError("Only nonempty training rows may fit the candidate")
    # Quotes/negations can have identical bag-of-words to a positive command.
    # They are rejected by the upstream guard and must not give contradictory
    # training labels to the downstream classifier's eligible-input domain.
    rows = [row for row in rows if eligibility(row["utterance"], row["input_source"]) is None]
    if {row["intent"] for row in rows} != {*READ_LABELS, "UNKNOWN"}:
        raise ValueError("Eligible training rows must cover every classifier label")
    features = [("word", TfidfVectorizer(preprocessor=feature_text, ngram_range=(1,2), sublinear_tf=True,
                                         strip_accents=None, max_features=12000))]
    if characters:
        features.append(("char", TfidfVectorizer(preprocessor=feature_text, analyzer="char_wb", ngram_range=(3,5),
                                                sublinear_tf=True, max_features=16000)))
    model = Pipeline([("features", FeatureUnion(features, transformer_weights={"word": 1., "char": .5} if characters else {"word": 1.})),
                      ("classifier", LogisticRegression(C=regularization, class_weight="balanced", max_iter=2000, random_state=SEED))])
    model.fit([row["utterance"] for row in rows], [row["intent"] for row in rows])
    return model


def predict(model, rows, temperature, threshold):
    probabilities = softmax(model.decision_function([row["utterance"] for row in rows])/temperature, axis=1)
    labels, reasons = [], []
    for row, probability in zip(rows, probabilities):
        reason = eligibility(row["utterance"], row["input_source"])
        label = str(model.classes_[probability.argmax()])
        if reason or label not in READ_LABELS or threshold is None or not np.isfinite(probability).all() or probability.max() < threshold:
            labels.append("UNKNOWN")
            reasons.append(reason or "unknown_or_low_confidence")
        else:
            labels.append(label)
            reasons.append("accepted")
    return labels, probabilities, reasons


def hybrid(rows, labels, baseline):
    result = []
    for row, label in zip(rows, labels):
        rule = baseline[row["id"]]
        reason = eligibility(row["utterance"], row["input_source"])
        if rule["intent"] not in {*READ_LABELS, "UNKNOWN"}:
            result.append(rule["intent"])  # Existing mutations never gain ML authority.
        elif reason:
            result.append("UNKNOWN")  # Guard unsafe read matches as well as ML fallback.
        elif rule["intent"] in READ_LABELS:
            result.append(rule["intent"])
        else:
            # Eligible state-negated queries can fall back to ML; actual command
            # negation was rejected above. No production EnglishRules is changed.
            result.append(label)
    return result


def entity(text, kind, start, end):
    return dict(type=kind, rawValue=text[start:end], startOffset=len(text[:start].encode("utf-16-le"))//2,
                endOffset=len(text[:end].encode("utf-16-le"))//2)


def extract(text, intent):
    """Original-string spans only. Missing/ambiguous entities never reach a read."""
    cleaned_end = re.search(r"(?:\s+(?:please|for me|right now|thanks|if possible))?[?!.]*$", text, re.I).start()
    core = text[:cleaned_end]
    if intent == "CHECK_MEMORY":
        pattern = r"\b(?:about|concerning|regarding|mentioning|mention|under|on|for)\s+(?:the\s+)?(.+)$"
        # Prefer a topic delimiter over the retrieval phrase 'look for'.
        topic_core = re.sub(r"^(look\s+)for(?=\s+my\s+earlier\s+writing\s+on\b)", r"\1   ", core, flags=re.I)
        matches = list(re.finditer(pattern, topic_core, re.I))
        if not matches:
            return []
        match = matches[-1]
        start, end = match.span(1)
        # Annotation topics may include a leading article in generated slots.
        if core[max(0,start-4):start].lower() == "the ":
            start -= 4
        raw = text[start:end].strip()
        if raw.lower() in {"it", "this", "that", "them", "something", "anything", "the thing"}:
            return []
        return [entity(text, "MEMORY", start, end)] if raw and end-start <= 200 else []
    if intent == "WHAT_DID_I_PROMISE":
        candidates = list(re.finditer(r"(?=\b(?:for|to|with|from|involving|towards|concerns|giving)\s+"
                                     r"(.+?)(?:\s+anything|\s+something|\s+recently)?$)", core, re.I))
        for match in reversed(candidates):
            raw = match.group(1)
            if raw.lower() not in {"her", "him", "them", "someone", "it", "anything", "something"} and \
                    len(raw.split()) <= 3 and len(raw) <= 100:
                return [entity(text, "PERSON", *match.span(1))]
        patterns = [
            r"\b(?:tell|told|assure)\s+(.+?)\s+i would\s+.+$",
            r"\b(?:promise|promised|owe|giving)\s+(.+?)(?:\s+anything|\s+something|\s+recently)?$",
            r"\b(?:help|helping)\s+(.+?)\s+with$",
        ]
        for pattern in patterns:
            match = re.search(pattern, core, re.I)
            if match:
                raw = match.group(1)
                if raw.lower() in {"her", "him", "them", "someone", "it", "anything", "something"} or \
                        len(raw.split()) > 3 or len(raw) > 100:
                    continue
                return [entity(text, "PERSON", *match.span(1))]
    return []


def resolve(text, label, source="TEXT"):
    if label not in READ_LABELS:
        return {"status": "FOLLOW_UP", "entities": [], "reads": 0, "proposals": 0, "completed": 0}
    if eligibility(text, source):
        return {"status": "FOLLOW_UP", "entities": [], "reads": 0, "proposals": 0, "completed": 0}
    entities = extract(text, label)
    if label == "CHECK_CALENDAR":
        status = "UNSUPPORTED"  # Never infer free time from unavailable access.
    elif label in {"CHECK_MEMORY", "WHAT_DID_I_PROMISE"} and not entities:
        status = "FOLLOW_UP"
    elif label == "CHECK_TASKS" and re.search(r"\b(?:today|tomorrow|yesterday|week)\b", text, re.I):
        status = "FOLLOW_UP"  # Existing unfiltered task reader cannot satisfy date filters.
    elif label == "WHAT_DID_I_PROMISE" and re.search(r"\b(?:recently|supposed)\b", text, re.I):
        status = "FOLLOW_UP"
    else:
        status = "ANSWER"
    return {"status": status, "entities": entities, "reads": int(status == "ANSWER"), "proposals": 0, "completed": 0,
            "scope": "Simulated read-only development pipeline, no actual repository or executor"}
