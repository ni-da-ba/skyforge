"""Frozen Platform v2 task and acceptance identity primitives."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from typing import Any


def _canonical_json(value: Any) -> str:
    """Return deterministic JSON for supported task/evidence data."""
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        allow_nan=False,
    )


def canonical_digest(value: Any) -> str:
    """Return a SHA-256 digest over canonical JSON."""
    encoded = _canonical_json(value).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


@dataclass(frozen=True)
class FrozenTaskSpec:
    """Immutable identity for one exact authorized task specification."""

    task_id: str
    authority_key: str
    base_sha: str
    spec_version: int
    canonical_payload: str
    spec_hash: str

    @classmethod
    def from_payload(
        cls,
        *,
        task_id: str,
        authority_key: str,
        base_sha: str,
        spec_version: int,
        payload: Any,
    ) -> "FrozenTaskSpec":
        canonical_payload = _canonical_json(payload)
        envelope = {
            "authority_key": authority_key,
            "base_sha": base_sha,
            "payload": json.loads(canonical_payload),
            "spec_version": spec_version,
            "task_id": task_id,
        }
        return cls(
            task_id=task_id,
            authority_key=authority_key,
            base_sha=base_sha,
            spec_version=spec_version,
            canonical_payload=canonical_payload,
            spec_hash=canonical_digest(envelope),
        )

    def payload(self) -> Any:
        """Return a fresh decoded copy of the frozen payload."""
        return json.loads(self.canonical_payload)


@dataclass(frozen=True)
class TaskAttemptIdentity:
    """Identity for one numbered attempt against one frozen task spec."""

    task_id: str
    spec_hash: str
    base_sha: str
    attempt_number: int
    attempt_id: str

    @classmethod
    def create(
        cls,
        spec: FrozenTaskSpec,
        *,
        attempt_number: int,
    ) -> "TaskAttemptIdentity":
        if attempt_number < 1:
            raise ValueError("attempt_number must be >= 1")
        identity = {
            "attempt_number": attempt_number,
            "base_sha": spec.base_sha,
            "spec_hash": spec.spec_hash,
            "task_id": spec.task_id,
        }
        return cls(
            task_id=spec.task_id,
            spec_hash=spec.spec_hash,
            base_sha=spec.base_sha,
            attempt_number=attempt_number,
            attempt_id=canonical_digest(identity),
        )


@dataclass(frozen=True)
class AcceptanceIdentity:
    """Exact-SHA/spec tuple required before an eventual merge transition."""

    current_head_sha: str
    required_evidence_sha: str
    reviewed_sha: str
    task_spec_hash: str
    accepted_task_spec_hash: str

    def exact_match(self) -> bool:
        shas = {
            self.current_head_sha,
            self.required_evidence_sha,
            self.reviewed_sha,
        }
        return (
            len(shas) == 1
            and "" not in shas
            and bool(self.task_spec_hash)
            and self.task_spec_hash == self.accepted_task_spec_hash
        )
