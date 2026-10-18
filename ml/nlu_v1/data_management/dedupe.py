"""Traceable duplicate/similarity quarantine. Never removes or rewrites input rows."""
from collections import defaultdict, deque
import copy
import math
import re
import unicodedata

from contract import utf16_boundaries
from source_registry import ROOT, UniqueLoader
import yaml
from .records import DataError, canonical_issues, facts_hash, load_groups, quarantine, sha_json, text_hash, transition, validate_record


def load_policy():
    policy = yaml.load((ROOT / "ml/nlu_v1/configs/dedupe_policy.yaml").read_text(encoding="utf-8"), Loader=UniqueLoader)
    if policy["policy_version"] != "nlu_v1_dedupe_001" or policy["normalization"] != "NFKC_CASEFOLD_WHITESPACE" or policy["skeleton"] != "NORMALIZED_TEXT_WITH_TYPED_SLOT_PLACEHOLDERS":
        raise DataError("UNKNOWN_DEDUPE_POLICY")
    if type(policy["cosine_threshold"]) not in (int, float) or not math.isfinite(policy["cosine_threshold"]) or not 0 < policy["cosine_threshold"] <= 1:
        raise DataError("INVALID_SIMILARITY_THRESHOLD")
    if policy["similarity_action"] != "QUARANTINE_ALL_MATCHES_NEVER_DELETE" or policy["missing_embeddings"] != "QUARANTINE":
        raise DataError("UNSAFE_SIMILARITY_POLICY")
    if any(scope not in ("GLOBAL", "SPLIT_LOCAL") for scope in policy["source_id_scopes"].values()):
        raise DataError("INVALID_SOURCE_ID_SCOPE")
    return policy


def normalized(text):
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def skeleton(row):
    boundaries = utf16_boundaries(row["text"])
    result, cursor = [], 0
    for slot in row["slots"]:
        start, end = boundaries[slot["start"]], boundaries[slot["end"]]
        result.extend([row["text"][cursor:start], "{" + slot["type"] + "}"])
        cursor = end
    result.append(row["text"][cursor:])
    return normalized("".join(result))


def collection_hash(rows):
    return sha_json([facts_hash(row) for row in rows])


def source_key(row, policy):
    source = row["source"]
    key = (source["dataset"], source["version"], source["source_id"])
    if policy["source_id_scopes"].get(source["dataset"], "GLOBAL") == "SPLIT_LOCAL":
        key += (source["original_split"],)
    return key


def prepare_embeddings(rows, artifact):
    if not rows:
        return {}, "NOT_APPLICABLE_EMPTY_INPUT", None, {}
    if artifact is None:
        return {}, "NOT_RUN_MISSING_EMBEDDINGS", None, {i: ["EMBEDDINGS_MISSING"] for i in range(len(rows))}
    required = {"embedding_version", "model_id", "model_sha256", "tokenizer_sha256", "configuration_sha256", "rows"}
    if not isinstance(artifact, dict) or set(artifact) != required or artifact["embedding_version"] != "nlu_v1_embeddings_001" or not isinstance(artifact["model_id"], str) or not artifact["model_id"] or not isinstance(artifact["rows"], list):
        raise DataError("INVALID_EMBEDDING_ARTIFACT")
    for key in ("model_sha256", "tokenizer_sha256", "configuration_sha256"):
        if not isinstance(artifact[key], str) or not re.fullmatch(r"[a-f0-9]{64}", artifact[key]):
            raise DataError("EMBEDDING_MODEL_FINGERPRINT_REQUIRED")
    entries, dimension = {}, None
    for entry in artifact["rows"]:
        if set(entry) != {"id", "text_sha256", "vector"} or entry["id"] in entries:
            raise DataError("DUPLICATE_OR_MALFORMED_EMBEDDING_ENTRY")
        vector = entry["vector"]
        if not isinstance(vector, list) or not vector or len(vector) > 4096 or any(type(x) not in (int, float) or not math.isfinite(x) for x in vector):
            raise DataError("INVALID_EMBEDDING_VECTOR")
        if dimension is None:
            dimension = len(vector)
        if len(vector) != dimension:
            raise DataError("EMBEDDING_DIMENSION_MISMATCH")
        norm = math.hypot(*vector)
        if not math.isfinite(norm) or norm == 0:
            raise DataError("ZERO_OR_INVALID_EMBEDDING_NORM")
        entries[entry["id"]] = (entry["text_sha256"], [x / norm for x in vector])
    if set(entries) - {row["id"] for row in rows}:
        raise DataError("EMBEDDING_REFERENCES_UNKNOWN_ROW")
    vectors, issues = {}, {}
    for index, row in enumerate(rows):
        entry = entries.get(row["id"])
        if entry is None:
            issues[index] = ["EMBEDDINGS_MISSING"]
        elif entry[0] != text_hash(row["text"]):
            issues[index] = ["EMBEDDING_TEXT_BINDING_STALE"]
        else:
            vectors[index] = entry[1]
    model = {key: artifact[key] for key in required - {"rows"}}
    model["artifact_sha256"] = sha_json(artifact)
    model["dimension"] = dimension
    return vectors, "COMPLETE" if not issues else "INCOMPLETE", model, issues


def audit(rows, embeddings=None, *, groups=None):
    """Exact reference cosine scan; supplied embeddings, no model/network inference."""
    rows = list(rows)
    for row in rows:
        validate_record(row)
    policy = load_policy()
    groups = groups or load_groups()
    reasons = {i: set(canonical_issues(row, groups)) for i, row in enumerate(rows)}
    report = {"report_version": "nlu_v1_dedupe_report_001", "policy": policy, "policy_sha256": sha_json(policy),
              "input_sha256": collection_hash(rows), "row_count": len(rows), "checks": {},
              "matches": [], "quarantined_indices": [], "deleted_rows": 0}

    def grouped(name, key):
        buckets = defaultdict(list)
        for index, row in enumerate(rows):
            buckets[key(row)].append(index)
        collisions = [indices for indices in buckets.values() if len(indices) > 1]
        report["checks"][name] = {"status": "COMPLETE", "collision_groups": len(collisions)}
        for indices in collisions:
            report["matches"].append({"check": name, "indices": indices, "row_ids": [rows[i]["id"] for i in indices]})
            for index in indices:
                reasons[index].add(name.upper())
            annotations = {sha_json({k: rows[i][k] for k in ("domain", "action", "slots", "disposition", "ood")}) for i in indices}
            if name in ("exact_text", "normalized_text") and len(annotations) > 1:
                for index in indices:
                    reasons[index].add("ANNOTATION_CONFLICT_REVIEW_REQUIRED")

    grouped("exact_text", lambda r: r["text"])
    grouped("normalized_text", lambda r: normalized(r["text"]))
    grouped("source_id", lambda r: source_key(r, policy))
    grouped("source_skeleton", lambda r: (r["source"]["dataset"], r["source"]["original_intent"], skeleton(r)))
    grouped("row_id", lambda r: r["id"])
    vectors, status, model, embedding_issues = prepare_embeddings(rows, embeddings)
    pairs = 0
    indices = sorted(vectors)
    for position, left in enumerate(indices):
        for right in indices[position + 1:]:
            similarity = max(-1.0, min(1.0, math.fsum(a * b for a, b in zip(vectors[left], vectors[right]))))
            if similarity >= policy["cosine_threshold"]:
                pairs += 1
                report["matches"].append({"check": "embedding_similarity", "indices": [left, right],
                                          "row_ids": [rows[left]["id"], rows[right]["id"]], "cosine": similarity})
                reasons[left].add("EMBEDDING_SIMILARITY")
                reasons[right].add("EMBEDDING_SIMILARITY")
    for index, extra in embedding_issues.items():
        reasons[index].update(extra)
    report["checks"]["embedding_similarity"] = {"status": status, "similar_pairs": pairs, "model": model}

    # Preserve parent groups across partitions and generated-parent edges.
    parents, by_id = defaultdict(list), defaultdict(list)
    for index, row in enumerate(rows):
        if row["semantic_parent_id"]:
            parents[row["semantic_parent_id"]].append(index)
        by_id[row["id"]].append(index)
    for indices in parents.values():
        partitions = {rows[i]["partition"] for i in indices} - {"UNASSIGNED"}
        if len(partitions) > 1:
            for index in indices:
                reasons[index].add("SEMANTIC_PARENT_PARTITION_CONFLICT")
    graph, indegree = defaultdict(set), {key: 0 for key in by_id}
    for index, row in enumerate(rows):
        for parent_id in (row["generation"] or {}).get("parent_ids", []):
            if parent_id not in by_id:
                reasons[index].add("GENERATION_PARENT_MISSING")
                continue
            for parent_index in by_id[parent_id]:
                parent = rows[parent_index]
                if parent["semantic_parent_id"] != row["semantic_parent_id"]:
                    reasons[index].add("GENERATION_PARENT_GROUP_MISMATCH")
                if row["privacy"] == "PUBLIC" and parent["privacy"] != "PUBLIC":
                    reasons[index].add("ANCESTOR_PRIVACY_REVIEW_REQUIRED")
            if row["id"] not in graph[parent_id]:
                graph[parent_id].add(row["id"])
                indegree[row["id"]] += 1
    queue = deque(key for key, degree in indegree.items() if degree == 0)
    while queue:
        parent = queue.popleft()
        for child in graph[parent]:
            indegree[child] -= 1
            if indegree[child] == 0:
                queue.append(child)
    for key, degree in indegree.items():
        if degree:
            for index in by_id[key]:
                reasons[index].add("GENERATION_LINEAGE_CYCLE_OR_DEPENDENT")
    report["checks"]["lineage_and_partitions"] = {"status": "COMPLETE"}

    output = []
    for index, row in enumerate(rows):
        if reasons[index]:
            output.append(quarantine(row, reasons[index]))
        elif row["review_status"] == "PENDING":
            output.append(transition(row, "AUTO_VALIDATED", actor="validator:nlu_v1_data_001", reason="CANONICAL_AUTO_VALIDATED", groups=groups))
        else:
            output.append(copy.deepcopy(row))  # Never auto-release prior quarantine.
        if output[-1]["quarantine_reasons"] or output[-1]["review_status"] in ("QUARANTINED", "REJECTED"):
            report["quarantined_indices"].append(index)
    report["findings"] = [{"index": i, "row_id": row["id"], "reasons": row["quarantine_reasons"]} for i, row in enumerate(output) if i in report["quarantined_indices"]]
    report["audit_complete"] = status in ("COMPLETE", "NOT_APPLICABLE_EMPTY_INPUT")
    report["report_sha256"] = sha_json(report)
    return output, report


def verify_report(rows, report):
    if report.get("report_version") != "nlu_v1_dedupe_report_001" or report.get("input_sha256") != collection_hash(rows):
        raise DataError("DEDUPE_REPORT_STALE")
    if report.get("report_sha256") != sha_json({k: v for k, v in report.items() if k != "report_sha256"}):
        raise DataError("DEDUPE_REPORT_INTEGRITY_FAILED")
    if report.get("policy_sha256") != sha_json(load_policy()) or report.get("policy_sha256") != sha_json(report.get("policy")) or report.get("audit_complete") is not True:
        raise DataError("DEDUPE_AUDIT_INCOMPLETE_OR_POLICY_CHANGED")
    required = {"exact_text", "normalized_text", "source_id", "source_skeleton", "row_id", "embedding_similarity", "lineage_and_partitions"}
    if set(report.get("checks", {})) != required or report.get("row_count") != len(rows) or report.get("deleted_rows") != 0:
        raise DataError("DEDUPE_REPORT_CHECKS_INCOMPLETE")
    if any(report["checks"][name].get("status") != "COMPLETE" for name in required - {"embedding_similarity"}):
        raise DataError("DEDUPE_REPORT_CHECKS_INCOMPLETE")
    expected = "COMPLETE" if rows else "NOT_APPLICABLE_EMPTY_INPUT"
    if report["checks"]["embedding_similarity"].get("status") != expected:
        raise DataError("DEDUPE_REPORT_EMBEDDINGS_INCOMPLETE")
    indices = report.get("quarantined_indices", [])
    if len(indices) != len(set(indices)) or any(type(index) is not int or not 0 <= index < len(rows) for index in indices):
        raise DataError("DEDUPE_REPORT_INDICES_INVALID")
