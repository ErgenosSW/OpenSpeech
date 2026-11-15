# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


assets = load('assets', 'prepare-assets.py')
release = load('release', 'check-release.py')


def artifact(path):
    return {'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


class ReleaseToolsTests(unittest.TestCase):
    def test_checksum_rejects_same_size_modification(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'data'
            path.write_bytes(b'yes'); expected = artifact(path); path.write_bytes(b'bad')
            with self.assertRaisesRegex(RuntimeError, 'Checksum mismatch'):
                assets.verify(path, expected)

    def test_unreviewed_voice_does_not_download(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'dependencies.lock.json').write_text(json.dumps({'voice': {'redistributionStatus': 'unverified'}}))
            with patch.object(assets, 'ROOT', root), patch.object(assets, 'download') as download:
                with self.assertRaisesRegex(RuntimeError, 'redistribution is unverified'):
                    assets.prepare()
                download.assert_not_called()

    def test_manifest_rejects_parent_traversal(self):
        with self.assertRaisesRegex(RuntimeError, 'Unsafe asset filename'):
            assets.manifest({'voice': {'files': ['../outside']}, 'artifacts': {}})

    def test_native_manifest_rejects_tampering_and_extra_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'third_party/library.c'; source.parent.mkdir(); source.write_text('source')
            manifest = root / 'native.json'; manifest.write_text(json.dumps({'third_party/library.c': artifact(source)}))
            lock = {'native': {'sourceManifest': 'native.json', 'sourceManifestSha256': artifact(manifest)['sha256']}}
            with patch.object(assets, 'ROOT', root):
                assets.verify_native(lock)
                source.write_text('broken')
                with self.assertRaisesRegex(RuntimeError, 'Checksum mismatch'):
                    assets.verify_native(lock)
                (source.parent / 'unexpected.c').write_text('extra')
                with self.assertRaisesRegex(RuntimeError, 'file list differs'):
                    assets.verify_native(lock)

    def review(self, root):
        lock = {'voice': {'redistributionStatus': 'approved', 'weightsLicense': 'CC-BY-SA-4.0'},
                'audit': {name: 'approved' for name in ('apkInventory', 'nativeSources', 'notices')}}
        (root / 'dependencies.lock.json').write_text(json.dumps(lock))
        evidence = root / 'evidence.txt'; evidence.write_text('Synthetic test evidence, not a real approval.')
        for name in ('app/gradle.lockfile', 'gradle/verification-metadata.xml'):
            path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text('fixture')
        record = {'dependencyLockSha256': artifact(root / 'dependencies.lock.json')['sha256'],
                  'reviewInputsSha256': release.fingerprint(root)}
        for name in ('weightsPermission', 'baseModelPermission', 'apkComponents', 'correspondingSource', 'notices', 'hostTests'):
            record[name] = {'status': 'approved', 'evidence': 'evidence.txt', 'sha256': artifact(evidence)['sha256']}
        (root / 'release-review.json').write_text(json.dumps(record))
        return record

    def test_candidate_build_does_not_approve_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.review(root)
            self.assertEqual(release.check(root), [])
            errors = release.check(root, publication=True)
            self.assertTrue(any('deviceTests' in value for value in errors))
            self.assertTrue(any('finalApkReview' in value for value in errors))

    def test_changed_source_evidence_and_lock_require_review(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.review(root)
            (root / 'build.sh').write_text('changed')
            (root / 'evidence.txt').write_text('changed evidence')
            with (root / 'dependencies.lock.json').open('a') as file: file.write('\n')
            errors = release.check(root)
            self.assertTrue(any('current source' in value for value in errors))
            self.assertTrue(any('Evidence checksum mismatch' in value for value in errors))
            self.assertTrue(any('dependencies.lock.json' in value for value in errors))

    def test_evidence_must_stay_inside_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); record = self.review(root)
            record['weightsPermission']['evidence'] = '../outside'
            (root / 'release-review.json').write_text(json.dumps(record))
            self.assertTrue(any('weightsPermission' in value for value in release.check(root)))

    def test_unknown_weights_block_otherwise_complete_review(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.review(root)
            path = root / 'dependencies.lock.json'; lock = json.loads(path.read_text()); lock['voice']['weightsLicense'] = None
            path.write_text(json.dumps(lock))
            self.assertTrue(any('weights' in value for value in release.check(root)))


if __name__ == '__main__':
    unittest.main()
