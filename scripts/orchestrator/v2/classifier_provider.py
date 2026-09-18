"""Durable ephemeral classifier proposal provider for Platform v2 R5C5."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
import json
import os
from pathlib import Path
import re
from typing import Any, Mapping, Protocol

from .decision import ClassifierDecision
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter
from .worker_provider import classify_provider_failure


CLASSIFIER_RUNS_RELATIVE_PATH = Path(".skyforge-platform-v2") / "classifier-runs.json"
CLASSIFIER_RUNS_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "classifier-runs.json.bak"
)


@dataclass(frozen=True)
class ClassifierProviderConfig:
    model: str
    reasoning_effort: str

    def __post_init__(self) -> None:
        if not self.model.strip() or not self.reasoning_effort.strip():
            raise ValueError("classifier model/reasoning must be non-empty")

    @property
    def digest(self) -> str:
        return canonical_digest({
            "model": self.model,
            "reasoning_effort": self.reasoning_effort,
        })


def classifier_provider_config(
    environ: Mapping[str, str] | None = None,
) -> ClassifierProviderConfig:
    env = os.environ if environ is None else environ
    model = str(env.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna")).strip()
    effort = str(env.get("SKYFORGE_ORCHESTRATOR_REASONING", "low")).strip()
    return ClassifierProviderConfig(model=model, reasoning_effort=effort)


@dataclass(frozen=True)
class ClassifierRequest:
    current_main: str
    semantic_input: Mapping[str, Any]
    instructions_version: int = 1

    def __post_init__(self) -> None:
        if (
            len(self.current_main) != 40
            or any(ch not in "0123456789abcdef" for ch in self.current_main)
        ):
            raise ValueError("current_main must be lowercase 40-character Git SHA")
        if not isinstance(self.semantic_input, Mapping):
            raise ValueError("semantic_input must be a mapping")
        if (
            isinstance(self.instructions_version, bool)
            or not isinstance(self.instructions_version, int)
            or self.instructions_version < 1
        ):
            raise ValueError("instructions_version must be positive")
        # Prove canonical JSON serializability eagerly.
        canonical_digest(dict(self.semantic_input))

    def as_dict(self) -> dict[str, Any]:
        return {
            "current_main": self.current_main,
            "semantic_input": dict(self.semantic_input),
            "instructions_version": self.instructions_version,
        }

    @property
    def request_id(self) -> str:
        return canonical_digest(self.as_dict())

    def prompt(self) -> str:
        schema = (
            "REQUIRED JSON PROPOSAL SCHEMA (all keys allowed; use null when not applicable):\n"
            '{"decision":"NOOP|DISPATCH|HUMAN_GATE|MERGE","lane":null,'
            '"pr_number":null,"objective":null,"stop_boundary":null,'
            '"reusable_evidence":null,"worker_tier":null,"allowed_paths":null,'
            '"reason":"","human_message":null}\n'
            "Rules: decision must be exactly one of NOOP, DISPATCH, HUMAN_GATE, MERGE. "
            "worker_tier, when present, must be LUNA or TERRA. DISPATCH requires lane, "
            "objective, and stop_boundary; LUNA DISPATCH requires non-empty allowed_paths. "
            "When task_authority is present, never widen its allowed_paths or protected boundary. "
            "For executable bounded task work, preserve the task_authority lane/objective/stop_boundary "
            "in the DISPATCH proposal; the outer controller remains authoritative.\n\n"
        )
        return (
            "A filtered Skyforge repository event batch requires a bounded proposal.\n\n"
            + schema
            + "SEMANTIC INPUT:\n"
            + json.dumps(
                dict(self.semantic_input),
                indent=2,
                sort_keys=True,
                ensure_ascii=False,
            )[:50000]
            + "\n\nReturn only one JSON object matching the schema above. This proposal has no authority "
            "until the outer controller validates it against repository-owned task authority."
        )


class ClassifierRunStatus(str, Enum):
    PREPARED = "PREPARED"
    RUNNING = "RUNNING"
    COMPLETE = "COMPLETE"
    FAILED = "FAILED"
    INTERRUPTED = "INTERRUPTED"


class ClassifierAdvanceDisposition(str, Enum):
    COMPLETE = "COMPLETE"
    ALREADY_COMPLETE = "ALREADY_COMPLETE"
    FAILED = "FAILED"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"


@dataclass(frozen=True)
class ClassifierRunRecord:
    request: ClassifierRequest
    config: ClassifierProviderConfig
    status: ClassifierRunStatus
    decision: ClassifierDecision | None = None
    raw_response_digest: str = ""
    failure_kind: str = ""
    retry_after_seconds: int = 0

    def __post_init__(self) -> None:
        if not isinstance(self.status, ClassifierRunStatus):
            raise ValueError("status must be ClassifierRunStatus")
        if self.retry_after_seconds < 0:
            raise ValueError("retry_after_seconds cannot be negative")
        if self.status is ClassifierRunStatus.COMPLETE and self.decision is None:
            raise ValueError("COMPLETE classifier run requires typed decision")
        if self.status is ClassifierRunStatus.COMPLETE and not self.raw_response_digest:
            raise ValueError("COMPLETE classifier run requires raw response digest")

    @property
    def run_id(self) -> str:
        return canonical_digest({
            "request_id": self.request.request_id,
            "config_digest": self.config.digest,
        })

    def as_dict(self) -> dict[str, Any]:
        return {
            "run_id": self.run_id,
            "request": self.request.as_dict(),
            "config": {
                "model": self.config.model,
                "reasoning_effort": self.config.reasoning_effort,
            },
            "status": self.status.value,
            "decision": self.decision.as_dict() if self.decision else None,
            "raw_response_digest": self.raw_response_digest,
            "failure_kind": self.failure_kind,
            "retry_after_seconds": self.retry_after_seconds,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ClassifierRunLedger:
    records: tuple[ClassifierRunRecord, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return {"schema_version": 1, "records": [record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "ClassifierRunLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid classifier-run ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("classifier-run records must be a list")
        return cls(tuple(_record_from_mapping(value) for value in values))

    def get(self, request_id: str) -> ClassifierRunRecord | None:
        matches = [r for r in self.records if r.request.request_id == request_id]
        if len(matches) > 1:
            raise ValueError("multiple classifier runs exist for request")
        return matches[0] if matches else None

    def put(self, record: ClassifierRunRecord) -> "ClassifierRunLedger":
        current = self.get(record.request.request_id)
        if current is not None and current.run_id != record.run_id:
            raise ValueError("classifier request provider identity drifted")
        return ClassifierRunLedger(
            tuple(
                record if r.request.request_id == record.request.request_id else r
                for r in self.records
            )
            if current
            else self.records + (record,)
        )


def _record_from_mapping(raw: Any) -> ClassifierRunRecord:
    if not isinstance(raw, Mapping):
        raise ValueError("classifier record must be object")
    request_raw = raw.get("request")
    config_raw = raw.get("config")
    if not isinstance(request_raw, Mapping) or not isinstance(config_raw, Mapping):
        raise ValueError("classifier request/config must be objects")
    request = ClassifierRequest(
        current_main=str(request_raw.get("current_main") or ""),
        semantic_input=request_raw.get("semantic_input") or {},
        instructions_version=int(request_raw.get("instructions_version") or 0),
    )
    config = ClassifierProviderConfig(
        model=str(config_raw.get("model") or ""),
        reasoning_effort=str(config_raw.get("reasoning_effort") or ""),
    )
    decision_raw = raw.get("decision")
    decision = (
        None
        if decision_raw is None
        else ClassifierDecision.from_legacy_mapping(decision_raw)
    )
    record = ClassifierRunRecord(
        request=request,
        config=config,
        status=ClassifierRunStatus(str(raw.get("status") or "")),
        decision=decision,
        raw_response_digest=str(raw.get("raw_response_digest") or ""),
        failure_kind=str(raw.get("failure_kind") or ""),
        retry_after_seconds=int(raw.get("retry_after_seconds") or 0),
    )
    if str(raw.get("run_id") or "") != record.run_id:
        raise ValueError("classifier run_id identity mismatch")
    return record


@dataclass(frozen=True)
class ClassifierRunStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ClassifierRunStore":
        root = Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / CLASSIFIER_RUNS_RELATIVE_PATH,
            backup_path=root / CLASSIFIER_RUNS_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ClassifierRunLedger:
        return ClassifierRunLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ClassifierRunLedger) -> None:
        self.adapter.save(ledger.as_dict())


class ClassifierProviderError(RuntimeError):
    def __init__(self, kind: str, retry_after_seconds: int, message: str) -> None:
        super().__init__(message)
        self.kind = str(kind).strip() or "classifier_failure"
        self.retry_after_seconds = max(0, int(retry_after_seconds))


class ClassifierProvider(Protocol):
    def classify(
        self,
        *,
        request: ClassifierRequest,
        root: Path,
        config: ClassifierProviderConfig,
    ) -> tuple[str, ClassifierDecision]: ...


def parse_classifier_response(text: str) -> ClassifierDecision:
    raw = str(text or "").strip()
    fenced = re.match(r"^```(?:json)?\s*(.*?)\s*```$", raw, re.S | re.I)
    if fenced:
        raw = fenced.group(1).strip()
    start = raw.find("{")
    end = raw.rfind("}")
    if start < 0 or end < start:
        raise ValueError("classifier did not return JSON")
    value = json.loads(raw[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("classifier JSON must be an object")
    # Parsing establishes only a typed proposal. Repository-owned R5C4 admission is
    # authoritative for whether the proposal is executable and may supply/narrow
    # controller-owned scope that the classifier does not own.
    return ClassifierDecision.from_legacy_mapping(value)


class CodexClassifierProvider:
    DEVELOPER_INSTRUCTIONS = """You are the Skyforge bounded classifier.
Return only one JSON proposal. The decision field must be exactly NOOP, DISPATCH, HUMAN_GATE, or MERGE; never invent decision kinds.
You have read-only repository access. You do not own task authority, dispatch, GitHub
mutation, merge, human gates, or repository policy. The outer controller validates every
proposal against repository-owned authority."""

    def classify(
        self,
        *,
        request: ClassifierRequest,
        root: Path,
        config: ClassifierProviderConfig,
    ) -> tuple[str, ClassifierDecision]:
        try:
            from openai_codex import Codex, Sandbox
            with Codex() as codex:
                thread = codex.thread_start(
                    cwd=str(Path(root).resolve()),
                    model=config.model,
                    config={"model_reasoning_effort": config.reasoning_effort},
                    sandbox=Sandbox.read_only,
                    developer_instructions=self.DEVELOPER_INSTRUCTIONS,
                    ephemeral=True,
                )
                result = thread.run(request.prompt(), sandbox=Sandbox.read_only)
                raw = str(result.final_response or "")
                return raw, parse_classifier_response(raw)
        except (ValueError, json.JSONDecodeError) as exc:
            raise ClassifierProviderError(
                "classifier_invalid_response",
                0,
                str(exc),
            ) from exc
        except ClassifierProviderError:
            raise
        except Exception as exc:
            kind, retry = classify_provider_failure(exc)
            raise ClassifierProviderError(kind, retry, f"classifier failed: {exc}") from exc


@dataclass(frozen=True)
class ClassifierAdvanceResult:
    disposition: ClassifierAdvanceDisposition
    reason: str
    record: ClassifierRunRecord

    @property
    def digest(self) -> str:
        return canonical_digest({
            "disposition": self.disposition.value,
            "reason": self.reason,
            "record_digest": self.record.digest,
        })


def advance_classifier(
    *,
    request: ClassifierRequest,
    root: Path,
    store: ClassifierRunStore,
    provider: ClassifierProvider,
    config: ClassifierProviderConfig | None = None,
) -> ClassifierAdvanceResult:
    cfg = config or classifier_provider_config()
    ledger = store.load()
    current = ledger.get(request.request_id)

    if current is not None:
        if current.request != request or current.config != cfg:
            raise ValueError("classifier request identity drifted")
        if current.status is ClassifierRunStatus.COMPLETE:
            return ClassifierAdvanceResult(
                ClassifierAdvanceDisposition.ALREADY_COMPLETE,
                "classifier proposal is already durable",
                current,
            )
        if current.status is ClassifierRunStatus.RUNNING:
            interrupted = replace(current, status=ClassifierRunStatus.INTERRUPTED)
            store.save(ledger.put(interrupted))
            return ClassifierAdvanceResult(
                ClassifierAdvanceDisposition.RECOVERY_REQUIRED,
                "classifier restarted with RUNNING state; do not spend another call automatically",
                interrupted,
            )
        if current.status is ClassifierRunStatus.INTERRUPTED:
            return ClassifierAdvanceResult(
                ClassifierAdvanceDisposition.RECOVERY_REQUIRED,
                "interrupted classifier request requires a new explicit request/revision",
                current,
            )
        if current.status is ClassifierRunStatus.FAILED:
            return ClassifierAdvanceResult(
                ClassifierAdvanceDisposition.FAILED,
                "failed classifier request remains immutable; new retry requires explicit request/revision",
                current,
            )
        prepared = current
    else:
        prepared = ClassifierRunRecord(
            request=request,
            config=cfg,
            status=ClassifierRunStatus.PREPARED,
        )
        ledger = ledger.put(prepared)
        store.save(ledger)

    running = replace(prepared, status=ClassifierRunStatus.RUNNING)
    ledger = ledger.put(running)
    store.save(ledger)

    try:
        raw, decision = provider.classify(request=request, root=Path(root), config=cfg)
    except ClassifierProviderError as exc:
        failed = replace(
            running,
            status=ClassifierRunStatus.FAILED,
            failure_kind=exc.kind,
            retry_after_seconds=exc.retry_after_seconds,
        )
        store.save(ledger.put(failed))
        return ClassifierAdvanceResult(
            ClassifierAdvanceDisposition.FAILED,
            str(exc),
            failed,
        )

    complete = replace(
        running,
        status=ClassifierRunStatus.COMPLETE,
        decision=decision,
        raw_response_digest=canonical_digest({"raw": raw}),
        failure_kind="",
        retry_after_seconds=0,
    )
    store.save(ledger.put(complete))
    return ClassifierAdvanceResult(
        ClassifierAdvanceDisposition.COMPLETE,
        "typed classifier proposal is durable and still requires dispatch admission",
        complete,
    )
