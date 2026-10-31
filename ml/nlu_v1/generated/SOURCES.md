# METIS NLU Dataset — External Sources & Licensing (`SOURCES.md`)

All external public datasets consulted during the design and phrasing synthesis of the METIS NLU training and development sets were audited for permissive open-source licensing prior to use. No rows were bulk-copied; instead, real phrasing patterns, code-mixed Roman-script Hindi structures, spoken ASR disfluencies, and out-of-scope query structures were rewritten, adapted to the student personal-assistant domain, and remapped to the 21 METIS labels. All personal identifiers were replaced with strictly fictional student/campus names.

## 1. Summary of Permissively Licensed Sources Used

| Source Tag (`notes`) | Dataset Name | Canonical URL | License | Access Date | Train Rows Influenced | Dev Rows Influenced | Total Rows Influenced |
| :--- | :--- | :--- | :--- | :--- | ---: | ---: | ---: |
| `web-derived:massive` | Amazon Science MASSIVE 1.1 (`en-US`, `hi-IN`) | https://huggingface.co/datasets/AmazonScience/massive | CC-BY-4.0 | 2026-10-30 | 401 | 30 | **431** |
| `web-derived:clinc150` | CLINC150 / OOS-Eval (`clinc/oos-eval`) | https://github.com/clinc/oos-eval | CC-BY-3.0 (Data) / Apache-2.0 (Code) | 2026-10-30 | 666 | 105 | **771** |
| `web-derived:hinglish_top` | Google Research Hinglish-TOP Dataset | https://github.com/google-research-datasets/Hinglish-TOP-Dataset | Apache-2.0 | 2026-10-30 | 130 | 30 | **160** |
| `web-derived:snips` | Snips NLU Benchmark (`snipsco/snips-nlu`) | https://github.com/snipsco/snips-nlu | Apache-2.0 / CC0-1.0 | 2026-10-30 | 18 | 0 | **18** |
| `web-derived:banking77` | PolyAI Banking77 (`PolyAI/banking77`) | https://huggingface.co/datasets/PolyAI/banking77 | CC-BY-4.0 | 2026-10-30 | 39 | 0 | **39** |
| `synthetic` | Native Student-Domain Synthesis (`SYNTH_GEM_*`) | N/A (Synthesized in-house for METIS) | Public Domain / MIT | 2026-10-30 | 1562 | 165 | **1727** |
| **Total** | **All Sources Combined** | — | — | **2026-10-30** | **2816** | **330** | **3146** |

## 2. How Each Source Informed Phrasing Patterns

1. **Amazon Science MASSIVE 1.1 (`web-derived:massive`)**:
   - **Repository / Paper**: FitzGerald et al. (2022), *MASSIVE: A 1M-Example Multilingual Natural Language Understanding Dataset with 51 Typologically-Diverse Languages* (https://github.com/alexa/massive and https://huggingface.co/datasets/AmazonScience/massive).
   - **License**: Creative Commons Attribution 4.0 International (**CC-BY-4.0**).
   - **Usage in METIS**: Informed natural single-shot voice assistant phrasing across `alarm_set` (`CREATE_ALARM`), `calendar_query` (`CHECK_CALENDAR`), `lists_query` (`CHECK_TASKS`), `lists_createoradd` (`CREATE_TASK`), `lists_remove` (`DELETE_TASK`), and `email_sendemail` / messaging (`SEND_MESSAGE`).

2. **CLINC150 / Out-of-Scope Evaluation (`web-derived:clinc150`)**:
   - **Repository / Paper**: Larson et al. (2019), *An Evaluation Dataset for Intent Classification and Out-of-Scope Prediction* (https://github.com/clinc/oos-eval and https://arxiv.org/html/1909.02027v1).
   - **License**: Creative Commons Attribution 3.0 Unported (**CC-BY-3.0**).
   - **Usage in METIS**: Informed short, unstructured command phrasing for `todo_list`, `todo_list_update`, `reminder`, `timer`, `alarm`, `text`, `make_call`, and crucially the out-of-scope (`UNKNOWN`) boundary (including destructive requests like *"nuke all items on my todo list"* -> remapped to `UNKNOWN` destructive asks, plus travel, dining, trivia, and small-talk queries).

3. **Google Research Hinglish-TOP Dataset (`web-derived:hinglish_top`)**:
   - **Repository**: Google Research Datasets (https://github.com/google-research-datasets/Hinglish-TOP-Dataset).
   - **License**: **Apache License 2.0**.
   - **Usage in METIS**: Informed Roman-script Hindi-English code-switched syntax (e.g., matrix-clause Hindi verbs like *laga do*, *yaad dilana*, *dikhao*, *shift kar do*, *bhej do* paired with English academic/technical noun phrases like *OS lab report*, *calendar*, *wake-up alarm*, *Pomodoro timer*).

4. **Snips NLU Benchmark (`web-derived:snips`)**:
   - **Repository**: Snips / Sonos Voice Experience Team (https://github.com/snipsco/snips-nlu and https://github.com/sonos/nlu-benchmark).
   - **License**: **Apache License 2.0 / CC0-1.0**.
   - **Usage in METIS**: Informed out-of-scope (`UNKNOWN`) media playback (`PlayMusic`, `AddToPlaylist`), weather forecast (`GetWeather`), and restaurant/ride booking queries that a student might mistakenly ask METIS.

5. **PolyAI Banking77 (`web-derived:banking77`)**:
   - **Repository**: Casanueva et al. (2020), *Efficient Intent Detection with Dual Sentence Encoders* (https://huggingface.co/datasets/PolyAI/banking77).
   - **License**: Creative Commons Attribution 4.0 International (**CC-BY-4.0**).
   - **Usage in METIS**: Informed out-of-scope (`UNKNOWN`) financial, wire transfer, currency exchange, and account balance queries.

## 3. Datasets Audited and Skipped Due to Restrictive Licenses

- **Amazon Science MASSIVE-AMR** (https://github.com/amazon-science/MASSIVE-AMR) — Licensed under **CC-BY-NC-4.0** (Non-Commercial restriction). **Skipped**.
- **Amazon Science WebIE** (https://github.com/amazon-science/webie) — Licensed under **CC-BY-NC-4.0** (Non-Commercial restriction). **Skipped**.
