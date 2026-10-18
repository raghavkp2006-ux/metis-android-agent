"""Original semicolon-delimited HWU annotated corpus; never normalized text."""
from .common import annotated_slots, load_mapping, make_row, MappingError, source_split, target


def map_record(raw, original_split=None):
    if raw.get("status", "").startswith("IRR_"):
        return None
    table = load_mapping("hwu_intent_mapping.yaml")
    scenario, intent = raw["scenario"], raw["intent"]
    short = intent[len(scenario) + 1:] if intent.startswith(scenario + "_") else intent
    entry = target(table, scenario + ":" + short)
    if entry is None:
        return None
    if "answer" not in raw or not isinstance(raw["answer"], str):
        raise MappingError("ORIGINAL_ANSWER_REQUIRED")
    slots = annotated_slots(raw["answer"], raw["answer_annotation"], table["slots"])
    return make_row("hwu_nlu_evaluation", raw["answerid"], raw["answer"], source_split(raw, original_split),
                    intent, entry, slots, table, metadata={"scenario": scenario, "userid": raw.get("userid"),
                                                        "status": raw.get("status"), "original_annotation": raw["answer_annotation"]})
