from __future__ import annotations

import hashlib
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_development_read_api import API_TOKEN, make_git_root
from test_platform_v2_hosted_runtime import SECRET, write_legacy


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def install_artifact_manifest(root: Path) -> tuple[str, bytes]:
    source_sha = make_git_root(root)
    payload = (root / "tracked.txt").read_bytes()
    raw = {
        "schema_version": 1,
        "artifacts": [
            {
                "artifact_id": "file:test",
                "kind": "FILE",
                "source_sha": source_sha,
                "title": "Tracked test artifact",
                "description": "Immutable repository-backed file artifact",
                "file": {
                    "source": "REPOSITORY_BLOB",
                    "repository_path": "tracked.txt",
                    "media_type": "text/plain",
                    "byte_size": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                },
            },
            {
                "artifact_id": "interactive:test",
                "kind": "INTERACTIVE_SPECIMEN",
                "source_sha": source_sha,
                "title": "Interactive test artifact",
                "description": "Interactive specimen has metadata but no direct bytes",
                "interactive": {
                    "specimen_kind": "test-interactive",
                    "parameters": {"specimen_key": 42},
                    "preparation_entry_points": [":test:prepare"],
                    "launch_entry_point": ":test:launch",
                    "review_actions": ["inspect specimen"],
                    "associated_artifact_ids": ["file:test"],
                },
            },
        ],
    }
    manifest = root / "docs" / "agent-state" / "REVIEW_ARTIFACTS.json"
    manifest.write_text(json.dumps(raw, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    git(root, "add", "docs/agent-state/REVIEW_ARTIFACTS.json")
    git(root, "commit", "-m", "register review artifacts")
    return source_sha, payload


class ArtifactApiTest(unittest.TestCase):
    def runtime(self, root: Path) -> tuple[hosted.HostedV2Substrate, bytes]:
        _source_sha, payload = install_artifact_manifest(root)
        write_legacy(root)
        runtime = hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=API_TOKEN,
            trusted_actors=("ni-da-ba",),
        )
        return runtime, payload

    def serve(self, runtime: hosted.HostedV2Substrate) -> str:
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def request(self, url: str, *, token: str | None = API_TOKEN):
        headers = {}
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        return urllib.request.urlopen(
            urllib.request.Request(url, headers=headers),
            timeout=3,
        )

    def test_list_and_get_return_exact_registered_manifests(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _payload = self.runtime(root)
            base = self.serve(runtime)
            with self.request(base + "/api/v1/artifacts") as response:
                listing = json.loads(response.read().decode("utf-8"))
            self.assertEqual(listing["artifact_count"], 2)
            self.assertTrue(listing["catalog_digest"])
            ids = [record["artifact_id"] for record in listing["artifacts"]]
            self.assertEqual(ids, ["file:test", "interactive:test"])

            with self.request(base + "/api/v1/artifacts/interactive%3Atest") as response:
                record = json.loads(response.read().decode("utf-8"))
            self.assertEqual(record["artifact_id"], "interactive:test")
            self.assertEqual(record["kind"], "INTERACTIVE_SPECIMEN")
            self.assertEqual(record["interactive"]["parameters"]["specimen_key"], 42)
            self.assertEqual(
                record["interactive"]["associated_artifact_ids"],
                ["file:test"],
            )

    def test_file_content_is_verified_and_served_with_manifest_media_type(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, payload = self.runtime(root)
            base = self.serve(runtime)
            with self.request(base + "/api/v1/artifacts/file%3Atest/content") as response:
                body = response.read()
                media_type = response.headers.get_content_type()
            self.assertEqual(body, payload)
            self.assertEqual(media_type, "text/plain")

    def test_interactive_content_request_is_409_not_fake_bytes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _payload = self.runtime(root)
            base = self.serve(runtime)
            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.request(base + "/api/v1/artifacts/interactive%3Atest/content")
            self.assertEqual(raised.exception.code, 409)
            body = json.loads(raised.exception.read().decode("utf-8"))
            self.assertIn("interactive", body["error"])

    def test_artifact_routes_require_same_development_api_bearer(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _payload = self.runtime(root)
            base = self.serve(runtime)
            for suffix in (
                "/api/v1/artifacts",
                "/api/v1/artifacts/file%3Atest",
                "/api/v1/artifacts/file%3Atest/content",
            ):
                with self.assertRaises(urllib.error.HTTPError) as raised:
                    self.request(base + suffix, token=None)
                self.assertEqual(raised.exception.code, 401)

    def test_missing_artifact_is_404(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _payload = self.runtime(root)
            base = self.serve(runtime)
            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.request(base + "/api/v1/artifacts/missing")
            self.assertEqual(raised.exception.code, 404)

    def test_digest_drift_returns_503_and_never_serves_bytes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _payload = self.runtime(root)
            base = self.serve(runtime)
            manifest = root / "docs" / "agent-state" / "REVIEW_ARTIFACTS.json"
            raw = json.loads(manifest.read_text(encoding="utf-8"))
            raw["artifacts"][0]["file"]["sha256"] = "0" * 64
            manifest.write_text(json.dumps(raw), encoding="utf-8")

            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.request(base + "/api/v1/artifacts/file%3Atest/content")
            self.assertEqual(raised.exception.code, 503)
            body = json.loads(raised.exception.read().decode("utf-8"))
            self.assertEqual(body["error"], "artifact content is unavailable")


if __name__ == "__main__":
    unittest.main()
