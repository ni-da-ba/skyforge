from __future__ import annotations

import asyncio
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest

from mcp import Client

import platform_v2_hosted_runtime as hosted
from platform_v2_mcp_server import BackendApiClient, BackendApiError, create_mcp_server
from test_platform_v2_artifact_api import install_artifact_manifest
from test_platform_v2_hosted_runtime import write_legacy
from test_platform_v2_development_read_api import API_TOKEN
from test_platform_v2_human_review_api import (
    WRITE_ACTOR,
    WRITE_TOKEN,
    prepare_root,
)
from test_platform_v2_hosted_runtime import SECRET
from v2.human_review import HumanReviewStore
from v2.objective_ingress import ObjectiveProposalStore


class McpFixture(unittest.TestCase):
    def serve(self, runtime: hosted.HostedV2Substrate) -> str:
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def run_async(self, coro):
        return asyncio.run(coro)


class McpReadAdapterTest(McpFixture):
    def test_read_only_toolset_matches_rest_and_exposes_no_secrets(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _source_sha, _payload = install_artifact_manifest(root)
            write_legacy(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                trusted_actors=(WRITE_ACTOR,),
            )
            base = self.serve(runtime)
            adapter = BackendApiClient(base, API_TOKEN)
            mcp = create_mcp_server(adapter)

            async def exercise():
                async with Client(mcp) as client:
                    tools = await client.list_tools()
                    names = [tool.name for tool in tools.tools]
                    self.assertEqual(
                        names,
                        [
                            "get_development_state",
                            "list_review_artifacts",
                            "get_review_artifact",
                            "read_review_artifact_content",
                        ],
                    )
                    serialized = json.dumps(tools.model_dump(mode="json"), sort_keys=True)
                    self.assertNotIn(API_TOKEN, serialized)
                    self.assertNotIn(WRITE_TOKEN, serialized)

                    state_result = await client.call_tool("get_development_state")
                    self.assertFalse(state_result.is_error)
                    self.assertEqual(
                        state_result.structured_content,
                        runtime.development_snapshot(),
                    )

                    listing = await client.call_tool("list_review_artifacts")
                    self.assertFalse(listing.is_error)
                    self.assertEqual(listing.structured_content["artifact_count"], 2)
                    self.assertEqual(
                        [item["artifact_id"] for item in listing.structured_content["artifacts"]],
                        ["file:test", "interactive:test"],
                    )

                    exact = await client.call_tool(
                        "get_review_artifact",
                        {"artifact_id": "interactive:test"},
                    )
                    self.assertFalse(exact.is_error)
                    self.assertEqual(exact.structured_content["artifact_id"], "interactive:test")
                    self.assertEqual(
                        exact.structured_content["interactive"]["parameters"]["specimen_key"],
                        42,
                    )

                    content = await client.call_tool(
                        "read_review_artifact_content",
                        {"artifact_id": "file:test"},
                    )
                    self.assertFalse(content.is_error)
                    self.assertEqual(content.structured_content["encoding"], "utf-8")
                    self.assertEqual(content.structured_content["text"], "accepted\n")
                    self.assertEqual(content.structured_content["byte_count"], 9)

                    interactive = await client.call_tool(
                        "read_review_artifact_content",
                        {"artifact_id": "interactive:test"},
                    )
                    self.assertTrue(interactive.is_error)
                    rendered = " ".join(
                        getattr(item, "text", "") for item in interactive.content
                    )
                    self.assertIn("interactive", rendered.lower())

            self.run_async(exercise())


    def test_streamable_http_transport_is_reachable_with_official_client(self):
        import socket
        import time
        import uvicorn

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _source_sha, _payload = install_artifact_manifest(root)
            write_legacy(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                trusted_actors=(WRITE_ACTOR,),
            )
            base = self.serve(runtime)
            mcp = create_mcp_server(BackendApiClient(base, API_TOKEN))
            app = mcp.streamable_http_app(
                streamable_http_path="/mcp",
                json_response=True,
                stateless_http=True,
                max_request_body_size=256_000,
                host="127.0.0.1",
            )

            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]

            server = uvicorn.Server(
                uvicorn.Config(
                    app,
                    host="127.0.0.1",
                    port=port,
                    log_level="error",
                    access_log=False,
                )
            )
            thread = threading.Thread(target=server.run, daemon=True)
            thread.start()
            self.addCleanup(thread.join, 3)
            self.addCleanup(setattr, server, "should_exit", True)
            for _ in range(100):
                if server.started:
                    break
                time.sleep(0.02)
            self.assertTrue(server.started)

            async def exercise():
                async with Client(f"http://127.0.0.1:{port}/mcp") as client:
                    tools = await client.list_tools()
                    self.assertIn(
                        "get_development_state",
                        [tool.name for tool in tools.tools],
                    )
                    result = await client.call_tool("get_development_state")
                    self.assertFalse(result.is_error)
                    self.assertEqual(
                        result.structured_content["snapshot_digest"],
                        runtime.development_snapshot()["snapshot_digest"],
                    )

            self.run_async(exercise())

    def test_backend_origin_is_fixed_and_cannot_embed_credentials_or_paths(self):
        for bad in (
            "file:///tmp/project",
            "http://user:secret@127.0.0.1:3000",
            "http://127.0.0.1:3000/api/v1",
            "http://127.0.0.1:3000?token=x",
        ):
            with self.assertRaises(ValueError):
                BackendApiClient(bad, API_TOKEN)


class McpWriteAdapterTest(McpFixture):
    def runtime(self, root: Path) -> tuple[hosted.HostedV2Substrate, str]:
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

    def test_write_enabled_toolset_forwards_typed_commands_exactly_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, source_sha = self.runtime(root)
            base = self.serve(runtime)
            adapter = BackendApiClient(base, API_TOKEN, WRITE_TOKEN)
            mcp = create_mcp_server(adapter)

            async def exercise():
                async with Client(mcp) as client:
                    tools = await client.list_tools()
                    names = [tool.name for tool in tools.tools]
                    self.assertIn("submit_objective", names)
                    self.assertIn("submit_human_review", names)
                    serialized = json.dumps(tools.model_dump(mode="json"), sort_keys=True)
                    self.assertNotIn(API_TOKEN, serialized)
                    self.assertNotIn(WRITE_TOKEN, serialized)

                    objective_args = {
                        "request_id": "mcp-objective-0001",
                        "objective": "Investigate landing gear",
                    }
                    first = await client.call_tool("submit_objective", objective_args)
                    replay = await client.call_tool("submit_objective", objective_args)
                    self.assertFalse(first.is_error)
                    self.assertFalse(replay.is_error)
                    self.assertEqual(first.structured_content["proposal_id"], replay.structured_content["proposal_id"])
                    self.assertFalse(first.structured_content["idempotent_replay"])
                    self.assertTrue(replay.structured_content["idempotent_replay"])
                    self.assertFalse(first.structured_content["executable_task_authority"])
                    self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 1)
                    self.assertEqual(len(runtime.task_authority_store.load().records), 0)

                    review_args = {
                        "request_id": "mcp-review-0001",
                        "gate_id": "review",
                        "artifact_id": "interactive:test",
                        "source_sha": source_sha,
                        "verdict": "CHANGES_REQUIRED",
                        "findings": ["water placement still needs repair"],
                        "positive_findings": ["channel is now visible"],
                        "material_delta": "reviewed new exact interactive specimen",
                        "next_boundary": "defer product repair until platform gate completes",
                        "deferred_product_work": True,
                        "prior_review_id": None,
                    }
                    review = await client.call_tool("submit_human_review", review_args)
                    self.assertFalse(review.is_error)
                    self.assertEqual(review.structured_content["verdict"], "CHANGES_REQUIRED")
                    self.assertEqual(review.structured_content["client"], "chatgpt-mcp")
                    self.assertEqual(len(HumanReviewStore.for_root(root).load().records), 1)

                    state = await client.call_tool("get_development_state")
                    self.assertFalse(state.is_error)
                    self.assertEqual(state.structured_content["objectives"][-1]["proposal_id"], first.structured_content["proposal_id"])
                    self.assertEqual(state.structured_content["human_reviews"][-1]["review_id"], review.structured_content["review_id"])

            self.run_async(exercise())

    def test_backend_conflicts_are_mcp_tool_errors_not_adapter_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _source_sha = self.runtime(root)
            base = self.serve(runtime)
            mcp = create_mcp_server(BackendApiClient(base, API_TOKEN, WRITE_TOKEN))

            async def exercise():
                async with Client(mcp) as client:
                    first = await client.call_tool(
                        "submit_objective",
                        {"request_id": "mcp-objective-0002", "objective": "Investigate landing gear"},
                    )
                    self.assertFalse(first.is_error)
                    conflict = await client.call_tool(
                        "submit_objective",
                        {"request_id": "mcp-objective-0002", "objective": "Fix landing gear"},
                    )
                    self.assertTrue(conflict.is_error)
                    rendered = " ".join(getattr(item, "text", "") for item in conflict.content)
                    self.assertIn("conflicting payload", rendered)

            self.run_async(exercise())


if __name__ == "__main__":
    unittest.main()
