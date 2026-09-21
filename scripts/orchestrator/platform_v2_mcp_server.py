"""Bounded MCP adapter for the canonical Skyforge development HTTP API.

This process is deliberately not a Platform-v2 state owner.  It has no state-store,
repository, shell, or Git capability.  Every project read/write is forwarded through
an already-accepted development API endpoint.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from dataclasses import dataclass
from typing import Any, Mapping
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

from mcp.server import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations


DEFAULT_BACKEND_URL = "http://127.0.0.1:3000"
DEFAULT_BIND = "127.0.0.1"
DEFAULT_PORT = 3001
MAX_ARTIFACT_CONTENT_BYTES = 2_000_000


class BackendApiError(RuntimeError):
    def __init__(self, status: int, message: str, payload: Any = None) -> None:
        super().__init__(message)
        self.status = int(status)
        self.payload = payload


@dataclass(frozen=True)
class BackendApiClient:
    base_url: str
    read_token: str
    write_token: str = ""
    timeout_seconds: float = 10.0

    def __post_init__(self) -> None:
        base = str(self.base_url or "").strip().rstrip("/")
        parsed = urlsplit(base)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError("MCP backend URL must be an absolute http(s) origin")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("MCP backend URL must not contain credentials/query/fragment")
        if parsed.path not in {"", "/"}:
            raise ValueError("MCP backend URL must be an origin without a path")
        token = str(self.read_token or "").strip()
        if len(token) < 32:
            raise ValueError("MCP backend read token must be at least 32 characters")
        write = str(self.write_token or "").strip()
        if write and len(write) < 32:
            raise ValueError("MCP backend write token must be at least 32 characters")
        object.__setattr__(self, "base_url", base)
        object.__setattr__(self, "read_token", token)
        object.__setattr__(self, "write_token", write)

    @property
    def write_enabled(self) -> bool:
        return bool(self.write_token)

    def _request_json(
        self,
        method: str,
        path: str,
        *,
        write: bool = False,
        payload: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        token = self.write_token if write else self.read_token
        if write and not token:
            raise BackendApiError(503, "MCP write tools are not configured")
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "X-Skyforge-Client": "chatgpt-mcp",
        }
        data = None
        if payload is not None:
            data = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = Request(
            self.base_url + path,
            data=data,
            method=method,
            headers=headers,
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                body = response.read()
                if not body:
                    return {}
                decoded = json.loads(body.decode("utf-8"))
                if not isinstance(decoded, dict):
                    raise BackendApiError(response.status, "backend returned non-object JSON")
                return decoded
        except HTTPError as exc:
            body = exc.read()
            decoded: Any = None
            if body:
                try:
                    decoded = json.loads(body.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    decoded = None
            message = (
                str(decoded.get("error"))
                if isinstance(decoded, Mapping) and decoded.get("error")
                else f"backend HTTP {exc.code}"
            )
            raise BackendApiError(exc.code, message, decoded) from None
        except (URLError, TimeoutError, OSError) as exc:
            raise BackendApiError(503, "Skyforge development backend is unavailable") from exc
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise BackendApiError(502, "backend returned invalid JSON") from exc

    def get_development_state(self) -> dict[str, Any]:
        return self._request_json("GET", "/api/v1/development-state")

    def list_artifacts(self) -> dict[str, Any]:
        return self._request_json("GET", "/api/v1/artifacts")

    def get_artifact(self, artifact_id: str) -> dict[str, Any]:
        artifact = str(artifact_id or "").strip()
        if not artifact:
            raise ValueError("artifact_id is required")
        return self._request_json("GET", "/api/v1/artifacts/" + quote(artifact, safe=""))

    def read_artifact_content(self, artifact_id: str) -> dict[str, Any]:
        artifact = str(artifact_id or "").strip()
        if not artifact:
            raise ValueError("artifact_id is required")
        path = "/api/v1/artifacts/" + quote(artifact, safe="") + "/content"
        request = Request(
            self.base_url + path,
            method="GET",
            headers={
                "Authorization": f"Bearer {self.read_token}",
                "Accept": "*/*",
                "X-Skyforge-Client": "chatgpt-mcp",
            },
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                data = response.read(MAX_ARTIFACT_CONTENT_BYTES + 1)
                if len(data) > MAX_ARTIFACT_CONTENT_BYTES:
                    raise BackendApiError(413, "artifact exceeds MCP content limit")
                media_type = str(response.headers.get("Content-Type") or "application/octet-stream")
        except HTTPError as exc:
            body = exc.read()
            decoded: Any = None
            if body:
                try:
                    decoded = json.loads(body.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    decoded = None
            message = (
                str(decoded.get("error"))
                if isinstance(decoded, Mapping) and decoded.get("error")
                else f"backend HTTP {exc.code}"
            )
            raise BackendApiError(exc.code, message, decoded) from None
        except (URLError, TimeoutError, OSError) as exc:
            raise BackendApiError(503, "Skyforge development backend is unavailable") from exc

        result: dict[str, Any] = {
            "artifact_id": artifact,
            "media_type": media_type,
            "byte_count": len(data),
            "sha256": hashlib.sha256(data).hexdigest(),
        }
        try:
            result["text"] = data.decode("utf-8")
            result["encoding"] = "utf-8"
        except UnicodeDecodeError:
            result["content_base64"] = base64.b64encode(data).decode("ascii")
            result["encoding"] = "base64"
        return result

    def submit_objective(self, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request_json("POST", "/api/v1/objectives", write=True, payload=payload)

    def submit_human_review(self, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request_json("POST", "/api/v1/human-reviews", write=True, payload=payload)


def _tool_result(callable_):
    try:
        return callable_()
    except BackendApiError as exc:
        raise ToolError(f"Skyforge backend HTTP {exc.status}: {exc}") from None
    except ValueError as exc:
        raise ToolError(str(exc)) from None


def create_mcp_server(client: BackendApiClient) -> MCPServer:
    server = MCPServer(
        name="skyforge-development",
        title="Skyforge Development",
        description=(
            "Current Skyforge development state and exact review artifacts from the canonical "
            "Platform-v2 backend. Project truth lives in the backend, not this MCP adapter."
        ),
        instructions=(
            "Use read tools to inspect current authoritative workflow state. Never infer acceptance "
            "from artifact appearance. Human-review verdicts must come from the user."
        ),
        version="1",
    )

    @server.tool(
        description="Return the canonical current Skyforge development snapshot.",
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def get_development_state() -> dict[str, Any]:
        return _tool_result(client.get_development_state)

    @server.tool(
        description="List exact registered human-review artifact manifests.",
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def list_review_artifacts() -> dict[str, Any]:
        return _tool_result(client.list_artifacts)

    @server.tool(
        description="Get one exact registered human-review artifact manifest by artifact id.",
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def get_review_artifact(artifact_id: str) -> dict[str, Any]:
        return _tool_result(lambda: client.get_artifact(artifact_id))

    @server.tool(
        description=(
            "Read content bytes for a registered FILE review artifact only. Interactive artifacts "
            "have no fake file content and are rejected by the backend."
        ),
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def read_review_artifact_content(artifact_id: str) -> dict[str, Any]:
        return _tool_result(lambda: client.read_artifact_content(artifact_id))

    if client.write_enabled:
        @server.tool(
            description=(
                "Submit a natural-language development objective through the canonical typed backend. "
                "This records a proposal only; it does not directly create task authority."
            ),
            annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=True, openWorldHint=False),
            structured_output=True,
        )
        def submit_objective(request_id: str, objective: str) -> dict[str, Any]:
            return _tool_result(lambda: client.submit_objective({"request_id": request_id, "objective": objective}))

        @server.tool(
            description=(
                "Record a human judgment for an exact current gate and registered artifact through "
                "the canonical backend. Never choose the verdict on the human's behalf."
            ),
            annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=True, openWorldHint=False),
            structured_output=True,
        )
        def submit_human_review(
            request_id: str,
            gate_id: str,
            artifact_id: str,
            source_sha: str,
            verdict: str,
            findings: list[str],
            material_delta: str,
            next_boundary: str,
            deferred_product_work: bool,
            positive_findings: list[str] | None = None,
            prior_review_id: str | None = None,
        ) -> dict[str, Any]:
            return _tool_result(
                lambda: client.submit_human_review(
                    {
                        "request_id": request_id,
                        "gate_id": gate_id,
                        "artifact_id": artifact_id,
                        "source_sha": source_sha,
                        "verdict": verdict,
                        "findings": findings,
                        "positive_findings": positive_findings or [],
                        "material_delta": material_delta,
                        "next_boundary": next_boundary,
                        "deferred_product_work": deferred_product_work,
                        "prior_review_id": prior_review_id,
                    }
                )
            )

    return server


def client_from_environment() -> BackendApiClient:
    return BackendApiClient(
        base_url=os.environ.get("SKYFORGE_MCP_BACKEND_URL", DEFAULT_BACKEND_URL),
        read_token=os.environ.get("SKYFORGE_DEVELOPMENT_API_TOKEN", ""),
        write_token=os.environ.get("SKYFORGE_DEVELOPMENT_WRITE_TOKEN", ""),
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default=os.environ.get("SKYFORGE_MCP_BIND", DEFAULT_BIND))
    parser.add_argument("--port", type=int, default=int(os.environ.get("SKYFORGE_MCP_PORT", str(DEFAULT_PORT))))
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.bind not in {"127.0.0.1", "::1", "localhost"}:
        raise SystemExit("Skyforge MCP adapter is localhost-only; use a secure tunnel/proxy for remote clients")
    if args.port <= 0 or args.port > 65535:
        raise SystemExit("invalid MCP port")
    client = client_from_environment()
    server = create_mcp_server(client)
    app = server.streamable_http_app(
        streamable_http_path="/mcp",
        json_response=True,
        stateless_http=True,
        max_request_body_size=256_000,
        host=args.bind,
    )
    import uvicorn

    uvicorn.run(app, host=args.bind, port=args.port, log_level="info")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
