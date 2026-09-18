"""Release-5 cutover readiness and authority-handoff contracts.

This module is side-effect free.  It projects the currently authoritative legacy
controller state into a bounded Platform-v2 operational snapshot and evaluates whether a
future production authority switch is allowed.  It does not stop/start services, acquire
production writer authority, mutate GitHub, or machine-pass human gates.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
import re
from typing import Any, Mapping

from .external import ExternalProducerClaim
from .identity import canonical_digest


_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


def _bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{label} must be boolean")
    return value


def _sha(value: Any, label: str) -> str:
    if not isinstance(value, str) or not _SHA_RE.fullmatch(value):
        raise ValueError(f"{label} must be a lowercase 40-character Git SHA")
    return value


def _optional_text(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{label} must be a string or null")
    text = value.strip()
    return text or None


def _canonical_value(value: Any, label: str) -> Any:
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


@dataclass(frozen=True)
class RoadmapProjection:
    roadmap_id: str
    active: Any
    blocked_nodes: Mapping[str, Any]
    manifest_fingerprint: str

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "RoadmapProjection":
        mapping = _mapping(raw or {}, "roadmap")
        blocked = mapping.get("blocked_nodes") or {}
        blocked_mapping = _mapping(blocked, "roadmap.blocked_nodes")
        return cls(
            roadmap_id=str(mapping.get("roadmap_id") or "").strip(),
            active=_canonical_value(mapping.get("active"), "roadmap.active"),
            blocked_nodes=_canonical_value(
                dict(blocked_mapping),
                "roadmap.blocked_nodes",
            ),
            manifest_fingerprint=str(mapping.get("manifest_fingerprint") or "").strip(),
        )

    @property
    def blocked_node_ids(self) -> tuple[str, ...]:
        return tuple(sorted(str(key) for key in self.blocked_nodes))

    def as_dict(self) -> dict[str, Any]:
        return {
            "roadmap_id": self.roadmap_id,
            "active": self.active,
            "blocked_nodes": dict(self.blocked_nodes),
            "manifest_fingerprint": self.manifest_fingerprint,
        }


@dataclass(frozen=True)
class LegacyOperationalProjection:
    paused: bool
    blocked_kind: str | None
    pending_worker: Any
    pending_decision: Any
    managed: Mapping[str, Any]
    pending_events: tuple[Any, ...]
    external_claims: tuple[ExternalProducerClaim, ...]
    roadmap: RoadmapProjection
    human_gate_records_digest: str
    source_state_digest: str

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "LegacyOperationalProjection":
        state = _mapping(raw, "legacy controller state")

        paused = state.get("paused", False)
        if not isinstance(paused, bool):
            raise ValueError("paused must be boolean")

        managed = _mapping(state.get("managed") or {}, "managed")
        pending_events_raw = state.get("pending_events") or []
        if not isinstance(pending_events_raw, list):
            raise ValueError("pending_events must be a list")

        claims_raw = _mapping(
            state.get("external_producer_claims") or {},
            "external_producer_claims",
        )
        claims: list[ExternalProducerClaim] = []
        for key, value in claims_raw.items():
            claim = ExternalProducerClaim.from_legacy_mapping(value)
            try:
                key_issue = int(str(key))
            except ValueError as exc:
                raise ValueError("external claim key must be an issue number") from exc
            if key_issue != claim.issue_number:
                raise ValueError("external claim key/issue identity mismatch")
            claims.append(claim)
        claims.sort(key=lambda item: item.issue_number)

        gates = _mapping(
            state.get("human_gate_records") or {},
            "human_gate_records",
        )

        return cls(
            paused=paused,
            blocked_kind=_optional_text(state.get("blocked_kind"), "blocked_kind"),
            pending_worker=_canonical_value(
                state.get("pending_worker"),
                "pending_worker",
            ),
            pending_decision=_canonical_value(
                state.get("pending_decision"),
                "pending_decision",
            ),
            managed=_canonical_value(dict(managed), "managed"),
            pending_events=tuple(
                _canonical_value(item, "pending event") for item in pending_events_raw
            ),
            external_claims=tuple(claims),
            roadmap=RoadmapProjection.from_legacy_mapping(state.get("roadmap")),
            human_gate_records_digest=canonical_digest(dict(gates)),
            source_state_digest=canonical_digest(dict(state)),
        )

    @property
    def quiescent(self) -> bool:
        return (
            self.blocked_kind is None
            and self.pending_worker is None
            and self.pending_decision is None
            and not self.managed
            and not self.pending_events
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "paused": self.paused,
            "blocked_kind": self.blocked_kind,
            "pending_worker": self.pending_worker,
            "pending_decision": self.pending_decision,
            "managed": dict(self.managed),
            "pending_events": list(self.pending_events),
            "external_claims": [claim.as_dict() for claim in self.external_claims],
            "roadmap": self.roadmap.as_dict(),
            "human_gate_records_digest": self.human_gate_records_digest,
            "source_state_digest": self.source_state_digest,
            "quiescent": self.quiescent,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


class CutoverReadinessDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    READY_FOR_AUTHORITY_SWITCH = "READY_FOR_AUTHORITY_SWITCH"


@dataclass(frozen=True)
class CutoverReadinessInput:
    release4_accepted: bool
    mutation_gate_disabled: bool
    accepted_main_sha: str
    legacy_runtime_sha: str
    legacy_service_active: bool
    pre_cutover_checkpoint_pass: bool
    hosted_v2_runtime_accepted: bool
    ingress_handoff_defined: bool
    writer_revocation_plan_defined: bool
    legacy_revocation_mechanism_ready: bool
    state_projection_complete: bool
    projection: LegacyOperationalProjection

    def __post_init__(self) -> None:
        for name in (
            "release4_accepted",
            "mutation_gate_disabled",
            "legacy_service_active",
            "pre_cutover_checkpoint_pass",
            "hosted_v2_runtime_accepted",
            "ingress_handoff_defined",
            "writer_revocation_plan_defined",
            "legacy_revocation_mechanism_ready",
            "state_projection_complete",
        ):
            _bool(getattr(self, name), name)
        _sha(self.accepted_main_sha, "accepted_main_sha")
        _sha(self.legacy_runtime_sha, "legacy_runtime_sha")
        if not isinstance(self.projection, LegacyOperationalProjection):
            raise ValueError("projection must be LegacyOperationalProjection")

    @property
    def legacy_matches_accepted_main(self) -> bool:
        return self.legacy_runtime_sha == self.accepted_main_sha


@dataclass(frozen=True)
class CutoverReadinessDecision:
    disposition: CutoverReadinessDisposition
    blockers: tuple[str, ...]
    projection_digest: str
    accepted_main_sha: str
    legacy_runtime_sha: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "blockers": list(self.blockers),
                "projection_digest": self.projection_digest,
                "accepted_main_sha": self.accepted_main_sha,
                "legacy_runtime_sha": self.legacy_runtime_sha,
            }
        )


def evaluate_cutover_readiness(value: CutoverReadinessInput) -> CutoverReadinessDecision:
    blockers: list[str] = []

    if not value.release4_accepted:
        blockers.append("Release 4 is not formally accepted")
    if not value.mutation_gate_disabled:
        blockers.append("bounded canary mutation gate is still enabled")
    if not value.projection.quiescent:
        blockers.append("legacy controller state is not operationally quiescent")
    if not value.state_projection_complete:
        blockers.append("legacy operational authority projection is incomplete")
    if not value.legacy_matches_accepted_main:
        blockers.append("legacy runtime checkout does not match accepted cutover main")
    if not value.pre_cutover_checkpoint_pass:
        blockers.append("pre-v2-cutover rollback checkpoint is not PASS")
    if not value.hosted_v2_runtime_accepted:
        blockers.append("hosted Platform-v2 production runtime is not accepted")
    if not value.ingress_handoff_defined:
        blockers.append("webhook/control ingress handoff is not defined")
    if not value.writer_revocation_plan_defined:
        blockers.append("old-writer revocation/rollback handoff is not defined")
    if not value.legacy_revocation_mechanism_ready:
        blockers.append("privileged legacy-writer revocation mechanism is not operational")

    return CutoverReadinessDecision(
        disposition=(
            CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH
            if not blockers
            else CutoverReadinessDisposition.BLOCKED
        ),
        blockers=tuple(blockers),
        projection_digest=value.projection.digest,
        accepted_main_sha=value.accepted_main_sha,
        legacy_runtime_sha=value.legacy_runtime_sha,
    )


class WriterAuthority(str, Enum):
    LEGACY = "LEGACY"
    NONE = "NONE"
    V2 = "V2"


class WriterAuthorityAction(str, Enum):
    REVOKE_LEGACY = "REVOKE_LEGACY"
    ACTIVATE_V2 = "ACTIVATE_V2"
    REVOKE_V2 = "REVOKE_V2"
    ACTIVATE_LEGACY = "ACTIVATE_LEGACY"


@dataclass(frozen=True)
class WriterAuthorityTransition:
    before: WriterAuthority
    action: WriterAuthorityAction
    after: WriterAuthority
    readiness_accepted: bool

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "before": self.before.value,
                "action": self.action.value,
                "after": self.after.value,
                "readiness_accepted": self.readiness_accepted,
            }
        )


def advance_writer_authority(
    before: WriterAuthority,
    action: WriterAuthorityAction,
    *,
    readiness_accepted: bool,
) -> WriterAuthorityTransition:
    if not isinstance(before, WriterAuthority):
        raise ValueError("before must be WriterAuthority")
    if not isinstance(action, WriterAuthorityAction):
        raise ValueError("action must be WriterAuthorityAction")
    _bool(readiness_accepted, "readiness_accepted")

    if action is WriterAuthorityAction.REVOKE_LEGACY:
        if before is not WriterAuthority.LEGACY:
            raise ValueError("legacy writer can be revoked only from LEGACY authority")
        after = WriterAuthority.NONE
    elif action is WriterAuthorityAction.ACTIVATE_V2:
        if before is not WriterAuthority.NONE:
            raise ValueError(
                "v2 writer can be activated only after legacy writer is visibly revoked"
            )
        if not readiness_accepted:
            raise ValueError("v2 writer activation requires accepted cutover readiness")
        after = WriterAuthority.V2
    elif action is WriterAuthorityAction.REVOKE_V2:
        if before is not WriterAuthority.V2:
            raise ValueError("v2 writer can be revoked only from V2 authority")
        after = WriterAuthority.NONE
    else:
        if before is not WriterAuthority.NONE:
            raise ValueError(
                "legacy writer rollback activation requires v2 writer to be revoked first"
            )
        after = WriterAuthority.LEGACY

    return WriterAuthorityTransition(
        before=before,
        action=action,
        after=after,
        readiness_accepted=readiness_accepted,
    )
