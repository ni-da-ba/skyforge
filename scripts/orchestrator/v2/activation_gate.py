"""Pure production-activation safety envelope for Platform v2 R5C20.

This policy sits above the accepted Release-5 cutover readiness decision. It can report
that the project is ready for operator review, but it cannot authorize or perform a
writer-authority switch.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
)
from .identity import canonical_digest


def _bool(value: object, label: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{label} must be boolean")
    return value


class ProductionActivationDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    READY_FOR_OPERATOR_REVIEW = "READY_FOR_OPERATOR_REVIEW"


@dataclass(frozen=True)
class ProductionActivationInput:
    cutover: CutoverReadinessDecision
    primary_workstation_preservation_pass: bool
    dr70_migration_hold_cleared: bool
    hosted_shadow_parity_accepted: bool
    hosted_execution_path_accepted: bool
    remote_effect_path_accepted: bool
    cutover_rollback_rehearsal_accepted: bool
    dr70_migration_hold_waived: bool = False

    def __post_init__(self) -> None:
        if not isinstance(self.cutover, CutoverReadinessDecision):
            raise ValueError("cutover must be CutoverReadinessDecision")
        for name in (
            "primary_workstation_preservation_pass",
            "dr70_migration_hold_cleared",
            "hosted_shadow_parity_accepted",
            "hosted_execution_path_accepted",
            "remote_effect_path_accepted",
            "cutover_rollback_rehearsal_accepted",
            "dr70_migration_hold_waived",
        ):
            _bool(getattr(self, name), name)

    def as_dict(self) -> dict[str, object]:
        return {
            "cutover_digest": self.cutover.digest,
            "cutover_disposition": self.cutover.disposition.value,
            "primary_workstation_preservation_pass": (
                self.primary_workstation_preservation_pass
            ),
            "dr70_migration_hold_cleared": self.dr70_migration_hold_cleared,
            "dr70_migration_hold_waived": self.dr70_migration_hold_waived,
            "hosted_shadow_parity_accepted": self.hosted_shadow_parity_accepted,
            "hosted_execution_path_accepted": self.hosted_execution_path_accepted,
            "remote_effect_path_accepted": self.remote_effect_path_accepted,
            "cutover_rollback_rehearsal_accepted": (
                self.cutover_rollback_rehearsal_accepted
            ),
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ProductionActivationDecision:
    disposition: ProductionActivationDisposition
    blockers: tuple[str, ...]
    input_digest: str
    cutover_digest: str
    accepted_main_sha: str
    operator_action_required: bool = True

    def __post_init__(self) -> None:
        if not isinstance(self.disposition, ProductionActivationDisposition):
            raise ValueError("disposition must be ProductionActivationDisposition")
        if not isinstance(self.blockers, tuple) or any(
            not isinstance(value, str) or not value.strip()
            for value in self.blockers
        ):
            raise ValueError("blockers must be a tuple of non-empty strings")
        if not isinstance(self.operator_action_required, bool):
            raise ValueError("operator_action_required must be boolean")
        if not self.operator_action_required:
            raise ValueError("production activation must always require operator action")

    def as_dict(self) -> dict[str, object]:
        return {
            "disposition": self.disposition.value,
            "blockers": list(self.blockers),
            "input_digest": self.input_digest,
            "cutover_digest": self.cutover_digest,
            "accepted_main_sha": self.accepted_main_sha,
            "operator_action_required": self.operator_action_required,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def evaluate_production_activation(
    value: ProductionActivationInput,
) -> ProductionActivationDecision:
    if not isinstance(value, ProductionActivationInput):
        raise ValueError("value must be ProductionActivationInput")

    blockers: list[str] = []

    if value.cutover.disposition is not CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH:
        if value.cutover.blockers:
            blockers.extend(
                f"cutover readiness: {reason}"
                for reason in value.cutover.blockers
            )
        else:
            blockers.append("cutover readiness is not accepted")

    if not value.primary_workstation_preservation_pass:
        blockers.append("primary Windows workstation preservation audit is not PASS")
    if not (value.dr70_migration_hold_cleared or value.dr70_migration_hold_waived):
        blockers.append(
            "DR-70 migration hold is neither cleared nor explicitly waived by the operator"
        )
    if not value.hosted_shadow_parity_accepted:
        blockers.append("hosted Platform-v2 shadow/parity evidence is not accepted")
    if not value.hosted_execution_path_accepted:
        blockers.append("hosted Platform-v2 execution path is not accepted")
    if not value.remote_effect_path_accepted:
        blockers.append("Platform-v2 remote-effect path is not accepted for production")
    if not value.cutover_rollback_rehearsal_accepted:
        blockers.append("production cutover/rollback runbook rehearsal is not accepted")

    return ProductionActivationDecision(
        disposition=(
            ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW
            if not blockers
            else ProductionActivationDisposition.BLOCKED
        ),
        blockers=tuple(blockers),
        input_digest=value.digest,
        cutover_digest=value.cutover.digest,
        accepted_main_sha=value.cutover.accepted_main_sha,
        operator_action_required=True,
    )
