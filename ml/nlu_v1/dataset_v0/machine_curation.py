"""Machine-policy curation queue. Never creates human approvals or trains models."""
import argparse
from collections import Counter, defaultdict
import copy
import hashlib
import json
from pathlib import Path
import re

from ml.nlu_v1.ingestion.common import REGISTRY, load_mapping
from ml.nlu_v1.ingestion.massive_mapper import map_record as massive
from ml.nlu_v1.ingestion.clinc_mapper import map_record as clinc
from ml.nlu_v1.data_management.records import (
    DataError, append_event, assign_group, classify_privacy, facts_hash, family_matches, load_groups,
    register_scenario, sha_json, text_hash, utc_now, validate_record,
)
from ml.nlu_v1.data_management.dedupe import audit, collection_hash, skeleton
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import INELIGIBLE, connected_groups, leakage_report, official, read_jsonl, strict_unicode
from ml.nlu_v1.dataset_v0.build import bucket, distribution, load_config
from contract import LABELS, unique_keys, verify_freeze
from source_registry import ROOT, UniqueLoader
from jsonschema import ValidationError
import yaml

VERSION = "nlu_v1_machine_curation_002"
POLICY = ROOT / "ml/nlu_v1/configs/machine_curation_002.yaml"
FAMILY_POLICY = ROOT / "ml/nlu_v1/configs/machine_families_002.yaml"
GLOBAL_BLOCKERS = ["HUMAN_QUARANTINE_RELEASE_AND_APPROVAL_REQUIRED", "VERIFIED_INTAKE_APPROVAL_REQUIRED",
                   "PUBLICATION_RIGHTS_APPROVAL_REQUIRED", "FRESH_FINAL_INDEPENDENT_DATA_RESERVED_NOT_COLLECTED"]


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_policy():
    value = yaml.load(POLICY.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if value != {"policy_version": VERSION, "review_path": "AUTO_CURATED_READY_FOR_REVIEW_QUEUE_ONLY", "human_approval_required": True,
        "release_existing_quarantine": False, "source_intake_approval_creation": False,
        "privacy_policy": "VERIFIED_PUBLIC_SOURCE_AND_CLOSED_NONPERSONAL_GRAMMAR", "publication_allowed": False,
        "disposition_correction": "NEEDS_CONTEXT_TO_COMPLETE_ONLY_WITH_FULL_GRAMMAR_PROOF",
        "family_selection": "UNIQUE_REGISTERED_FAMILY_OR_EXPLICIT_SLOT_COMPOSITE", "embeddings": "EXTERNAL_APPROVED_ARTIFACT_REQUIRED",
        "final_independent": "RESERVED_NEVER_GENERATED"}:
        raise DataError("MACHINE_POLICY_CONTRACT_CHANGED")
    return value


class PublicEvidence:
    """Exact native-row/source/license evidence, not an intake approval sidecar."""
    def __init__(self, root=DATASETS):
        self.root, self.sources, self.errors = Path(root), {}, {}
        pinned = json.loads((ROOT / "ml/nlu_v1/configs/dataset_source_evidence.json").read_text(encoding="utf-8"))
        for source_id in ("massive_1_1_en_us", "clinc150_full"):
            try:
                source = REGISTRY[source_id]
                base = self.root / "raw" / ("v0_" + source_id)
                observation = json.loads((base / "observation.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
                expected = {"source_id": source_id, "source_version": source["version"], "revision": source["revision"],
                    "requested_url": source["source_url"], "resolved_url": source["source_url"], "locale": source["locale"],
                    "license_id": source["license"]["id"], "status": "RAW_PUBLIC_QUARANTINE_REVIEW_PENDING"}
                if source["license"]["verification"] != "VERIFIED_METADATA" or source["metis_status"] != "REGISTERED_PENDING_INTAKE" or any(observation.get(k) != v for k, v in expected.items()):
                    raise DataError("PUBLIC_SOURCE_IDENTITY_OR_LICENSE_UNVERIFIED")
                member = (base / observation["member_file"]).resolve()
                if member.parent != base.resolve():
                    raise DataError("PUBLIC_MEMBER_PATH_INVALID")
                archive = base / ("source.tar.gz" if source_id.startswith("massive") else "source.json")
                license_file = base / "license-evidence.txt"
                license_url = source["license"]["evidence_url"].replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/")
                hashes = [item["sha256"] for record in pinned["sources"].values() if record["repository"] == source["repository"] for item in record["metadata"] if item["url"] == license_url]
                if file_hash(member) != observation["member_sha256"] or file_hash(archive) != observation["sha256"] or hashes != [file_hash(license_file)] or observation["license_evidence_sha256"] != hashes[0]:
                    raise DataError("PUBLIC_SOURCE_OR_LICENSE_HASH_MISMATCH")
                if source_id.startswith("massive"):
                    native = [json.loads(line, object_pairs_hook=unique_keys) for line in member.read_bytes().splitlines()]
                    lookup = {(row["partition"], str(row["id"])): row for row in native if row["partition"] == "train"}
                    if len(lookup) != sum(row["partition"] == "train" for row in native):
                        raise DataError("DUPLICATE_NATIVE_SOURCE_ID")
                else:
                    native = json.loads(member.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
                    if set(native) != {"train", "val", "test", "oos_train", "oos_val", "oos_test"}:
                        raise DataError("CLINC_SPLIT_PROVENANCE_INVALID")
                    lookup = {(split, str(index)): row for split in ("train", "oos_train") for index, row in enumerate(native[split])}
                self.sources[source_id] = {"observation": observation, "lookup": lookup, "license_sha256": hashes[0],
                    "observation_sha256": file_hash(base / "observation.json"), "license_id": source["license"]["id"]}
            except (OSError, ValueError, KeyError, TypeError):
                self.errors[source_id] = "PUBLIC_EVIDENCE_UNAVAILABLE_OR_INVALID"

    def bind(self, row):
        source = row["source"]
        if official(row) or row["partition"] in ("PROTECTED_EVALUATION", "FINAL_INDEPENDENT_TEST") or source["source_class"] != "PUBLIC_DATASET":
            raise DataError("PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED")
        evidence = self.sources.get(source["dataset"])
        if not evidence:
            raise DataError("PUBLIC_EVIDENCE_UNAVAILABLE_OR_INVALID")
        raw = evidence["lookup"].get((source["original_split"], source["source_id"]))
        if raw is None:
            raise DataError("SOURCE_ROW_NOT_VERIFIED_TRAINING_SPLIT")
        expected = massive(raw) if source["dataset"].startswith("massive") else clinc(raw, source["original_split"], source["source_id"])
        if expected is None:
            raise DataError("SOURCE_MAPPING_DROP")
        for field in ("id", "text", "domain", "action", "slots", "ood", "tokens", "bio_tags"):
            if row[field] != expected[field]:
                raise DataError("SOURCE_SEMANTIC_OR_SPAN_CONTRADICTION")
        if row["metadata"] != expected["metadata"]:
            raise DataError("UNREVIEWED_SOURCE_METADATA_CHANGED")
        if any(source.get(key) != value for key, value in expected["source"].items() if key not in ("artifact_sha256", "intake_approval_id")):
            raise DataError("SOURCE_MAPPING_PROVENANCE_CHANGED")
        if source["artifact_sha256"] != evidence["observation"]["member_sha256"] or row["provenance"]["import_file_sha256"] != source["artifact_sha256"] or source["version"] != expected["source"]["version"] or source["original_intent"] != expected["source"]["original_intent"] or source["mapping_version"] != expected["source"]["mapping_version"]:
            raise DataError("SOURCE_PROVENANCE_BINDING_INVALID")
        table = "massive" if source["dataset"].startswith("massive") else "clinc"
        mapping_file = ROOT / f"ml/nlu_v1/ingestion/mappings/{table}_intent_mapping.yaml"
        if row["provenance"]["mapping_configuration_sha256"] != hashlib.sha256(mapping_file.read_bytes().replace(b"\r\n", b"\n")).hexdigest():
            raise DataError("SOURCE_MAPPING_CONFIGURATION_CHANGED")
        return {"source_id": source["dataset"], "source_record_id": source["source_id"], "source_split": source["original_split"],
            "member_sha256": source["artifact_sha256"], "native_row_sha256": sha_json(raw), "text_sha256": text_hash(row["text"]),
            "license_id": evidence["license_id"], "license_sha256": evidence["license_sha256"],
            "observation_sha256": evidence["observation_sha256"], "expected_disposition": expected["disposition"],
            "intake_approval_present": source["intake_approval_id"] is not None,
            "acquisition_privacy_reviewed": evidence["observation"].get("privacy_reviewed") is True}


def family_for(row, families):
    matches = sorted(key for key, definition in families["families"].items() if family_matches(row, definition))
    policy = yaml.load(FAMILY_POLICY.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if policy["policy_version"] != VERSION:
        raise DataError("MACHINE_FAMILY_POLICY_VERSION_MISMATCH")
    extra_names = set(policy["families"])
    for selector in policy["selectors"]:
        if row["source"]["original_intent"] == selector["source_intent"] and {s["type"] for s in row["slots"]} == set(selector["exact_slot_types"]) and set(matches) - extra_names == set(selector["primitive_families"]) and selector["result_family"] in matches:
            return selector["result_family"]
    if len(matches) != 1:
        raise DataError("AMBIGUOUS_FAMILY" if matches else "NO_DETERMINISTIC_FAMILY")
    return matches[0]


# These are closed whole-utterance grammars, not keyword confidence rules.
TAIL = r"(?: (?:on|for)? ?\{date\})?(?: \{recurrence\})?"
RULES = {
    ("ALARM", "SET"): [rf"(?:please )?(?:set|create|add)(?: me)? (?:an? |my |the )?alarm(?: clock)? (?:for|at) \{{time\}}{TAIL}",
        r"(?:please )?(?:set|create|add) (?:an? |my |the )?alarm for \{date\} at \{time\}", rf"wake me up at \{{time\}}{TAIL}"],
    ("ALARM", "READ"): [r"(?:please )?(?:show|list|read|check)(?: me)?(?: all)?(?: my| the)? alarms(?: (?:for|on|at) \{(?:date|time)\})?",
        r"what alarms (?:are currently set|have been set|will wake me up \{date\})"],
    ("ALARM", "DELETE"): [r"(?:please )?(?:delete|remove|cancel) all(?: my| the)? alarms(?: (?:for|on|at) \{(?:date|time)\})?"],
    ("CALENDAR", "READ"): [r"(?:please )?(?:show|check|read)(?: me)?(?: my| the)? (?:calendar|schedule)(?: (?:for|on) \{date\})?",
        r"what(?:'s| is) on my calendar(?: (?:for|on) \{date\})?",
        r"(?:please )?(?:show|list)(?: my| the)? \{event\}(?: (?:for|on) \{date\})?"],
    ("CALENDAR", "CREATE"): [r"(?:please )?(?:schedule|add|create) (?:a |an |the )?\{event\} on \{date\} at \{time\}"],
    ("COMMUNICATION", "READ"): [r"(?:please )?read(?: me)?(?: my| the)? (?:emails|mail|inbox)", r"(?:please )?show(?: me)?(?: my| the)? (?:emails|mail|inbox)"],
    ("PERSON", "READ"): [r"(?:please )?(?:show|list|open)(?: my| the)? (?:contacts|contact list|address book)"],
}
PREFIX = r"(?:(?:please|kindly) |(?:can|could|would|will) you (?:please )?)?"
RULES[("ALARM", "SET")].extend([
    rf"{PREFIX}(?:set|create|add|make)(?: me)? (?:an? |my |the )?(?:wake up )?alarm(?: clock)?(?: to wake me up)? (?:for|at) \{{time\}}{TAIL}",
    rf"{PREFIX}(?:set|create|add|make)(?: me)? (?:an? |my |the )?alarm for \{{date\}} at \{{time\}}",
    rf"i (?:need|want) to (?:get|wake) up at \{{time\}}{TAIL}",
    rf"i (?:need|want|would like) (?:an? )?alarm (?:set )?(?:for|at) \{{time\}}{TAIL}",
    rf"{PREFIX}wake me up \{{date\}} at \{{time\}}"])
RULES[("ALARM", "READ")].extend([
    rf"{PREFIX}(?:show|list|check)(?: me)? (?:all |my |all my |the )?alarms(?: (?:for|on|at) \{{(?:date|time)\}})?",
    r"do i have any alarms(?: set)?(?: (?:for|on|at) \{(?:date|time)\})?",
    r"what time is my (?:next )?alarm(?: set for)?",
    r"how many alarms do i have(?: set)?",
    r"what (?:are|is) my (?:next |upcoming )?alarms?(?: (?:for|on|at) \{(?:date|time)\})?",
    r"show me what alarms i have(?: set)?"])
RULES[("CALENDAR", "READ")].extend([
    rf"{PREFIX}(?:show|check|read)(?: me)? (?:my |the )?(?:calendar|schedule)(?: (?:for|on) \{{date\}})?",
    r"what(?:'s| is| are) my (?:next|upcoming) \{event\}",
    r"when (?:is|are) my next \{event\}",
    r"what \{event\} (?:do i have|are scheduled|have i got)(?: (?:for|on)? ?\{date\})?",
    rf"{PREFIX}(?:show|list)(?: me)? (?:my |the )?\{{event\}}(?: (?:for|on) \{{date\}})?(?: at \{{time\}})?"])
RULES[("CALENDAR", "CREATE")].append(rf"{PREFIX}(?:schedule|add|create) (?:a |an |the )?\{{event\}} on \{{date\}} at \{{time\}}")
RULES[("COMMUNICATION", "READ")].extend([
    rf"{PREFIX}(?:read|show|check)(?: me)? (?:my |the )?(?:emails|mail|inbox)",
    rf"{PREFIX}(?:read|show|check)(?: me)? (?:my |the )?\{{kind\}} emails",
    r"do i have any \{kind\} emails",
    r"how many \{kind\} emails do i have"])
RULES[("PERSON", "READ")].append(rf"{PREFIX}(?:show|list|open|get)(?: me)? (?:my |the )?(?:contacts|contact list|address book)")
NUMBER_WORDS = set("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty hundred oh quarter half past to o'clock".split())
DATE_WORDS = set("today tomorrow yesterday next this the day after before in days weeks week on of first second third fourth fifth sixth seventh eighth ninth tenth eleventh twelfth thirteenth fourteenth fifteenth sixteenth seventeenth eighteenth nineteenth twentieth thirtieth twenty thirty monday tuesday wednesday thursday friday saturday sunday january february march april may june july august september october november december".split())


def safe_slot(slot):
    value = slot["text"].casefold().strip()
    kind = slot["type"]
    if kind == "EVENT":
        return value in {"meeting", "meetings", "appointment", "appointments", "event", "events"}
    if kind == "RECURRENCE":
        return value in {"daily", "every day", "every weekday", "on weekdays", "weekdays"}
    if kind == "KIND":
        return value in {"new", "unread", "read", "all"}
    if kind == "TIME":
        if value in {"noon", "midnight", "morning", "afternoon", "evening"}:
            return True
        if re.fullmatch(r"(?:[01]?\d|2[0-3])(?::[0-5]\d)?(?:\s*[ap]\.?m\.?)?", value):
            return True
        words = re.sub(r"\b[ap]\.?m\.?$", "", value).strip().split()
        return bool(words) and set(words) <= NUMBER_WORDS
    if kind == "DATE":
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
            return True  # Raw-date extraction only; date resolution remains external.
        words = re.findall(r"[a-z]+|\d+(?:st|nd|rd|th)?", value)
        return bool(words) and " ".join(words) == value and all(word in DATE_WORDS or re.fullmatch(r"\d{1,2}(?:st|nd|rd|th)?|(?:19|20|21)\d{2}", word) for word in words)
    return False


def semantics(row):
    if any(slot["type"] == "REFERENCE" for slot in row["slots"]):
        return None, "UNRESOLVED_REFERENCE"
    form = skeleton(row).rstrip("?.!")
    pair = row["domain"], row["action"]
    for slot in row["slots"]:
        if slot["type"] == "TIME" and not (re.search(r"[ap]\.?m\.?$", slot["text"].casefold()) or slot["text"].casefold() in {"noon", "midnight"} or (row["action"] == "READ" and slot["text"].casefold() in {"morning", "afternoon", "evening"}) or re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", slot["text"])):
            return None, "TEMPORAL_CONTEXT_UNRESOLVED"
    if any(re.fullmatch(pattern, form) for pattern in RULES.get(pair, [])):
        return "CLOSED_REQUEST_GRAMMAR", None
    intent = row["source"]["original_intent"]
    if pair == ("UNKNOWN", "NONE") and row["disposition"] == "OUT_OF_DOMAIN" and intent in {"balance", "apr", "credit_score", "credit_limit", "interest_rate"}:
        if re.fullmatch(r"(?:what(?:'s| is)|show|check|tell me what) my (?:bank |account |credit card |checking |savings )?(?:balance|apr|interest rate|credit score|credit rating)(?: is)?", form):
            return "CLOSED_EXTERNAL_FINANCE_GRAMMAR", None
    if pair == ("UNKNOWN", "NONE") and row["disposition"] == "KNOWN_UNSUPPORTED" and intent in {"general_greet", "greeting", "goodbye"} and form in {"hello", "hi", "bye", "goodbye", "good morning"}:
        return "CLOSED_UNSUPPORTED_GREETING", None
    return None, "SEMANTICS_NOT_PROVEN_BY_MACHINE_POLICY"


def correct_disposition(row, proof, rule):
    result = copy.deepcopy(row)
    expected = proof["expected_disposition"]
    if semantics(row)[0] != rule:
        rule = None
    if row["disposition"] == "SUPPORTED_NEEDS_CONTEXT" and expected == "SUPPORTED_COMPLETE" and rule == "CLOSED_REQUEST_GRAMMAR" and not any(s["type"] == "REFERENCE" for s in row["slots"]):
        result["disposition"] = "SUPPORTED_COMPLETE"
        return result, True
    if row["disposition"] != expected:
        raise DataError("DISPOSITION_SOURCE_CONTRADICTION")
    return result, False


def bind_privacy(row, source_proof, rule):
    if row["privacy"] in ("PRIVATE", "RESTRICTED"):
        raise DataError("PRIVACY_CLASSIFICATION_CANNOT_OVERRIDE_RESTRICTION")
    registered = REGISTRY.get(row["source"]["dataset"])
    if not rule or semantics(row)[0] != rule or not registered or source_proof.get("license_id") != registered["license"]["id"] or source_proof.get("text_sha256") != text_hash(row["text"]) or source_proof.get("member_sha256") != row["source"]["artifact_sha256"] or not re.fullmatch(r"[a-f0-9]{64}", source_proof.get("license_sha256", "")):
        raise DataError("PRIVACY_SOURCE_EVIDENCE_REQUIRED")
    if not all(safe_slot(slot) for slot in row["slots"]):
        raise DataError("PRIVACY_FREE_TEXT_OR_PERSONAL_SLOT_UNRESOLVED")
    proposal = copy.deepcopy(row)
    proposal["privacy"] = "PUBLIC"
    evidence = {"binding_sha256": facts_hash(proposal), "review_id": "machine-privacy:" + sha_json([source_proof, rule]),
        "reviewer_id": "machine:" + VERSION, "reviewed_at": utc_now(), "publication_allowed": False, "license_id": source_proof["license_id"]}
    return classify_privacy(row, "PUBLIC", evidence)


def curate_row(original, groups, evidence):
    row = copy.deepcopy(original)
    strict_unicode(row)
    validate_record(row)
    reasons, proof, rule, corrected, assigned = [], None, None, False, False
    if official(row) or row["partition"] in ("PROTECTED_EVALUATION", "FINAL_INDEPENDENT_TEST") or row["source"]["source_class"] != "PUBLIC_DATASET":
        return row, ["PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED"], groups, False
    if row["review_status"] not in ("PENDING", "QUARANTINED"):
        return row, ["MACHINE_PATH_REQUIRES_UNREVIEWED_OR_QUARANTINED_INPUT"], groups, False
    if row["privacy"] in ("PRIVATE", "RESTRICTED"):
        return row, ["PRIVACY_ALREADY_RESTRICTED_REQUIRES_HUMAN_REVIEW"], groups, False
    try:
        proof = evidence.bind(row)
    except DataError as error:
        reasons.append(str(error))
    rule, error = semantics(row)
    if error:
        reasons.append(error)
    if proof is None:
        return row, sorted(set(reasons + ["FAMILY_SOURCE_EVIDENCE_UNVERIFIED", "PRIVACY_NONPERSONAL_PROOF_INCOMPLETE"])), groups, False
    if proof:
        try:
            row, corrected = correct_disposition(row, proof, rule)
        except DataError as error:
            reasons.append(str(error))
    if row["privacy_review"] is not None:
        # New fact annotations require a new binding. Never carry an old privacy
        # approval onto changed facts or silently override a restricted class.
        row["privacy_review"] = None
        row["privacy"] = "UNKNOWN_PRIVACY"
    row["metadata"]["machine_curation"] = {"policy_version": VERSION, "policy_sha256": file_hash(POLICY),
        "input_facts_sha256": facts_hash(original), "source_evidence": proof, "semantic_rule": rule,
        "disposition_corrected": corrected, "review_path": "MACHINE_QUEUE_ONLY_NO_HUMAN_APPROVAL"}
    append_event(row, row["review_status"], row["review_status"], "machine:" + VERSION, "MACHINE_FACTS_ANNOTATED")
    try:
        if proof is None:
            raise DataError("FAMILY_SOURCE_EVIDENCE_UNVERIFIED")
        family = family_for(row, groups[0])
        parent = "machine-parent:" + sha_json([row["source"]["dataset"], row["source"]["version"], row["source"]["original_intent"], skeleton(row)])
        scenario = "machine-scenario:" + sha_json([family, parent, original["metadata"].get("scenario", row["source"]["original_intent"])])
        if row["family"] is not None or row["scenario_id"] is not None:
            if row["family"] != family or row["scenario_id"] != scenario:
                raise DataError("EXISTING_GROUP_ASSIGNMENT_REQUIRES_REVIEW")
        if scenario not in groups[1]["scenarios"]:
            groups = groups[0], register_scenario(groups[1], groups[0], scenario, family, parent, "Machine source/slot-skeleton grouping; no invented paraphrase independence")
        row = assign_group(row, family, scenario, groups=groups)
        assigned = True
    except DataError as error:
        reasons.append(str(error))
    if proof and rule:
        try:
            row = bind_privacy(row, proof, rule)
        except DataError as error:
            reasons.append(str(error))
    else:
        reasons.append("PRIVACY_NONPERSONAL_PROOF_INCOMPLETE")
    validate_record(row)
    return row, sorted(set(reasons)), groups, assigned


def approved_embeddings(path=None, approval_path=None):
    if path is None or approval_path is None:
        return None, "APPROVED_EMBEDDING_ARTIFACT_MISSING"
    artifact = json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    approval = json.loads(Path(approval_path).read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    if approval.get("kind") != "EMBEDDING_ARTIFACT" or approval.get("status") != "APPROVED" or not approval.get("approval_id") or not approval.get("reviewer_id") or approval.get("artifact_sha256") != file_hash(path) or any(approval.get(key) != artifact.get(key) for key in ("model_sha256", "tokenizer_sha256", "configuration_sha256")):
        raise DataError("EMBEDDING_ARTIFACT_APPROVAL_INVALID")
    return artifact, None


def full_coverage(rows, groups):
    base = distribution(rows)
    base["all_labels"] = {key: {label: base[key].get(label, 0) for label in LABELS[key]} for key in ("domains", "actions", "slots", "dispositions")}
    base["family_counts"] = {key: sum(row["family"] == key for row in rows) for key in groups[0]["families"]}
    base["scenario_counts"] = dict(sorted(Counter(row["scenario_id"] or "UNRESOLVED" for row in rows).items()))
    return base


def prepare_additions(missing, evidence, root=DATASETS):
    """Source-first proposals only. No relabeling, approval or protected inputs."""
    public_intents = {"TASK": {"lists_query", "lists_createoradd", "todo_list", "todo_list_update"},
        "PREFERENCE_KNOWLEDGE_ETC": {"change_language", "change_accent", "qa_definition", "qa_factoid"}}
    references = []
    for group in missing:
        for source_id, source in evidence.sources.items():
            matches = []
            for (split, ident), native in source.get("lookup", {}).items():
                intent = native["intent"] if isinstance(native, dict) else native[1]
                text = native["utt"] if isinstance(native, dict) else native[0]
                if intent in public_intents.get(group, set()):
                    matches.append({"requested_group": group, "source": source_id, "source_id": ident, "original_split": split,
                        "original_intent": intent, "text_sha256": text_hash(text), "native_row_sha256": sha_json(native),
                        "status": "PUBLIC_SOURCE_REVIEW_PROPOSAL_NO_RELABEL_OR_APPROVAL"})
            references.extend(sorted(matches, key=sha_json)[:8])
    synthetic, synthetic_groups = [], None
    origins = {"TASK": "task_read_subject_date_status", "MEMORY": "memory_read_subject", "PROMISE": "promise_read_person"}
    path = Path(root) / "quarantine/plan05-seed17.jsonl"
    if path.exists():
        from ml.nlu_v1.synthetic.generate import verify_generation_batch
        verify_generation_batch(root, "plan05-seed17")
        snapshot = json.loads((Path(root) / "raw/plan05-seed17.groups.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
        synthetic_groups = snapshot["families"], snapshot["scenarios"]
        # Reuse the immutable Plan 05 seed group, including positive parents and
        # their boundaries. No historical template or generated row is edited.
        synthetic = [row for row in read_jsonl(path) if row["metadata"]["structure"]["origin_family"] in {origins[key] for key in missing if key in origins}]
    template_proposals = [
        {"family": "goal_read", "domain": "GOAL", "action": "READ", "disposition": "SUPPORTED_COMPLETE", "required_slots": [], "canonical": "show my goals"},
        {"family": "project_read", "domain": "PROJECT", "action": "READ", "disposition": "SUPPORTED_COMPLETE", "required_slots": [], "canonical": "show my projects"},
        {"family": "planning_plan", "domain": "PLANNING", "action": "PLAN", "disposition": "SUPPORTED_COMPLETE", "required_slots": [], "canonical": "plan my day"},
        {"family": "focus_start", "domain": "FOCUS", "action": "START", "disposition": "SUPPORTED_COMPLETE", "required_slots": ["DURATION"], "canonical": "start a focus session for {duration}"},
        {"family": "habit_read", "domain": "HABIT", "action": "READ", "disposition": "SUPPORTED_COMPLETE", "required_slots": [], "canonical": "show my habits"},
        {"family": "preference_set", "domain": "PREFERENCE", "action": "SET", "disposition": "SUPPORTED_COMPLETE", "required_slots": ["FIELD", "PREFERENCE_VALUE"], "canonical": "set my preferred {field} to {preference_value}"},
        {"family": "knowledge_read", "domain": "KNOWLEDGE", "action": "READ", "disposition": "SUPPORTED_COMPLETE", "required_slots": ["SUBJECT"], "canonical": "what is {subject}"}]
    return {"public_references": references, "controlled_candidates": synthetic, "groups": synthetic_groups,
        "plan": {"status": "ADDITION_REVIEW_ONLY_NOT_ELIGIBLE_TRAINING", "public_candidate_references": len(references),
            "controlled_plan05_candidates": len(synthetic), "controlled_source_rights_review_pending": True,
            "new_template_proposals": [spec for spec in template_proposals if bucket(spec) in missing],
            "template_proposal_state": "STRUCTURE_FIRST_PROPOSALS_REQUIRE_NEW_TEMPLATE_SOURCE_VERSION_AND_BOUNDARIES_BEFORE_GENERATION",
            "historical_templates_modified": False, "source_intake_approvals_created": 0}}


def run(rows, *, evidence=None, embeddings=None, embedding_blocker="APPROVED_EMBEDDING_ARTIFACT_MISSING"):
    load_policy()
    from ml.nlu_v1.context_contract import check as verify_contracts
    verify_contracts()
    rows = list(rows)
    evidence = evidence or PublicEvidence()
    groups = load_groups()
    composite_policy = yaml.load(FAMILY_POLICY.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if composite_policy["policy_version"] != VERSION or set(composite_policy["families"]) & set(groups[0]["families"]):
        raise DataError("MACHINE_COMPOSITE_FAMILY_REGISTRY_COLLISION")
    groups[0]["families"].update(copy.deepcopy(composite_policy["families"]))
    revised, local_reasons, family_assignments = [], [], 0
    for row in rows:
        updated, reasons, groups, assigned = curate_row(row, groups, evidence)
        revised.append(updated)
        local_reasons.append(reasons)
        family_assignments += assigned
    required = load_config()["required_groups"]
    candidate_rows = [row for row in rows if not official(row) and row["partition"] not in ("PROTECTED_EVALUATION", "FINAL_INDEPENDENT_TEST") and row["source"]["source_class"] == "PUBLIC_DATASET"]
    missing_input = sorted(set(required) - {bucket(row) for row in candidate_rows})
    additions = prepare_additions(missing_input, evidence, root=evidence.root)
    if additions["groups"]:
        merged = copy.deepcopy(groups)
        for index, field in ((0, "families"), (1, "scenarios")):
            for key, value in additions["groups"][index][field].items():
                if key in merged[index][field] and merged[index][field][key] != value:
                    raise DataError("ADDITION_GROUP_REGISTRY_COLLISION")
                merged[index][field][key] = value
        groups = merged
    all_audited, duplicate_report = audit(revised + additions["controlled_candidates"], embeddings, groups=groups)
    audited = all_audited[:len(revised)]
    additions["controlled_candidates"] = all_audited[len(revised):]
    duplicate_indices = set()
    for match in duplicate_report["matches"]:
        duplicate_indices.update(match["indices"])
    resolved = [row for row in all_audited if row["family"] and row["scenario_id"] and row["semantic_parent_id"]]
    # Existing partition memberships only. No tuning assignments are proposed
    # before approvals, privacy rights and embeddings pass.
    assignment = {row["id"]: row["partition"] if row["partition"] != "UNASSIGNED" else INELIGIBLE for row in resolved}
    leakage = leakage_report(resolved, assignment) if resolved else {"status": "NO_RESOLVED_GROUPS", "leakage_passed": False, "findings": []}
    components = connected_groups(resolved) if len({r["id"] for r in resolved}) == len(resolved) else []
    leaked_ids = {ident for finding in leakage["findings"] for ident in finding["row_ids"]}
    auto, edge_rows, queues, excluded = [], [], defaultdict(list), []
    readiness = []
    for index, row in enumerate(audited):
        reasons = set(local_reasons[index])
        if index in duplicate_indices:
            reasons.add("DUPLICATE_SKELETON_SOURCE_ID_OR_SIMILARITY_REVIEW")
        if row["id"] in leaked_ids:
            reasons.add("EXISTING_PARTITION_LEAKAGE")
        if "PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED" in reasons:
            excluded.append({"id": row["id"], "source": row["source"]["dataset"], "original_split": row["source"]["original_split"],
                "facts_sha256": facts_hash(row), "reason": "PROTECTED_OR_NONPUBLIC_CANDIDATE_EXCLUDED"})
            continue
        if reasons:
            edge_rows.append(row)
            queues[(tuple(sorted(reasons)), row["source"]["dataset"], row["source"]["original_intent"],
                    row["family"] or "UNRESOLVED", tuple(sorted({slot["type"] for slot in row["slots"]})))].append(row)
        else:
            auto.append(row)
            readiness.append({"id": row["id"], "queue_state": "AUTO_CURATED_READY_FOR_REVIEW", "facts_sha256": facts_hash(row),
                "state_event_sha256": row["review_history"][-1]["event_sha256"], "policy_version": VERSION,
                "remaining_gates": GLOBAL_BLOCKERS + ([embedding_blocker] if embedding_blocker else [])})
    manual = [{"queue_id": sha_json(list(key)), "reasons": list(key[0]), "source": key[1], "original_intent": key[2],
               "row_ids": [row["id"] for row in group], "sample": [{"id": row["id"], "text": row["text"], "domain": row["domain"], "action": row["action"], "slots": row["slots"]} for row in group[:3]],
               "expand_all_rows_before_any_human_approval": True} for key, group in sorted(queues.items())]
    coverage = {"input": full_coverage(candidate_rows, groups), "machine_ready": full_coverage(auto, groups), "all_processed": full_coverage([row for row in audited if row["id"] not in {item["id"] for item in excluded}], groups),
        "addition_candidates": full_coverage(additions["controlled_candidates"], groups)}
    missing = {"report_version": VERSION, "missing_input_groups": missing_input,
        "missing_machine_ready_groups": sorted(set(required) - set(coverage["machine_ready"]["groups"])),
        "public_source_order": ["PUBLIC_DATASET", "TRAINABLE_LEGACY", "CONTROLLED_SYNTHETIC", "HARD_NEGATIVE"],
        "approved_public_intake_sources": [key for key, value in evidence.sources.items() if value["observation"].get("intake_approval_id")],
        "public_addition_state": "VERIFIED_ACQUISITIONS_REQUIRE_INTAKE_APPROVAL_BEFORE_ADDITIONS_CAN_BE_ELIGIBLE"}
    missing["additions"] = additions["plan"]
    blockers = GLOBAL_BLOCKERS + ([embedding_blocker] if embedding_blocker else [])
    if len(resolved) != len(all_audited):
        blockers.append("UNRESOLVED_OR_AMBIGUOUS_GROUPS")
    if not duplicate_report["audit_complete"]:
        blockers.append("COMPLETE_EMBEDDING_LEAKAGE_AUDIT_REQUIRED")
    if duplicate_indices or leakage["findings"]:
        blockers.append("DUPLICATE_OR_LEAKAGE_REVIEW_REQUIRED")
    if missing["missing_machine_ready_groups"]:
        blockers.append("MISSING_CAPABILITY_COVERAGE")
    report = {"policy_version": VERSION, "input_rows": len(rows), "machine_auto_curated_rows": len(auto), "approved_rows": 0,
        "manual_edge_case_rows": len(edge_rows), "manual_queue_entries": len(manual), "family_assignments": family_assignments,
        "protected_or_nonpublic_excluded_rows": len(excluded),
        "privacy_evidence_bound_rows": sum(row["privacy"] == "PUBLIC" and bool(row["privacy_review"]) for row in audited),
        "disposition_corrections": sum(row["metadata"].get("machine_curation", {}).get("disposition_corrected", False) for row in audited),
        "canonical_quarantined_rows": sum(row["review_status"] == "QUARANTINED" and row["id"] not in {item["id"] for item in excluded} for row in audited),
        "rejected_rows": sum(row["review_status"] == "REJECTED" and row["id"] not in {item["id"] for item in excluded} for row in audited),
        "manual_reason_counts": dict(sorted(Counter(reason for key, values in queues.items() for reason in key[0] for _ in values).items())),
        "global_approval_queue_rows": len(rows) - len(excluded), "human_review_events_created": 0,
        "resolved_components": len(components), "audited_candidate_additions": len(additions["controlled_candidates"]),
        "group_leakage_audit_complete": len(resolved) == len(all_audited) and bool(leakage.get("leakage_passed")),
        "blockers": sorted(set(blockers)), "dataset_v0_frozen": False, "plan07_complete": False, "can_start_plan08": False}
    return {"rows": audited, "auto_curated": auto, "manual_review_queue": manual, "edge_rows": edge_rows,
        "readiness": readiness, "groups": groups, "curation_report": report, "coverage_report": coverage,
        "missing_coverage_report": missing, "dedupe_report": duplicate_report, "leakage_report": leakage, "additions": additions,
        "excluded": excluded}


def refuse_freeze(result):
    if result["curation_report"]["blockers"] or result["curation_report"]["approved_rows"] == 0:
        raise DataError("MACHINE_QUEUE_IS_NOT_AN_APPROVED_DATASET_FREEZE")


def write(result, destination):
    destination = Path(destination)
    if destination.exists():
        raise DataError("MACHINE_CURATION_RUN_ALREADY_EXISTS")
    resolved = destination.resolve()
    for protected in (ROOT / "archive", ROOT / "datasets/legacy", ROOT / "app", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions"):
        if resolved == protected.resolve() or protected.resolve() in resolved.parents:
            raise DataError("PROTECTED_CURATION_LOCATION")
    files = {"auto_curated.jsonl": jsonl_bytes(result["auto_curated"]), "manual_review_queue.jsonl": jsonl_bytes(result["manual_review_queue"]),
        "quarantined.jsonl": jsonl_bytes([row for row in result["rows"] if row["review_status"] in ("QUARANTINED", "REJECTED") and row["id"] not in {item["id"] for item in result["excluded"]}]),
        "excluded_protected_refs.jsonl": jsonl_bytes(result["excluded"]),
        "auto_review_readiness.jsonl": jsonl_bytes(result["readiness"]),
        "group_registry.json": json_bytes({"families": result["groups"][0], "scenarios": result["groups"][1]}),
        **{name + ".json": json_bytes(result[name]) for name in ("curation_report", "coverage_report", "missing_coverage_report", "dedupe_report", "leakage_report")},
        "machine_policy.yaml": POLICY.read_bytes(), "machine_families.yaml": FAMILY_POLICY.read_bytes(),
        "machine_curation.py": Path(__file__).read_bytes()}
    files.update({"public_addition_review_references.jsonl": jsonl_bytes(result["additions"]["public_references"]),
        "controlled_addition_candidates.jsonl": jsonl_bytes(result["additions"]["controlled_candidates"]),
        "candidate_addition_plan.json": json_bytes(result["additions"]["plan"])})
    manifest = {"manifest_version": VERSION, "status": "MACHINE_CURATED_QUEUE_NOT_FROZEN", "input_sha256": result["dedupe_report"]["input_sha256"],
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}, "training_authorized": False,
        "final_independent": "RESERVED_NOT_FABRICATED", "dataset_v0_frozen": False,
        "code_sha256": {name: file_hash(ROOT / "ml/nlu_v1" / name) for name in ("dataset_v0/machine_curation.py", "data_management/records.py", "data_management/dedupe.py", "splitting/split.py")}}
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        atomic_new(destination / name, data)
    atomic_new(destination / "curation_manifest.json", json_bytes(manifest))
    return manifest


def verify_run(destination):
    destination = Path(destination)
    manifest = json.loads((destination / "curation_manifest.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    expected = {"auto_curated.jsonl", "manual_review_queue.jsonl", "quarantined.jsonl", "excluded_protected_refs.jsonl", "auto_review_readiness.jsonl",
        "group_registry.json", "curation_report.json", "coverage_report.json", "missing_coverage_report.json", "dedupe_report.json", "leakage_report.json",
        "machine_policy.yaml", "machine_families.yaml", "machine_curation.py", "public_addition_review_references.jsonl", "controlled_addition_candidates.jsonl", "candidate_addition_plan.json"}
    if manifest.get("manifest_version") != VERSION or manifest.get("status") != "MACHINE_CURATED_QUEUE_NOT_FROZEN" or manifest.get("training_authorized") is not False or manifest.get("dataset_v0_frozen") is not False or manifest.get("final_independent") != "RESERVED_NOT_FABRICATED" or set(manifest.get("files", {})) != expected:
        raise DataError("MACHINE_CURATION_MANIFEST_INVALID")
    if {path.name for path in destination.iterdir()} != expected | {"curation_manifest.json"}:
        raise DataError("UNDECLARED_CURATION_ARTIFACT")
    for name, digest in manifest["files"].items():
        path = destination / name
        if path.resolve().parent != destination.resolve() or file_hash(path) != digest:
            raise DataError("MACHINE_CURATION_ARTIFACT_CHANGED")
    auto = read_jsonl(destination / "auto_curated.jsonl")
    quarantined = read_jsonl(destination / "quarantined.jsonl")
    report = json.loads((destination / "curation_report.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    manual = [json.loads(line, object_pairs_hook=unique_keys) for line in (destination / "manual_review_queue.jsonl").read_bytes().splitlines()]
    if len(auto) != report["machine_auto_curated_rows"] or len(manual) != report["manual_queue_entries"] or sum(len(item["row_ids"]) for item in manual) != report["manual_edge_case_rows"] or len(auto) + report["manual_edge_case_rows"] + report["protected_or_nonpublic_excluded_rows"] != report["input_rows"]:
        raise DataError("MACHINE_CURATION_QUEUE_COUNTS_INVALID")
    if report["approved_rows"] != 0 or report["human_review_events_created"] != 0 or report["dataset_v0_frozen"] is not False or report["can_start_plan08"] is not False:
        raise DataError("MACHINE_PATH_CANNOT_DECLARE_APPROVAL_OR_TRAINING_READY")
    if any(event["next"] in ("HUMAN_REVIEWED", "APPROVED") and event["actor"].startswith("machine:") for row in auto + quarantined for event in row["review_history"]):
        raise DataError("MACHINE_IMPERSONATED_HUMAN_REVIEW")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DATASETS / "quarantine/plan07_public_candidates.jsonl")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--verify", type=Path)
    parser.add_argument("--evidence-root", type=Path, default=DATASETS)
    parser.add_argument("--embeddings", type=Path)
    parser.add_argument("--embedding-approval", type=Path)
    args = parser.parse_args()
    if args.check:
        load_policy()
        from ml.nlu_v1.context_contract import check as verify_contracts
        verify_contracts()
        print("PASS: machine queue-only curation policy; frozen schema history unchanged")
        return
    if args.verify:
        print(json.dumps(verify_run(args.verify), sort_keys=True))
        return
    if not args.output:
        parser.error("--output is required for a new curation run")
    embeddings, blocker = approved_embeddings(args.embeddings, args.embedding_approval)
    result = run(read_jsonl(args.input), evidence=PublicEvidence(args.evidence_root), embeddings=embeddings, embedding_blocker=blocker)
    write(result, args.output)
    print(json.dumps(result["curation_report"], sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, ValidationError) as error:
        code = str(error) if isinstance(error, DataError) else type(error).__name__
        raise SystemExit("Machine curation rejected: " + code) from None
