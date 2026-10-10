#!/usr/bin/env python3
"""Build a deterministic, synthetic, family-split intent corpus for Phase 15.

All phrases and slots are fictional and authored here. Variations from one
template family stay in the same split so the classifier never sees a close
paraphrase of a held-out family during fitting.
"""

from __future__ import annotations

import hashlib
import json
import re
from difflib import SequenceMatcher
from pathlib import Path

from corpus import FIXED_TIME, FIXED_ZONE, atomic_text, sha256, validate, write_csv, write_native_input


SEED = 1501
VARIANTS = 10
VERSION = "metis-p15-synthetic-v2"

TOPICS = [
    "the lab report", "my project notes", "the reading", "the course form",
    "the outline", "the draft", "the practice set", "the design review",
    "the budget sheet", "the study plan",
]
PEOPLE = ["Ravi", "Mina", "Arun", "Leela", "Dev", "Nila", "Ira", "Kiran", "Asha", "Neel"]
TITLES = [
    "review the lab notes", "send the project outline", "check the draft",
    "prepare the practice set", "read the course brief", "update the budget sheet",
    "finish the study plan", "review the design", "submit the form", "organize the notes",
]
DATES = ["tomorrow", "today", "Friday", "next Monday", "October 18", "the 20th", "in two days", "Saturday", "November 2", "next week"]
TIMES = ["8 am", "9:30 am", "2 pm", "16:00", "6:15 pm", "07:00", "noon", "3:45 pm", "18:00", "10 am"]
DURATIONS = ["1 second", "5 seconds", "10 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes", "15 minutes", "20 minutes"]
TAILS = ["", " please", " when you can", " for me", " please?", " when possible", " now", " thanks", " if possible", ""]
TASK_ITEMS = ["my tasks", "my pending tasks", "my open tasks", "my unfinished tasks",
              "my current tasks", "my outstanding tasks", "my work items", "my to-dos",
              "my remaining tasks", "my incomplete tasks"]
TASK_LISTS = ["my task list", "my to-do list", "my list of tasks", "my pending task list",
              "my work queue", "my list of open tasks", "my unfinished task list",
              "my outstanding task list", "my current task list", "my remaining task list"]
CITIES = ["Pune", "Mysuru", "Chennai", "Kochi", "Jaipur", "Delhi", "Indore", "Nagpur", "Surat", "Patna"]
OBJECTS = ["a solar panel", "a bicycle", "a telescope", "a camera", "a water pump",
           "a keyboard", "a radio", "a coffee machine", "a satellite", "a compass"]
FOODS = ["rice", "potatoes", "lentils", "carrots", "oats", "spinach", "tomatoes", "beans", "bread", "peas"]
GENRES = ["jazz", "pop", "classical", "folk", "rock", "blues", "ambient", "piano", "dance", "instrumental"]
ROOMS = ["kitchen", "bedroom", "hall", "study", "office", "lounge", "garage", "porch", "workshop", "balcony"]

FAMILIES: dict[str, list[str]] = {
    "CHECK_TASKS": [
        "show {task_items}", "what is on {task_list}", "list {task_items}", "display {task_list}",
        "bring up {task_list}", "let me see {task_items}", "read out {task_list}", "open {task_list}",
        "give me an overview of {task_items}", "which items remain in {task_list}",
        "what work is still pending in {task_list}", "can you pull up {task_list}",
        "i want to review {task_items}", "tell me what is outstanding in {task_list}",
        "could i see {task_list}", "what remains in {task_list}",
        "show me {task_list}", "which tasks are still open in {task_list}",
        "give me {task_list}", "let me check {task_items}",
    ],
    "CHECK_MEMORY": [
        "what do you know about {topic}", "look up {topic} in my saved notes",
        "search my notes for {topic}", "find what i saved about {topic}",
        "recall the details about {topic}", "show my stored information on {topic}",
        "retrieve my note about {topic}", "what have i recorded about {topic}",
        "check my memory for {topic}", "bring back the saved facts about {topic}",
        "find my old note on {topic}", "tell me what i previously stored about {topic}",
        "look in memory for {topic}", "can you find my saved detail about {topic}",
        "what information did i save for {topic}", "retrieve the stored note mentioning {topic}",
        "search my personal records for {topic}", "show the memory entry about {topic}",
        "what facts are saved under {topic}", "find my earlier details concerning {topic}",
    ],
    "WHAT_DID_I_PROMISE": [
        "what did i promise {person}", "remind me what i said i would do for {person}",
        "what commitment did i make to {person}", "did i agree to anything with {person}",
        "recall my promise to {person}", "what did i tell {person} i would handle",
        "show the promise i recorded for {person}", "what was i going to do for {person}",
        "find my commitment involving {person}", "what have i pledged to {person}",
        "check whether i owe {person} something", "what did i say i would take care of for {person}",
        "look up my promise involving {person}", "did i make a commitment to {person}",
        "tell me what i agreed to do for {person}", "what did i undertake for {person}",
        "check my recorded commitments to {person}", "what did i assure {person} i would do",
        "find what i owe {person}", "what responsibility did i accept from {person}",
    ],
    "CHECK_CALENDAR": [
        "when am i free {date}", "show my calendar for {date}",
        "what is scheduled on {date}", "check my appointments for {date}",
        "do i have anything booked {date}", "find an open time {date}",
        "what does my day look like {date}", "list my events for {date}",
        "am i available {date}", "show the gaps in my schedule {date}",
        "check availability on {date}", "what meetings do i have {date}",
        "pull up my agenda for {date}", "is there room in my schedule {date}",
        "tell me my free time on {date}", "check what is in my diary {date}",
        "show which hours are open {date}", "find my meetings scheduled {date}",
        "what is my availability like {date}", "look at my plans for {date}",
    ],
    "CREATE_REMINDER": [
        "remind me {date} at {time} to {title}",
        "set a reminder for {date} {time} about {title}",
        "notify me {date} at {time} to {title}",
        "make sure i remember {title} on {date} at {time}",
        "put a reminder on {date} at {time} for {title}",
        "add a reminder {date} {time} to {title}",
        "schedule a notification for {date} at {time} about {title}",
        "i need a reminder {date} at {time} for {title}",
        "help me remember to {title} {date} at {time}",
        "alert me {date} at {time} to {title}",
        "create a reminder so i {title} {date} at {time}",
        "save a reminder {date} {time} for {title}",
        "please remind me to {title} at {time} {date}",
        "set up an alert {date} at {time} to {title}",
        "make a notification for {title} {date} at {time}",
        "put {title} on my reminders for {date} at {time}",
        "i want an alert to {title} {date} at {time}",
        "schedule me a reminder about {title} {date} at {time}",
        "at {time} {date}, remind me about {title}",
        "save an alert for {title} at {time} on {date}",
    ],
    "CREATE_TASK": [
        "add {title} to my task list", "i need to {title}",
        "make a task to {title}", "put {title} on my to do list",
        "track {title} as work to do", "create an item to {title}",
        "save {title} as a task", "i have work to do: {title}",
        "include {title} in my pending work", "record a task to {title}",
        "add a to do to {title}", "add a task so i can {title}",
        "make sure {title} is on my list", "write down {title} as something to do",
        "put a new task in to {title}", "i want to add {title} to my workload",
        "track that i need to {title}", "record {title} as unfinished work",
        "create a to do item to {title}", "add a new work item: {title}",
    ],
    "CREATE_TIMER": [
        "start a timer for {duration}", "time {duration} for me",
        "count down {duration}", "begin a {duration} countdown",
        "set the timer to {duration}", "run a timer lasting {duration}",
        "please time {duration}", "start counting down for {duration}",
        "i need a {duration} timer", "set up a countdown of {duration}",
        "begin timing {duration}", "use a timer that runs for {duration}",
        "start timing me for {duration}", "make the timer last {duration}",
        "count down for {duration} starting now", "run a countdown timer for {duration}",
        "please start timing {duration}", "set a countdown lasting {duration}",
        "time this session for {duration}", "begin a timer that lasts {duration}",
    ],
    "UNKNOWN": [
        "translate the words {topic} into French", "find a recipe using {food}",
        "explain how {object} works", "write a poem about {city}",
        "what is the weather in {city}", "play some {genre} music for me",
        "open a website about {object}", "calculate the square root of {number}",
        "tell me a joke about {object}", "turn on the {room} lights",
        "book a flight to {city}", "summarize this article about {object}",
        "find a nearby cafe in {city}", "generate an image of {object}",
        "what is the population of {city}", "recommend {genre} albums",
        "search the web for news on {city}", "convert {number} kilometers to miles",
        "draft an email to {person}", "explain the meaning of the words {topic}",
    ],
}


def grouped_templates(label: str, templates: list[str]) -> dict[int, str]:
    """Keep obvious near-duplicate template stems together before assigning splits."""
    def canonical(template: str) -> str:
        template = re.sub(r"\b(?:show|list|display)\b", "inspect", template)
        template = re.sub(r"\b(?:create|make|add)\b", "create", template)
        return re.sub(r"\bplease\s+", "", template)

    parents = list(range(len(templates)))
    def root(index: int) -> int:
        while parents[index] != index:
            index = parents[index]
        return index
    for index, first in enumerate(templates):
        for other, second in enumerate(templates[:index]):
            if SequenceMatcher(None, canonical(first), canonical(second), autojunk=False).ratio() >= 0.9:
                parents[root(index)] = root(other)
    groups = {}
    for index in range(len(templates)):
        members = [templates[item] for item in range(len(templates)) if root(item) == root(index)]
        digest = hashlib.sha256("\n".join(sorted(members)).encode()).hexdigest()[:10]
        groups[index] = f"{label.lower()}-{digest}"
    return groups


def row_text(label: str, template: str, variant: int) -> str:
    data = {
        "topic": TOPICS[variant],
        "person": PEOPLE[variant],
        "date": DATES[variant],
        "time": TIMES[variant],
        "title": TITLES[variant],
        "duration": DURATIONS[variant],
        "number": str(variant + 2),
        "task_items": TASK_ITEMS[variant], "task_list": TASK_LISTS[variant],
        "city": CITIES[variant], "object": OBJECTS[variant], "food": FOODS[variant],
        "genre": GENRES[variant], "room": ROOMS[variant],
    }
    text = template.format(**data)
    if label in {"CHECK_TASKS", "CHECK_MEMORY", "WHAT_DID_I_PROMISE", "CHECK_CALENDAR"}:
        text += TAILS[variant]
    elif label == "UNKNOWN" and variant % 4 == 0:
        text += "?"
    elif label in {"CREATE_REMINDER", "CREATE_TASK", "CREATE_TIMER"} and variant % 3 == 0:
        text = "please " + text
    return text.replace("on today", "today").replace("on tomorrow", "tomorrow").replace("on next ", "next ")


def build(output: Path) -> None:
    rows = []
    for label, templates in FAMILIES.items():
        if len(templates) != 20:
            raise ValueError(f"Expected 20 templates for {label}")
        groups = grouped_templates(label, templates)
        ordered = sorted(set(groups.values()), key=lambda value: hashlib.sha256(f"{SEED}:{value}".encode()).hexdigest())
        train_end = round(len(ordered) * 0.70)
        validation_end = train_end + round(len(ordered) * 0.15)
        split_by_group = {family: ("train" if index < train_end else "validation" if index < validation_end else "test")
                          for index, family in enumerate(ordered)}
        for family_index, template in enumerate(templates):
            family_id = groups[family_index]
            split = split_by_group[family_id]
            for variant in range(VARIANTS):
                utterance = row_text(label, template, variant)
                digest = hashlib.sha256(f"{VERSION}:{label}:{utterance}".encode()).hexdigest()[:16]
                outcome = "reject" if label == "UNKNOWN" else (
                    "propose" if label in {"CREATE_REMINDER", "CREATE_TASK", "CREATE_TIMER"} else "execute_eligible")
                rows.append({
                    "id": f"p15v2-{digest}", "intent": label, "utterance": utterance,
                    "language": "en-IN", "style": "normal", "source": "synthetic", "noise_type": "none",
                    "entities": "[]", "difficulty": "medium", "split": split, "family_id": family_id,
                    "expected_outcome": outcome, "case_kind": "intent_only", "input_source": "TEXT",
                    "timestamp": FIXED_TIME, "zone": FIXED_ZONE, "expected_status": "",
                })
    # Deduplicate repeated generated variants within the same split/label; conflicts fail validation.
    unique = {}
    for row in rows:
        key = (row["intent"], row["utterance"].casefold().strip())
        previous = unique.setdefault(key, row)
        if previous["split"] != row["split"]:
            raise ValueError("Generated duplicate would cross splits")
    rows = list(unique.values())
    write_csv(output, rows)
    from safety_cases import build_safety
    safety = build_safety()
    safety_path = output.with_name("safety-v2.csv")
    write_csv(safety_path, safety, require_splits=False)
    write_native_input(output.with_name("rules-input-v2.tsv"), rows + safety)
    manifest = {"version": VERSION, "seed": SEED, "synthetic_only": True,
                "method": "Template families, near-duplicate stem components before approximately 70/15/15 family split; integer rounding per label",
                "data": validate(rows), "safety_rows": len(safety),
                "hashes": {path.name: sha256(path) for path in (
                    output, safety_path, output.with_name("rules-input-v2.tsv"), Path(__file__),
                    Path(__file__).with_name("safety_cases.py"), Path(__file__).with_name("corpus.py"))},
                "evaluation_limit": "Exploratory v1 was inspected during development; v2 is a corrected synthetic experiment, not an independent blind benchmark."}
    atomic_text(output.with_name("manifest-v2.json"), json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    build(Path(__file__).with_name("intent-v2.csv"))
