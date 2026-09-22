"""Canonical read-only development-state projection for Platform-v2 clients."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import json
import re
from typing import Any, Iterable, Mapping

from .identity import canonical_digest
from .hosted_policy import HOSTED_WORKER_CONCURRENCY_LIMIT
from .state_store import JsonStateStoreAdapter

_SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
_RECENT_LIMIT = 50


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


def _sha40(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not _SHA40_RE.fullmatch(text):
        raise ValueError(f"{label} must be a lowercase 40-character Git SHA")
    return text


def _json_value(value: Any, label: str) -> Any:
    try:
        encoded = json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        )
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} must be JSON serializable") from exc
    return json.loads(encoded)


def _recent(values: Iterable[Mapping[str, Any]]) -> tuple[Mapping[str, Any], ...]:
    records = tuple(values)
    return records[-_RECENT_LIMIT:]


def _objective(raw: Mapping[str, Any]) -> dict[str, Any]:
    source = _mapping(raw.get("source"), "objective source")
    compiled = _mapping(raw.get("compiled"), "compiled objective")
    candidate = compiled.get("candidate_task")
    gate = compiled.get("human_gate")
    return {
        "proposal_id": str(raw.get("proposal_id") or ""),
        "source": {
            "kind": str(source.get("kind") or "GITHUB_COMMENT"),
            "issue_number": source.get("issue_number"),
            "comment_id": source.get("comment_id"),
            "request_id": source.get("request_id"),
            "client": source.get("client"),
            "actor": str(source.get("actor") or ""),
            "created_at": str(source.get("created_at") or ""),
            "submitted_at": str(source.get("submitted_at") or ""),
            "parent_proposal_id": str(source.get("parent_proposal_id") or ""),
            "lane": str(source.get("lane") or ""),
            "scope_digest": str(source.get("scope_digest") or ""),
            "objective_text": str(source.get("objective_text") or ""),
        },
        "disposition": str(compiled.get("disposition") or ""),
        "reason": str(compiled.get("reason") or ""),
        "candidate_task": (
            None
            if candidate is None
            else {
                "roadmap_id": candidate.get("roadmap_id"),
                "node_id": candidate.get("node_id"),
                "lane": candidate.get("lane"),
                "issue_number": candidate.get("issue_number"),
                "objective": candidate.get("objective"),
                "stop_boundary": candidate.get("stop_boundary"),
            }
        ),
        "human_gate": (
            None
            if gate is None
            else {
                "roadmap_id": gate.get("roadmap_id"),
                "node_id": gate.get("node_id"),
                "lane": gate.get("lane"),
                "message": gate.get("message"),
            }
        ),
    }


def _artifact(raw: Mapping[str, Any]) -> dict[str, Any]:
    artifact_id = str(raw.get("artifact_id") or "").strip()
    if not artifact_id:
        raise ValueError("artifact_id is required")
    return _json_value(dict(raw), "artifact manifest")


def _human_review(
    raw: Mapping[str, Any],
    artifacts: Mapping[str, Mapping[str, Any]],
) -> dict[str, Any]:
    source = _mapping(raw.get("source"), "human review source")
    artifact_id = str(raw.get("artifact_id") or "")
    return {
        "review_id": str(raw.get("review_id") or ""),
        "gate_id": str(raw.get("gate_id") or ""),
        "artifact_id": artifact_id,
        "artifact": (
            dict(artifacts[artifact_id]) if artifact_id in artifacts else None
        ),
        "source_sha": _sha40(raw.get("source_sha"), "human review source_sha"),
        "verdict": str(raw.get("verdict") or ""),
        "positive_findings": list(raw.get("positive_findings") or []),
        "findings": list(raw.get("findings") or []),
        "material_delta": str(raw.get("material_delta") or ""),
        "next_boundary": str(raw.get("next_boundary") or ""),
        "deferred_product_work": raw.get("deferred_product_work") is True,
        "prior_review_id": raw.get("prior_review_id"),
        "source": {
            "kind": str(source.get("kind") or "GITHUB_COMMENT"),
            "issue_number": source.get("issue_number"),
            "comment_id": source.get("comment_id"),
            "request_id": source.get("request_id"),
            "client": source.get("client"),
            "actor": str(source.get("actor") or ""),
            "created_at": str(source.get("created_at") or ""),
            "submitted_at": str(source.get("submitted_at") or ""),
        },
    }


def _worker(raw: Mapping[str, Any]) -> dict[str, Any]:
    spec = _mapping(raw.get("spec"), "worker spec")
    config = _mapping(raw.get("config") or {}, "worker config")
    return {
        "run_id": str(raw.get("run_id") or ""),
        "task_id": str(spec.get("task_id") or ""),
        "attempt_id": str(spec.get("attempt_id") or ""),
        "lane": str(spec.get("lane") or ""),
        "objective": str(spec.get("objective") or ""),
        "stop_boundary": str(spec.get("stop_boundary") or ""),
        "base_sha": str(spec.get("base_sha") or ""),
        "branch": str(spec.get("branch") or ""),
        "tier": str(config.get("tier") or spec.get("tier") or ""),
        "model": str(config.get("model") or ""),
        "status": str(raw.get("status") or ""),
        "summary": str(raw.get("summary") or ""),
        "failure_kind": str(raw.get("failure_kind") or ""),
        "retry_after_seconds": raw.get("retry_after_seconds", 0),
    }


def _admission(raw: Mapping[str, Any] | None) -> dict[str, Any] | None:
    if raw is None:
        return None
    worker = raw.get("worker_spec")
    attempt = raw.get("attempt")
    return {
        "record_id": str(raw.get("record_id") or ""),
        "plan_id": str(raw.get("plan_id") or ""),
        "event_id": str(raw.get("event_id") or ""),
        "issue_number": raw.get("issue_number"),
        "current_main": str(raw.get("current_main") or ""),
        "attempt_number": raw.get("attempt_number"),
        "outcome": str(raw.get("outcome") or ""),
        "reason": str(raw.get("reason") or ""),
        "attempt_id": (
            str(attempt.get("attempt_id") or "")
            if isinstance(attempt, Mapping)
            else ""
        ),
        "worker": (
            None
            if not isinstance(worker, Mapping)
            else {
                "task_id": str(worker.get("task_id") or ""),
                "lane": str(worker.get("lane") or ""),
                "branch": str(worker.get("branch") or ""),
                "base_sha": str(worker.get("base_sha") or ""),
                "tier": str(worker.get("tier") or ""),
            }
        ),
    }


def _plan(raw: Mapping[str, Any] | None) -> dict[str, Any] | None:
    if raw is None:
        return None
    return {
        "plan_id": str(raw.get("plan_id") or ""),
        "event_id": str(raw.get("event_id") or ""),
        "issue_number": raw.get("issue_number"),
        "status": str(raw.get("status") or ""),
        "reason": str(raw.get("reason") or ""),
    }


def load_worker_scheduler_read_records(
    root: Path,
) -> tuple[Mapping[str, Any], ...]:
    """Read scheduler truth without importing mutation-capable scheduler code."""
    root = Path(root).resolve()
    state_dir = root / ".skyforge-platform-v2"
    adapter = JsonStateStoreAdapter(
        path=state_dir / "worker-scheduler.json",
        backup_path=state_dir / "worker-scheduler.json.bak",
    )
    raw = adapter.load().as_dict()
    if raw in ({}, None):
        return ()
    if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
        raise ValueError("invalid worker scheduler read ledger")
    if raw.get("concurrency_limit") != HOSTED_WORKER_CONCURRENCY_LIMIT:
        raise ValueError("worker scheduler concurrency policy mismatch")
    values = raw.get("records")
    if not isinstance(values, list):
        raise ValueError("worker scheduler read records must be a list")
    normalized = []
    seen: set[str] = set()
    allowed_states = {
        "RUNNABLE",
        "EXECUTING",
        "WAIT_LIMIT",
        "WAIT_CLAIM",
        "WAIT_QUOTA",
        "RECOVERY_REQUIRED",
        "COMPLETED",
        "RETIRED",
    }
    for value in values:
        item = _mapping(value, "worker scheduler record")
        attempt_id = str(item.get("attempt_id") or "")
        admission_record_id = str(item.get("admission_record_id") or "")
        state = str(item.get("state") or "")
        if not attempt_id or attempt_id in seen:
            raise ValueError("duplicate or missing scheduler attempt identity")
        if not admission_record_id or state not in allowed_states:
            raise ValueError("invalid scheduler admission/state")
        seen.add(attempt_id)
        normalized.append(
            {
                "attempt_id": attempt_id,
                "admission_record_id": admission_record_id,
                "state": state,
                "reason": str(item.get("reason") or ""),
                "claim_id": str(item.get("claim_id") or ""),
                "worker_run_id": str(item.get("worker_run_id") or ""),
                "tier": str(item.get("tier") or ""),
            }
        )
    return tuple(sorted(normalized, key=lambda value: value["attempt_id"]))


def load_local_commit_read_records(root: Path) -> tuple[Mapping[str, Any], ...]:
    """Read local-commit durable state without importing its mutation-capable service."""
    root = Path(root).resolve()
    state_dir = root / ".skyforge-platform-v2"
    adapter = JsonStateStoreAdapter(
        path=state_dir / "dormant-handoff-commit.json",
        backup_path=state_dir / "dormant-handoff-commit.json.bak",
    )
    raw = adapter.load().as_dict()
    if raw in ({}, None):
        return ()
    if not isinstance(raw, Mapping):
        raise ValueError("invalid local commit read ledger")
    version = raw.get("schema_version")
    if version == 1:
        record = raw.get("record")
        values = [] if record is None else [record]
    elif version == 2:
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("local commit read records must be a list")
    else:
        raise ValueError("invalid local commit read ledger")
    normalized: list[Mapping[str, Any]] = []
    seen_attempts: set[str] = set()
    for value in values:
        item = _local_commit(_mapping(value, "local commit"))
        attempt_id = str(item["attempt_id"])
        if not attempt_id or attempt_id in seen_attempts:
            raise ValueError("duplicate or missing local commit attempt identity")
        seen_attempts.add(attempt_id)
        normalized.append(item)
    return tuple(normalized)


def _local_commit(raw: Mapping[str, Any]) -> dict[str, Any]:
    paths = raw.get("changed_paths") or []
    if not isinstance(paths, list):
        raise ValueError("local commit changed_paths must be a list")
    return {
        "record_id": str(raw.get("record_id") or ""),
        "admission_record_id": str(raw.get("admission_record_id") or ""),
        "worker_run_id": str(raw.get("worker_run_id") or ""),
        "attempt_id": str(raw.get("attempt_id") or ""),
        "branch": str(raw.get("branch") or ""),
        "base_sha": str(raw.get("base_sha") or ""),
        "outcome": str(raw.get("outcome") or ""),
        "reason": str(raw.get("reason") or ""),
        "head_sha": str(raw.get("head_sha") or ""),
        "changed_paths": [str(value) for value in paths],
    }


def _completion(raw: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "completion_id": str(raw.get("completion_id") or ""),
        "plan_id": str(raw.get("plan_id") or ""),
        "event_id": str(raw.get("event_id") or ""),
        "issue_number": raw.get("issue_number"),
        "attempt_id": str(raw.get("attempt_id") or ""),
        "worker_run_id": str(raw.get("worker_run_id") or ""),
        "status": str(raw.get("status") or ""),
    }


def _concurrency_claim(raw: Mapping[str, Any]) -> dict[str, Any]:
    paths = raw.get("allowed_paths")
    if not isinstance(paths, list) or not paths:
        raise ValueError("concurrency claim allowed_paths must be a non-empty list")
    return {
        "claim_id": str(raw.get("claim_id") or ""),
        "attempt_id": str(raw.get("attempt_id") or ""),
        "task_id": str(raw.get("task_id") or ""),
        "authority_key": str(raw.get("authority_key") or ""),
        "base_sha": str(raw.get("base_sha") or ""),
        "lane": str(raw.get("lane") or ""),
        "allowed_paths": [str(value) for value in paths],
    }


def _claim(raw: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "issue_number": raw.get("issue_number"),
        "claimed_by": str(raw.get("claimed_by") or ""),
        "lane": raw.get("lane"),
        "branch": raw.get("branch"),
        "pr_number": raw.get("pr_number"),
        "state": str(raw.get("state") or "active"),
    }


def _human_gate(raw: Mapping[str, Any]) -> dict[str, Any]:
    gate_id = str(raw.get("gate_id") or "").strip()
    if not gate_id:
        raise ValueError("human gate gate_id is required")
    return {
        "gate_id": gate_id,
        "lane": str(raw.get("lane") or ""),
        "message": str(raw.get("message") or ""),
        "blocked_reason": str(raw.get("blocked_reason") or ""),
    }


def _review_order_key(review: Mapping[str, Any]) -> tuple[str, str]:
    source = review.get("source")
    if not isinstance(source, Mapping):
        source = {}
    return (
        str(source.get("created_at") or source.get("submitted_at") or ""),
        str(review.get("review_id") or ""),
    )


def _current_product_state(
    *,
    program_progression: Mapping[str, Any],
    human_gates: tuple[Mapping[str, Any], ...],
    human_reviews: tuple[Mapping[str, Any], ...],
) -> dict[str, Any]:
    latest_review = human_reviews[-1] if human_reviews else None
    historical_review = (
        None
        if latest_review is None
        else {
            "review_id": latest_review.get("review_id"),
            "gate_id": latest_review.get("gate_id"),
            "artifact_id": latest_review.get("artifact_id"),
            "verdict": latest_review.get("verdict"),
            "created_at": (latest_review.get("source") or {}).get("created_at"),
            "next_boundary": latest_review.get("next_boundary"),
            "deferred_product_work": latest_review.get("deferred_product_work") is True,
        }
    )

    projection = program_progression.get("projection")
    if not isinstance(projection, Mapping):
        projection = {}
    nodes = projection.get("nodes")
    if not isinstance(nodes, list):
        nodes = []
    active = program_progression.get("active_session")
    if isinstance(active, Mapping):
        node_id = str(active.get("current_node_id") or "")
        node = next(
            (
                value
                for value in nodes
                if isinstance(value, Mapping) and value.get("node_id") == node_id
            ),
            {},
        )
        disposition = str(active.get("disposition") or "")
        statuses = {
            "WAIT_HUMAN": "REVIEW_REQUIRED",
            "WAIT_STRATEGIC": "STRATEGIC_REVIEW_REQUIRED",
            "WAIT_AUTHORITY": "WAITING_FOR_AUTHORITY",
            "WAIT_CHILD": "WORK_IN_PROGRESS",
            "WAIT_CONTROL": "PAUSED",
            "ADVANCING": "ADVANCING",
            "BLOCKED": "BLOCKED",
            "COMPLETE": "COMPLETE",
        }
        status = statuses.get(disposition, disposition or "WORK_IN_PROGRESS")
        gate_id = str(active.get("gate_id") or node.get("review_gate_id") or "")
        return {
            "status": status,
            "source": "PROGRAM_CONTINUATION",
            "program_id": str(active.get("program_id") or projection.get("program_id") or ""),
            "node_id": node_id,
            "node_kind": str(node.get("kind") or ""),
            "issue_number": node.get("issue_number"),
            "lane": node.get("lane"),
            "disposition": disposition,
            "gate_id": gate_id or None,
            "reason": str(active.get("reason") or node.get("message") or ""),
            "action_required": status
            in {"REVIEW_REQUIRED", "STRATEGIC_REVIEW_REQUIRED", "BLOCKED"},
            "historical_review": historical_review,
        }

    if human_gates:
        gate = human_gates[0] if len(human_gates) == 1 else None
        return {
            "status": "REVIEW_REQUIRED",
            "source": "HUMAN_GATE",
            "program_id": str(projection.get("program_id") or ""),
            "node_id": None,
            "node_kind": "human_gate",
            "issue_number": None,
            "lane": gate.get("lane") if gate is not None else None,
            "disposition": "WAIT_HUMAN",
            "gate_id": gate.get("gate_id") if gate is not None else None,
            "reason": (
                str(gate.get("message") or gate.get("blocked_reason") or "")
                if gate is not None
                else f"{len(human_gates)} human gates require review"
            ),
            "action_required": True,
            "historical_review": historical_review,
        }

    if latest_review is not None:
        verdict = str(latest_review.get("verdict") or "")
        deferred = latest_review.get("deferred_product_work") is True
        status = (
            "CHANGES_REQUIRED_DEFERRED"
            if verdict == "CHANGES_REQUIRED" and deferred
            else verdict or "HISTORICAL_REVIEW"
        )
        return {
            "status": status,
            "source": "HISTORICAL_REVIEW",
            "program_id": str(projection.get("program_id") or ""),
            "node_id": None,
            "node_kind": "",
            "issue_number": None,
            "lane": None,
            "disposition": "",
            "gate_id": latest_review.get("gate_id"),
            "reason": str(latest_review.get("next_boundary") or ""),
            "action_required": verdict == "CHANGES_REQUIRED" and not deferred,
            "historical_review": historical_review,
        }

    return {
        "status": "NO_PRODUCT_REVIEW",
        "source": "NONE",
        "program_id": str(projection.get("program_id") or ""),
        "node_id": None,
        "node_kind": "",
        "issue_number": None,
        "lane": None,
        "disposition": "",
        "gate_id": None,
        "reason": "No product review or active product boundary is recorded.",
        "action_required": False,
        "historical_review": None,
    }


@dataclass(frozen=True)
class DevelopmentSnapshot:
    repo: str
    checkout_head_sha: str
    roadmap: Mapping[str, Any]
    human_gates: tuple[Mapping[str, Any], ...]
    objectives: tuple[Mapping[str, Any], ...]
    objective_count: int
    objective_controls: tuple[Mapping[str, Any], ...]
    objective_scopes: tuple[Mapping[str, Any], ...]
    scorecard: Mapping[str, Any]
    active_plan: Mapping[str, Any] | None
    admission: Mapping[str, Any] | None
    plans: tuple[Mapping[str, Any], ...]
    plan_count: int
    admissions: tuple[Mapping[str, Any], ...]
    admission_count: int
    local_commits: tuple[Mapping[str, Any], ...]
    local_commit_count: int
    worker_scheduler: tuple[Mapping[str, Any], ...]
    worker_scheduler_count: int
    concurrency_claims: tuple[Mapping[str, Any], ...]
    concurrency_claim_count: int
    workers: tuple[Mapping[str, Any], ...]
    worker_count: int
    completions: tuple[Mapping[str, Any], ...]
    completion_count: int
    external_claims: tuple[Mapping[str, Any], ...]
    artifacts: tuple[Mapping[str, Any], ...]
    artifact_count: int
    human_reviews: tuple[Mapping[str, Any], ...]
    human_review_count: int
    current_product_state: Mapping[str, Any]
    program_progression: Mapping[str, Any]
    runtime: Mapping[str, Any]

    def payload(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "repo": self.repo,
            "checkout_head_sha": self.checkout_head_sha,
            "roadmap": _json_value(dict(self.roadmap), "roadmap"),
            "human_gates": [dict(value) for value in self.human_gates],
            "objectives": [dict(value) for value in self.objectives],
            "objective_count": self.objective_count,
            "objective_controls": [
                dict(value) for value in self.objective_controls
            ],
            "objective_scopes": [
                dict(value) for value in self.objective_scopes
            ],
            "scorecard": _json_value(dict(self.scorecard), "scorecard"),
            "execution": {
                "active_plan": (
                    None if self.active_plan is None else dict(self.active_plan)
                ),
                "admission": (
                    None if self.admission is None else dict(self.admission)
                ),
                "plans": [dict(value) for value in self.plans],
                "plan_count": self.plan_count,
                "admissions": [dict(value) for value in self.admissions],
                "admission_count": self.admission_count,
                "local_commits": [dict(value) for value in self.local_commits],
                "local_commit_count": self.local_commit_count,
                "worker_concurrency_limit": HOSTED_WORKER_CONCURRENCY_LIMIT,
                "worker_scheduler": [
                    dict(value) for value in self.worker_scheduler
                ],
                "worker_scheduler_count": self.worker_scheduler_count,
                "executing_attempts": [
                    dict(value)
                    for value in self.worker_scheduler
                    if value.get("state") == "EXECUTING"
                ],
                "runnable_attempts": [
                    dict(value)
                    for value in self.worker_scheduler
                    if value.get("state") == "RUNNABLE"
                ],
                "waiting_attempts": [
                    dict(value)
                    for value in self.worker_scheduler
                    if str(value.get("state") or "").startswith("WAIT_")
                    or value.get("state") == "RECOVERY_REQUIRED"
                ],
                "concurrency_claims": [
                    dict(value) for value in self.concurrency_claims
                ],
                "concurrency_claim_count": self.concurrency_claim_count,
                "workers": [dict(value) for value in self.workers],
                "worker_count": self.worker_count,
                "completions": [dict(value) for value in self.completions],
                "completion_count": self.completion_count,
            },
            "external_claims": [dict(value) for value in self.external_claims],
            "artifacts": [dict(value) for value in self.artifacts],
            "artifact_count": self.artifact_count,
            "human_reviews": [dict(value) for value in self.human_reviews],
            "human_review_count": self.human_review_count,
            "current_product_state": _json_value(
                dict(self.current_product_state),
                "current_product_state",
            ),
            "program_progression": _json_value(
                dict(self.program_progression),
                "program_progression",
            ),
            "runtime": _json_value(dict(self.runtime), "runtime"),
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.payload())

    def as_dict(self) -> dict[str, Any]:
        return {**self.payload(), "snapshot_digest": self.digest}


def build_development_snapshot(
    *,
    repo: str,
    checkout_head_sha: str,
    legacy_projection: Mapping[str, Any],
    objective_records: Iterable[Mapping[str, Any]] = (),
    objective_controls: Iterable[Mapping[str, Any]] = (),
    objective_scopes: Iterable[Mapping[str, Any]] = (),
    active_plan: Mapping[str, Any] | None = None,
    admission: Mapping[str, Any] | None = None,
    plan_records: Iterable[Mapping[str, Any]] = (),
    admission_records: Iterable[Mapping[str, Any]] = (),
    local_commit_records: Iterable[Mapping[str, Any]] = (),
    worker_scheduler_records: Iterable[Mapping[str, Any]] = (),
    concurrency_claims: Iterable[Mapping[str, Any]] = (),
    worker_records: Iterable[Mapping[str, Any]] = (),
    completion_records: Iterable[Mapping[str, Any]] = (),
    external_claims: Iterable[Mapping[str, Any]] = (),
    artifact_records: Iterable[Mapping[str, Any]] = (),
    human_reviews: Iterable[Mapping[str, Any]] = (),
    human_gates: Iterable[Mapping[str, Any]] = (),
    program_progression: Mapping[str, Any] | None = None,
    scorecard: Mapping[str, Any] | None = None,
    runtime: Mapping[str, Any],
) -> DevelopmentSnapshot:
    repository = str(repo or "").strip()
    if repository.count("/") != 1:
        raise ValueError("repo must be owner/name")
    checkout = _sha40(checkout_head_sha, "checkout_head_sha")
    projection = _mapping(legacy_projection, "legacy projection")
    roadmap = _mapping(projection.get("roadmap") or {}, "legacy roadmap projection")

    objectives_all = tuple(_objective(_mapping(value, "objective")) for value in objective_records)
    controls_all = tuple(
        _json_value(dict(_mapping(value, "objective control")), "objective control")
        for value in objective_controls
    )
    scopes_all = tuple(
        _json_value(dict(_mapping(value, "objective scope")), "objective scope")
        for value in objective_scopes
    )
    plans_all = tuple(
        _plan(_mapping(value, "plan"))
        for value in plan_records
    )
    admissions_all = tuple(
        _admission(_mapping(value, "admission"))
        for value in admission_records
    )
    local_commits_all = tuple(
        _local_commit(_mapping(value, "local commit"))
        for value in local_commit_records
    )
    scheduler_all = tuple(
        dict(_mapping(value, "worker scheduler record"))
        for value in worker_scheduler_records
    )
    claims_all = tuple(
        _concurrency_claim(_mapping(value, "concurrency claim"))
        for value in concurrency_claims
    )
    workers_all = tuple(_worker(_mapping(value, "worker")) for value in worker_records)
    completions_all = tuple(
        _completion(_mapping(value, "completion")) for value in completion_records
    )
    artifacts_all = tuple(
        _artifact(_mapping(value, "artifact")) for value in artifact_records
    )
    artifacts_by_id = {
        str(value["artifact_id"]): value for value in artifacts_all
    }
    if len(artifacts_by_id) != len(artifacts_all):
        raise ValueError("duplicate artifact identity in development snapshot")
    reviews_all = tuple(
        sorted(
            (
                _human_review(_mapping(value, "human review"), artifacts_by_id)
                for value in human_reviews
            ),
            key=_review_order_key,
        )
    )
    claims = tuple(_claim(_mapping(value, "external claim")) for value in external_claims)
    gates = tuple(_human_gate(_mapping(value, "human gate")) for value in human_gates)
    progression = _json_value(
        dict(program_progression or {}),
        "program_progression",
    )
    current_product_state = _current_product_state(
        program_progression=progression,
        human_gates=gates,
        human_reviews=reviews_all,
    )

    return DevelopmentSnapshot(
        repo=repository,
        checkout_head_sha=checkout,
        roadmap={
            "roadmap_id": str(roadmap.get("roadmap_id") or ""),
            "manifest_fingerprint": str(roadmap.get("manifest_fingerprint") or ""),
            "active": _json_value(roadmap.get("active"), "roadmap.active"),
            "completed_runs": _json_value(
                roadmap.get("completed_runs") or {}, "roadmap.completed_runs"
            ),
            "blocked_nodes": _json_value(
                roadmap.get("blocked_nodes") or {}, "roadmap.blocked_nodes"
            ),
            "claims_day": roadmap.get("claims_day"),
            "claims_today": roadmap.get("claims_today", 0),
        },
        human_gates=gates,
        objectives=_recent(objectives_all),
        objective_count=len(objectives_all),
        objective_controls=controls_all,
        objective_scopes=_recent(scopes_all),
        scorecard=_json_value(dict(scorecard or {}), "scorecard"),
        active_plan=_plan(active_plan),
        admission=_admission(admission),
        plans=tuple(value for value in plans_all if value is not None),
        plan_count=len(plans_all),
        admissions=tuple(value for value in admissions_all if value is not None),
        admission_count=len(admissions_all),
        local_commits=local_commits_all,
        local_commit_count=len(local_commits_all),
        worker_scheduler=scheduler_all,
        worker_scheduler_count=len(scheduler_all),
        concurrency_claims=claims_all,
        concurrency_claim_count=len(claims_all),
        workers=_recent(workers_all),
        worker_count=len(workers_all),
        completions=_recent(completions_all),
        completion_count=len(completions_all),
        external_claims=claims,
        artifacts=artifacts_all,
        artifact_count=len(artifacts_all),
        human_reviews=_recent(reviews_all),
        human_review_count=len(reviews_all),
        current_product_state=current_product_state,
        program_progression=progression,
        runtime=_json_value(runtime, "runtime"),
    )
