"""Check every mapping decision and inventory without loading dataset utterances."""
import json
from pathlib import Path
import sys
if not __package__:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
    __package__ = "ml.nlu_v1.ingestion"
from .common import HERE, LABELS, load_mapping, validate_prediction, verify_freeze


def check():
    verify_freeze()
    inventory = json.loads((HERE / "mappings/source_label_inventory.json").read_text(encoding="utf-8"))
    massive = load_mapping("massive_intent_mapping.yaml")
    clinc = load_mapping("clinc_intent_mapping.yaml")
    if set(massive["intents"]) != set(inventory["massive"]["intents"]) or len(massive["intents"]) != 60:
        raise ValueError("Incomplete MASSIVE intent mapping")
    if set(clinc["intents"]) != set(inventory["clinc"]["intents"]) | {"oos"} or len(clinc["intents"]) != 151:
        raise ValueError("Incomplete CLINC intent mapping")
    for name in ("massive", "hwu", "mtop", "clinc", "legacy"):
        table = load_mapping(name + "_intent_mapping.yaml")
        for intent, entry in table["intents"].items():
            if name == "legacy" and intent == "UNKNOWN":
                if entry["decision"] != "REANNOTATE" or not entry["reason"]:
                    raise ValueError("Legacy UNKNOWN must be reinterpreted")
                continue
            if entry["decision"] not in ("MAP", "KNOWN_UNSUPPORTED", "OOD", "DROP") or not entry["reason"]:
                raise ValueError("Invalid mapping decision")
            if entry["decision"] == "DROP":
                continue
            prediction = {"schema_version": LABELS["schema_version"], "text": "fixture",
                          "tokens": [{"start": 0, "end": 7, "text": "fixture"}], "bio_tags": ["O"],
                          "meaning": {"domain": entry["domain"], "action": entry["action"], "slots": [],
                                      "disposition": entry["disposition"], "ood": entry["decision"] == "OOD"}}
            validate_prediction(prediction, "output")
            if entry["decision"] == "MAP" and not entry["disposition"].startswith("SUPPORTED_"):
                raise ValueError("MAP requires a supported disposition")
        for label in table["slots"].values():
            if label not in LABELS["slots"] and label not in (None, "REVIEW_TEMPORAL"):
                raise ValueError("Unknown mapped slot type")
    return {"massive_intents": 60, "clinc_labels_including_oos": 151, "tables": 5}


if __name__ == "__main__":
    print(json.dumps(check()))
