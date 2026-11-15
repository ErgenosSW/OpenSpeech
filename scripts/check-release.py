#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail closed until a hash-bound release review is completed."""
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def fingerprint(root):
    paths = set()
    for name in ('dependencies.lock.json', 'app/gradle.lockfile', 'gradle/verification-metadata.xml',
                 'THIRD_PARTY.md', 'LICENSE', 'build.gradle.kts', 'settings.gradle.kts',
                 'app/build.gradle.kts', 'gradle.properties', 'build.sh', 'build-release.sh', 'gradlew', 'release-certificate.sha256'):
        path = root / name
        if path.is_file():
            paths.add(path)
    for directory in ('app/src/main', 'app/libs', 'scripts', 'licenses', 'gradle/wrapper', 'third_party'):
        paths.update(p for p in (root / directory).rglob('*')
                     if p.is_file() and '__pycache__' not in p.parts)
    digest = hashlib.sha256()
    for path in sorted(paths):
        with path.open('rb') as source:
            sha = hashlib.file_digest(source, 'sha256').hexdigest()
        digest.update(f'{path.relative_to(root).as_posix()}\0{sha}\n'.encode())
    return digest.hexdigest()


def check(root=ROOT, publication=False):
    lock_bytes = (root / 'dependencies.lock.json').read_bytes()
    lock = json.loads(lock_bytes)
    errors = []
    voice = lock['voice']
    if voice.get('redistributionStatus') != 'approved' or not voice.get('weightsLicense'):
        errors.append('Voice weights have no approved redistribution license.')
    for name in ('apkInventory', 'nativeSources', 'notices'):
        if lock['audit'].get(name) != 'approved':
            errors.append(f'Component audit incomplete: {name}.')
    review_file = root / 'release-review.json'
    if not review_file.exists():
        errors.append('Missing release-review.json with evidence for this dependency lock.')
    else:
        review = json.loads(review_file.read_text())
        if review.get('reviewInputsSha256') != fingerprint(root):
            errors.append('Release review does not match the current source and dependency files.')
        if review.get('dependencyLockSha256') != hashlib.sha256(lock_bytes).hexdigest():
            errors.append('Release review does not match dependencies.lock.json.')
        required = ['weightsPermission', 'baseModelPermission', 'apkComponents', 'correspondingSource', 'notices', 'hostTests']
        if publication:
            required += ['deviceTests', 'finalApkReview']
        for name in required:
            entry = review.get(name, {})
            evidence = entry.get('evidence')
            path = (root / evidence).resolve() if isinstance(evidence, str) and evidence else None
            if (entry.get('status') != 'approved' or path is None
                    or not path.is_relative_to(root.resolve()) or not path.is_file()
                    or path.stat().st_size == 0):
                errors.append(f'Missing approved evidence: {name}.')
            elif entry.get('sha256') != hashlib.sha256(path.read_bytes()).hexdigest():
                errors.append(f'Evidence checksum mismatch: {name}.')
    for filename in ('app/gradle.lockfile', 'gradle/verification-metadata.xml'):
        if not (root / filename).is_file():
            errors.append(f'Missing dependency verification: {filename}.')
    return errors


if __name__ == '__main__':
    if sys.argv[1:] == ['--fingerprint']:
        print(fingerprint(ROOT))
        sys.exit(0)
    if sys.argv[1:] not in ([], ['--publication']):
        raise SystemExit('Usage: check-release.py [--fingerprint | --publication]')
    try:
        errors = check(publication=sys.argv[1:] == ['--publication'])
    except (OSError, KeyError, ValueError, TypeError) as error:
        errors = [f'Invalid release records: {error}']
    if errors:
        print('RELEASE BLOCKED:\n' + '\n'.join('- ' + error for error in errors), file=sys.stderr)
        sys.exit(1)
    print('Recorded build prerequisites passed. Before publishing, run check-release.py --publication.')
