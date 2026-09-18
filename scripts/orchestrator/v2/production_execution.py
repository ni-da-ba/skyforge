"""Gated production execution primitives for Platform v2 R5C24.

This module composes already-accepted durable identities. It does not itself start an HTTP
server or switch writer authority. Mutation-capable functions require an explicit local
ProductionExecutionPermit proving an operator-reviewed NONE -> V2 handoff.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import re
import subprocess
from typing import Any, Mapping

from .activation_gate import (
    ProductionActivationDecision,
    ProductionActivationDisposition,
)
from .dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitStore,
)
from .effects import RemoteEffectPresence
from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .identity import canonical_digest
from .ordinary_effect_executor import (
    OrdinaryEffectExecutionDisposition,
    advance_remote_effect,
)
from .ordinary_effects import OrdinaryEffectStore, OrdinaryMutationScope
from .ordinary_remote import GhGitOrdinaryEffectAdapter, OrdinaryEffectBinding
from .ordinary_service import ManagedOrdinaryHandoff
from .state_store import JsonStateStoreAdapter


PRODUCTION_PERMIT_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "production-execution-permit.json"
)
PRODUCTION_PERMIT_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "production-execution-permit.json.bak"
)
MANAGED_HANDOFF_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-managed-handoff.json"
)
MANAGED_HANDOFF_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-managed-handoff.json.bak"
)
_PR_REMOTE_RE = re.compile(
    r"^pr:(?P<number>[1-9][0-9]*):(?P<state>[A-Z_]+)@(?P<head>[0-9a-f]{40})$"
)


def _sha40(value: Any, label: str) -> str:
    text=str(value or "").strip()
    if len(text)!=40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _sha64(value: Any, label: str) -> str:
    text=str(value or "").strip()
    if len(text)!=64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


@dataclass(frozen=True)
class ProductionExecutionPermit:
    accepted_main_sha: str
    activation_decision_digest: str
    activation_disposition: str
    operator_approved: bool
    legacy_writer_revoked: bool
    writer_authority: str = "V2"

    def __post_init__(self) -> None:
        object.__setattr__(
            self,"accepted_main_sha",_sha40(self.accepted_main_sha,"accepted_main_sha")
        )
        object.__setattr__(
            self,
            "activation_decision_digest",
            _sha64(self.activation_decision_digest,"activation_decision_digest"),
        )
        if self.activation_disposition != ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW.value:
            raise ValueError("production permit requires READY_FOR_OPERATOR_REVIEW")
        if self.operator_approved is not True:
            raise ValueError("production permit requires explicit operator approval")
        if self.legacy_writer_revoked is not True:
            raise ValueError("production permit requires observably revoked legacy writer")
        if self.writer_authority != "V2":
            raise ValueError("production permit writer_authority must be V2")

    @classmethod
    def from_activation_decision(
        cls,
        decision: ProductionActivationDecision,
        *,
        operator_approved: bool,
        legacy_writer_revoked: bool,
    ) -> "ProductionExecutionPermit":
        if not isinstance(decision,ProductionActivationDecision):
            raise ValueError("decision must be ProductionActivationDecision")
        return cls(
            accepted_main_sha=decision.accepted_main_sha,
            activation_decision_digest=decision.digest,
            activation_disposition=decision.disposition.value,
            operator_approved=operator_approved,
            legacy_writer_revoked=legacy_writer_revoked,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "accepted_main_sha": self.accepted_main_sha,
            "activation_decision_digest": self.activation_decision_digest,
            "activation_disposition": self.activation_disposition,
            "operator_approved": self.operator_approved,
            "legacy_writer_revoked": self.legacy_writer_revoked,
            "writer_authority": self.writer_authority,
            "permit_id": self.permit_id,
        }

    @property
    def permit_id(self) -> str:
        return canonical_digest({
            "accepted_main_sha": self.accepted_main_sha,
            "activation_decision_digest": self.activation_decision_digest,
            "activation_disposition": self.activation_disposition,
            "operator_approved": self.operator_approved,
            "legacy_writer_revoked": self.legacy_writer_revoked,
            "writer_authority": self.writer_authority,
        })

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProductionExecutionPermit":
        if not isinstance(raw,Mapping):
            raise ValueError("production execution permit must be object")
        value=cls(
            accepted_main_sha=raw.get("accepted_main_sha"),
            activation_decision_digest=raw.get("activation_decision_digest"),
            activation_disposition=str(raw.get("activation_disposition") or ""),
            operator_approved=raw.get("operator_approved"),
            legacy_writer_revoked=raw.get("legacy_writer_revoked"),
            writer_authority=str(raw.get("writer_authority") or ""),
        )
        if _sha64(raw.get("permit_id"),"permit_id") != value.permit_id:
            raise ValueError("production execution permit identity mismatch")
        return value


@dataclass(frozen=True)
class ProductionExecutionPermitStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ProductionExecutionPermitStore":
        root=Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / PRODUCTION_PERMIT_RELATIVE_PATH,
            backup_path=root / PRODUCTION_PERMIT_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ProductionExecutionPermit | None:
        raw=self.adapter.load().as_dict()
        if not raw:
            return None
        return ProductionExecutionPermit.from_mapping(raw)

    def save(self, permit: ProductionExecutionPermit) -> ProductionExecutionPermit:
        self.adapter.save(permit.as_dict())
        return permit


def read_checkout_head(root: Path, *, runner=subprocess.run) -> str:
    try:
        result=runner(
            ["git","rev-parse","HEAD"],
            cwd=Path(root).resolve(),
            check=True,text=True,capture_output=True,timeout=30,
        )
    except subprocess.SubprocessError as exc:
        raise RuntimeError(f"unable to verify production checkout: {exc}") from exc
    return _sha40(str(result.stdout or "").strip(),"checkout HEAD")


def validate_production_permit(
    root: Path,
    permit: ProductionExecutionPermit,
    *,
    runner=subprocess.run,
) -> None:
    if not isinstance(permit,ProductionExecutionPermit):
        raise ValueError("permit must be ProductionExecutionPermit")
    head=read_checkout_head(root,runner=runner)
    if head != permit.accepted_main_sha:
        raise RuntimeError(
            "production permit accepted-main SHA does not match runtime checkout"
        )


@dataclass(frozen=True)
class HostedManagedHandoffLedger:
    handoff: ManagedOrdinaryHandoff | None = None

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedManagedHandoffLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw,Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid hosted managed-handoff ledger")
        value=raw.get("handoff")
        handoff=None if value is None else ManagedOrdinaryHandoff.from_mapping(value)
        if handoff is not None:
            if _sha64(raw.get("handoff_digest"),"handoff_digest") != handoff.digest:
                raise ValueError("hosted managed-handoff digest mismatch")
        return cls(handoff)

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version":1,
            "handoff": self.handoff.as_dict() if self.handoff else None,
            "handoff_digest": self.handoff.digest if self.handoff else "",
        }


@dataclass(frozen=True)
class HostedManagedHandoffStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedManagedHandoffStore":
        root=Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / MANAGED_HANDOFF_RELATIVE_PATH,
            backup_path=root / MANAGED_HANDOFF_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> HostedManagedHandoffLedger:
        return HostedManagedHandoffLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedManagedHandoffLedger) -> HostedManagedHandoffLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


class RemoteHandoffDisposition(str, Enum):
    COMPLETE="COMPLETE"
    ALREADY_COMPLETE="ALREADY_COMPLETE"
    NOT_READY="NOT_READY"
    BLOCKED="BLOCKED"


@dataclass(frozen=True)
class RemoteHandoffResult:
    disposition: RemoteHandoffDisposition
    reason: str
    handoff: ManagedOrdinaryHandoff | None = None

    @property
    def digest(self) -> str:
        return canonical_digest({
            "disposition":self.disposition.value,
            "reason":self.reason,
            "handoff_digest":self.handoff.digest if self.handoff else "",
        })


def _effect_ok(result) -> bool:
    return result.disposition in {
        OrdinaryEffectExecutionDisposition.EXECUTED,
        OrdinaryEffectExecutionDisposition.RECONCILED,
        OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE,
    }


def _pr_metadata(admission) -> tuple[str,str,bool]:
    assert admission.frozen_task is not None
    payload=admission.frozen_task.payload()
    authority=payload.get("authority") if isinstance(payload,dict) else None
    if not isinstance(authority,dict):
        raise ValueError("frozen task lacks repository authority payload")
    lane=str(authority.get("lane") or "").strip()
    objective=str(authority.get("objective") or "").strip()
    stop=str(authority.get("stop_boundary") or "").strip()
    auto=authority.get("auto_merge_eligible",False)
    if not lane or not objective or not stop or not isinstance(auto,bool):
        raise ValueError("frozen repository authority is malformed")
    title=f"CODEX {lane}: {objective}"
    body=(
        "Managed by Platform v2.\n\n"
        f"Task: {admission.frozen_task.task_id}\n"
        f"Issue: #{admission.issue_number}\n"
        f"Task-Spec: {admission.frozen_task.spec_hash}\n"
        f"Attempt: {admission.attempt.attempt_id}\n"
        f"Stop boundary: {stop}"
    )
    return title,body,auto


def advance_committed_remote_handoff(
    *,
    root: Path,
    repo: str,
    runner=subprocess.run,
) -> RemoteHandoffResult:
    root=Path(root).resolve()
    admission=HostedAdmissionStore.for_root(root).load().record
    commit=DormantHandoffCommitStore.for_root(root).load().record
    existing=HostedManagedHandoffStore.for_root(root).load().handoff

    if existing is not None:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.ALREADY_COMPLETE,
            "exact managed handoff is already durable",
            existing,
        )
    if admission is None or admission.outcome is not HostedAdmissionOutcome.ADMITTED:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.NOT_READY,
            "no admitted hosted task is ready for remote handoff",
        )
    if commit is None or commit.outcome is not DormantCommitOutcome.COMMITTED:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.NOT_READY,
            "bounded local commit is not COMMITTED",
        )
    if admission.attempt is None or admission.worker_spec is None or admission.frozen_task is None:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.BLOCKED,
            "admission lacks frozen task/attempt/worker identity",
        )
    if (
        commit.admission_record_id != admission.record_id
        or commit.attempt_id != admission.attempt.attempt_id
        or commit.branch != admission.worker_spec.branch
        or commit.base_sha != admission.worker_spec.base_sha
    ):
        return RemoteHandoffResult(
            RemoteHandoffDisposition.BLOCKED,
            "local commit identity differs from admitted worker authority",
        )

    title,body,auto=_pr_metadata(admission)
    scope=OrdinaryMutationScope(
        attempt_id=admission.attempt.attempt_id,
        repo=repo,
        base_sha=commit.base_sha,
        branch=commit.branch,
        expected_head_sha=commit.head_sha,
        pr_title=title,
        pr_body=body,
        issue_number=admission.issue_number,
    )
    effects=OrdinaryEffectStore.for_root(root)

    push_binding=OrdinaryEffectBinding(scope=scope,identity=scope.push_identity())
    push=advance_remote_effect(
        store=effects,
        identity=push_binding.identity,
        adapter=GhGitOrdinaryEffectAdapter(root=root,binding=push_binding,runner=runner),
    )
    if not _effect_ok(push):
        return RemoteHandoffResult(RemoteHandoffDisposition.BLOCKED,push.reason)

    create_binding=OrdinaryEffectBinding(scope=scope,identity=scope.create_pr_identity())
    adapter=GhGitOrdinaryEffectAdapter(root=root,binding=create_binding,runner=runner)
    create=advance_remote_effect(
        store=effects,identity=create_binding.identity,adapter=adapter,
    )
    if not _effect_ok(create):
        return RemoteHandoffResult(RemoteHandoffDisposition.BLOCKED,create.reason)

    truth=adapter.observe(create_binding.identity)
    if truth.presence is not RemoteEffectPresence.PRESENT_EXACT:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.BLOCKED,
            "completed CREATE_PR lacks exact current PR truth",
        )
    match=_PR_REMOTE_RE.fullmatch(truth.remote_identity)
    if not match or match.group("state")!="OPEN" or match.group("head")!=commit.head_sha:
        return RemoteHandoffResult(
            RemoteHandoffDisposition.BLOCKED,
            "current managed draft PR identity is malformed or drifted",
        )
    pr_number=int(match.group("number"))
    handoff=ManagedOrdinaryHandoff(
        task_id=admission.frozen_task.task_id,
        authority_key=admission.frozen_task.authority_key,
        task_spec_hash=admission.frozen_task.spec_hash,
        lane=admission.worker_spec.lane,
        scope=scope,
        pr_number=pr_number,
        changed_paths=commit.changed_paths,
        auto_merge_eligible=auto,
    )
    HostedManagedHandoffStore.for_root(root).save(
        HostedManagedHandoffLedger(handoff)
    )
    return RemoteHandoffResult(
        RemoteHandoffDisposition.COMPLETE,
        "bounded commit reached exact managed draft PR",
        handoff,
    )
