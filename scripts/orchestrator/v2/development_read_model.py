"""Canonical read-only development-state projection for Platform-v2 clients."""

from __future__ import annotations

from dataclasses import dataclass
import json
import re
from typing import Any, Iterable, Mapping

from .identity import canonical_digest

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


@dataclass(frozen=True)
class DevelopmentSnapshot:
    repo: str
    checkout_head_sha: str
    roadmap: Mapping[str, Any]
    human_gates: tuple[Mapping[str, Any], ...]
    objectives: tuple[Mapping[str, Any], ...]
    objective_count: int
    active_plan: Mapping[str, Any] | None
    admission: Mapping[str, Any] | None
    workers: tuple[Mapping[str, Any], ...]
    worker_count: int
    completions: tuple[Mapping[str, Any], ...]
    completion_count: int
    external_claims: tuple[Mapping[str, Any], ...]
    artifacts: tuple[Mapping[str, Any], ...]
    artifact_count: int
    human_reviews: tuple[Mapping[str, Any], ...]
    human_review_count: int
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
            "execution": {
                "active_plan": (
                    None if self.active_plan is None else dict(self.active_plan)
                ),
                "admission": (
                    None if self.admission is None else dict(self.admission)
                ),
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
    active_plan: Mapping[str, Any] | None = None,
    admission: Mapping[str, Any] | None = None,
    worker_records: Iterable[Mapping[str, Any]] = (),
    completion_records: Iterable[Mapping[str, Any]] = (),
    external_claims: Iterable[Mapping[str, Any]] = (),
    artifact_records: Iterable[Mapping[str, Any]] = (),
    human_reviews: Iterable[Mapping[str, Any]] = (),
    human_gates: Iterable[Mapping[str, Any]] = (),
    runtime: Mapping[str, Any],
) -> DevelopmentSnapshot:
    repository = str(repo or "").strip()
    if repository.count("/") != 1:
        raise ValueError("repo must be owner/name")
    checkout = _sha40(checkout_head_sha, "checkout_head_sha")
    projection = _mapping(legacy_projection, "legacy projection")
    roadmap = _mapping(projection.get("roadmap") or {}, "legacy roadmap projection")

    objectives_all = tuple(_objective(_mapping(value, "objective")) for value in objective_records)
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
        _human_review(_mapping(value, "human review"), artifacts_by_id)
        for value in human_reviews
    )
    claims = tuple(_claim(_mapping(value, "external claim")) for value in external_claims)
    gates = tuple(_human_gate(_mapping(value, "human gate")) for value in human_gates)

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
        active_plan=_plan(active_plan),
        admission=_admission(admission),
        workers=_recent(workers_all),
        worker_count=len(workers_all),
        completions=_recent(completions_all),
        completion_count=len(completions_all),
        external_claims=claims,
        artifacts=artifacts_all,
        artifact_count=len(artifacts_all),
        human_reviews=_recent(reviews_all),
        human_review_count=len(reviews_all),
        runtime=_json_value(runtime, "runtime"),
    )
