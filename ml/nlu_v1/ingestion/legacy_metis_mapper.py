"""Approved legacy rows only. Never imports, trains or evaluates archived models."""
import copy
from .common import digest, load_mapping, make_row, MappingError, source_split, target


def map_record(raw, original_split=None, *, row_approval=None, reannotation=None):
    # The approval is supplied from a trusted sidecar, not copied from row flags.
    approval = row_approval or {}
    if approval.get("classification") != "TRAINABLE_LEGACY" or raw.get("classification") != "TRAINABLE_LEGACY":
        raise MappingError("LEGACY_NOT_TRAINABLE")
    denied = {"EXPOSED", "PROTECTED_EVALUATION", "PRIVATE_DO_NOT_PUBLISH"}
    if any(raw.get(k) in denied or approval.get(k) in denied for k in ("exposure", "privacy")):
        raise MappingError("LEGACY_QUARANTINED")
    if approval.get("source_id") != str(raw["id"]) or approval.get("text_sha256") != digest(raw["text"]):
        raise MappingError("LEGACY_APPROVAL_NOT_BOUND_TO_ROW")
    if not approval.get("approval_id") or approval.get("license_verified") is not True or approval.get("privacy_reviewed") is not True:
        raise MappingError("LEGACY_APPROVAL_INCOMPLETE")
    table = load_mapping("legacy_intent_mapping.yaml")
    intent = raw["intent"]
    if intent == "UNKNOWN":
        if not reannotation or reannotation.get("source_id") != str(raw["id"]) or reannotation.get("text_sha256") != digest(raw["text"]):
            raise MappingError("LEGACY_UNKNOWN_REANNOTATION_REQUIRED")
        if not reannotation.get("reason") or not reannotation.get("reviewer_id"):
            raise MappingError("LEGACY_UNKNOWN_REVIEW_REQUIRED")
        entry = copy.deepcopy(reannotation["target"])
        slots = copy.deepcopy(reannotation["slots"])
    else:
        entry = target(table, intent)
        slots = []
        for entity in raw.get("entities", []):
            label = entity["type"]
            if label not in table["slots"]:
                raise MappingError("UNMAPPED_LEGACY_SLOT")
            slots.append({"type": table["slots"][label], "start": entity["startOffset"],
                          "end": entity["endOffset"], "text": entity["rawValue"]})
    return make_row("metis_trainable_legacy", raw["id"], raw["text"], source_split(raw, original_split),
                    intent, entry, slots, table, privacy=raw["privacy"], family=raw.get("family_id"),
                    metadata={"row_approval_id": approval["approval_id"],
                              "reinterpretation": copy.deepcopy(reannotation) if intent == "UNKNOWN" else None})
