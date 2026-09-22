from __future__ import annotations

import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_development_read_api import API_TOKEN, make_git_root
from test_platform_v2_hosted_runtime import SECRET, write_legacy


class OperationsConsoleTest(unittest.TestCase):
    def runtime(self, root: Path) -> hosted.HostedV2Substrate:
        make_git_root(root)
        write_legacy(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=API_TOKEN,
            trusted_actors=("ni-da-ba",),
        )

    def serve(self, runtime: hosted.HostedV2Substrate) -> str:
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def request(self, url: str, *, token: str | None = None, headers=None):
        merged = dict(headers or {})
        if token is not None:
            merged["Authorization"] = f"Bearer {token}"
        return urllib.request.urlopen(
            urllib.request.Request(url, headers=merged),
            timeout=3,
        )

    def test_console_static_routes_have_strict_headers_and_no_secret(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            for path, expected_type in (
                ("/console", "text/html"),
                ("/console/", "text/html"),
                ("/console/app.js", "text/javascript"),
                ("/console/styles.css", "text/css"),
            ):
                with self.request(base + path) as response:
                    body = response.read().decode("utf-8")
                    self.assertEqual(response.status, 200)
                    self.assertTrue(response.headers["Content-Type"].startswith(expected_type))
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
                    self.assertEqual(response.headers["Referrer-Policy"], "no-referrer")
                    self.assertEqual(response.headers["X-Content-Type-Options"], "nosniff")
                    csp = response.headers["Content-Security-Policy"]
                    self.assertIn("default-src 'self'", csp)
                    self.assertIn("object-src 'none'", csp)
                    self.assertIn("frame-ancestors 'none'", csp)
                    self.assertNotIn(API_TOKEN, body)
                    self.assertNotIn("SKYFORGE_DEVELOPMENT_API_TOKEN", body)

    def test_console_uses_canonical_current_product_state_and_continue_status(self):
        source = (
            Path(__file__).resolve().parent / "console" / "app.js"
        ).read_text(encoding="utf-8")
        self.assertIn("state.current_product_state || {}", source)
        self.assertIn('"Continue Skyforge"', source)
        self.assertIn('"Current product boundary"', source)
        self.assertIn('"Latest historical review"', source)
        self.assertNotIn(
            'let product = review ? friendlyStatus(review.verdict) : "No review needed";',
            source,
        )

    def test_unknown_console_asset_is_not_directory_browsing(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.request(base + "/console/../../.git/config")
            self.assertEqual(raised.exception.code, 404)

    def test_development_state_etag_returns_304_when_unchanged(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            with self.request(
                base + "/api/v1/development-state",
                token=API_TOKEN,
            ) as first:
                payload = json.loads(first.read().decode("utf-8"))
                etag = first.headers["ETag"]
            self.assertEqual(etag, f'"{payload["snapshot_digest"]}"')

            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={
                    "Authorization": f"Bearer {API_TOKEN}",
                    "If-None-Match": etag,
                },
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 304)
            self.assertEqual(raised.exception.headers["ETag"], etag)
            self.assertEqual(raised.exception.read(), b"")

    def test_etag_does_not_bypass_bearer_auth(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={"If-None-Match": '"anything"'},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 401)

    def test_client_uses_safe_text_rendering_and_header_auth(self):
        app = (
            Path(hosted.__file__).resolve().parent / "console" / "app.js"
        ).read_text(encoding="utf-8")
        self.assertIn("textContent", app)
        self.assertIn('headers.set("Authorization"', app)
        self.assertIn("sessionStorage", app)
        self.assertIn("If-None-Match", app)
        self.assertIn("setInterval(refresh, POLL_MS)", app)
        self.assertIn("/api/v1/objectives", app)
        self.assertIn("/api/v1/human-reviews", app)
        self.assertIn("/api/v1/objective-controls", app)
        self.assertIn("X-Skyforge-Client", app)
        self.assertIn("WRITE_TOKEN_KEY", app)
        self.assertNotIn("innerHTML", app)
        self.assertNotIn("outerHTML", app)
        self.assertNotIn("insertAdjacentHTML", app)
        self.assertNotIn("document.write", app)
        self.assertNotIn("?token=", app)
        self.assertNotIn("access_token=", app)
        self.assertNotIn(API_TOKEN, app)

    def test_console_contains_only_admitted_typed_domain_mutation_controls(self):
        html = (
            Path(hosted.__file__).resolve().parent / "console" / "index.html"
        ).read_text(encoding="utf-8")
        for section in (
            'id="roadmap"',
            'id="scorecard"',
            'id="objectives"',
            'id="workers"',
            'id="execution"',
            'id="objective-control"',
            'id="objective-form"',
            'id="objective-text"',
            'id="objective-lifecycle-control"',
            'id="objective-lifecycle-form"',
            'id="objective-lifecycle-id"',
            'id="objective-lifecycle-operation"',
            'id="objective-cancel-confirm"',
            'id="reviews"',
            'id="review-control"',
            'id="review-form"',
            'id="review-gate"',
            'id="review-artifact"',
            'id="review-verdict"',
            'id="claims"',
            'id="completions"',
        ):
            self.assertIn(section, html)
        lowered = html.lower()
        self.assertIn("submit objective", lowered)
        self.assertIn("scopes and validates it before any worker may run", lowered)
        self.assertIn("record human review", lowered)
        self.assertIn("never chooses the verdict for you", lowered)
        self.assertIn("objective lifecycle", lowered)
        self.assertIn("cancel permanently", lowered)
        self.assertIn("does not erase history or remote work", lowered)
        self.assertNotIn("arbitrary terminal", lowered)


    def test_console_prioritizes_human_operator_state_over_history(self):
        root = Path(hosted.__file__).resolve().parent / "console"
        html = (root / "index.html").read_text(encoding="utf-8")
        app = (root / "app.js").read_text(encoding="utf-8")

        for operator_surface in (
            'id="overview"',
            'id="attention-panel"',
            'id="current-work"',
            'id="product-review"',
            'id="operator-controls"',
            'id="history"',
            'id="technical"',
            'id="worker-history"',
        ):
            self.assertIn(operator_surface, html)

        self.assertIn("Needs attention", html)
        self.assertIn("Current work", html)
        self.assertIn("Product / human review", html)
        self.assertIn("Objective history", html)
        self.assertIn("Technical platform details", html)
        self.assertIn("FRIENDLY_STATUS", app)
        self.assertIn("renderAttention(state)", app)
        self.assertIn("renderProductReview(state)", app)
        self.assertIn("currentHumanGate(state)", app)
        self.assertIn('active.disposition === "WAIT_HUMAN"', app)
        self.assertIn('review.verdict === "CHANGES_REQUIRED"', app)
        self.assertIn("migrated/legacy blocked gate", app)
        self.assertIn("Previous review", app)
        self.assertIn("No worker is currently running.", app)
        self.assertIn("Stale PR preserved safely", app)
        self.assertIn("technicalDetails", app)
        self.assertNotIn('innerHTML', app)

    def test_post_console_route_is_not_a_mutation_surface(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            request = urllib.request.Request(
                base + "/console",
                data=b"{}",
                method="POST",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
