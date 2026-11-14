#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Restore pinned binary dependencies (Python 3.12+)."""
import hashlib
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools' / 'downloads'
CACHE.mkdir(parents=True, exist_ok=True)

def download(name, url, expected):
    dest = CACHE / name
    if not dest.exists():
        temp = dest.with_suffix('.part')
        request = urllib.request.Request(url, headers={'User-Agent': 'GlosFacet-build/0.1'})
        with urllib.request.urlopen(request, timeout=60) as response, temp.open('wb') as out:
            shutil.copyfileobj(response, out)
        temp.replace(dest)
    with dest.open('rb') as source:
        actual = hashlib.file_digest(source, 'sha256').hexdigest()
    if actual != expected:
        raise RuntimeError(f'Checksum mismatch: {dest}. Remove it and retry.')
    return dest

library = download('sherpa-onnx-1.12.20.aar',
    'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.20/sherpa-onnx-1.12.20.aar',
    'e3905b1258b577d8d374db798618602ceaaff5805cbf9b356ab25267cd98144d')
libs = ROOT / 'app' / 'libs'
libs.mkdir(parents=True, exist_ok=True)
shutil.copy2(library, libs / library.name)
archive = download('darkman.tar.bz2',
    'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-darkman-medium.tar.bz2',
    '444727aa46eb6db645a2bc88fe73868e4bd7596b7f56ca39fad5ef53c41210d4')
target = ROOT / 'app' / 'src' / 'main' / 'assets' / 'voice'
with tempfile.TemporaryDirectory() as tmp:
    with tarfile.open(archive) as package:
        package.extractall(tmp, filter='data')
    shutil.copytree(Path(tmp) / 'vits-piper-pl_PL-darkman-medium', target, dirs_exist_ok=True)
print('Darkman and sherpa-onnx are ready.')
