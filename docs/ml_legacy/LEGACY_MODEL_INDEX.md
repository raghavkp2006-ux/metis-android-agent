# Legacy model index

Status: RETIRED. Raw evidence is private; no legacy model has been rerun.

## V4

Architecture: Word/character TF-IDF + logistic regression, numeric JSON export

Status: RETIRED (V16 original decision: NO_GO).

Generated Macro-F1 ~0.9911; later human benchmark ~0.6116.

Model artifact: 2 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 6 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v4/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V5

Architecture: Word/character TF-IDF + logistic regression with request-derived lexical features

Status: RETIRED (V16 original decision: NO_GO).

Generated Macro-F1 ~0.9133; approved human evaluation ~0.4328; 14 forbidden simulated reads.

Model artifact: 3 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 30 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v5/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V9

Architecture: Word/character TF-IDF + balanced logistic regression

Status: RETIRED (V16 original decision: NO_GO).

Selected synthetic holdout Macro-F1 ~0.4390; zero selected negative accepts; zero safe-read gain.

Model artifact: 1 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 12 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v9/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V11

Architecture: Flat five-label word/character TF-IDF + balanced logistic regression; optional lexical cues

Status: RETIRED (V16 original decision: NO_GO).

Experimental development generation; no new benchmark computed.

Model artifact: 2 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 15 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v11/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V12

Architecture: Hierarchical two-head word/character TF-IDF + logistic regression

Status: RETIRED (V16 original decision: NO_GO).

Experimental development generation; development queries exposed; no independent score claimed.

Model artifact: 2 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 27 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v12/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V14

Architecture: Reuses isolated V12 two-head word/character TF-IDF engine with optional lexical cues

Status: RETIRED (V16 original decision: NO_GO).

Experimental development generation; no new benchmark computed.

Model artifact: 2 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 30 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v14/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V16

Architecture: Word TF-IDF 1–2 grams + char_wb TF-IDF 3–5 grams + balanced logistic regression

Status: RETIRED (V16 original decision: NO_GO).

Development-test Macro-F1 0.4219298245614035; zero UNKNOWN false accepts on that test; 60 on synthetic challenge; six existing reads lost; zero safe gain; -12 incremental ML admissions; NO_GO.

Model artifact: 5 schema-confirmed historical exports; exact paths and selected settings in registry.

Training/evaluation scripts: 99 preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).

Source commit: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4` plus exact pre-retirement working bytes in manifest.

Artifact hashes: [SHA-256 index](../../archive/legacy_ml/v16/hashes.json).

Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.

## V16 selected settings

Seed 1515; C=1.0; class_weight=balanced; cues=false; cutoff=0.92; temperature=0.3620356161781817.

TRAIN 254; CALIBRATION 100; SELECTION 50; DEVELOPMENT_TEST 55; DEVELOPMENT_VALIDATION 55; SYNTHETIC_CHALLENGE 313.

Selection F1 0.3746778711 (30 known abstentions); raw development-test argmax F1 0.6412121212 (4 UNKNOWN false accepts); diagnostic validation F1 0.5568253968; synthetic challenge F1 0.4387792784.

Android ML adapter: NOT_AVAILABLE. Production ML: disabled. Per-class metrics/confusion matrices: [aggregate evidence](../../archive/legacy_ml/public/v16-aggregate-results.json). Row-level probabilities/logits are preserved only where recorded; no missing outputs were fabricated.
