"""MTOP English parse adapter; archive license/split gates remain unresolved."""
import re
from .common import load_mapping, make_row, MappingError, source_split, target, u16, digest, utf16_boundaries


def parse_tree(text, annotation):
    """Align bracket-tree leaves monotonically to original text; no fuzzy search."""
    parts = re.findall(r"\[|\]|[^\s\[\]]+", annotation)
    index, cursor, nodes = 0, 0, []

    def parse(depth=0):
        nonlocal index, cursor
        if depth > 64 or index >= len(parts) or parts[index] != "[":
            raise MappingError("MALFORMED_MTOP_TREE")
        index += 1
        if index >= len(parts) or not re.fullmatch(r"(?:IN|SL):[A-Z0-9_]+", parts[index]):
            raise MappingError("MALFORMED_MTOP_LABEL")
        label = parts[index]
        index += 1
        node = {"label": label, "start": None, "end": None, "children": []}
        nodes.append(node)
        starts, ends = [], []
        while index < len(parts) and parts[index] != "]":
            if parts[index] == "[":
                child = parse(depth + 1)
                node["children"].append(child)
                starts.append(child["start"])
                ends.append(child["end"])
            else:
                word = parts[index]
                while cursor < len(text) and text[cursor].isspace():
                    cursor += 1
                if not text.startswith(word, cursor):
                    raise MappingError("MTOP_LEAF_TEXT_MISMATCH")
                starts.append(u16(text, cursor))
                cursor += len(word)
                ends.append(u16(text, cursor))
                node["children"].append({"text": word, "start": starts[-1], "end": ends[-1]})
                index += 1
        if index >= len(parts) or not starts:
            raise MappingError("MALFORMED_OR_EMPTY_MTOP_NODE")
        index += 1
        node.update(start=min(starts), end=max(ends))
        return node

    tree = parse()
    if index != len(parts) or text[cursor:].strip() or not tree["label"].startswith("IN:"):
        raise MappingError("MTOP_TREE_DOES_NOT_COVER_INPUT")
    return tree, nodes


def map_record(raw, original_split=None, *, temporal_review=None):
    if raw.get("locale") not in ("en", "en-US"):
        raise MappingError("MTOP_ENGLISH_ONLY")
    table = load_mapping("mtop_intent_mapping.yaml")
    text, annotation = raw["text"], raw["parse"]
    tree, nodes = parse_tree(text, annotation)
    intent = tree["label"]
    original_intent = raw.get("intent", intent)
    if original_intent.removeprefix("IN:") != intent.removeprefix("IN:"):
        raise MappingError("MTOP_ROOT_INTENT_DISAGREEMENT")
    entry = target(table, intent)
    if entry is None:
        return None
    slots = []
    temporal_nodes = []

    def flatten(node, inside_slot=False):
        label = node.get("label", "")
        is_slot = label.startswith("SL:")
        if is_slot and not inside_slot:
            if label not in table["slots"]:
                raise MappingError("UNMAPPED_MTOP_SLOT")
            slot_type = table["slots"][label]
            if slot_type == "REVIEW_TEMPORAL":
                temporal_nodes.append(node)
            elif slot_type is not None:
                boundaries = utf16_boundaries(text)
                slots.append({"type": slot_type, "start": node["start"], "end": node["end"],
                              "text": text[boundaries[node["start"]]:boundaries[node["end"]]]})
        for child in node.get("children", []):
            if "label" in child:
                flatten(child, inside_slot or is_slot)

    flatten(tree)
    if temporal_nodes:
        review = temporal_review or {}
        if review.get("text_sha256") != digest(text) or not review.get("reviewer_id"):
            raise MappingError("MTOP_TEMPORAL_REVIEW_REQUIRED")
        reviewed = review.get("slots", [])
        for node in temporal_nodes:
            covered = [s for s in reviewed if node["start"] <= s["start"] < s["end"] <= node["end"]]
            if not covered or any(s["type"] not in ("DATE", "TIME", "DATE_RANGE") for s in covered):
                raise MappingError("MTOP_TEMPORAL_REVIEW_INCOMPLETE")
            slots.extend(covered)
        if any(not any(n["start"] <= s["start"] < s["end"] <= n["end"] for n in temporal_nodes) for s in reviewed):
            raise MappingError("MTOP_TEMPORAL_REVIEW_OUTSIDE_NODE")
    nested_intents = sum(n["label"].startswith("IN:") for n in nodes) > 1
    if nested_intents:
        entry = {"decision": "KNOWN_UNSUPPORTED", "domain": "UNKNOWN", "action": "NONE",
                 "disposition": "KNOWN_UNSUPPORTED", "reason": "COMPOSITE_INTENTS_NOT_SILENTLY_EXECUTABLE"}
    return make_row("mtop_en", raw["id"], text, source_split(raw, original_split), original_intent, entry, slots, table,
                    metadata={"locale": raw["locale"], "nested_intents": nested_intents,
                              "source_domain": raw.get("domain"),
                              "native_slot_annotation": raw.get("native_slots"), "native_tokenization": raw.get("native_tokenization")},
                    composition={"original_parse": annotation, "tree": tree, "flattening": "OUTERMOST_SLOT_SPANS_ONLY",
                                 "temporal_review": temporal_review})
