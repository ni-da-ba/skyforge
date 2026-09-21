#!/usr/bin/env python3
"""Protocol-level readiness probe for the localhost Skyforge MCP sidecar."""

from __future__ import annotations

import argparse
import asyncio

from mcp import Client

READ_TOOLS = {
    "get_development_state",
    "list_review_artifacts",
    "get_review_artifact",
    "read_review_artifact_content",
}
WRITE_TOOLS = {"submit_objective", "submit_human_review"}


async def probe(url: str, *, expect_write: bool) -> None:
    async with Client(url) as client:
        result = await client.list_tools()
        names = {tool.name for tool in result.tools}
        missing = sorted(READ_TOOLS - names)
        if missing:
            raise RuntimeError("MCP read tools missing: " + ", ".join(missing))
        present_write = WRITE_TOOLS & names
        if expect_write and present_write != WRITE_TOOLS:
            raise RuntimeError("MCP write tools missing while write authority is configured")
        if not expect_write and present_write:
            raise RuntimeError("MCP write tools exposed without configured write authority")
        state = await client.call_tool("get_development_state")
        if state.is_error or not isinstance(state.structured_content, dict):
            raise RuntimeError("MCP development-state probe failed")
        if not state.structured_content.get("snapshot_digest"):
            raise RuntimeError("MCP development-state probe returned no snapshot digest")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:3001/mcp")
    parser.add_argument("--expect-write", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    asyncio.run(probe(args.url, expect_write=args.expect_write))
    print("PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
