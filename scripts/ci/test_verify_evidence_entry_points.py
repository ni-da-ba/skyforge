import json
import tempfile
import unittest
from pathlib import Path

import verify_evidence_entry_points as verifier


class EvidenceContractTests(unittest.TestCase):
    def test_extracts_only_target_step_and_preserves_order(self):
        workflow = """jobs:\n  build:\n    steps:\n      - name: Before\n        run: test -f skyforge-reference/build/evidence/ignored/index.html\n      - name: Verify evidence review entry points\n        run: |\n          test -f skyforge-reference/build/evidence/alpha/index.html\n          test -f skyforge-reference/build/evidence/beta/atlas.png\n      - name: After\n        run: test -f skyforge-reference/build/evidence/ignored/after.html\n"""
        self.assertEqual(
            [
                "skyforge-reference/build/evidence/alpha/index.html",
                "skyforge-reference/build/evidence/beta/atlas.png",
            ],
            verifier.extract_required_paths(workflow),
        )

    def test_duplicate_step_fails_closed(self):
        workflow = """steps:\n  - name: Verify evidence review entry points\n    run: test -f skyforge-reference/build/evidence/a/index.html\n  - name: Verify evidence review entry points\n    run: test -f skyforge-reference/build/evidence/b/index.html\n"""
        with self.assertRaises(verifier.ContractError):
            verifier.extract_required_paths(workflow)

    def test_unsafe_or_glob_paths_fail_closed(self):
        for path in (
            "../outside/index.html",
            "skyforge-reference/build/evidence/*/index.html",
        ):
            with self.subTest(path=path), self.assertRaises(verifier.ContractError):
                verifier._validate_path(path)

    def test_manifest_loader_validates_schema_and_paths(self):
        payload = verifier.make_manifest(
            ["skyforge-reference/build/evidence/alpha/index.html"]
        )
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            loaded = verifier.load_manifest(manifest)
        self.assertEqual(payload, loaded)

    def test_equivalence_is_order_sensitive(self):
        paths = [
            "skyforge-reference/build/evidence/a/index.html",
            "skyforge-reference/build/evidence/b/index.html",
        ]
        verifier.verify_equivalence(paths, list(paths))
        with self.assertRaises(verifier.ContractError):
            verifier.verify_equivalence(paths, list(reversed(paths)))

    def test_verify_files_reports_missing(self):
        paths = ["skyforge-reference/build/evidence/a/index.html"]
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            with self.assertRaises(verifier.ContractError):
                verifier.verify_files(paths, root)
            target = root / paths[0]
            target.parent.mkdir(parents=True)
            target.write_text("ok", encoding="utf-8")
            verifier.verify_files(paths, root)


if __name__ == "__main__":
    unittest.main()
