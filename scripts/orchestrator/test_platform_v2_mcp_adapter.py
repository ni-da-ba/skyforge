from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from test_platform_v2_artifact_api import install_artifact_manifest
from test_platform_v2_development_read_api import API_TOKEN
from test_platform_v2_human_review_api import (
    WRITE_ACTOR,
    WRITE_TOKEN,
    payload as review_payload,
    prepare_root,
)
import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from v2.mcp_adapter import (
    LEGACY_PROTOCOL_VERSION,
    MODERN_PROTOCOL_VERSION,
    McpAdapter,
    READ_TOOLS,
    READ_TOOL_NAMES,
    WRITE_TOOLS,
    WRITE_TOOL_NAMES,
)


def legacy_request(method: str, request_id=1, params=None):
    return {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
        "params": {} if params is None else params,
    }


def modern_request(method: str, request_id=1, params=None):
    value = dict(params or {})
    value["_meta"] = {
        "io.modelcontextprotocol/protocolVersion": MODERN_PROTOCOL_VERSION,
        "io.modelcontextprotocol/clientCapabilities": {},
        "io.modelcontextprotocol/clientInfo": {
            "name": "skyforge-test-client",
            "version": "1.0.0",
        },
    }
    return {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
        "params": value,
    }


def modern_headers(method: str, *, name: str | None = None):
    headers = {
        "MCP-Protocol-Version": MODERN_PROTOCOL_VERSION,
        "Mcp-Method": method,
    }
    if name is not None:
        headers["Mcp-Name"] = name
    return headers


class McpProtocolTest(unittest.TestCase):
    def runtime(self, root: Path):
        _manifest, source_sha = prepare_root(root)
        runtime = hosted.HostedV2Substrate(
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
        return runtime, source_sha

    def test_legacy_initialize_and_role_filtered_tool_lists(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)

            initialized = adapter.handle(
                legacy_request(
                    "initialize",
                    params={
                        "protocolVersion": LEGACY_PROTOCOL_VERSION,
                        "capabilities": {},
                        "clientInfo": {"name": "legacy-test", "version": "1"},
                    },
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            self.assertEqual(initialized.status, 200)
            self.assertEqual(
                initialized.payload["result"]["protocolVersion"],
                LEGACY_PROTOCOL_VERSION,
            )
            self.assertIn("tools", initialized.payload["result"]["capabilities"])

            read_list = adapter.handle(
                legacy_request("tools/list"),
                authorization=f"Bearer {API_TOKEN}",
                headers={"MCP-Protocol-Version": LEGACY_PROTOCOL_VERSION},
            )
            write_list = adapter.handle(
                legacy_request("tools/list"),
                authorization=f"Bearer {WRITE_TOKEN}",
                headers={"MCP-Protocol-Version": LEGACY_PROTOCOL_VERSION},
            )
            self.assertEqual(
                [tool["name"] for tool in read_list.payload["result"]["tools"]],
                [tool["name"] for tool in READ_TOOLS],
            )
            self.assertEqual(
                set(tool["name"] for tool in read_list.payload["result"]["tools"]),
                READ_TOOL_NAMES,
            )
            self.assertEqual(
                [tool["name"] for tool in write_list.payload["result"]["tools"]],
                [tool["name"] for tool in WRITE_TOOLS],
            )
            self.assertEqual(
                set(tool["name"] for tool in write_list.payload["result"]["tools"]),
                WRITE_TOOL_NAMES,
            )
            self.assertTrue(all(tool["annotations"]["readOnlyHint"] for tool in read_list.payload["result"]["tools"]))
            self.assertTrue(all(not tool["annotations"]["readOnlyHint"] for tool in write_list.payload["result"]["tools"]))

    def test_modern_discovery_and_tools_list_require_per_request_metadata(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            discover = adapter.handle(
                modern_request("server/discover"),
                authorization=f"Bearer {API_TOKEN}",
                headers=modern_headers("server/discover"),
            )
            self.assertEqual(discover.status, 200)
            self.assertEqual(
                discover.payload["result"]["supportedVersions"],
                [MODERN_PROTOCOL_VERSION],
            )
            self.assertEqual(discover.payload["result"]["cacheScope"], "private")
            self.assertIn(
                "io.modelcontextprotocol/serverInfo",
                discover.payload["result"]["_meta"],
            )

            listing = adapter.handle(
                modern_request("tools/list"),
                authorization=f"Bearer {API_TOKEN}",
                headers=modern_headers("tools/list"),
            )
            self.assertEqual(listing.status, 200)
            self.assertEqual(listing.payload["result"]["ttlMs"], 0)
            self.assertEqual(
                set(tool["name"] for tool in listing.payload["result"]["tools"]),
                READ_TOOL_NAMES,
            )

            missing_meta = adapter.handle(
                legacy_request("tools/list"),
                authorization=f"Bearer {API_TOKEN}",
                headers=modern_headers("tools/list"),
            )
            self.assertEqual(missing_meta.status, 400)
            self.assertEqual(missing_meta.payload["error"]["code"], -32600)

    def test_modern_routing_headers_fail_closed_on_mismatch(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            request = modern_request(
                "tools/call",
                params={"name": "get_development_state", "arguments": {}},
            )
            mismatch = adapter.handle(
                request,
                authorization=f"Bearer {API_TOKEN}",
                headers=modern_headers("tools/call", name="wrong-tool"),
            )
            self.assertEqual(mismatch.status, 400)
            self.assertEqual(mismatch.payload["error"]["code"], -32020)

    def test_auth_wall_precedes_tool_dispatch_and_never_discloses_tokens(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            denied = adapter.handle(
                legacy_request("tools/list"),
                authorization=None,
                headers={},
            )
            self.assertEqual(denied.status, 401)
            serialized = json.dumps(denied.payload, sort_keys=True)
            self.assertNotIn(API_TOKEN, serialized)
            self.assertNotIn(WRITE_TOKEN, serialized)

            listing = adapter.handle(
                legacy_request("tools/list"),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            serialized = json.dumps(listing.payload, sort_keys=True)
            self.assertNotIn(API_TOKEN, serialized)
            self.assertNotIn(WRITE_TOKEN, serialized)
            self.assertNotIn(SECRET, serialized)

    def test_read_token_cannot_call_write_tool_and_write_token_cannot_call_read_tool(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            read_attempt = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "get_development_state", "arguments": {}},
                ),
                authorization=f"Bearer {WRITE_TOKEN}",
                headers={},
            )
            self.assertTrue(read_attempt.payload["result"]["isError"])
            self.assertEqual(read_attempt.payload["result"]["structuredContent"]["status"], 401)

            write_attempt = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={
                        "name": "submit_objective",
                        "arguments": {
                            "request_id": "objective-mcp-0001",
                            "objective": "Investigate landing gear",
                        },
                    },
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            self.assertTrue(write_attempt.payload["result"]["isError"])
            self.assertEqual(write_attempt.payload["result"]["structuredContent"]["status"], 401)

    def test_development_state_tool_is_exact_http_backend_snapshot(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            status, expected = runtime.handle_development_read(f"Bearer {API_TOKEN}")
            self.assertEqual(status, 200)
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "get_development_state", "arguments": {}},
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            actual = result.payload["result"]["structuredContent"]
            self.assertEqual(actual, expected)
            self.assertEqual(actual["snapshot_digest"], expected["snapshot_digest"])

    def test_objective_trace_tool_matches_canonical_backend_trace(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _ = self.runtime(root)
            submit_status, submitted = runtime.handle_objective_submit(
                f"Bearer {WRITE_TOKEN}",
                {
                    "request_id": "objective-mcp-trace-0001",
                    "objective": "Investigate landing gear",
                },
                client="trace-fixture",
            )
            self.assertEqual(submit_status, 202)
            correlation_id = submitted["proposal_id"]
            status, expected = runtime.handle_objective_trace(
                f"Bearer {API_TOKEN}",
                correlation_id,
            )
            self.assertEqual(status, 200)

            adapter = McpAdapter(runtime)
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={
                        "name": "get_objective_trace",
                        "arguments": {"correlation_id": correlation_id},
                    },
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            actual = result.payload["result"]["structuredContent"]
            self.assertEqual(actual, expected)
            self.assertEqual(actual["trace_digest"], expected["trace_digest"])
            self.assertNotIn(API_TOKEN, json.dumps(result.payload))
            self.assertNotIn(WRITE_TOKEN, json.dumps(result.payload))

    def test_human_gate_tool_joins_current_gate_without_inventing_review(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "get_human_gate", "arguments": {"gate_id": "review"}},
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            value = result.payload["result"]["structuredContent"]
            self.assertTrue(value["current"])
            self.assertEqual(value["gate"]["gate_id"], "review")
            self.assertIsNone(value["latest_review"])
            self.assertIsNone(value["artifact"])

    def test_submit_objective_uses_same_domain_command_and_server_bound_actor(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            result = adapter.handle(
                modern_request(
                    "tools/call",
                    params={
                        "name": "submit_objective",
                        "arguments": {
                            "request_id": "objective-mcp-0002",
                            "objective": "Investigate landing gear",
                        },
                    },
                ),
                authorization=f"Bearer {WRITE_TOKEN}",
                headers=modern_headers("tools/call", name="submit_objective"),
            )
            self.assertEqual(result.status, 200)
            value = result.payload["result"]["structuredContent"]
            self.assertEqual(value["actor"], WRITE_ACTOR)
            self.assertEqual(value["client"], "chatgpt-mcp")
            self.assertFalse(value["task_authority_recorded"])
            self.assertFalse(result.payload["result"]["isError"])

            spoof = adapter.handle(
                legacy_request(
                    "tools/call",
                    request_id=2,
                    params={
                        "name": "submit_objective",
                        "arguments": {
                            "request_id": "objective-mcp-0003",
                            "objective": "Investigate landing gear",
                            "actor": "mallory",
                        },
                    },
                ),
                authorization=f"Bearer {WRITE_TOKEN}",
                headers={},
            )
            self.assertTrue(spoof.payload["result"]["isError"])
            self.assertEqual(spoof.payload["result"]["structuredContent"]["status"], 400)

    def test_submit_human_review_matches_backend_reconciliation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, source_sha = self.runtime(root)
            adapter = McpAdapter(runtime)
            args = review_payload(source_sha, request_id="review-mcp-0001")
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "submit_human_review", "arguments": args},
                ),
                authorization=f"Bearer {WRITE_TOKEN}",
                headers={},
            )
            self.assertFalse(result.payload["result"]["isError"])
            value = result.payload["result"]["structuredContent"]
            self.assertEqual(value["actor"], WRITE_ACTOR)
            self.assertEqual(value["client"], "chatgpt-mcp")
            self.assertEqual(value["roadmap_disposition"], "ACCEPTED")
            snapshot = runtime.development_snapshot()
            self.assertEqual(snapshot["roadmap"]["completed_runs"]["review"], 1)
            self.assertEqual(snapshot["human_reviews"][-1]["review_id"], value["review_id"])

    def test_initialized_notification_has_no_jsonrpc_response(self):
        with tempfile.TemporaryDirectory() as td:
            runtime, _ = self.runtime(Path(td))
            adapter = McpAdapter(runtime)
            notification = {
                "jsonrpc": "2.0",
                "method": "notifications/initialized",
                "params": {},
            }
            result = adapter.handle(
                notification,
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            self.assertEqual(result.status, 202)
            self.assertIsNone(result.payload)


class McpFileArtifactTest(unittest.TestCase):
    def runtime(self, root: Path):
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

    def test_file_artifact_tool_preserves_verified_bytes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, expected = self.runtime(root)
            adapter = McpAdapter(runtime)
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "get_file_artifact", "arguments": {"artifact_id": "file:test"}},
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            tool = result.payload["result"]
            self.assertFalse(tool["isError"])
            self.assertEqual(tool["structuredContent"]["byte_size"], len(expected))
            resource = tool["content"][0]["resource"]
            self.assertEqual(resource["uri"], "skyforge-artifact://file:test")
            self.assertEqual(resource["text"].encode("utf-8"), expected)

    def test_interactive_artifact_never_fabricates_file_bytes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _expected = self.runtime(root)
            adapter = McpAdapter(runtime)
            result = adapter.handle(
                legacy_request(
                    "tools/call",
                    params={"name": "get_file_artifact", "arguments": {"artifact_id": "interactive:test"}},
                ),
                authorization=f"Bearer {API_TOKEN}",
                headers={},
            )
            self.assertTrue(result.payload["result"]["isError"])
            self.assertEqual(result.payload["result"]["structuredContent"]["status"], 409)


if __name__ == "__main__":
    unittest.main()
