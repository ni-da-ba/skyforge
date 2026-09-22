"""Durable disposable-worker execution contracts for Platform v2 R5C3."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
import json
import os
from pathlib import Path
import re
import subprocess
import threading
from typing import Any, Mapping, Protocol

from .identity import canonical_digest
from .path_scope import path_is_allowed
from .state_store import JsonStateStoreAdapter


WORKER_RUNS_RELATIVE_PATH = Path(".skyforge-platform-v2") / "worker-runs.json"
WORKER_RUNS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "worker-runs.json.bak"
_WORKER_RUN_LOCK = threading.RLock()

PATCH_BEGIN = "SKYFORGE_PATCH_BEGIN"
PATCH_END = "SKYFORGE_PATCH_END"
PATCH_SUMMARY = "SKYFORGE_WORKER_SUMMARY"
_PROTECTED_WORKER_PREFIXES = (
    ".git/",
    ".skyforge-orchestrator/",
    ".skyforge-platform-v2/",
)


class WorkerTier(str, Enum):
    LUNA = "LUNA"
    TERRA = "TERRA"


class WorkerRunStatus(str, Enum):
    PREPARED = "PREPARED"
    RUNNING = "RUNNING"
    HANDOFF_READY = "HANDOFF_READY"
    FAILED = "FAILED"
    INTERRUPTED = "INTERRUPTED"


class WorkerAdvanceDisposition(str, Enum):
    HANDOFF_READY = "HANDOFF_READY"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    FAILED = "FAILED"
    ALREADY_READY = "ALREADY_READY"


@dataclass(frozen=True)
class WorkerProviderConfig:
    tier: WorkerTier
    model: str
    reasoning_effort: str

    @property
    def digest(self) -> str:
        return canonical_digest({
            "tier": self.tier.value,
            "model": self.model,
            "reasoning_effort": self.reasoning_effort,
        })


def provider_config_for_tier(
    tier: WorkerTier,
    environ: Mapping[str, str] | None = None,
) -> WorkerProviderConfig:
    env = os.environ if environ is None else environ
    if tier is WorkerTier.LUNA:
        model = str(
            env.get(
                "SKYFORGE_LUNA_WORKER_MODEL",
                env.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna"),
            )
        ).strip()
        effort = str(env.get("SKYFORGE_LUNA_WORKER_REASONING", "low")).strip()
    elif tier is WorkerTier.TERRA:
        model = str(env.get("SKYFORGE_WORKER_MODEL", "gpt-5.6-terra")).strip()
        effort = str(env.get("SKYFORGE_WORKER_REASONING", "medium")).strip()
    else:
        raise ValueError("unknown worker tier")
    if not model or not effort:
        raise ValueError("worker model/reasoning configuration must be non-empty")
    return WorkerProviderConfig(tier=tier, model=model, reasoning_effort=effort)


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha40(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def deterministic_worker_branch(lane: str, task_id: str, attempt_id: str) -> str:
    lane_slug = re.sub(r"[^a-z0-9]+", "-", _required(lane, "lane").lower()).strip("-") or "lane"
    task_slug = re.sub(r"[^a-z0-9]+", "-", _required(task_id, "task_id").lower()).strip("-") or "task"
    attempt = _sha64(attempt_id, "attempt_id")[:12]
    return f"codex/{lane_slug}-{task_slug[:48]}-{attempt}"


@dataclass(frozen=True)
class FrozenWorkerSpec:
    task_id: str
    authority_key: str
    task_spec_hash: str
    attempt_id: str
    lane: str
    objective: str
    stop_boundary: str
    base_sha: str
    allowed_paths: tuple[str, ...]
    protected_paths: tuple[str, ...] = ()
    tier: WorkerTier = WorkerTier.TERRA
    context_text: str = ""

    def __post_init__(self) -> None:
        for name in ("task_id", "authority_key", "lane", "objective", "stop_boundary"):
            object.__setattr__(self, name, _required(getattr(self, name), name))
        object.__setattr__(self, "task_spec_hash", _sha64(self.task_spec_hash, "task_spec_hash"))
        object.__setattr__(self, "attempt_id", _sha64(self.attempt_id, "attempt_id"))
        object.__setattr__(self, "base_sha", _sha40(self.base_sha, "base_sha"))
        if not isinstance(self.tier, WorkerTier):
            raise ValueError("tier must be WorkerTier")
        if not self.allowed_paths or any(not str(p).strip() for p in self.allowed_paths):
            raise ValueError("allowed_paths must contain at least one non-empty path")
        if any(not str(p).strip() for p in self.protected_paths):
            raise ValueError("protected_paths must contain non-empty paths")

    @property
    def branch(self) -> str:
        return deterministic_worker_branch(self.lane, self.task_id, self.attempt_id)

    def as_dict(self) -> dict[str, Any]:
        return {
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "task_spec_hash": self.task_spec_hash,
            "attempt_id": self.attempt_id,
            "lane": self.lane,
            "objective": self.objective,
            "stop_boundary": self.stop_boundary,
            "base_sha": self.base_sha,
            "branch": self.branch,
            "allowed_paths": list(self.allowed_paths),
            "protected_paths": list(self.protected_paths),
            "tier": self.tier.value,
            "context_text": self.context_text,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def prompt(self) -> str:
        allowed = "\n".join(f"- {p}" for p in self.allowed_paths)
        protected = "\n".join(f"- {p}" for p in self.protected_paths) or "N/A"
        context = self.context_text.strip() or "N/A"
        return f"""Bounded objective:
{self.objective}

Acceptance / stop boundary:
{self.stop_boundary}

Authority:
- task: {self.task_id}
- authority: {self.authority_key}
- task spec: {self.task_spec_hash}
- attempt: {self.attempt_id}
- base SHA: {self.base_sha}
- branch: {self.branch}

Allowed edit scope:
{allowed}

Explicit protected paths:
{protected}

Authoritative bounded context:
{context}

Work only until the stop boundary. Persist the bounded result as local file changes and
tests. Do not commit, push, open or merge pull requests, mutate issues/comments, or use
network access. The outer controller owns authority and handoff."""


@dataclass(frozen=True)
class WorkerRunRecord:
    spec: FrozenWorkerSpec
    worktree: str
    config: WorkerProviderConfig
    status: WorkerRunStatus
    summary: str = ""
    failure_kind: str = ""
    retry_after_seconds: int = 0

    def __post_init__(self) -> None:
        _required(self.worktree, "worktree")
        if not isinstance(self.status, WorkerRunStatus):
            raise ValueError("status must be WorkerRunStatus")
        if self.retry_after_seconds < 0:
            raise ValueError("retry_after_seconds cannot be negative")
        if self.status is WorkerRunStatus.HANDOFF_READY and not self.summary.strip():
            raise ValueError("HANDOFF_READY requires summary")

    @property
    def run_id(self) -> str:
        return canonical_digest({
            "attempt_id": self.spec.attempt_id,
            "spec_digest": self.spec.digest,
            "branch": self.spec.branch,
            "worktree": self.worktree,
            "provider_config": self.config.digest,
        })

    def as_dict(self) -> dict[str, Any]:
        return {
            "run_id": self.run_id,
            "spec": self.spec.as_dict(),
            "worktree": self.worktree,
            "config": {
                "tier": self.config.tier.value,
                "model": self.config.model,
                "reasoning_effort": self.config.reasoning_effort,
            },
            "status": self.status.value,
            "summary": self.summary,
            "failure_kind": self.failure_kind,
            "retry_after_seconds": self.retry_after_seconds,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class WorkerRunLedger:
    records: tuple[WorkerRunRecord, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return {"schema_version": 1, "records": [r.as_dict() for r in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "WorkerRunLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid worker-run ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("worker-run records must be a list")
        records = tuple(_record_from_mapping(v) for v in values)
        ids = [r.run_id for r in records]
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate worker run_id")
        return cls(records)

    def find_attempt(self, attempt_id: str) -> WorkerRunRecord | None:
        matches = [r for r in self.records if r.spec.attempt_id == attempt_id]
        if len(matches) > 1:
            raise ValueError("multiple worker records exist for one attempt")
        return matches[0] if matches else None

    def put(self, record: WorkerRunRecord) -> "WorkerRunLedger":
        existing = self.find_attempt(record.spec.attempt_id)
        if existing is None:
            return WorkerRunLedger(self.records + (record,))
        if existing.run_id != record.run_id:
            raise ValueError("worker attempt identity drifted")
        return WorkerRunLedger(tuple(
            record if r.spec.attempt_id == record.spec.attempt_id else r
            for r in self.records
        ))


def _record_from_mapping(raw: Any) -> WorkerRunRecord:
    if not isinstance(raw, Mapping):
        raise ValueError("worker-run record must be object")
    spec_raw = raw.get("spec")
    cfg_raw = raw.get("config")
    if not isinstance(spec_raw, Mapping) or not isinstance(cfg_raw, Mapping):
        raise ValueError("worker-run spec/config must be objects")
    spec = FrozenWorkerSpec(
        task_id=spec_raw.get("task_id"),
        authority_key=spec_raw.get("authority_key"),
        task_spec_hash=spec_raw.get("task_spec_hash"),
        attempt_id=spec_raw.get("attempt_id"),
        lane=spec_raw.get("lane"),
        objective=spec_raw.get("objective"),
        stop_boundary=spec_raw.get("stop_boundary"),
        base_sha=spec_raw.get("base_sha"),
        allowed_paths=tuple(spec_raw.get("allowed_paths") or ()),
        protected_paths=tuple(spec_raw.get("protected_paths") or ()),
        tier=WorkerTier(str(spec_raw.get("tier") or "")),
        context_text=str(spec_raw.get("context_text") or ""),
    )
    cfg = WorkerProviderConfig(
        tier=WorkerTier(str(cfg_raw.get("tier") or "")),
        model=_required(cfg_raw.get("model"), "config.model"),
        reasoning_effort=_required(cfg_raw.get("reasoning_effort"), "config.reasoning_effort"),
    )
    record = WorkerRunRecord(
        spec=spec,
        worktree=_required(raw.get("worktree"), "worktree"),
        config=cfg,
        status=WorkerRunStatus(str(raw.get("status") or "")),
        summary=str(raw.get("summary") or ""),
        failure_kind=str(raw.get("failure_kind") or ""),
        retry_after_seconds=int(raw.get("retry_after_seconds") or 0),
    )
    if str(raw.get("run_id") or "") != record.run_id:
        raise ValueError("worker run_id does not match canonical identity")
    return record


@dataclass(frozen=True)
class WorkerRunStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "WorkerRunStore":
        root = Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / WORKER_RUNS_RELATIVE_PATH,
            backup_path=root / WORKER_RUNS_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> WorkerRunLedger:
        return WorkerRunLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: WorkerRunLedger) -> WorkerRunLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


class WorkerProviderError(RuntimeError):
    def __init__(self, kind: str, retry_after_seconds: int, message: str) -> None:
        super().__init__(message)
        self.kind = _required(kind, "provider failure kind")
        self.retry_after_seconds = max(0, int(retry_after_seconds))


class WorkerProvider(Protocol):
    def run(self, *, spec: FrozenWorkerSpec, worktree: Path, config: WorkerProviderConfig) -> str: ...


class CodexWorkerProvider:
    """Initial provider adapter preserving accepted legacy worker SDK semantics."""

    DEVELOPER_INSTRUCTIONS = """You are a bounded Skyforge repository worker.
Respect the exact task scope and stop boundary supplied by the outer controller.
Do not commit, push, create/merge PRs, mutate GitHub, or use network access.
Leave bounded repository changes and tests in the isolated worktree for controller review."""

    def run(
        self,
        *,
        spec: FrozenWorkerSpec,
        worktree: Path,
        config: WorkerProviderConfig,
    ) -> str:
        try:
            from openai_codex import Codex, Sandbox
            with Codex() as codex:
                thread = codex.thread_start(
                    cwd=str(Path(worktree).resolve()),
                    model=config.model,
                    config={"model_reasoning_effort": config.reasoning_effort},
                    sandbox=Sandbox.workspace_write,
                    developer_instructions=self.DEVELOPER_INSTRUCTIONS,
                    ephemeral=True,
                )
                result = thread.run(spec.prompt(), sandbox=Sandbox.workspace_write)
                summary = str(result.final_response or "").strip()
                if not summary:
                    raise WorkerProviderError("empty_response", 0, "worker returned no final response")
                return summary
        except WorkerProviderError:
            raise
        except Exception as exc:
            kind, retry = classify_provider_failure(exc)
            raise WorkerProviderError(kind, retry, f"{config.tier.value.lower()} worker failed: {exc}") from exc


def classify_provider_failure(exc: Exception) -> tuple[str, int]:
    text = f"{type(exc).__name__}: {exc}".lower()
    if any(token in text for token in ("rate limit", "quota", "capacity", "429")):
        return "provider_capacity", 900
    if any(token in text for token in ("auth", "401", "403", "credential")):
        return "provider_authentication", 0
    if any(token in text for token in ("timeout", "timed out")):
        return "provider_timeout", 300
    return "provider_failure", 300


@dataclass(frozen=True)
class WorkerAdvanceResult:
    disposition: WorkerAdvanceDisposition
    reason: str
    record: WorkerRunRecord

    @property
    def digest(self) -> str:
        return canonical_digest({
            "disposition": self.disposition.value,
            "reason": self.reason,
            "record_digest": self.record.digest,
        })


def interrupt_running_worker_attempt(
    *,
    root: Path,
    attempt_id: str,
) -> WorkerRunRecord | None:
    """Durably fence one stale RUNNING attempt after process restart."""
    with _WORKER_RUN_LOCK:
        store = WorkerRunStore.for_root(Path(root).resolve())
        ledger = store.load()
        current = ledger.find_attempt(attempt_id)
        if current is None:
            return None
        if current.status is WorkerRunStatus.RUNNING:
            current = replace(current, status=WorkerRunStatus.INTERRUPTED)
            store.save(ledger.put(current))
        return current


def advance_worker_run(
    *,
    spec: FrozenWorkerSpec,
    worktree: Path,
    store: WorkerRunStore,
    provider: WorkerProvider,
    config: WorkerProviderConfig | None = None,
) -> WorkerAdvanceResult:
    resolved = Path(worktree).resolve()
    cfg = config or provider_config_for_tier(spec.tier)

    # Durable preparation/RUNNING transition is serialized, but the provider call is
    # deliberately outside the lock so disjoint attempts may execute concurrently.
    with _WORKER_RUN_LOCK:
        ledger = store.load()
        current = ledger.find_attempt(spec.attempt_id)

        if current is not None:
            if (
                current.spec.digest != spec.digest
                or Path(current.worktree).resolve() != resolved
                or current.config != cfg
            ):
                raise ValueError("worker attempt identity drifted")
            if current.status is WorkerRunStatus.HANDOFF_READY:
                return WorkerAdvanceResult(
                    WorkerAdvanceDisposition.ALREADY_READY,
                    "worker handoff is already durably ready",
                    current,
                )
            if current.status is WorkerRunStatus.RUNNING:
                interrupted = replace(current, status=WorkerRunStatus.INTERRUPTED)
                store.save(ledger.put(interrupted))
                return WorkerAdvanceResult(
                    WorkerAdvanceDisposition.RECOVERY_REQUIRED,
                    "worker process restarted with RUNNING state; preserve worktree and do not re-call provider",
                    interrupted,
                )
            if current.status is WorkerRunStatus.INTERRUPTED:
                return WorkerAdvanceResult(
                    WorkerAdvanceDisposition.RECOVERY_REQUIRED,
                    "interrupted worker requires explicit reconciliation before another provider call",
                    current,
                )
            if current.status is WorkerRunStatus.FAILED:
                return WorkerAdvanceResult(
                    WorkerAdvanceDisposition.FAILED,
                    "failed worker remains durable; retry requires a new explicit attempt/revision",
                    current,
                )
            prepared = current
        else:
            prepared = WorkerRunRecord(
                spec=spec,
                worktree=str(resolved),
                config=cfg,
                status=WorkerRunStatus.PREPARED,
            )
            ledger = ledger.put(prepared)
            store.save(ledger)

        running = replace(prepared, status=WorkerRunStatus.RUNNING)
        ledger = store.load().put(running)
        store.save(ledger)

    try:
        summary = provider.run(spec=spec, worktree=resolved, config=cfg)
    except WorkerProviderError as exc:
        with _WORKER_RUN_LOCK:
            latest = store.load()
            current = latest.find_attempt(spec.attempt_id)
            if current is None or current.run_id != running.run_id:
                raise ValueError("worker attempt disappeared or changed during provider failure")
            failed = replace(
                current,
                status=WorkerRunStatus.FAILED,
                failure_kind=exc.kind,
                retry_after_seconds=exc.retry_after_seconds,
            )
            store.save(latest.put(failed))
        return WorkerAdvanceResult(
            WorkerAdvanceDisposition.FAILED,
            str(exc),
            failed,
        )

    with _WORKER_RUN_LOCK:
        latest = store.load()
        current = latest.find_attempt(spec.attempt_id)
        if current is None or current.run_id != running.run_id:
            raise ValueError("worker attempt disappeared or changed during provider execution")
        if current.status is not WorkerRunStatus.RUNNING:
            return WorkerAdvanceResult(
                WorkerAdvanceDisposition.RECOVERY_REQUIRED,
                "worker durable state changed while provider was running; preserve output and fail closed",
                current,
            )
        ready = replace(
            current,
            status=WorkerRunStatus.HANDOFF_READY,
            summary=summary[:8000],
            failure_kind="",
            retry_after_seconds=0,
        )
        store.save(latest.put(ready))
    return WorkerAdvanceResult(
        WorkerAdvanceDisposition.HANDOFF_READY,
        "worker provider completed and handoff state is durable",
        ready,
    )
