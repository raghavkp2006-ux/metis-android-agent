"""Offline reuse of Plan07's pinned MiniLM ONNX recipe and typed skeleton policy."""
import hashlib
from importlib.metadata import version
import json
from pathlib import Path

from ml.data.common import ROOT, clauses, normalize

EVIDENCE = ROOT / '.local-evidence/phase0/unrelated-datasets'
SNAPSHOT = EVIDENCE / 'quarantine/plan07-semantic-groups-002/snapshot'
REVISION = '10dbd2f06a8baf40ad285b037edda610c1b9a57c'
CACHE = EVIDENCE / 'raw/similarity-model' / REVISION
THRESHOLD = .95
PINS = {
    'onnx/model.onnx': ('sha256', '6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452'),
    'tokenizer.json': ('git', 'cb202bfe2e3c98645018a6d12f182a434c9d3e02'),
    'tokenizer_config.json': ('git', 'c79f2b6a0cea6f4b564fed1938984bace9d30ff0'),
    'special_tokens_map.json': ('git', 'e7b0375001f109a6b8873d756ad4f7bbb15fbaa5'),
    'config.json': ('git', '72b987fd805cfa2b58c4c8c952b274a11bfd5a00'),
    '1_Pooling/config.json': ('git', 'd1514c3162bbe87b343f565fadc62e6c06f04f03'),
    'README.md': ('git', '58d4a9a45664eb9e12de9549c548c09b6134c17f'),
}
VERSIONS = {'onnxruntime':'1.30.0', 'tokenizers':'0.22.2', 'numpy':'2.5.3'}


def skeleton(row):
    """Replace literal entity spans by typed placeholders, then NFKC/casefold/space.

    CSV has no offsets. Resolve repeated values leftmost, longest-first; overlapping
    values are skipped. No stemming or guessed spans. Record skeleton collisions.
    """
    text = row['text']
    spans = []
    values = {(key, value) for part in clauses(row) for key, value in part['entities'].items()}
    for key, value in sorted(values, key=lambda item: (-len(item[1]), item)):
        start = text.find(value)
        end = start + len(value)
        if start >= 0 and not any(start < b and a < end for a, b, _ in spans):
            spans.append((start, end, '{' + key.upper() + '}'))
    for start, end, placeholder in sorted(spans, reverse=True):
        text = text[:start] + placeholder + text[end:]
    return normalize(text)


def embed(texts, cache=CACHE):
    """Same verified CPU/one-thread/masked-mean/L2 recipe as preserved Plan07.

    Check every pinned byte and runtime before inference. Missing cache fails closed.
    No remote loading, package installation, fit, or tuning capability exists here.
    """
    for package, expected in VERSIONS.items():
        if version(package) != expected:
            raise ValueError('PINNED_EMBEDDING_RUNTIME_VERSION_MISMATCH: ' + package)
    for name, (kind, expected) in PINS.items():
        content = (Path(cache) / name).read_bytes()
        digest = hashlib.sha256(content).hexdigest() if kind == 'sha256' else hashlib.sha1(
            b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
        if digest != expected:
            raise ValueError('PINNED_CHECKPOINT_HASH_MISMATCH: ' + name)
    pooling = json.loads((Path(cache) / '1_Pooling/config.json').read_text())
    if pooling.get('pooling_mode_mean_tokens') is not True or pooling.get('pooling_mode_cls_token') is not False:
        raise ValueError('UNEXPECTED_POOLING_CONFIGURATION')
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    tokenizer = Tokenizer.from_file(str(Path(cache) / 'tokenizer.json'))
    tokenizer.enable_truncation(max_length=256)
    tokenizer.enable_padding(pad_id=0, pad_token='[PAD]')
    options = ort.SessionOptions()
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    session = ort.InferenceSession(str(Path(cache) / 'onnx/model.onnx'), sess_options=options,
                                   providers=['CPUExecutionProvider'])
    vectors = []
    for start in range(0, len(texts), 16):
        encodings = tokenizer.encode_batch(texts[start:start+16])
        arrays = {'input_ids': np.array([e.ids for e in encodings], dtype=np.int64),
                  'attention_mask': np.array([e.attention_mask for e in encodings], dtype=np.int64),
                  'token_type_ids': np.array([e.type_ids for e in encodings], dtype=np.int64)}
        feed = {item.name: arrays[item.name] for item in session.get_inputs()}
        tokens = session.run(None, feed)[0]
        if not np.array_equal(tokens, session.run(None, feed)[0]):
            raise ValueError('EMBEDDING_NONDETERMINISTIC')
        mask = arrays['attention_mask'][..., None].astype(np.float32)
        pooled = (tokens * mask).sum(axis=1) / mask.sum(axis=1).clip(min=1)
        pooled /= np.linalg.norm(pooled, axis=1, keepdims=True).clip(min=1e-12)
        if not np.isfinite(pooled).all():
            raise ValueError('NONFINITE_EMBEDDING')
        vectors.extend(pooled.tolist())
    return vectors


def cross_collisions(left, right, *, semantic=True):
    matches = []
    for i, a in enumerate(left):
        for j, b in enumerate(right):
            reasons = []
            if a['text'] == b['text']:
                reasons.append('exact')
            if normalize(a['text']) == normalize(b['text']):
                reasons.append('normalized')
            if skeleton(a) == skeleton(b):
                reasons.append('skeleton')
            if reasons:
                matches.append(dict(left=i+1, right=j+1, reasons=reasons))
    # A known collision already fails; do not require inference to prove that failure.
    if semantic and left and right and not matches:
        import math
        vectors = embed([r['text'] for r in left + right])
        for i in range(len(left)):
            for j in range(len(right)):
                cosine = max(-1., min(1., math.fsum(a*b for a,b in zip(vectors[i], vectors[len(left)+j]))))
                if cosine >= THRESHOLD:
                    matches.append(dict(left=i+1, right=j+1, reasons=['semantic'], cosine=cosine))
    return matches
