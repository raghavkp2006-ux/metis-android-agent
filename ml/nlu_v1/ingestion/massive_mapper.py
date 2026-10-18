"""MASSIVE 1.1 raw en-US JSONL rows; pure conversion, pending review."""
from .common import annotated_slots, load_mapping, make_row, MappingError, source_split, target


def map_record(raw, original_split=None):
    if raw.get("locale") != "en-US":
        raise MappingError("LOCALE_NOT_EN_US")
    table = load_mapping("massive_intent_mapping.yaml")
    intent = raw["intent"]
    if raw["scenario"] != intent.split("_", 1)[0]:
        raise MappingError("SCENARIO_INTENT_DISAGREEMENT")
    entry = target(table, intent)
    if entry is None:
        return None
    slots = annotated_slots(raw["utt"], raw["annot_utt"], table["slots"])
    return make_row("massive_1_1_en_us", raw["id"], raw["utt"], source_split(raw, original_split, "partition"),
                    intent, entry, slots, table, metadata={"scenario": raw["scenario"], "worker_id": raw.get("worker_id"),
                                                        "original_annotation": raw["annot_utt"]})
