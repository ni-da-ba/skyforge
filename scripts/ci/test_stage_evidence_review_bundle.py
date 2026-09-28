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


    def test_studio_s2_stages_nested_visuals_and_semantic_json(self):
        directory = 'studio-bound-hydrology-semantic-v1'
        self.assertEqual(
            [
                f'{verifier.EVIDENCE_ROOT}{directory}/**/*.png',
                f'{verifier.EVIDENCE_ROOT}{directory}/**/*.html',
                f'{verifier.EVIDENCE_ROOT}{directory}/**/*.json',
            ],
            bundle.upload_patterns([directory]),
        )
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            manifest = self.write_manifest(root, [
                f'{verifier.EVIDENCE_ROOT}{directory}/index.html',
                f'{verifier.EVIDENCE_ROOT}{directory}/hydrology-semantic-layer.json',
            ])
            source = root / verifier.EVIDENCE_ROOT / directory
            terrain = source / 'terrain'
            terrain.mkdir(parents=True)
            (source / 'index.html').write_text('review index', encoding='utf-8')
            (source / 'hydrology-semantic-layer.json').write_text('{}', encoding='utf-8')
            (source / 'unneeded.csv').write_text('excluded', encoding='utf-8')
            (terrain / 'terrain-semantic-volume.json').write_text('{}', encoding='utf-8')
            (terrain / 'top-surface-semantics.png').write_bytes(b'png')
            destination = root / 'bundle'

            files, _ = bundle.stage_bundle(manifest, root, destination)

            self.assertEqual(4, files)
            self.assertTrue((destination / directory / 'index.html').is_file())
            self.assertTrue((destination / directory / 'hydrology-semantic-layer.json').is_file())
            self.assertTrue((destination / directory / 'terrain/terrain-semantic-volume.json').is_file())
            self.assertTrue((destination / directory / 'terrain/top-surface-semantics.png').is_file())
            self.assertFalse((destination / directory / 'unneeded.csv').exists())

if __name__ == '__main__':
    unittest.main()
