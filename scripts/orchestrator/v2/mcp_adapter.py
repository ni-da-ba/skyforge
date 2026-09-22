"""Thin dependency-free MCP adapter over the canonical Skyforge development backend.

The adapter owns no project state. It exposes the accepted development read/artifact
surfaces and typed objective/review commands through MCP tool semantics while leaving
all domain authorization, idempotence, conflict, and reconciliation inside the hosted
Platform-v2 runtime.
"""

from __future__ import annotations

from dataclasses import dataclass
import base64
import hmac
import json
from typing import Any, Mapping

MODERN_PROTOCOL_VERSION = "2026-07-28"
LEGACY_PROTOCOL_VERSION = "2025-11-25"
SUPPORTED_PROTOCOL_VERSIONS = (MODERN_PROTOCOL_VERSION, LEGACY_PROTOCOL_VERSION)
SERVER_NAME = "skyforge-development"
SERVER_VERSION = "1.0.0"
MAX_INLINE_FILE_BYTES = 8 * 1024 * 1024

_JSONRPC_PARSE_ERROR = -32700
_JSONRPC_INVALID_REQUEST = -32600
_JSONRPC_METHOD_NOT_FOUND = -32601
_JSONRPC_INVALID_PARAMS = -32602
_JSONRPC_INTERNAL_ERROR = -32603
_JSONRPC_HEADER_MISMATCH = -32020


@dataclass(frozen=True)
class McpAccess:
    can_read: bool = False
    can_write: bool = False

    @property
    def any(self) -> bool:
        return self.can_read or self.can_write


@dataclass(frozen=True)
class McpHttpResult:
    status: int
    payload: dict[str, Any] | None
    headers: Mapping[str, str] | None = None


def _object_schema(
    properties: Mapping[str, Any] | None = None,
    *,
    required: tuple[str, ...] = (),
) -> dict[str, Any]:
    schema: dict[str, Any] = {
        "type": "object",
        "properties": dict(properties or {}),
        "additionalProperties": False,
    }
    if required:
        schema["required"] = list(required)
    return schema


def _string_schema(description: str) -> dict[str, Any]:
    return {"type": "string", "minLength": 1, "description": description}


def _string_list_schema(description: str) -> dict[str, Any]:
    return {
        "type": "array",
        "items": {"type": "string", "minLength": 1},
        "description": description,
    }


def _read_annotations() -> dict[str, bool]:
    return {
        "readOnlyHint": True,
        "destructiveHint": False,
        "idempotentHint": True,
        "openWorldHint": False,
    }


def _write_annotations(*, destructive: bool = False) -> dict[str, bool]:
    return {
        "readOnlyHint": False,
        "destructiveHint": destructive,
        "idempotentHint": True,
        "openWorldHint": False,
    }


READ_TOOLS: tuple[dict[str, Any], ...] = (
    {
        "name": "get_development_state",
        "title": "Get Skyforge development state",
        "description": (
            "Read the canonical current Skyforge project/objective/worker/human-gate/platform "
            "snapshot. This is read-only and returns the same snapshot identity as the "
            "Operations Console development-state API."
        ),
        "inputSchema": _object_schema(),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "get_current_product_state",
        "title": "Get current Skyforge product state",
        "description": (
            "Read the canonical human-facing current product/program boundary derived by the "
            "shared development backend. Historical reviews remain context and cannot override "
            "an active Continue Skyforge task, review gate, or strategic boundary."
        ),
        "inputSchema": _object_schema(),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "get_objective_trace",
        "title": "Get Skyforge objective trace",
        "description": (
            "Read one exact durable objective correlation trace from objective proposal through "
            "context, task authority, worker, repository effects, and completion where those "
            "persisted stages exist. No fuzzy title/text matching or client-side correlation occurs."
        ),
        "inputSchema": _object_schema(
            {
                "correlation_id": _string_schema(
                    "Exact durable objective correlation/proposal identifier."
                )
            },
            required=("correlation_id",),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "list_review_artifacts",
        "title": "List Skyforge review artifacts",
        "description": (
            "Read the canonical registered review-artifact catalog. Interactive specimens "
            "include deterministic preparation/launch metadata; no arbitrary filesystem access occurs."
        ),
        "inputSchema": _object_schema(),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "get_review_artifact",
        "title": "Get exact Skyforge review artifact",
        "description": (
            "Read one exact registered artifact manifest by artifact_id, including immutable "
            "source-SHA provenance and interactive review metadata where applicable."
        ),
        "inputSchema": _object_schema(
            {"artifact_id": _string_schema("Exact registered artifact identifier.")},
            required=("artifact_id",),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "get_file_artifact",
        "title": "Retrieve verified Skyforge file artifact",
        "description": (
            "Retrieve bytes for one registered immutable FILE artifact after the canonical "
            "source-SHA/path/size/SHA-256 checks. Interactive specimens intentionally have no file bytes."
        ),
        "inputSchema": _object_schema(
            {"artifact_id": _string_schema("Exact registered FILE artifact identifier.")},
            required=("artifact_id",),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
    {
        "name": "get_human_gate",
        "title": "Get Skyforge human-gate context",
        "description": (
            "Read an exact human gate plus its latest durable review, material delta, and artifact "
            "manifest from the canonical development snapshot. This never accepts or changes a gate."
        ),
        "inputSchema": _object_schema(
            {"gate_id": _string_schema("Exact roadmap human-gate node identifier.")},
            required=("gate_id",),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _read_annotations(),
    },
)

WRITE_TOOLS: tuple[dict[str, Any], ...] = (
    {
        "name": "submit_objective",
        "title": "Submit Skyforge objective",
        "description": (
            "State-changing typed command. Persist one natural-language objective proposal through "
            "the accepted exactly-once objective pipeline. This never creates task authority directly."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "objective": _string_schema("Natural-language Skyforge development objective."),
            },
            required=("request_id", "objective"),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
    {
        "name": "continue_skyforge",
        "title": "Continue Skyforge",
        "description": (
            "State-changing ergonomic wrapper for the exact typed objective Continue Skyforge. "
            "It reuses the accepted objective/program-progression pipeline, stops at real human, "
            "strategic, ownership, and safety boundaries, and never creates task authority directly."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
            },
            required=("request_id",),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
    {
        "name": "pause_objective",
        "title": "Pause Skyforge objective",
        "description": (
            "Pause future automatic progression for one exact objective at the next safe durable "
            "boundary. This does not kill an already-running provider process or mutate remote authority."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "proposal_id": _string_schema("Exact durable objective proposal/correlation identifier."),
                "reason": _string_schema("Operator reason for pausing this objective."),
            },
            required=("request_id", "proposal_id", "reason"),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
    {
        "name": "resume_objective",
        "title": "Resume Skyforge objective",
        "description": (
            "Remove the pause from one exact non-cancelled objective and wake ordinary model-free "
            "reconciliation. Existing authority, quota, recovery, and human gates still apply."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "proposal_id": _string_schema("Exact durable objective proposal/correlation identifier."),
                "reason": _string_schema("Operator reason for resuming this objective."),
            },
            required=("request_id", "proposal_id", "reason"),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
    {
        "name": "cancel_objective",
        "title": "Cancel Skyforge objective",
        "description": (
            "Terminally fence future automatic progression for one exact objective and its "
            "not-yet-executing program descendants. This does not delete remote work or completed history."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "proposal_id": _string_schema("Exact durable objective proposal/correlation identifier."),
                "reason": _string_schema("Explicit operator reason for cancellation."),
            },
            required=("request_id", "proposal_id", "reason"),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(destructive=True),
    },
    {
        "name": "reconcile_objective",
        "title": "Reconcile Skyforge objective",
        "description": (
            "Wake model-free reconciliation for one exact objective without changing its lifecycle "
            "state. Exactly-once, claim, quota, recovery, and human-gate boundaries remain authoritative."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "proposal_id": _string_schema("Exact durable objective proposal/correlation identifier."),
                "reason": _string_schema("Reason for requesting reconciliation."),
            },
            required=("request_id", "proposal_id", "reason"),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
    {
        "name": "submit_human_review",
        "title": "Submit Skyforge human review",
        "description": (
            "State-changing human judgment. Persist and reconcile one explicit review against an exact "
            "current gate and registered artifact. The server never chooses the verdict for the human."
        ),
        "inputSchema": _object_schema(
            {
                "request_id": _string_schema("Immutable client-generated idempotency identifier."),
                "gate_id": _string_schema("Exact current human-gate identifier."),
                "artifact_id": _string_schema("Exact registered artifact identifier."),
                "source_sha": _string_schema("Exact 40-character artifact source Git SHA."),
                "verdict": {
                    "type": "string",
                    "enum": ["ACCEPTED", "CHANGES_REQUIRED"],
                    "description": "Explicit human-supplied verdict.",
                },
                "findings": _string_list_schema("Required human findings/judgment notes."),
                "positive_findings": _string_list_schema("Optional positive observations."),
                "material_delta": _string_schema("Material change actually reviewed since the prior gate review."),
                "next_boundary": _string_schema("Next work boundary after this judgment."),
                "deferred_product_work": {
                    "type": "boolean",
                    "description": "Whether product work should remain deferred after this judgment.",
                },
                "prior_review_id": {
                    "type": ["string", "null"],
                    "description": "Exact prior review id for repeated review, or null when none exists.",
                },
            },
            required=(
                "request_id",
                "gate_id",
                "artifact_id",
                "source_sha",
                "verdict",
                "findings",
                "positive_findings",
                "material_delta",
                "next_boundary",
                "deferred_product_work",
                "prior_review_id",
            ),
        ),
        "outputSchema": {"type": "object"},
        "annotations": _write_annotations(),
    },
)

READ_TOOL_NAMES = frozenset(tool["name"] for tool in READ_TOOLS)
WRITE_TOOL_NAMES = frozenset(tool["name"] for tool in WRITE_TOOLS)
ALL_TOOL_NAMES = READ_TOOL_NAMES | WRITE_TOOL_NAMES


class McpAdapter:
    def __init__(self, runtime: Any) -> None:
        self.runtime = runtime

    @property
    def instructions(self) -> str:
        return (
            "Skyforge development workflow tools. Project truth comes from the canonical Platform-v2 "
            "backend, not conversation history. Read tools return current state/artifact identities. "
            "Write tools are typed domain commands and remain subject to backend idempotence, ownership, "
            "artifact-provenance, X-4 human-review, and roadmap rules. Never infer or self-select a "
            "subjective human-review verdict."
        )

    def authorize(self, authorization: str | None) -> tuple[int, McpAccess | None]:
        supplied = str(authorization or "")
        if not self.runtime.development_api_token and not self.runtime.development_write_token:
            return 503, None
        read = False
        write = False
        if self.runtime.development_api_token:
            read = hmac.compare_digest(
                supplied,
                f"Bearer {self.runtime.development_api_token}",
            )
        if self.runtime.development_write_token:
            write = hmac.compare_digest(
                supplied,
                f"Bearer {self.runtime.development_write_token}",
            )
        if not (read or write):
            return 401, None
        return 200, McpAccess(can_read=read, can_write=write)

    def tools_for(self, access: McpAccess) -> tuple[dict[str, Any], ...]:
        values: list[dict[str, Any]] = []
        if access.can_read:
            values.extend(READ_TOOLS)
        if access.can_write and self.runtime.development_write_token:
            values.extend(WRITE_TOOLS)
        return tuple(values)

    def _server_meta(self) -> dict[str, Any]:
        return {
            "io.modelcontextprotocol/serverInfo": {
                "name": SERVER_NAME,
                "version": SERVER_VERSION,
            }
        }

    def _rpc_result(
        self,
        request_id: Any,
        result: Mapping[str, Any],
        *,
        modern: bool,
    ) -> dict[str, Any]:
        body = dict(result)
        if modern:
            meta = dict(body.get("_meta") or {})
            meta.update(self._server_meta())
            body["_meta"] = meta
        return {"jsonrpc": "2.0", "id": request_id, "result": body}

    def _rpc_error(
        self,
        request_id: Any,
        code: int,
        message: str,
        *,
        data: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        error: dict[str, Any] = {"code": code, "message": message}
        if data is not None:
            error["data"] = dict(data)
        return {"jsonrpc": "2.0", "id": request_id, "error": error}

    def _tool_ok(
        self,
        value: Mapping[str, Any],
        summary: str,
        *,
        content: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        return {
            "content": content or [{"type": "text", "text": summary}],
            "structuredContent": dict(value),
            "isError": False,
        }

    def _tool_error(self, status: int, payload: Mapping[str, Any]) -> dict[str, Any]:
        error = str(payload.get("error") or "Skyforge backend operation failed")
        value = {"status": status, **dict(payload)}
        return {
            "content": [{"type": "text", "text": f"Skyforge backend error ({status}): {error}"}],
            "structuredContent": value,
            "isError": True,
        }

    def _read_authorization(self) -> str:
        return f"Bearer {self.runtime.development_api_token}"

    def _write_authorization(self) -> str:
        return f"Bearer {self.runtime.development_write_token}"

    def _call_read_tool(self, name: str, arguments: Mapping[str, Any]) -> dict[str, Any]:
        if name == "get_development_state":
            status, payload = self.runtime.handle_development_read(self._read_authorization())
            if status != 200:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                f"Canonical Skyforge development snapshot {payload.get('snapshot_digest', '')}.",
            )

        if name == "get_current_product_state":
            status, payload = self.runtime.handle_development_read(self._read_authorization())
            if status != 200:
                return self._tool_error(status, payload)
            product = payload.get("current_product_state")
            if not isinstance(product, Mapping):
                return self._tool_error(
                    500,
                    {"error": "canonical current product state is unavailable"},
                )
            value = {
                "snapshot_digest": payload.get("snapshot_digest"),
                "current_product_state": dict(product),
            }
            return self._tool_ok(
                value,
                (
                    "Current Skyforge product state: "
                    f"{product.get('status', '')}; "
                    f"node={product.get('node_id') or 'none'}."
                ),
            )

        if name == "get_objective_trace":
            correlation_id = str(arguments.get("correlation_id") or "").strip()
            if not correlation_id:
                return self._tool_error(
                    400,
                    {"error": "correlation_id is required"},
                )
            status, payload = self.runtime.handle_objective_trace(
                self._read_authorization(),
                correlation_id,
            )
            if status != 200:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                (
                    f"Skyforge objective trace {payload.get('correlation_id', '')}: "
                    f"{payload.get('terminal_stage', '')}/"
                    f"{payload.get('terminal_status', '')}."
                ),
            )

        if name == "list_review_artifacts":
            status, payload = self.runtime.handle_artifact_list(self._read_authorization())
            if status != 200:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                f"Skyforge review artifact catalog contains {payload.get('artifact_count', 0)} artifacts.",
            )

        if name == "get_review_artifact":
            artifact_id = str(arguments.get("artifact_id") or "").strip()
            if not artifact_id:
                return self._tool_error(400, {"error": "artifact_id is required"})
            status, payload = self.runtime.handle_artifact_get(
                self._read_authorization(), artifact_id
            )
            if status != 200:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                f"Skyforge review artifact {artifact_id} ({payload.get('kind', '')}) at source {payload.get('source_sha', '')}.",
            )

        if name == "get_file_artifact":
            artifact_id = str(arguments.get("artifact_id") or "").strip()
            if not artifact_id:
                return self._tool_error(400, {"error": "artifact_id is required"})
            meta_status, metadata = self.runtime.handle_artifact_get(
                self._read_authorization(), artifact_id
            )
            if meta_status != 200:
                return self._tool_error(meta_status, metadata)
            status, media_type, payload = self.runtime.handle_artifact_content(
                self._read_authorization(), artifact_id
            )
            if status != 200 or not isinstance(payload, bytes) or media_type is None:
                if isinstance(payload, Mapping):
                    return self._tool_error(status, payload)
                return self._tool_error(status, {"error": "artifact content is unavailable"})
            if len(payload) > MAX_INLINE_FILE_BYTES:
                return self._tool_error(
                    413,
                    {
                        "error": "artifact is too large for inline MCP transfer",
                        "artifact_id": artifact_id,
                        "byte_size": len(payload),
                        "max_inline_bytes": MAX_INLINE_FILE_BYTES,
                    },
                )
            encoded = base64.b64encode(payload).decode("ascii")
            value = {
                "artifact": metadata,
                "media_type": media_type,
                "byte_size": len(payload),
                "encoding": "base64",
            }
            if media_type.startswith("image/"):
                content = [{"type": "image", "data": encoded, "mimeType": media_type}]
            else:
                resource: dict[str, Any] = {
                    "uri": f"skyforge-artifact://{artifact_id}",
                    "mimeType": media_type,
                }
                if media_type.startswith("text/"):
                    try:
                        resource["text"] = payload.decode("utf-8")
                    except UnicodeDecodeError:
                        resource["blob"] = encoded
                else:
                    resource["blob"] = encoded
                content = [{"type": "resource", "resource": resource}]
            return self._tool_ok(
                value,
                f"Retrieved verified Skyforge file artifact {artifact_id} ({len(payload)} bytes).",
                content=content,
            )

        if name == "get_human_gate":
            gate_id = str(arguments.get("gate_id") or "").strip()
            if not gate_id:
                return self._tool_error(400, {"error": "gate_id is required"})
            status, snapshot = self.runtime.handle_development_read(self._read_authorization())
            if status != 200:
                return self._tool_error(status, snapshot)
            current_gate = next(
                (gate for gate in snapshot.get("human_gates", []) if gate.get("gate_id") == gate_id),
                None,
            )
            reviews = [
                review
                for review in snapshot.get("human_reviews", [])
                if review.get("gate_id") == gate_id
            ]
            latest_review = reviews[-1] if reviews else None
            if current_gate is None and latest_review is None:
                return self._tool_error(404, {"error": "human gate not found", "gate_id": gate_id})
            artifact = latest_review.get("artifact") if isinstance(latest_review, Mapping) else None
            value = {
                "snapshot_digest": snapshot.get("snapshot_digest"),
                "gate_id": gate_id,
                "current": current_gate is not None,
                "gate": current_gate,
                "latest_review": latest_review,
                "artifact": artifact,
            }
            verdict = latest_review.get("verdict") if isinstance(latest_review, Mapping) else "unreviewed"
            return self._tool_ok(
                value,
                f"Skyforge human gate {gate_id}: current={current_gate is not None}, latest verdict={verdict}.",
            )

        return self._tool_error(404, {"error": "unknown read tool", "tool": name})

    def _call_write_tool(self, name: str, arguments: Mapping[str, Any]) -> dict[str, Any]:
        if name == "submit_objective":
            status, payload = self.runtime.handle_objective_submit(
                self._write_authorization(),
                arguments,
                client="chatgpt-mcp",
            )
            if status not in {200, 202}:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                (
                    f"Durable Skyforge objective proposal {payload.get('proposal_id', '')} reconciled "
                    f"with disposition {payload.get('objective_disposition', '')}."
                ),
            )

        if name == "continue_skyforge":
            request = {
                "request_id": arguments.get("request_id"),
                "objective": "Continue Skyforge",
            }
            status, payload = self.runtime.handle_objective_submit(
                self._write_authorization(),
                request,
                client="chatgpt-mcp",
            )
            if status not in {200, 202}:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                (
                    f"Continue Skyforge proposal {payload.get('proposal_id', '')} reconciled "
                    f"with disposition {payload.get('objective_disposition', '')}."
                ),
            )

        lifecycle_operations = {
            "pause_objective": "PAUSE",
            "resume_objective": "RESUME",
            "cancel_objective": "CANCEL",
            "reconcile_objective": "RECONCILE",
        }
        if name in lifecycle_operations:
            request = dict(arguments)
            request["operation"] = lifecycle_operations[name]
            status, payload = self.runtime.handle_objective_control(
                self._write_authorization(),
                request,
                client="chatgpt-mcp",
            )
            if status not in {200, 202}:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                (
                    f"Skyforge objective {payload.get('proposal_id', '')} "
                    f"{payload.get('operation', '').lower()} command reconciled; "
                    f"state={payload.get('state', '')}."
                ),
            )

        if name == "submit_human_review":
            status, payload = self.runtime.handle_human_review_submit(
                self._write_authorization(),
                arguments,
                client="chatgpt-mcp",
            )
            if status not in {200, 202}:
                return self._tool_error(status, payload)
            return self._tool_ok(
                payload,
                (
                    f"Durable Skyforge human review {payload.get('review_id', '')} reconciled "
                    f"as {payload.get('verdict', '')}."
                ),
            )

        return self._tool_error(404, {"error": "unknown write tool", "tool": name})

    def _call_tool(
        self,
        access: McpAccess,
        name: str,
        arguments: Any,
    ) -> dict[str, Any] | None:
        if not isinstance(arguments, Mapping):
            return None
        if name in READ_TOOL_NAMES:
            if not access.can_read:
                return self._tool_error(401, {"error": "read authority is required for this MCP tool"})
            return self._call_read_tool(name, arguments)
        if name in WRITE_TOOL_NAMES:
            if not access.can_write:
                return self._tool_error(401, {"error": "write authority is required for this MCP tool"})
            return self._call_write_tool(name, arguments)
        return None

    def _modern_validation_error(
        self,
        message: Mapping[str, Any],
        headers: Mapping[str, str],
    ) -> tuple[int, dict[str, Any]] | None:
        request_id = message.get("id")
        method = str(message.get("method") or "")
        protocol_header = str(headers.get("MCP-Protocol-Version") or headers.get("Mcp-Protocol-Version") or "")
        method_header = str(headers.get("Mcp-Method") or "")
        if protocol_header != MODERN_PROTOCOL_VERSION:
            return 400, self._rpc_error(
                request_id,
                _JSONRPC_HEADER_MISMATCH,
                "MCP-Protocol-Version header mismatch",
                data={"supportedVersions": [MODERN_PROTOCOL_VERSION]},
            )
        if method_header != method:
            return 400, self._rpc_error(
                request_id,
                _JSONRPC_HEADER_MISMATCH,
                "Mcp-Method header mismatch",
            )
        if method == "tools/call":
            params = message.get("params")
            name = str(params.get("name") or "") if isinstance(params, Mapping) else ""
            if str(headers.get("Mcp-Name") or "") != name:
                return 400, self._rpc_error(
                    request_id,
                    _JSONRPC_HEADER_MISMATCH,
                    "Mcp-Name header mismatch",
                )
        params = message.get("params")
        meta = params.get("_meta") if isinstance(params, Mapping) else None
        if not isinstance(meta, Mapping):
            return 400, self._rpc_error(
                request_id,
                _JSONRPC_INVALID_REQUEST,
                "modern MCP request requires params._meta",
            )
        if meta.get("io.modelcontextprotocol/protocolVersion") != MODERN_PROTOCOL_VERSION:
            return 400, self._rpc_error(
                request_id,
                _JSONRPC_INVALID_REQUEST,
                "modern MCP protocolVersion metadata is missing or unsupported",
                data={"supportedVersions": [MODERN_PROTOCOL_VERSION]},
            )
        if not isinstance(meta.get("io.modelcontextprotocol/clientCapabilities"), Mapping):
            return 400, self._rpc_error(
                request_id,
                _JSONRPC_INVALID_REQUEST,
                "modern MCP clientCapabilities metadata is required",
            )
        return None

    def handle(
        self,
        message: Any,
        *,
        authorization: str | None,
        headers: Mapping[str, str],
    ) -> McpHttpResult:
        auth_status, access = self.authorize(authorization)
        if access is None:
            error = "MCP authorization is unavailable" if auth_status == 503 else "MCP bearer authorization required"
            return McpHttpResult(
                auth_status,
                {"error": error},
                headers={"WWW-Authenticate": 'Bearer realm="skyforge-mcp"'},
            )
        if not isinstance(message, Mapping):
            return McpHttpResult(
                400,
                self._rpc_error(None, _JSONRPC_INVALID_REQUEST, "JSON-RPC request must be an object"),
            )
        if message.get("jsonrpc") != "2.0":
            return McpHttpResult(
                400,
                self._rpc_error(message.get("id"), _JSONRPC_INVALID_REQUEST, "jsonrpc must be 2.0"),
            )
        method = str(message.get("method") or "")
        if not method:
            return McpHttpResult(
                400,
                self._rpc_error(message.get("id"), _JSONRPC_INVALID_REQUEST, "JSON-RPC method is required"),
            )

        modern = (
            method == "server/discover"
            or str(headers.get("MCP-Protocol-Version") or headers.get("Mcp-Protocol-Version") or "")
            == MODERN_PROTOCOL_VERSION
        )
        if modern:
            validation = self._modern_validation_error(message, headers)
            if validation is not None:
                status, payload = validation
                return McpHttpResult(status, payload)

        request_id = message.get("id")
        params = message.get("params")
        if params is None:
            params = {}
        if not isinstance(params, Mapping):
            return McpHttpResult(
                400,
                self._rpc_error(request_id, _JSONRPC_INVALID_PARAMS, "params must be an object"),
            )

        if method == "notifications/initialized":
            return McpHttpResult(202, None)

        if request_id is None:
            return McpHttpResult(202, None)

        if method == "initialize":
            requested = str(params.get("protocolVersion") or "")
            negotiated = LEGACY_PROTOCOL_VERSION
            result = {
                "protocolVersion": negotiated,
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
                "instructions": self.instructions,
            }
            return McpHttpResult(200, self._rpc_result(request_id, result, modern=False))

        if method == "server/discover":
            result = {
                "supportedVersions": [MODERN_PROTOCOL_VERSION],
                "capabilities": {"tools": {}},
                "instructions": self.instructions,
                "ttlMs": 0,
                "cacheScope": "private",
            }
            return McpHttpResult(200, self._rpc_result(request_id, result, modern=True))

        if method == "ping":
            if modern:
                return McpHttpResult(
                    200,
                    self._rpc_error(request_id, _JSONRPC_METHOD_NOT_FOUND, "Method not found"),
                )
            return McpHttpResult(200, self._rpc_result(request_id, {}, modern=False))

        if method == "tools/list":
            result: dict[str, Any] = {"tools": [dict(tool) for tool in self.tools_for(access)]}
            if modern:
                result.update({"ttlMs": 0, "cacheScope": "private"})
            return McpHttpResult(200, self._rpc_result(request_id, result, modern=modern))

        if method == "tools/call":
            name = str(params.get("name") or "").strip()
            arguments = params.get("arguments") or {}
            if not name or name not in ALL_TOOL_NAMES:
                return McpHttpResult(
                    200,
                    self._rpc_error(request_id, _JSONRPC_INVALID_PARAMS, "Unknown MCP tool"),
                )
            result = self._call_tool(access, name, arguments)
            if result is None:
                return McpHttpResult(
                    200,
                    self._rpc_error(request_id, _JSONRPC_INVALID_PARAMS, "Tool arguments must be an object"),
                )
            return McpHttpResult(200, self._rpc_result(request_id, result, modern=modern))

        return McpHttpResult(
            200,
            self._rpc_error(request_id, _JSONRPC_METHOD_NOT_FOUND, "Method not found"),
        )
