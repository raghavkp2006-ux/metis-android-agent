"""Pinned, CPU-only offline sentence embeddings; no training or remote inference."""
import argparse
import hashlib
import json
from importlib.metadata import version
from pathlib import Path
import urllib.request

from ml.nlu_v1.dataset_v0.machine_curation import file_hash
from ml.nlu_v1.data_management.records import DataError, sha_json, text_hash
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from ml.nlu_v1.splitting.split import read_jsonl

MODEL = 'sentence-transformers/all-MiniLM-L6-v2'
REVISION = '10dbd2f06a8baf40ad285b037edda610c1b9a57c'
# Publisher Git/LFS hashes at the immutable revision, not trust-on-first-use.
FILES = {
    'onnx/model.onnx': ('sha256', '6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452'),
    'tokenizer.json': ('git', 'cb202bfe2e3c98645018a6d12f182a434c9d3e02'),
    'tokenizer_config.json': ('git', 'c79f2b6a0cea6f4b564fed1938984bace9d30ff0'),
    'special_tokens_map.json': ('git', 'e7b0375001f109a6b8873d756ad4f7bbb15fbaa5'),
    'config.json': ('git', '72b987fd805cfa2b58c4c8c952b274a11bfd5a00'),
    '1_Pooling/config.json': ('git', 'd1514c3162bbe87b343f565fadc62e6c06f04f03'),
    'README.md': ('git', '58d4a9a45664eb9e12de9549c548c09b6134c17f'),
}
CACHE = DATASETS / 'raw/similarity-model' / REVISION
VERSIONS = {'onnxruntime': '1.30.0', 'tokenizers': '0.22.2', 'numpy': '2.5.3'}


def verify_file(name, content):
    kind, expected = FILES[name]
    digest = hashlib.sha256(content).hexdigest() if kind == 'sha256' else hashlib.sha1(
        b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
    if digest != expected:
        raise DataError('PINNED_CHECKPOINT_HASH_MISMATCH')


def cache_model(cache=CACHE, *, download=False):
    cache = Path(cache)
    for name in FILES:
        path = cache / name
        if not path.exists():
            if not download:
                raise DataError('OFFLINE_CHECKPOINT_NOT_CACHED')
            content = urllib.request.urlopen(f'https://huggingface.co/{MODEL}/resolve/{REVISION}/{name}', timeout=120).read()
            verify_file(name, content)
            path.parent.mkdir(parents=True, exist_ok=True)
            atomic_new(path, content)
        verify_file(name, path.read_bytes())
    pooling = json.loads((cache / '1_Pooling/config.json').read_text())
    if pooling.get('pooling_mode_mean_tokens') is not True or pooling.get('pooling_mode_cls_token') is not False:
        raise DataError('UNEXPECTED_POOLING_CONFIGURATION')
    if 'license: apache-2.0' not in (cache / 'README.md').read_text(encoding='utf-8'):
        raise DataError('CHECKPOINT_LICENSE_METADATA_MISSING')
    return {name: file_hash(cache / name) for name in FILES}


def configuration():
    return {'model': MODEL, 'revision': REVISION, 'versions': VERSIONS, 'provider': 'CPUExecutionProvider',
            'threads': 1, 'execution': 'SEQUENTIAL', 'optimization': 'DISABLED', 'max_tokens': 256,
            'batch_size': 16, 'pooling': 'ATTENTION_MASKED_MEAN_THEN_L2', 'dtype': 'float32',
            'training': False, 'network_inference': False}


def embed(rows, cache=CACHE):
    # All network access is isolated in the explicit cache command.
    hashes = cache_model(cache)
    for package, expected in VERSIONS.items():
        if version(package) != expected:
            raise DataError('PINNED_EMBEDDING_RUNTIME_VERSION_MISMATCH')
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    if len({r['id'] for r in rows}) != len(rows) or any(r['text_sha256'] != text_hash(r['text']) for r in rows):
        raise DataError('EMBEDDING_COLLECTION_ID_OR_TEXT_INVALID')
    tokenizer = Tokenizer.from_file(str(Path(cache) / 'tokenizer.json'))
    tokenizer.enable_truncation(max_length=256)
    tokenizer.enable_padding(pad_id=0, pad_token='[PAD]')
    options = ort.SessionOptions()
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    session = ort.InferenceSession(str(Path(cache) / 'onnx/model.onnx'), sess_options=options, providers=['CPUExecutionProvider'])
    entries = []
    for start in range(0, len(rows), 16):
        batch = rows[start:start + 16]
        encodings = tokenizer.encode_batch([r['text'] for r in batch])
        arrays = {'input_ids': np.array([e.ids for e in encodings], dtype=np.int64),
                  'attention_mask': np.array([e.attention_mask for e in encodings], dtype=np.int64),
                  'token_type_ids': np.array([e.type_ids for e in encodings], dtype=np.int64)}
        feed = {item.name: arrays[item.name] for item in session.get_inputs()}
        tokens = session.run(None, feed)[0]
        mask = arrays['attention_mask'][..., None].astype(np.float32)
        pooled = (tokens * mask).sum(axis=1) / mask.sum(axis=1).clip(min=1)
        pooled /= np.linalg.norm(pooled, axis=1, keepdims=True).clip(min=1e-12)
        # Detect runtime nondeterminism by exact repeat, not fabricated vectors.
        repeated = session.run(None, feed)[0]
        if not np.array_equal(tokens, repeated) or not np.isfinite(pooled).all():
            raise DataError('EMBEDDING_NONDETERMINISTIC_OR_NONFINITE')
        entries.extend({'id': row['id'], 'text_sha256': row['text_sha256'], 'vector': vector.tolist()}
                       for row, vector in zip(batch, pooled))
    artifact = {'embedding_version': 'nlu_v1_embeddings_001', 'model_id': MODEL + '@' + REVISION,
                'model_sha256': hashes['onnx/model.onnx'],
                'tokenizer_sha256': sha_json({k: v for k, v in hashes.items() if 'token' in k}),
                'configuration_sha256': sha_json({'inference': configuration(), 'checkpoint_files': hashes}), 'rows': entries}
    receipt = {'kind': 'OFFLINE_SIMILARITY_EXECUTION', 'status': 'VERIFIED_MACHINE_RUN',
               'authorization': 'USER_REQUEST_PLAN07_PART_H', 'human_data_approval': False,
               'checkpoint_files_sha256': hashes, 'configuration': configuration(),
               'artifact_sha256': hashlib.sha256(json_bytes(artifact)).hexdigest(),
               'collection_binding_sha256': sha_json([[r['id'], r['text_sha256']] for r in rows]),
               'engine_sha256': file_hash(__file__), 'repeat_inference_exact_match': True}
    return artifact, receipt


def verify_binding(rows, artifact, receipt, cache=CACHE):
    hashes = cache_model(cache)
    if receipt.get('status') != 'VERIFIED_MACHINE_RUN' or receipt.get('human_data_approval') is not False or receipt.get('artifact_sha256') != hashlib.sha256(json_bytes(artifact)).hexdigest():
        raise DataError('EMBEDDING_RECEIPT_INVALID')
    if receipt.get('checkpoint_files_sha256') != hashes or receipt.get('configuration') != configuration():
        raise DataError('EMBEDDING_CHECKPOINT_OR_CONFIG_CHANGED')
    if receipt.get('collection_binding_sha256') != sha_json([[r['id'], text_hash(r['text'])] for r in rows]):
        raise DataError('EMBEDDING_COLLECTION_CHANGED')
    if artifact.get('model_sha256') != hashes['onnx/model.onnx'] or artifact.get('model_id') != MODEL + '@' + REVISION:
        raise DataError('EMBEDDING_MODEL_IDENTITY_CHANGED')
    if artifact.get('tokenizer_sha256') != sha_json({k: v for k, v in hashes.items() if 'token' in k}) or artifact.get('configuration_sha256') != sha_json({'inference': configuration(), 'checkpoint_files': hashes}):
        raise DataError('EMBEDDING_CONFIGURATION_BINDING_CHANGED')
    from ml.nlu_v1.data_management.dedupe import prepare_embeddings
    _, status, _, issues = prepare_embeddings(rows, artifact)
    if status != 'COMPLETE' or issues or len(artifact['rows']) != len(rows):
        raise DataError('EMBEDDING_ROWS_INCOMPLETE')
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=CACHE)
    parser.add_argument('--download', action='store_true')
    parser.add_argument('--input', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.download:
        print(json.dumps(cache_model(args.cache, download=True), sort_keys=True))
    elif args.input and args.output:
        artifact, receipt = embed(read_jsonl(args.input), args.cache)
        verify_binding(read_jsonl(args.input), artifact, receipt, args.cache)
        args.output.mkdir(parents=True, exist_ok=False)
        atomic_new(args.output / 'embeddings.json', json_bytes(artifact))
        atomic_new(args.output / 'embedding_receipt.json', json_bytes(receipt))
        print(json.dumps({'embedded_rows': len(artifact['rows']), 'determinism': 'EXACT_REPEAT_PASS'}))
    else:
        print(json.dumps(cache_model(args.cache), sort_keys=True))


if __name__ == '__main__':
    main()
