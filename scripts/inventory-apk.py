#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inventory an APK without treating embedded version labels as legal approval."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def inventory(path):
    with path.open('rb') as file:
        digest = hashlib.file_digest(file, 'sha256').hexdigest()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate ZIP paths: ambiguous APK contents')
        files = []
        for item in archive.infolist():
            if item.is_dir():
                continue
            with archive.open(item) as file:
                checksum = hashlib.file_digest(file, 'sha256').hexdigest()
            files.append({'path': item.filename, 'bytes': item.file_size, 'sha256': checksum})
        versions = {}
        for name in names:
            if name.startswith('META-INF/') and name.endswith('.version'):
                value = archive.read(name).decode('utf-8').strip()
                versions[name] = {'embeddedValue': value, 'resolvedVersion': None if 'task ' in value or 'property ' in value else value}
    return {'apkSha256': digest, 'apkBytes': path.stat().st_size, 'auditStatus': 'unreviewed', 'versions': versions, 'files': files}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(inventory(args.apk), indent=2, ensure_ascii=False) + '\n')
