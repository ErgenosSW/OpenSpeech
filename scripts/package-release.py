#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Package a signed candidate and its corresponding source, without publishing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def package(output):
    subprocess.run(['python3', str(ROOT / 'scripts/check-release.py')], check=True)
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    sdk = Path(os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools/35.0.0'
    verification = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', verification)
    expected = (ROOT / 'release-certificate.sha256').read_text().strip()
    if not match or match.group(1) != expected:
        raise RuntimeError('APK signing certificate differs from the permanent release certificate.')
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    if "name='pl.ergenos.glosfacet'" not in badging or 'application-debuggable' in badging:
        raise RuntimeError('Unexpected application ID or debuggable release APK.')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if any(any(retired in name.lower() for retired in ('darkman', 'lessac', 'sherpa', 'zonos')) for name in names):
            raise RuntimeError('Retired voice/runtime found in the APK.')
        if any(key in badging for key in ('android.permission.INTERNET', 'android.permission.RECORD_AUDIO')):
            raise RuntimeError('Unexpected network or microphone permission.')
        for required in ('Matcha-CC-BY-SA-4.0.txt', 'Matcha-ATTRIBUTION.md', 'espeak-ng-GPL-3.0.txt', 'onnxruntime-ThirdPartyNotices.txt', 'OpenSpeech-LICENSE'):
            if 'assets/licenses/' + required not in names:
                raise RuntimeError('Missing packaged license: ' + required)
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, output / 'OpenSpeech.apk')
    # Explicit exclusions also work when rebuilding from a ZIP, without Git installed.
    excluded_dirs = {'.git', '.tools', '.gradle', '.kotlin', '.cxx', '__pycache__'}
    with zipfile.ZipFile(output / 'OpenSpeech.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for directory, dirs, files in os.walk(ROOT):
            directory = Path(directory)
            dirs[:] = sorted(name for name in dirs if name not in excluded_dirs and
                             directory / name not in (ROOT / 'app/src/main/assets/voice', ROOT / 'build', ROOT / 'app/build') and
                             (directory / name).resolve() != output.resolve())
            for name in sorted(files):
                source = directory / name
                if source.is_symlink() or source.suffix in ('.keystore', '.jks', '.pyc', '.apk'):
                    continue
                if directory == ROOT / 'app/libs' and source.suffix == '.aar':
                    continue
                archive.write(source, 'OpenSpeech/' + source.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(output / 'OpenSpeech.zip') as archive:
        if archive.testzip() is not None:
            raise RuntimeError('Invalid source ZIP.')
        sources = json.loads((ROOT / 'licenses/native-sources.json').read_text())
        for name, record in sources.items():
            if archive.getinfo('OpenSpeech/' + name).file_size != record['bytes']:
                raise RuntimeError('Missing or incomplete corresponding source: ' + name)
    (output / 'SHA256SUMS').write_text(''.join(digest(output / name) + '  ' + name + '\n'
                                            for name in ('OpenSpeech.apk', 'OpenSpeech.zip')))
    print('Packaged signed candidate and corresponding sources. Phone testing/publication review is separate.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    package(parser.parse_args().output.resolve())
