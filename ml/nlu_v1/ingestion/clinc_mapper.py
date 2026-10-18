"""CLINC data_full.json split arrays; caller supplies the original row ordinal."""
from .common import load_mapping, make_row, MappingError, target


def map_record(raw, original_split, source_id):
    if not isinstance(raw, list) or len(raw) != 2 or not all(isinstance(v, str) for v in raw):
        raise MappingError("CLINC_TEXT_LABEL_PAIR_REQUIRED")
    text, intent = raw
    if original_split.startswith("oos_") != (intent == "oos"):
        raise MappingError("CLINC_OOS_SPLIT_LABEL_DISAGREEMENT")
    table = load_mapping("clinc_intent_mapping.yaml")
    entry = target(table, intent)
    return make_row("clinc150_full", source_id, text, original_split, intent, entry, [], table,
                    metadata={"slot_annotation_status": "NOT_PROVIDED_BY_SOURCE"})
