import json
import tempfile
import unittest
from pathlib import Path

import stage_evidence_review_bundle as bundle
import verify_evidence_entry_points as verifier


class EvidenceReviewBundleTests(unittest.TestCase):
    def write_manifest(self, root: Path, paths: list[str]) -> Path:
        manifest = root / 'manifest.json'
        manifest.write_text(json.dumps(verifier.make_manifest(paths)), encoding='utf-8')
        return manifest

    def test_patterns_preserve_directory_order_and_special_extensions(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            paths = [
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/index.html',
                f'{verifier.EVIDENCE_ROOT}alpha/index.html',
            ]
            manifest = self.write_manifest(root, paths)
            directories = bundle.manifest_directories(manifest)
        self.assertEqual(['fixed-seed-island-v1', 'alpha'], directories)
        self.assertEqual(
            [
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/**/*.png',
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/**/*.html',
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/**/*.json',
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/**/*.csv',
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/**/*.sha256',
                f'{verifier.EVIDENCE_ROOT}alpha/**/*.png',
                f'{verifier.EVIDENCE_ROOT}alpha/**/*.html',
                f'{verifier.EVIDENCE_ROOT}alpha/**/*.csv',
            ],
            bundle.upload_patterns(directories),
        )

    def test_inline_equivalence_is_order_sensitive(self):
        directories = ['alpha']
        patterns = bundle.upload_patterns(directories)
        workflow = bundle.PUBLISH_STEP + '\n'.join(f'            {path}' for path in patterns) + '\n'
        bundle.verify_inline_equivalence(workflow, directories)
        reversed_workflow = bundle.PUBLISH_STEP + '\n'.join(f'            {path}' for path in reversed(patterns)) + '\n'
        with self.assertRaises(bundle.BundleError):
            bundle.verify_inline_equivalence(reversed_workflow, directories)

    def test_s2_stage_includes_review_package_once_across_nested_manifest_directories(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = f'{verifier.EVIDENCE_ROOT}studio-bound-hydrology-semantic-v1'
            paths = [
                f'{base}/index.html',
                f'{base}/hydrology-semantic-layer.json',
                f'{base}/terrain/terrain-semantic-volume.json',
                f'{base}/terrain/summary.json',
                f'{base}/terrain/legend.png',
                f'{base}/terrain/top-surface-semantics.png',
                f'{base}/terrain/east-west-section.png',
                f'{base}/terrain/north-south-section.png',
                f'{base}/terrain/isometric-top-semantics.png',
            ]
            manifest = self.write_manifest(root, paths)
            source = root / verifier.EVIDENCE_ROOT / 'studio-bound-hydrology-semantic-v1'
            terrain = source / 'terrain'
            terrain.mkdir(parents=True)
            for relative in [
                'index.html',
                'hydrology-semantic-layer.json',
                'terrain/terrain-semantic-volume.json',
                'terrain/summary.json',
                'terrain/legend.png',
                'terrain/top-surface-semantics.png',
                'terrain/east-west-section.png',
                'terrain/north-south-section.png',
                'terrain/isometric-top-semantics.png',
            ]:
                target = source / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(relative.encode('utf-8'))
            (terrain / 'ignored.csv').write_text('not a review deliverable', encoding='utf-8')
            destination = root / 'bundle'

            files, _ = bundle.stage_bundle(manifest, root, destination)

            self.assertEqual(9, files)
            staged = sorted(path.relative_to(destination).as_posix() for path in destination.rglob('*') if path.is_file())
            self.assertEqual(sorted(path[len(base) + 1:] for path in paths), staged)
            self.assertFalse((destination / 'studio-bound-hydrology-semantic-v1/terrain/ignored.csv').exists())

    def test_stage_copies_only_policy_extensions_and_preserves_tree(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            paths = [
                f'{verifier.EVIDENCE_ROOT}alpha/index.html',
                f'{verifier.EVIDENCE_ROOT}fixed-seed-island-v1/index.html',
            ]
            manifest = self.write_manifest(root, paths)
            alpha = root / verifier.EVIDENCE_ROOT / 'alpha'
            fixed = root / verifier.EVIDENCE_ROOT / 'fixed-seed-island-v1'
            alpha.mkdir(parents=True)
            fixed.mkdir(parents=True)
            (alpha / 'index.html').write_text('html', encoding='utf-8')
            (alpha / 'atlas.png').write_bytes(b'png')
            (alpha / 'ignored.json').write_text('{}', encoding='utf-8')
            (fixed / 'index.html').write_text('html', encoding='utf-8')
            (fixed / 'meta.json').write_text('{}', encoding='utf-8')
            destination = root / 'bundle'

            files, _ = bundle.stage_bundle(manifest, root, destination)

            self.assertEqual(4, files)
            self.assertTrue((destination / 'alpha/index.html').is_file())
            self.assertTrue((destination / 'alpha/atlas.png').is_file())
            self.assertFalse((destination / 'alpha/ignored.json').exists())
            self.assertTrue((destination / 'fixed-seed-island-v1/meta.json').is_file())


if __name__ == '__main__':
    unittest.main()
