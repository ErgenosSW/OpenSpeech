#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Download and verify the licensed, revision-pinned offline voice."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import urllib.request
import runpy
import struct

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def verify(path, artifact):
    if path.stat().st_size != artifact['bytes']:
        raise RuntimeError(f'Unexpected size: {path}')
    if digest(path) != artifact['sha256']:
        raise RuntimeError(f'Checksum mismatch: {path}. Remove it and retry.')


def download(cache, name, artifact):
    dest = cache / name
    if not dest.exists():
        part = dest.with_suffix(dest.suffix + '.part')
        try:
            request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'OpenSpeech-build'})
            with urllib.request.urlopen(request, timeout=120) as source, part.open('wb') as out:
                shutil.copyfileobj(source, out)
            verify(part, artifact)
            part.replace(dest)
        finally:
            part.unlink(missing_ok=True)
    verify(dest, artifact)
    return dest


def manifest(lock):
    result = {}
    for name in lock['voice']['files']:
        if Path(name).is_absolute() or '..' in Path(name).parts or name in ('.complete', 'manifest.json'):
            raise RuntimeError(f'Unsafe asset filename: {name}')
        item = lock['artifacts'][name]
        result[name] = {'bytes': item['bytes'], 'sha256': item['sha256']}
    return (json.dumps(result, sort_keys=True, indent=2) + '\n').encode()


def verify_native(lock):
    path = ROOT / lock['native']['sourceManifest']
    if digest(path) != lock['native']['sourceManifestSha256']:
        raise RuntimeError('Native source manifest differs from dependency lock.')
    expected = json.loads(path.read_text())
    actual = {p.relative_to(ROOT).as_posix() for p in (ROOT / 'third_party').rglob('*') if p.is_file()}
    if set(expected) != actual:
        raise RuntimeError('Native source file list differs from reviewed manifest.')
    for name, artifact in expected.items():
        if not name.startswith('third_party/') or '..' in Path(name).parts or (ROOT / name).is_symlink():
            raise RuntimeError(f'Unsafe native source path: {name}')
        verify(ROOT / name, artifact)


def verify_installed():
    lock = json.loads((ROOT / 'dependencies.lock.json').read_text())
    target = ROOT / 'app/src/main/assets/voice'
    expected = set(lock['voice']['files']) | {'manifest.json'}
    actual = {p.relative_to(target).as_posix() for p in target.rglob('*') if p.is_file()}
    if expected != actual:
        raise RuntimeError('Installed voice file list differs from dependency lock.')
    for name in lock['voice']['files']:
        if (target / name).is_symlink():
            raise RuntimeError(f'Unexpected asset symlink: {name}')
        verify(target / name, lock['artifacts'][name])
    if (target / 'manifest.json').read_bytes() != manifest(lock):
        raise RuntimeError('Installed voice manifest differs from dependency lock.')
    verify_native(lock)


def prepare():
    lock = json.loads((ROOT / 'dependencies.lock.json').read_text())
    if lock['voice'].get('redistributionStatus') != 'approved' or not lock['voice'].get('weightsLicense'):
        raise RuntimeError('Voice redistribution is unverified. No assets downloaded.')
    verify_native(lock)
    cache = ROOT / '.tools/downloads/voice'
    cache.mkdir(parents=True, exist_ok=True)
    files = {name: download(cache, name, lock['artifacts'][name]) for name in lock['voice']['files'] if 'url' in lock['artifacts'][name]}
    data = runpy.run_path(str(ROOT / 'scripts/build-phoneme-data.py'))['build']()
    for name in lock['voice']['files']:
        if name.startswith('espeak-ng-data/'):
            files[name] = data / Path(name).relative_to('espeak-ng-data')
    settings = json.loads(files['voices.json'].read_text())
    vector = [struct.unpack('<f',struct.pack('<f',a+b))[0] for a,b in zip(settings['speakers']['0']['emb'],settings['styles']['8']['emb'])]
    config = cache / 'voice.properties'
    config.write_text('temperature='+str(settings['defaults']['temperature'])+'\nlengthScale='+str(settings['defaults']['length_scale'])+'\nspeaker='+','.join(format(v,'.9g') for v in vector)+'\n')
    files['voice.properties'] = config
    for name, path in files.items():
        verify(path, lock['artifacts'][name])
    target = ROOT / 'app/src/main/assets/voice'
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=target.parent) as directory:
        stage = Path(directory) / 'new'
        stage.mkdir()
        for name, source in files.items():
            (stage / name).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, stage / name)
        (stage / 'manifest.json').write_bytes(manifest(lock))
        backup = Path(directory) / 'previous'
        if target.exists():
            target.rename(backup)
        try:
            stage.rename(target)
        except BaseException:
            if backup.exists():
                backup.rename(target)
            raise
    print('Licensed voice assets verified and installed. Device validation is still required before release.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only', action='store_true')
    try:
        if parser.parse_args().verify_only:
            verify_installed()
            print('Voice and native source checksums verified.')
        else:
            prepare()
    except (OSError, RuntimeError, KeyError, ValueError) as error:
        raise SystemExit(str(error))
