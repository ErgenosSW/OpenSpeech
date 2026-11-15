#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Compile Polish/English eSpeak NG data from the vendored GPL sources."""
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def build():
    source = ROOT / 'third_party/espeak-ng'
    work = ROOT / '.tools/phoneme-data'
    binary = ROOT / '.tools/phonemizer-host'
    subprocess.run(['cmake', '-S', str(ROOT / 'app/src/main/cpp'), '-B', str(binary),
                    '-DCMAKE_BUILD_TYPE=Release', '-DOPENSPEECH_HOST_DATA_ONLY=ON'], check=True)
    subprocess.run(['cmake', '--build', str(binary), '--target', 'espeak-ng-bin', '--parallel', '4'], check=True)
    cli = binary / 'espeak/espeak-ng'
    data = work / 'espeak-ng-data'
    data.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source / 'phsource', work / 'phsource', dirs_exist_ok=True)
    # Both voices are free data in the source tree. English is the library's bootstrap voice.
    for language in ('zlw/pl', 'gmw/en'):
        target = data / 'lang' / language
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / 'espeak-ng-data/lang' / language, target)
    env = {**os.environ, 'ESPEAK_DATA_PATH': str(work)}
    subprocess.run([str(cli), '--compile-intonations'], cwd=source / 'phsource', env=env, check=True)
    subprocess.run([str(cli), '--compile-phonemes'], cwd=source / 'phsource', env=env, check=True)
    dictionaries = work / 'dictsource'
    dictionaries.mkdir(exist_ok=True)
    for language in ('pl', 'en'):
        for name in (f'{language}_rules', f'{language}_list', f'{language}_listx', f'{language}_emoji'):
            candidate = source / 'dictsource' / name
            if not candidate.exists():
                candidate = source / 'dictsource/extra' / name
            if candidate.exists():
                shutil.copy2(candidate, dictionaries / name)
        subprocess.run([str(cli), '--compile=' + language], cwd=dictionaries, env=env, check=True)
    # The compiler manifest records source paths and is not a runtime input.
    (data / 'phondata-manifest').unlink(missing_ok=True)
    return data


if __name__ == '__main__':
    print(build())
