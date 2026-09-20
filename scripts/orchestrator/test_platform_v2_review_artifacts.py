from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.review_artifacts import (
    ReviewArtifactCatalog,
    ReviewArtifactError,
    ReviewArtifactKind,
    retrieve_file_artifact,
    validate_artifact_source,
)

ROOT = Path(__file__).resolve().parents[2]
DR70_SHA = "a318f1b1ffb22805087eac52b67035d041a03fb6"


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def make_repo(root: Path, payload: bytes = b"artifact-bytes\n") -> tuple[str, bytes]:
    git(root, "init", "-b", "main")
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge Artifact Test")
    artifact = root / "review" / "artifact.txt"
    artifact.parent.mkdir(parents=True)
    artifact.write_bytes(payload)
    git(root, "add", "review/artifact.txt")
    git(root, "commit", "-m", "artifact source")
    return git(root, "rev-parse", "HEAD"), payload


def file_manifest(sha: str, payload: bytes, *, path: str = "review/artifact.txt") -> dict:
    return {
        "schema_version": 1,
        "artifacts": [
            {
                "artifact_id": "file:test",
                "kind": "FILE",
                "source_sha": sha,
                "title": "Test artifact",
                "description": "Bound repository blob",
                "file": {
                    "source": "REPOSITORY_BLOB",
                    "repository_path": path,
                    "media_type": "text/plain",
                    "byte_size": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                },
            }
        ],
    }


class ReviewArtifactCatalogTest(unittest.TestCase):
    def test_key_2885_manifest_matches_reviewed_source_contract(self):
        catalog = ReviewArtifactCatalog.for_root(ROOT)
        record = catalog.get("dr70:key-2885")
        self.assertIsNotNone(record)
        assert record is not None
        self.assertEqual(record.kind, ReviewArtifactKind.INTERACTIVE_SPECIMEN)
        self.assertEqual(record.source_sha, DR70_SHA)
        self.assertIsNotNone(record.interactive)
        assert record.interactive is not None
        self.assertEqual(record.interactive.parameters["island_key"], 2885)
        self.assertEqual(record.interactive.parameters["suspension_y"], 220.0)
        self.assertEqual(
            record.interactive.preparation_entry_points,
            (":skyforge-neoforge-1211:dr70HydrologyReviewAcceptance",),
        )
        self.assertEqual(
            record.interactive.launch_entry_point,
            ":skyforge-neoforge-1211:runDr70HumanReviewClient",
        )
        self.assertIn("/tp @s 0 320 0", record.interactive.review_actions)

    def test_repository_blob_retrieval_verifies_source_size_and_digest(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            sha, payload = make_repo(root)
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(file_manifest(sha, payload)), encoding="utf-8")
            catalog = ReviewArtifactCatalog.for_root(
                root,
                manifest_relative_path=Path("manifest.json"),
            )
            record = catalog.get("file:test")
            self.assertIsNotNone(record)
            assert record is not None
            self.assertEqual(retrieve_file_artifact(root, record), payload)

    def test_repository_path_traversal_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            sha, payload = make_repo(root)
            manifest = root / "manifest.json"
            manifest.write_text(
                json.dumps(file_manifest(sha, payload, path="../secret.txt")),
                encoding="utf-8",
            )
            with self.assertRaises(ReviewArtifactError):
                ReviewArtifactCatalog.for_root(
                    root,
                    manifest_relative_path=Path("manifest.json"),
                )

    def test_digest_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            sha, payload = make_repo(root)
            raw = file_manifest(sha, payload)
            raw["artifacts"][0]["file"]["sha256"] = "0" * 64
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(raw), encoding="utf-8")
            record = ReviewArtifactCatalog.for_root(
                root,
                manifest_relative_path=Path("manifest.json"),
            ).get("file:test")
            assert record is not None
            with self.assertRaisesRegex(ReviewArtifactError, "SHA-256"):
                retrieve_file_artifact(root, record)

    def test_size_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            sha, payload = make_repo(root)
            raw = file_manifest(sha, payload)
            raw["artifacts"][0]["file"]["byte_size"] += 1
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(raw), encoding="utf-8")
            record = ReviewArtifactCatalog.for_root(
                root,
                manifest_relative_path=Path("manifest.json"),
            ).get("file:test")
            assert record is not None
            with self.assertRaisesRegex(ReviewArtifactError, "byte size"):
                retrieve_file_artifact(root, record)

    def test_interactive_specimen_has_no_fake_file_bytes(self):
        record = ReviewArtifactCatalog.for_root(ROOT).get("dr70:key-2885")
        assert record is not None
        with self.assertRaisesRegex(ReviewArtifactError, "does not provide"):
            retrieve_file_artifact(ROOT, record)


if __name__ == "__main__":
    unittest.main()
