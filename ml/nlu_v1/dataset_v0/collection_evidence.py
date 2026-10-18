"""Acquire authoritative collection papers once; verify pinned local bytes offline."""
import argparse
from collections import defaultdict
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request

from ml.nlu_v1.data_management.records import DataError
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from ml.nlu_v1.dataset_v0 import exception_packet as packet

FOLDER = DATASETS / 'raw/plan07-collection-evidence-003'
PAPERS = {
    'clinc': ('D19-1131', 'CLINC150 collection; sections 2.1–2.3, PDF page 2'),
    'massive': ('2023.acl-long.235', 'MASSIVE professional localization of SLURP; abstract and data creation'),
    'slurp': ('2020.emnlp-main.588', 'English text seed: prompted AMT commands; section 3.1, PDF page 2')}


class Citation(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = defaultdict(list)

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'meta' and values.get('name', '').startswith('citation_'):
            self.values[values['name']].append(values.get('content', ''))


def fetch(url):
    if urllib.parse.urlparse(url).hostname != 'aclanthology.org' or not url.startswith('https://'):
        raise DataError('NONAUTHORITATIVE_COLLECTION_EVIDENCE_URL')
    with urllib.request.urlopen(url, timeout=60) as response:
        if urllib.parse.urlparse(response.url).hostname != 'aclanthology.org':
            raise DataError('COLLECTION_EVIDENCE_REDIRECT_OUTSIDE_AUTHORITY')
        return response.read(), response.url


def acquire(extractor_python, folder=FOLDER):
    folder = Path(folder)
    if folder.exists() or (DATASETS / 'raw').resolve() not in folder.resolve().parents:
        raise DataError('NEW_LOCAL_COLLECTION_EVIDENCE_FOLDER_REQUIRED')
    folder.mkdir()
    records = {}
    for key, (anthology_id, relationship) in PAPERS.items():
        url = 'https://aclanthology.org/' + anthology_id + '/'
        html, resolved = fetch(url)
        citation = Citation()
        citation.feed(html.decode('utf-8'))
        metadata = citation.values
        pdf_url = metadata['citation_pdf_url'][0]
        if pdf_url != 'https://aclanthology.org/' + anthology_id + '.pdf':
            raise DataError('CANONICAL_PAPER_PDF_ID_MISMATCH')
        pdf, resolved_pdf = fetch(pdf_url)
        if not pdf.startswith(b'%PDF-') or not metadata['citation_author']:
            raise DataError('INVALID_COLLECTION_PAPER')
        atomic_new(folder / (key + '.html'), html)
        atomic_new(folder / (key + '.pdf'), pdf)
        records[key] = {'canonical_url': url, 'resolved_url': resolved, 'pdf_url': pdf_url,
                        'resolved_pdf_url': resolved_pdf, 'title': metadata['citation_title'][0],
                        'authors': metadata['citation_author'], 'publication': metadata['citation_conference_title'][0],
                        'publication_date': metadata['citation_publication_date'][0],
                        'retrieved_at_utc': datetime.now(timezone.utc).isoformat(),
                        'source_relationship': relationship, 'pdf_sha256': hashlib.sha256(pdf).hexdigest(),
                        'html_sha256': hashlib.sha256(html).hexdigest()}
    # Extract only downloaded papers, offline, using the installed document runtime.
    script = ('import json,sys; from pypdf import PdfReader; from importlib.metadata import version; '
              'from pathlib import Path; p=Path(sys.argv[1]); '
              'v={k:[page.extract_text() for page in PdfReader(p/(k+".pdf")).pages] for k in ("clinc","massive","slurp")}; '
              'print(json.dumps({"extractor":"pypdf","version":version("pypdf"),"pages":v},ensure_ascii=True))')
    result = subprocess.run([str(extractor_python), '-c', script, str(folder)], check=True, capture_output=True)
    extracted = json.loads(result.stdout)
    atomic_new(folder / 'paper_text.json', json_bytes(extracted))
    files = {p.name: packet.file_hash(p) for p in folder.iterdir() if p.is_file()}
    manifest = {'version': 'nlu_v1_collection_evidence_003', 'records': records, 'files': files,
                'dataset_rows_downloaded': False, 'privacy_approval': False}
    atomic_new(folder / 'manifest.json', json_bytes(manifest))
    return verify(folder)


def verify(folder=FOLDER):
    folder = Path(folder)
    manifest = packet.read_json(folder / 'manifest.json')
    if manifest.get('version') != 'nlu_v1_collection_evidence_003' or set(manifest['records']) != set(PAPERS):
        raise DataError('INVALID_COLLECTION_EVIDENCE_MANIFEST')
    if {p.name for p in folder.iterdir()} != set(manifest['files']) | {'manifest.json'}:
        raise DataError('COLLECTION_EVIDENCE_ARTIFACT_SET_CHANGED')
    for name, digest in manifest['files'].items():
        if Path(name).name != name or packet.file_hash(folder / name) != digest:
            raise DataError('COLLECTION_EVIDENCE_HASH_CHANGED')
    for key, record in manifest['records'].items():
        if record['pdf_sha256'] != manifest['files'][key + '.pdf'] or record['html_sha256'] != manifest['files'][key + '.html']:
            raise DataError('COLLECTION_EVIDENCE_RECORD_BINDING_CHANGED')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--acquire', action='store_true')
    parser.add_argument('--extractor-python', type=Path)
    args = parser.parse_args()
    if args.acquire and not args.extractor_python:
        parser.error('--acquire requires the installed PDF-runtime Python path')
    result = acquire(args.extractor_python) if args.acquire else verify()
    print(json.dumps({'papers': list(result['records']), 'verified_local_evidence': True}))


if __name__ == '__main__':
    main()
