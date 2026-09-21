from __future__ import annotations

import hashlib
import json
from http.server import ThreadingHTTPServer
import subprocess
import tempfile
import threading
from pathlib import Path
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from v2.concurrency_claims import ConcurrencyClaimLedger, acquire_concurrency_claim
from v2.worker_provider import FrozenWorkerSpec, WorkerTier


API_TOKEN = "development-api-test-token-0123456789abcdef"


def make_git_root(root: Path) -> str:
    subprocess.run(["git", "init", "-b", "main"], cwd=root, check=True, capture_output=True)
    subprocess.run(
        ["git", "config", "user.email", "test@example.invalid"],
        cwd=root,
        check=True,
    )
    subprocess.run(
        ["git", "config", "user.name", "Skyforge Development API Test"],
        cwd=root,
        check=True,
    )
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    (root / "tracked.txt").write_text("accepted\n", encoding="utf-8")
    artifact_manifest = root / "docs" / "agent-state" / "REVIEW_ARTIFACTS.json"
    artifact_manifest.parent.mkdir(parents=True, exist_ok=True)
    artifact_manifest.write_text(
        json.dumps({"schema_version": 1, "artifacts": []}) + "\n",
        encoding="utf-8",
    )
    subprocess.run(
        [
            "git",
            "add",
            ".gitignore",
            "tracked.txt",
            "docs/agent-state/REVIEW_ARTIFACTS.json",
        ],
        cwd=root,
        check=True,
    )
    subprocess.run(["git", "commit", "-m", "accepted"], cwd=root, check=True, capture_output=True)
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()


class DevelopmentReadApiTest(unittest.TestCase):
    def runtime(self, root: Path, *, token: str | None = API_TOKEN) -> hosted.HostedV2Substrate:
        make_git_root(root)
        write_legacy(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=token,
            trusted_actors=("ni-da-ba",),
        )

    def serve(self, runtime: hosted.HostedV2Substrate):
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def get_json(self, url: str, *, token: str | None = None):
        headers = {}
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, json.loads(response.read().decode("utf-8"))

    def test_authorized_endpoint_matches_direct_snapshot(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)

            direct = runtime.development_snapshot()
            status, payload = self.get_json(
                base + "/api/v1/development-state",
                token=API_TOKEN,
            )

            self.assertEqual(status, 200)
            self.assertEqual(payload, direct)
            self.assertEqual(payload["repo"], "ni-da-ba/skyforge")
            self.assertEqual(payload["runtime"]["status"], "ok")
            self.assertEqual(payload["snapshot_digest"], direct["snapshot_digest"])
            self.assertNotIn(API_TOKEN, json.dumps(payload, sort_keys=True))


    def test_persisted_concurrency_claim_is_visible_with_bounded_execution(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            worker = FrozenWorkerSpec(
                task_id="claim-read-test",
                authority_key="authority:claim-read-test",
                task_spec_hash=hashlib.sha256(b"claim-read-spec").hexdigest(),
                attempt_id=hashlib.sha256(b"claim-read-attempt").hexdigest(),
                lane="Implementation",
                objective="prove claim visibility",
                stop_boundary="stop after claim visibility",
                base_sha="a" * 40,
                allowed_paths=("docs/claims/**",),
                tier=WorkerTier.TERRA,
                context_text="private claim context must not leak",
            )
            acquired = acquire_concurrency_claim(ConcurrencyClaimLedger(), worker)
            runtime.concurrency_claim_store.save(acquired.ledger)

            status, payload = runtime.handle_development_read(
                f"Bearer {API_TOKEN}"
            )
            self.assertEqual(status, 200)
            claims = payload["execution"]["concurrency_claims"]
            self.assertEqual(payload["execution"]["concurrency_claim_count"], 1)
            self.assertEqual(claims[0]["attempt_id"], worker.attempt_id)
            self.assertEqual(claims[0]["allowed_paths"], ["docs/claims/**"])
            self.assertNotIn(worker.context_text, json.dumps(payload))

            health = runtime.health_snapshot()
            self.assertEqual(health["active_concurrency_claim_count"], 1)
            self.assertEqual(health["hosted_execution_concurrency_limit"], 2)
            self.assertEqual(
                payload["runtime"]["hosted_execution_concurrency_limit"],
                2,
            )

    def test_corrupt_concurrency_claim_state_makes_development_read_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            store = runtime.concurrency_claim_store
            store.save(ConcurrencyClaimLedger())
            store.adapter.path.write_text("not-json", encoding="utf-8")
            store.adapter.backup_path.write_text("also-not-json", encoding="utf-8")
            status, payload = runtime.handle_development_read(
                f"Bearer {API_TOKEN}"
            )
            self.assertEqual(status, 503)
            self.assertEqual(payload["error"], "development state is unavailable")

    def test_unauthorized_endpoint_is_401_and_does_not_return_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)

            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.get_json(base + "/api/v1/development-state")
            self.assertEqual(raised.exception.code, 401)
            body = json.loads(raised.exception.read().decode("utf-8"))
            self.assertEqual(body["error"], "development API authorization required")

    def test_unconfigured_endpoint_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root, token=None)
            status, payload = runtime.handle_development_read(
                f"Bearer {API_TOKEN}"
            )
            self.assertEqual(status, 503)
            self.assertEqual(payload["error"], "development API is not configured")
            self.assertFalse(runtime.health_snapshot()["development_read_api_enabled"])

    def test_corrupt_durable_worker_state_returns_503_not_empty_success(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            state_dir = root / ".skyforge-platform-v2"
            state_dir.mkdir(parents=True, exist_ok=True)
            (state_dir / "worker-runs.json").write_text(
                '{"schema_version":1,"records":"corrupt"}\n',
                encoding="utf-8",
            )
            status, payload = runtime.handle_development_read(
                f"Bearer {API_TOKEN}"
            )
            self.assertEqual(status, 503)
            self.assertEqual(payload["error"], "development state is unavailable")
            self.assertEqual(payload["failure_kind"], "ValueError")

    def test_short_api_token_is_rejected_at_startup(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_git_root(root)
            write_legacy(root)
            with self.assertRaisesRegex(RuntimeError, "DEVELOPMENT_API_TOKEN"):
                hosted.HostedV2Substrate(
                    root,
                    repo="ni-da-ba/skyforge",
                    require_webhook_secret=True,
                    startup_reconcile=True,
                    webhook_secret=SECRET,
                    development_api_token="too-short",
                    trusted_actors=("ni-da-ba",),
                )

    def test_health_remains_public_and_only_reports_enabled_boolean(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            status, health = self.get_json(base + "/healthz")
            self.assertEqual(status, 200)
            self.assertTrue(health["development_read_api_enabled"])
            encoded = json.dumps(health, sort_keys=True)
            self.assertNotIn(API_TOKEN, encoded)
            self.assertNotIn("SKYFORGE_DEVELOPMENT_API_TOKEN", encoded)

    def test_post_to_read_endpoint_is_not_a_mutation_surface(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            request = urllib.request.Request(
                base + "/api/v1/development-state",
                data=b"{}",
                method="POST",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
