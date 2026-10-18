# METIS NLU v1

Status: NOT_TRAINED. Architecture planned; implementation and labels are not frozen.
Legacy classifiers through V16 are retired historical evidence.

Target: tokenizer → semantic transformer encoder → shared representation with
domain classification, action classification, slot/entity extraction and OOD/UNKNOWN
heads. Structured meaning flows through Context Agent, deterministic validators,
Safety Agent, the specific agent and Verification Agent.

The model must never write storage, decide authorization, invent missing entities,
send messages, create tasks/events, or bypass safety and confirmation. Confidence
alone never authorizes execution. Existing deterministic app behavior remains active.

Next work: define and approve DOMAIN, ACTION, SLOT/entity labels, OOD/UNKNOWN
policy, clarification states, dataset schema and split policy before generating
training examples or fitting any model. No old classifier code is copied here.

Folders: configs, data, models, training, evaluation, export and tests are empty
reserved workspaces. `datasets/nlu_v1` holds the future data policy.

Every future run must preserve classification micro/macro/per-class metrics and
confusion matrices; UNKNOWN false accepts, known false rejects, OOD precision/recall/F1
and abstention; slot precision/recall/F1, exact spans and exact structured requests;
forbidden executions, valid requests lost, clarification accuracy and unsupported
operation rejection; model/APK sizes, p50/p95 latency, peak RAM, battery and cold-start
costs; seed, model/dataset/tokenizer/configuration hashes, Git commit and environment.
