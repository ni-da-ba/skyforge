"""Pure Release-4 canary authority and mutation-gate contracts."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import re
from typing import Any, Mapping

from .identity import FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .ownership import OwnershipToken


CANARY_BRANCH_PREFIX = "platform/v2-canary/"
CANARY_DOC_PREFIX = "docs/operations/platform-v2-canary/"
WRITER_FENCE_RELATIVE_PATH = ".skyforge-platform-v2/writer.lock"
_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _required_text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be a non-empty string")
    return value.strip()


def _sha(value: Any, label: str) -> str:
    text = _required_text(value, label).lower()
    if not _SHA_RE.fullmatch(text):
        raise ValueError(f"{label} must be a lowercase 40-character Git SHA")
    return text


class CanaryGuardDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    READY = "READY"
    ALLOW_MUTATION = "ALLOW_MUTATION"


@dataclass(frozen=True)
class MutationGateRecord:
    schema_version: int
    release3_accepted: bool
    release3_acceptance_main: str
    workstation_preservation_passed: bool
    workstation_evidence: str | None
    canary_enabled: bool
    canary_issue_number: int | None

    @classmethod
    def from_mapping(cls, raw: Any) -> "MutationGateRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("mutation gate must be an object")
        if raw.get("schema_version") != 1:
            raise ValueError("unsupported mutation-gate schema_version")
        for key in (
            "release3_accepted",
            "workstation_preservation_passed",
            "canary_enabled",
        ):
            if not isinstance(raw.get(key), bool):
                raise ValueError(f"{key} must be boolean")
        evidence = raw.get("workstation_evidence")
        if evidence is not None and (not isinstance(evidence, str) or not evidence.strip()):
            raise ValueError("workstation_evidence must be null or non-empty text")
        issue = raw.get("canary_issue_number")
        if issue is not None:
            issue = _positive_int(issue, "canary_issue_number")
        return cls(
            schema_version=1,
            release3_accepted=raw["release3_accepted"],
            release3_acceptance_main=_sha(
                raw.get("release3_acceptance_main"),
                "release3_acceptance_main",
            ),
            workstation_preservation_passed=raw["workstation_preservation_passed"],
            workstation_evidence=evidence.strip() if isinstance(evidence, str) else None,
            canary_enabled=raw["canary_enabled"],
            canary_issue_number=issue,
        )


@dataclass(frozen=True)
class CanaryTaskContract:
    task_id: str
    issue_number: int
    base_sha: str
    head_branch: str
    expected_head_sha: str
    documentation_path: str
    pr_title: str
    pr_body: str
    spec_version: int = 1

    def __post_init__(self) -> None:
        _required_text(self.task_id, "task_id")
        _positive_int(self.issue_number, "issue_number")
        _sha(self.base_sha, "base_sha")
        _sha(self.expected_head_sha, "expected_head_sha")
        if not self.head_branch.startswith(CANARY_BRANCH_PREFIX):
            raise ValueError(
                f"head_branch must stay under {CANARY_BRANCH_PREFIX}"
            )
        if (
            not self.documentation_path.startswith(CANARY_DOC_PREFIX)
            or ".." in self.documentation_path.split("/")
            or not self.documentation_path.endswith((".md", ".json"))
        ):
            raise ValueError(
                f"documentation_path must be a .md/.json file under {CANARY_DOC_PREFIX}"
            )
        _required_text(self.pr_title, "pr_title")
        _required_text(self.pr_body, "pr_body")
        if isinstance(self.spec_version, bool) or not isinstance(self.spec_version, int):
            raise ValueError("spec_version must be an integer")
        if self.spec_version <= 0:
            raise ValueError("spec_version must be positive")

    @classmethod
    def from_mapping(cls, raw: Any) -> "CanaryTaskContract":
        if not isinstance(raw, Mapping):
            raise ValueError("canary task must be an object")
        return cls(
            task_id=_required_text(raw.get("task_id"), "task_id"),
            issue_number=_positive_int(raw.get("issue_number"), "issue_number"),
            base_sha=_sha(raw.get("base_sha"), "base_sha"),
            head_branch=_required_text(raw.get("head_branch"), "head_branch"),
            expected_head_sha=_sha(raw.get("expected_head_sha"), "expected_head_sha"),
            documentation_path=_required_text(
                raw.get("documentation_path"), "documentation_path"
            ),
            pr_title=_required_text(raw.get("pr_title"), "pr_title"),
            pr_body=_required_text(raw.get("pr_body"), "pr_body"),
            spec_version=raw.get("spec_version", 1),
        )

    def frozen_spec(self) -> FrozenTaskSpec:
        payload = {
            "issue_number": self.issue_number,
            "head_branch": self.head_branch,
            "expected_head_sha": self.expected_head_sha,
            "documentation_path": self.documentation_path,
            "pr_title": self.pr_title,
            "pr_body": self.pr_body,
            "canary_class": "PRESTAGED_DOCUMENTATION_PR",
        }
        return FrozenTaskSpec.from_payload(
            task_id=self.task_id,
            authority_key=f"issue:{self.issue_number}",
            base_sha=self.base_sha,
            spec_version=self.spec_version,
            payload=payload,
        )

    def attempt(self, attempt_number: int) -> TaskAttemptIdentity:
        return TaskAttemptIdentity.create(
            self.frozen_spec(),
            attempt_number=attempt_number,
        )


@dataclass(frozen=True)
class LegacyCanaryExclusion:
    issue_number: int
    active: bool
    branch: str | None
    claimed_by: str | None
    pr_number: int | None = None

    @classmethod
    def from_legacy_state(
        cls,
        state: Any,
        *,
        issue_number: int,
    ) -> "LegacyCanaryExclusion":
        issue = _positive_int(issue_number, "issue_number")
        if not isinstance(state, Mapping):
            raise ValueError("legacy controller state must be an object")
        claims = state.get("external_producer_claims") or {}
        if not isinstance(claims, Mapping):
            raise ValueError("external_producer_claims must be an object")
        raw = claims.get(str(issue))
        if raw is None:
            return cls(issue, False, None, None, None)
        if not isinstance(raw, Mapping):
            raise ValueError("external producer claim must be an object")
        stored_issue = _positive_int(
            raw.get("issue_number"),
            "external producer claim issue_number",
        )
        if stored_issue != issue:
            raise ValueError("external producer claim issue identity mismatch")
        state_value = str(raw.get("state") or "active").strip()
        branch = raw.get("branch")
        claimed_by = raw.get("claimed_by")
        pr = raw.get("pr_number")
        if branch is not None:
            branch = _required_text(branch, "external claim branch")
        if claimed_by is not None:
            claimed_by = _required_text(claimed_by, "external claim claimed_by")
        if pr is not None:
            pr = _positive_int(pr, "external claim pr_number")
        return cls(
            issue_number=issue,
            active=state_value == "active",
            branch=branch,
            claimed_by=claimed_by,
            pr_number=pr,
        )


@dataclass(frozen=True)
class CanaryGuardDecision:
    disposition: CanaryGuardDisposition
    reason: str
    task_spec_hash: str
    attempt_id: str
    ownership_token_digest: str
    writer_fence_relative_path: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "task_spec_hash": self.task_spec_hash,
                "attempt_id": self.attempt_id,
                "ownership_token_digest": self.ownership_token_digest,
                "writer_fence_relative_path": self.writer_fence_relative_path,
            }
        )


def evaluate_canary_guard(
    *,
    gate: MutationGateRecord,
    task: CanaryTaskContract,
    exclusion: LegacyCanaryExclusion,
    current_main: str,
    ownership_token: OwnershipToken,
    attempt_number: int,
    execute_requested: bool,
) -> CanaryGuardDecision:
    if not isinstance(execute_requested, bool):
        raise ValueError("execute_requested must be boolean")
    current_main = _sha(current_main, "current_main")
    spec = task.frozen_spec()
    attempt = TaskAttemptIdentity.create(spec, attempt_number=attempt_number)

    def decision(
        disposition: CanaryGuardDisposition,
        reason: str,
    ) -> CanaryGuardDecision:
        return CanaryGuardDecision(
            disposition=disposition,
            reason=reason,
            task_spec_hash=spec.spec_hash,
            attempt_id=attempt.attempt_id,
            ownership_token_digest=ownership_token.digest,
            writer_fence_relative_path=WRITER_FENCE_RELATIVE_PATH,
        )

    if not gate.release3_accepted:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "Release 3 has not been accepted",
        )
    if not gate.workstation_preservation_passed:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "primary-workstation preservation audit is still pending",
        )
    if not gate.workstation_evidence:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "workstation preservation PASS lacks durable evidence",
        )
    if not gate.canary_enabled or gate.canary_issue_number is None:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "Release-4 canary authority is not enabled",
        )
    if gate.canary_issue_number != task.issue_number:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "task issue is outside the explicitly enabled canary authority",
        )
    if current_main != task.base_sha:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "current main no longer matches frozen canary base SHA",
        )
    if exclusion.issue_number != task.issue_number or not exclusion.active:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "legacy controller exclusion claim is not active for the exact canary issue",
        )
    if exclusion.branch != task.head_branch:
        return decision(
            CanaryGuardDisposition.BLOCKED,
            "legacy controller exclusion claim does not bind the exact canary branch",
        )

    if execute_requested:
        return decision(
            CanaryGuardDisposition.ALLOW_MUTATION,
            "all canary safety gates are satisfied; a separately fenced mutation adapter may execute",
        )
    return decision(
        CanaryGuardDisposition.READY,
        "all canary safety gates are satisfied; mutation was not requested",
    )
