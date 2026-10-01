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
from test_platform_v2_development_read_api import API_TOKEN
from test_platform_v2_human_review_api import WRITE_ACTOR, WRITE_TOKEN, prepare_root
from test_platform_v2_hosted_runtime import SECRET
from v2.mcp_adapter import LEGACY_PROTOCOL_VERSION, MODERN_PROTOCOL_VERSION


class McpHttpTest(unittest.TestCase):
    def runtime(self, root: Path) -> hosted.HostedV2Substrate:
        prepare_root(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=API_TOKEN,
            development_write_token=WRITE_TOKEN,
            development_write_actor=WRITE_ACTOR,
            trusted_actors=(WRITE_ACTOR,),
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

    def post(
        self,
        base: str,
        message: dict | bytes,
        *,
        token: str | None = API_TOKEN,
        headers: dict[str, str] | None = None,
        content_type: str = "application/json",
    ):
        raw = message if isinstance(message, bytes) else json.dumps(message).encode("utf-8")
        request_headers = {"Content-Type": content_type}
        if token is not None:
            request_headers["Authorization"] = f"Bearer {token}"
        request_headers.update(headers or {})
        request = urllib.request.Request(
            base + "/mcp",
            data=raw,
            method="POST",
            headers=request_headers,
        )
        try:
            response = urllib.request.urlopen(request, timeout=3)
            body = response.read()
            payload = json.loads(body.decode("utf-8")) if body else None
            return response.status, payload, response.headers
        except urllib.error.HTTPError as exc:
            body = exc.read()
            payload = json.loads(body.decode("utf-8")) if body else None
            return exc.code, payload, exc.headers

    def modern_message(self, method: str, *, request_id=1, params=None):
        value = dict(params or {})
        value["_meta"] = {
            "io.modelcontextprotocol/protocolVersion": MODERN_PROTOCOL_VERSION,
            "io.modelcontextprotocol/clientCapabilities": {},
            "io.modelcontextprotocol/clientInfo": {
                "name": "http-test-client",
                "version": "1.0",
            },
        }
        return {"jsonrpc": "2.0", "id": request_id, "method": method, "params": value}

    def modern_headers(self, method: str, *, name: str | None = None):
        values = {
            "MCP-Protocol-Version": MODERN_PROTOCOL_VERSION,
            "Mcp-Method": method,
            "Accept": "application/json, text/event-stream",
        }
        if name is not None:
            values["Mcp-Name"] = name
        return values

    def test_legacy_initialize_list_and_call_over_http(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, initialized, headers = self.post(
                base,
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": LEGACY_PROTOCOL_VERSION,
                        "capabilities": {},
                        "clientInfo": {"name": "legacy-http", "version": "1"},
                    },
                },
            )
            self.assertEqual(status, 200)
            self.assertEqual(initialized["result"]["protocolVersion"], LEGACY_PROTOCOL_VERSION)
            self.assertIsNone(headers.get("Mcp-Session-Id"))
            self.assertEqual(headers["Cache-Control"], "no-store")

            status, listing, _ = self.post(
                base,
                {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}},
                headers={"MCP-Protocol-Version": LEGACY_PROTOCOL_VERSION},
            )
            self.assertEqual(status, 200)
            names = [tool["name"] for tool in listing["result"]["tools"]]
            self.assertEqual(
                names,
                [
                    "get_development_state",
                    "get_current_product_state",
                    "get_objective_trace",
                    "list_review_artifacts",
                    "get_review_artifact",
                    "get_file_artifact",
                    "get_human_gate",
                ],
            )

            status, state_result, _ = self.post(
                base,
                {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "tools/call",
                    "params": {"name": "get_development_state", "arguments": {}},
                },
                headers={"MCP-Protocol-Version": LEGACY_PROTOCOL_VERSION},
            )
            self.assertEqual(status, 200)
            status_http, expected = runtime.handle_development_read(f"Bearer {API_TOKEN}")
            self.assertEqual(status_http, 200)
            self.assertEqual(state_result["result"]["structuredContent"], expected)

    def test_modern_discover_list_and_call_over_stateless_http(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, discover, _ = self.post(
                base,
                self.modern_message("server/discover"),
                headers=self.modern_headers("server/discover"),
            )
            self.assertEqual(status, 200)
            self.assertEqual(discover["result"]["supportedVersions"], [MODERN_PROTOCOL_VERSION])

            status, listing, _ = self.post(
                base,
                self.modern_message("tools/list", request_id=2),
                headers=self.modern_headers("tools/list"),
            )
            self.assertEqual(status, 200)
            self.assertEqual(listing["result"]["cacheScope"], "private")

            call = self.modern_message(
                "tools/call",
                request_id=3,
                params={"name": "get_human_gate", "arguments": {"gate_id": "review"}},
            )
            status, result, _ = self.post(
                base,
                call,
                headers=self.modern_headers("tools/call", name="get_human_gate"),
            )
            self.assertEqual(status, 200)
            self.assertTrue(result["result"]["structuredContent"]["current"])
            self.assertIn("io.modelcontextprotocol/serverInfo", result["result"]["_meta"])

    def test_write_credential_sees_only_typed_write_tools_and_objective_replay_is_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, listing, _ = self.post(
                base,
                {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
                token=WRITE_TOKEN,
            )
            self.assertEqual(status, 200)
            self.assertEqual(
                [tool["name"] for tool in listing["result"]["tools"]],
                [
                    "submit_objective",
                    "continue_skyforge",
                    "pause_objective",
                    "resume_objective",
                    "cancel_objective",
                    "reconcile_objective",
                    "submit_human_review",
                ],
            )

            call = {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/call",
                "params": {
                    "name": "submit_objective",
                    "arguments": {
                        "request_id": "objective-mcp-http-0001",
                        "objective": "Investigate landing gear",
                    },
                },
            }
            first_status, first, _ = self.post(base, call, token=WRITE_TOKEN)
            replay_status, replay, _ = self.post(base, call, token=WRITE_TOKEN)
            self.assertEqual(first_status, 200)
            self.assertEqual(replay_status, 200)
            first_value = first["result"]["structuredContent"]
            replay_value = replay["result"]["structuredContent"]
            self.assertFalse(first_value["idempotent_replay"])
            self.assertTrue(replay_value["idempotent_replay"])
            self.assertEqual(first_value["proposal_id"], replay_value["proposal_id"])
            self.assertEqual(first_value["client"], "chatgpt-mcp")
            self.assertEqual(len(runtime.objective_proposal_store.load().records), 1)

    def test_auth_is_checked_before_json_parse(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, payload, headers = self.post(base, b"not-json", token=None)
            self.assertEqual(status, 401)
            self.assertIn("authorization", payload["error"].lower())
            self.assertIn("Bearer", headers["WWW-Authenticate"])

            status, payload, _ = self.post(base, b"not-json", token=API_TOKEN)
            self.assertEqual(status, 400)
            self.assertEqual(payload["error"]["code"], -32700)

    def test_origin_content_type_and_get_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, payload, _ = self.post(
                base,
                {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
                headers={"Origin": "https://attacker.example"},
            )
            self.assertEqual(status, 403)
            self.assertIn("Origin", payload["error"])

            status, _, _ = self.post(
                base,
                {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
                content_type="text/plain",
            )
            self.assertEqual(status, 415)

            request = urllib.request.Request(
                base + "/mcp",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 405)
            self.assertEqual(raised.exception.headers["Allow"], "POST")

    def test_modern_header_mismatch_is_jsonrpc_400_without_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            message = self.modern_message(
                "tools/call",
                params={
                    "name": "submit_objective",
                    "arguments": {
                        "request_id": "objective-mcp-http-0002",
                        "objective": "Investigate landing gear",
                    },
                },
            )
            status, payload, _ = self.post(
                base,
                message,
                token=WRITE_TOKEN,
                headers=self.modern_headers("tools/call", name="wrong-name"),
            )
            self.assertEqual(status, 400)
            self.assertEqual(payload["error"]["code"], -32020)
            self.assertEqual(len(runtime.objective_proposal_store.load().records), 0)

    def test_initialized_notification_returns_empty_202(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            status, payload, headers = self.post(
                base,
                {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}},
            )
            self.assertEqual(status, 202)
            self.assertIsNone(payload)
            self.assertEqual(headers["Content-Length"], "0")

    def test_read_and_write_tokens_must_be_distinct(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            with self.assertRaisesRegex(RuntimeError, "must be distinct"):
                hosted.HostedV2Substrate(
                    root,
                    repo="ni-da-ba/skyforge",
                    require_webhook_secret=True,
                    startup_reconcile=True,
                    webhook_secret=SECRET,
                    development_api_token=API_TOKEN,
                    development_write_token=API_TOKEN,
                    development_write_actor=WRITE_ACTOR,
                    trusted_actors=(WRITE_ACTOR,),
                )


if __name__ == "__main__":
    unittest.main()
