import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

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

    def test_contract_digest_is_ordered_and_newline_delimited(self):
        paths = [
            "skyforge-reference/build/evidence/a/index.html",
            "skyforge-reference/build/evidence/b/index.html",
        ]
        expected = hashlib.sha256(("\n".join(paths) + "\n").encode("utf-8")).hexdigest()
        self.assertEqual(expected, verifier.contract_digest(paths))
        self.assertNotEqual(verifier.contract_digest(paths), verifier.contract_digest(list(reversed(paths))))

    def test_compact_manifest_round_trips_exact_order(self):
        paths = [
            "skyforge-reference/build/evidence/alpha/index.html",
            "skyforge-reference/build/evidence/alpha/atlas.png",
            "skyforge-reference/build/evidence/alpha/manifest.csv",
            "skyforge-reference/build/evidence/alpha/cells.csv",
            "skyforge-reference/build/evidence/beta/index.html",
        ]
        payload = verifier.make_manifest(paths)
        self.assertEqual(2, payload["schema_version"])
        self.assertLess(len(json.dumps(payload)), len(json.dumps({"required_paths": paths})))
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            loaded = verifier.load_manifest(manifest)
        self.assertEqual(paths, loaded["required_paths"])

    def test_schema_one_remains_readable_during_migration(self):
        paths = ["skyforge-reference/build/evidence/alpha/index.html"]
        payload = {
            "schema_version": 1,
            "source_step": verifier.STEP_NAME,
            "required_paths": paths,
        }
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            loaded = verifier.load_manifest(manifest)
        self.assertEqual(paths, loaded["required_paths"])

    def test_compact_manifest_rejects_duplicate_directories(self):
        payload = {
            "schema_version": 2,
            "source_step": verifier.STEP_NAME,
            "root": verifier.EVIDENCE_ROOT,
            "default_files": verifier.DEFAULT_FILES,
            "groups": [{"d": "alpha"}, {"d": "alpha"}],
        }
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaises(verifier.ContractError):
                verifier.load_manifest(manifest)

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

    def test_verify_files_cli_does_not_require_workflow_yaml(self):
        paths = ["skyforge-reference/build/evidence/a/index.html"]
        payload = {
            "schema_version": 1,
            "source_step": verifier.STEP_NAME,
            "required_paths": paths,
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            target = root / paths[0]
            target.parent.mkdir(parents=True)
            target.write_text("ok", encoding="utf-8")
            missing_workflow = root / "does-not-exist.yml"
            argv = [
                "verify_evidence_entry_points.py",
                "--workflow",
                str(missing_workflow),
                "--manifest",
                str(manifest),
                "--verify-files",
                "--root",
                str(root),
            ]
            with mock.patch.object(sys, "argv", argv):
                self.assertEqual(0, verifier.main())

    def test_validate_manifest_cli_does_not_require_workflow_yaml(self):
        paths = ["skyforge-reference/build/evidence/a/index.html"]
        payload = {
            "schema_version": 1,
            "source_step": verifier.STEP_NAME,
            "required_paths": paths,
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")
            argv = [
                "verify_evidence_entry_points.py",
                "--workflow",
                str(root / "does-not-exist.yml"),
                "--manifest",
                str(manifest),
                "--validate-manifest",
            ]
            with mock.patch.object(sys, "argv", argv):
                self.assertEqual(0, verifier.main())


if __name__ == "__main__":
    unittest.main()
