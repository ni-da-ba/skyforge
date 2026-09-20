"""Routine accepted-main upgrade composition for production Platform-v2.

This module composes the already-accepted rollback/cutover primitives.  It deliberately
does not weaken protected-authority semantics: the common quiescent path is one bounded
operator action, while any protected/non-quiescent fallback state stops on paused legacy
for explicit reconciliation/transfer.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
import os
from pathlib import Path
import re
import subprocess
import time
from typing import Any, Mapping

from .activation_gate import (
    ProductionActivationDisposition,
    ProductionActivationInput,
    evaluate_production_activation,
)
from .cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
    LegacyOperationalProjection,
    WriterAuthority,
)
from .hosted_execution_runtime import production_activation_input_from_mapping
from .identity import canonical_digest
from .operator_cutover import (
    LEGACY_SERVICE,
    V2_SERVICE,
    OperatorCutoverController,
    OperatorDisposition,
)

_SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
_SPECIAL_UPGRADE_PATHS = (
    "deploy/orchestrator/skyforge-orchestrator.service.in",
    "deploy/orchestrator/skyforge-orchestrator-v2.service.in",
    "scripts/orchestrator/requirements.txt",
    "scripts/orchestrator/stage_platform_v2_cutover.sh",
)

# A production checkout may occasionally sit on an unmerged validation-maintenance
# commit after inspecting or repairing CI.  Discarding arbitrary divergent work is
# forbidden.  Only current-only changes confined to these non-runtime validation
# surfaces are portable to an accepted target that descends from the last activation
# baseline.  In particular, scripts/orchestrator/** is intentionally NOT included.
_PORTABLE_CHECKOUT_ONLY_PREFIXES = (
    ".github/workflows/",
    "scripts/ci/",
    "config/ci/",
)


class RoutineUpgradeDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    READY = "READY"
    COMPLETE = "COMPLETE"
    FAILED_SAFE_LEGACY = "FAILED_SAFE_LEGACY"
    FAILED_SAFE_NONE = "FAILED_SAFE_NONE"


@dataclass(frozen=True)
class PortableActivationBaseline:
    raw: Mapping[str, Any]
    production: ProductionActivationInput
    accepted_main_sha: str
    source_digest: str


@dataclass(frozen=True)
class RoutineUpgradeEvent:
    sequence: int
    kind: str
    authority: WriterAuthority
    detail: str = ""

    def as_dict(self) -> dict[str, object]:
        return {
            "sequence": self.sequence,
            "kind": self.kind,
            "authority": self.authority.value,
            "detail": self.detail,
        }


@dataclass(frozen=True)
class RoutineUpgradeReport:
    disposition: RoutineUpgradeDisposition
    authority: WriterAuthority
    blockers: tuple[str, ...]
    previous_head_sha: str
    prior_accepted_main_sha: str
    target_sha: str
    events: tuple[RoutineUpgradeEvent, ...] = ()
    checkpoint_path: str = ""
    activation_template_path: str = ""

    def as_dict(self) -> dict[str, object]:
        return {
            "disposition": self.disposition.value,
            "authority": self.authority.value,
            "blockers": list(self.blockers),
            "previous_head_sha": self.previous_head_sha,
            "prior_accepted_main_sha": self.prior_accepted_main_sha,
            "target_sha": self.target_sha,
            "checkpoint_path": self.checkpoint_path,
            "activation_template_path": self.activation_template_path,
            "events": [event.as_dict() for event in self.events],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def _read_mapping(path: Path, label: str) -> dict[str, Any]:
    try:
        raw = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{label} is unreadable: {path}") from exc
    if not isinstance(raw, Mapping):
        raise ValueError(f"{label} must be a JSON object")
    return dict(raw)


def _atomic_write_json(path: Path, value: Mapping[str, Any]) -> None:
    path = Path(path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    payload = json.dumps(dict(value), sort_keys=True, indent=2) + "\n"
    with tmp.open("w", encoding="utf-8") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(tmp, path)


def _production_mapping(value: ProductionActivationInput) -> dict[str, object]:
    return {
        "cutover": {
            "disposition": value.cutover.disposition.value,
            "blockers": list(value.cutover.blockers),
            "projection_digest": value.cutover.projection_digest,
            "accepted_main_sha": value.cutover.accepted_main_sha,
            "legacy_runtime_sha": value.cutover.legacy_runtime_sha,
        },
        "primary_workstation_preservation_pass": value.primary_workstation_preservation_pass,
        "dr70_migration_hold_cleared": value.dr70_migration_hold_cleared,
        "dr70_migration_hold_waived": value.dr70_migration_hold_waived,
        "hosted_shadow_parity_accepted": value.hosted_shadow_parity_accepted,
        "hosted_execution_path_accepted": value.hosted_execution_path_accepted,
        "remote_effect_path_accepted": value.remote_effect_path_accepted,
        "cutover_rollback_rehearsal_accepted": value.cutover_rollback_rehearsal_accepted,
    }


def load_portable_activation_baseline(path: Path) -> PortableActivationBaseline:
    raw = _read_mapping(path, "activation evidence")
    if raw.get("schema_version") != 1:
        raise ValueError("activation evidence schema_version must be 1")
    production_raw = raw.get("production_activation_input")
    production = production_activation_input_from_mapping(production_raw)
    if str(raw.get("production_activation_input_digest") or "") != production.digest:
        raise ValueError("activation evidence production input digest mismatch")
    decision = evaluate_production_activation(production)
    if decision.disposition is not ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW:
        raise ValueError("prior production activation evidence is not accepted")
    accepted = str(raw.get("accepted_main_sha") or "")
    if not _SHA40_RE.fullmatch(accepted):
        raise ValueError("activation evidence accepted_main_sha is invalid")
    if accepted != decision.accepted_main_sha:
        raise ValueError("activation evidence accepted main differs from production evidence")
    if raw.get("operator_activation_requested") is not True:
        raise ValueError("prior activation did not record explicit operator request")
    if raw.get("legacy_writer_revoked_observed") is not True:
        raise ValueError("prior activation lacks observed legacy revocation")
    if raw.get("writer_authority") != WriterAuthority.NONE.value:
        raise ValueError("prior activation did not cross a writer NONE boundary")
    return PortableActivationBaseline(
        raw=raw,
        production=production,
        accepted_main_sha=accepted,
        source_digest=canonical_digest(raw),
    )


def build_routine_upgrade_template(
    *,
    baseline: PortableActivationBaseline,
    target_sha: str,
    legacy_state: Mapping[str, Any],
) -> dict[str, object]:
    if not _SHA40_RE.fullmatch(str(target_sha or "")):
        raise ValueError("target_sha must be a lowercase 40-character Git SHA")
    projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state)
    if not projection.paused:
        raise ValueError("legacy fallback state must remain paused during routine upgrade")
    if not projection.quiescent:
        raise ValueError(
            "legacy fallback state is not quiescent; reconcile/transfer authority before cutover"
        )

    cutover = CutoverReadinessDecision(
        disposition=CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH,
        blockers=(),
        projection_digest=projection.digest,
        accepted_main_sha=target_sha,
        legacy_runtime_sha=target_sha,
    )
    prior = baseline.production
    production = ProductionActivationInput(
        cutover=cutover,
        primary_workstation_preservation_pass=prior.primary_workstation_preservation_pass,
        dr70_migration_hold_cleared=prior.dr70_migration_hold_cleared,
        hosted_shadow_parity_accepted=prior.hosted_shadow_parity_accepted,
        hosted_execution_path_accepted=prior.hosted_execution_path_accepted,
        remote_effect_path_accepted=prior.remote_effect_path_accepted,
        cutover_rollback_rehearsal_accepted=prior.cutover_rollback_rehearsal_accepted,
        dr70_migration_hold_waived=prior.dr70_migration_hold_waived,
    )
    decision = evaluate_production_activation(production)
    if decision.disposition is not ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW:
        raise ValueError(
            "portable production evidence no longer satisfies activation: "
            + "; ".join(decision.blockers)
        )
    template: dict[str, object] = {
        "schema_version": 1,
        "operator_activation_requested": True,
        "accepted_main_sha": target_sha,
        "production_activation_input_digest": production.digest,
        "production_activation_input": _production_mapping(production),
        "routine_upgrade": {
            "prior_accepted_main_sha": baseline.accepted_main_sha,
            "prior_activation_evidence_digest": baseline.source_digest,
            "legacy_projection_digest": projection.digest,
        },
    }
    for key in ("dr70_migration_hold_disposition", "dr70_product_status"):
        if key in baseline.raw:
            template[key] = baseline.raw[key]
    return template


class RoutineUpgradeController:
    def __init__(
        self,
        *,
        root: Path,
        activation_template: Path,
        activation_evidence: Path,
        operator: OperatorCutoverController,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.activation_template = Path(activation_template).resolve()
        self.activation_evidence = Path(activation_evidence).resolve()
        self.operator = operator
        self.runner = runner

    def _git_result(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return self.runner(
            ["git", "-C", str(self.root), *args],
            text=True,
            capture_output=True,
            check=check,
        )

    def _git(self, *args: str) -> str:
        return self._git_result(*args).stdout.strip()

    def _head(self) -> str:
        return self._git("rev-parse", "HEAD")

    def _tracked_clean(self) -> bool:
        return not bool(self._git("status", "--porcelain", "--untracked-files=no"))

    def _is_ancestor(self, older: str, newer: str) -> bool:
        return self._git_result("merge-base", "--is-ancestor", older, newer, check=False).returncode == 0

    def _current_only_paths(self, current: str, target: str) -> tuple[str, ...]:
        merge_base = self._git("merge-base", current, target)
        result = self._git("diff", "--name-only", merge_base, current)
        return tuple(line for line in result.splitlines() if line.strip())

    def _portable_current_divergence(self, current: str, target: str) -> tuple[bool, tuple[str, ...]]:
        paths = self._current_only_paths(current, target)
        if not paths:
            return True, ()
        portable = all(
            any(path.startswith(prefix) for prefix in _PORTABLE_CHECKOUT_ONLY_PREFIXES)
            for path in paths
        )
        return portable, paths

    def _legacy_state(self) -> dict[str, Any]:
        return _read_mapping(
            self.root / ".skyforge-orchestrator" / "state.json",
            "legacy fallback state",
        )

    def _legacy_projection_blockers(self) -> list[str]:
        blockers: list[str] = []
        try:
            projection = LegacyOperationalProjection.from_legacy_mapping(self._legacy_state())
            if not projection.paused:
                blockers.append("legacy fallback state is not paused")
            if not projection.quiescent:
                blockers.append(
                    "legacy fallback state is not quiescent; explicit reconciliation/transfer required"
                )
        except Exception as exc:
            blockers.append(f"legacy fallback state invalid: {type(exc).__name__}: {exc}")
        return blockers

    def _special_path_changes(self, older: str, newer: str) -> tuple[str, ...]:
        result = self._git(
            "diff",
            "--name-only",
            older,
            newer,
            "--",
            *_SPECIAL_UPGRADE_PATHS,
        )
        return tuple(line for line in result.splitlines() if line.strip())

    def _authority(self) -> WriterAuthority:
        legacy = self.operator.services.observe(LEGACY_SERVICE)
        v2 = self.operator.services.observe(V2_SERVICE)
        if legacy.active and not v2.active:
            return WriterAuthority.LEGACY
        if v2.active and not legacy.active:
            return WriterAuthority.V2
        return WriterAuthority.NONE

    def preflight(self, target_sha: str) -> RoutineUpgradeReport:
        target = str(target_sha or "").strip()
        blockers: list[str] = []
        previous = ""
        prior = ""
        authority = WriterAuthority.NONE
        if not _SHA40_RE.fullmatch(target):
            blockers.append("target SHA must be a lowercase 40-character Git SHA")

        baseline: PortableActivationBaseline | None = None
        try:
            baseline = load_portable_activation_baseline(self.activation_evidence)
            prior = baseline.accepted_main_sha
        except Exception as exc:
            blockers.append(f"prior activation evidence invalid: {type(exc).__name__}: {exc}")

        try:
            previous = self._head()
            if not self._tracked_clean():
                blockers.append("tracked production checkout is not clean")
            if _SHA40_RE.fullmatch(target):
                if self._git_result("cat-file", "-e", f"{target}^{{commit}}", check=False).returncode != 0:
                    blockers.append("target SHA is not present in the production repository")
                else:
                    origin_main = self._git("rev-parse", "origin/main")
                    if target != origin_main:
                        blockers.append("target SHA must equal the fetched origin/main")
                    if baseline is not None and not self._is_ancestor(baseline.accepted_main_sha, target):
                        blockers.append("target is not a forward descendant of prior accepted activation")
                    if previous and not self._is_ancestor(previous, target):
                        portable, current_only = self._portable_current_divergence(previous, target)
                        if not portable:
                            blockers.append(
                                "current checkout contains non-portable divergent changes: "
                                + ", ".join(current_only)
                            )
                    if baseline is not None:
                        changed = self._special_path_changes(baseline.accepted_main_sha, target)
                        if changed:
                            blockers.append(
                                "routine path does not cover deployment/dependency contract changes: "
                                + ", ".join(changed)
                            )
        except Exception as exc:
            blockers.append(f"repository upgrade preflight failed: {type(exc).__name__}: {exc}")

        try:
            legacy = self.operator.services.observe(LEGACY_SERVICE)
            v2 = self.operator.services.observe(V2_SERVICE)
            authority = self._authority()
            blockers.extend(self.operator._unit_contract_blockers(legacy, v2))
            if legacy.active:
                blockers.append("legacy writer must be inactive before routine V2 upgrade")
            if legacy.enabled:
                blockers.append("legacy writer must be boot-disabled before routine V2 upgrade")
            if not v2.active:
                blockers.append("Platform-v2 writer must be active before routine V2 upgrade")
            if not v2.enabled:
                blockers.append("Platform-v2 writer must be boot-enabled before routine V2 upgrade")
        except Exception as exc:
            blockers.append(f"service upgrade preflight failed: {type(exc).__name__}: {exc}")

        blockers.extend(self._legacy_projection_blockers())
        return RoutineUpgradeReport(
            RoutineUpgradeDisposition.READY if not blockers else RoutineUpgradeDisposition.BLOCKED,
            authority,
            tuple(blockers),
            previous,
            prior,
            target,
            activation_template_path=str(self.activation_template),
        )

    def _checkpoint(
        self,
        *,
        baseline: PortableActivationBaseline,
        previous_head: str,
        target_sha: str,
    ) -> Path:
        path = (
            self.root
            / ".skyforge-platform-v2"
            / "operator-evidence"
            / f"pre-routine-upgrade-{time.strftime('%Y%m%dT%H%M%SZ', time.gmtime())}-{time.time_ns()}.json"
        )
        legacy = self.operator.services.observe(LEGACY_SERVICE)
        v2 = self.operator.services.observe(V2_SERVICE)
        value = {
            "schema_version": 1,
            "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "previous_head_sha": previous_head,
            "prior_accepted_main_sha": baseline.accepted_main_sha,
            "target_sha": target_sha,
            "prior_activation_evidence_digest": baseline.source_digest,
            "legacy_state_digest": canonical_digest(self._legacy_state()),
            "legacy_service": legacy.as_dict(),
            "v2_service": v2.as_dict(),
        }
        _atomic_write_json(path, value)
        return path

    def _record(
        self,
        events: list[RoutineUpgradeEvent],
        kind: str,
        detail: str = "",
    ) -> None:
        events.append(
            RoutineUpgradeEvent(len(events) + 1, kind, self._authority(), detail)
        )

    def _restart_legacy_at_target(self) -> None:
        self.operator.services.stop(LEGACY_SERVICE)
        self.operator._wait_service(LEGACY_SERVICE, active=False)
        if self.operator.services.observe(V2_SERVICE).active:
            raise RuntimeError("Platform-v2 became active during legacy target restart")
        self.operator.services.start(LEGACY_SERVICE)
        self.operator._wait_service(LEGACY_SERVICE, active=True)
        self.operator._verify_legacy_health()

    def _recover_legacy(self, previous_head: str) -> WriterAuthority:
        report = self.operator.rollback(execute=True)
        if report.disposition is OperatorDisposition.ROLLBACK_COMPLETE:
            return WriterAuthority.LEGACY
        try:
            legacy = self.operator.services.observe(LEGACY_SERVICE)
            v2 = self.operator.services.observe(V2_SERVICE)
            if v2.active:
                self.operator.services.stop(V2_SERVICE)
                self.operator._wait_service(V2_SERVICE, active=False)
                self.operator.services.disable(V2_SERVICE)
            if legacy.active:
                self.operator.services.stop(LEGACY_SERVICE)
                self.operator._wait_service(LEGACY_SERVICE, active=False)
            if self._tracked_clean() and _SHA40_RE.fullmatch(previous_head):
                self._git("switch", "--detach", previous_head)
            self.operator.services.enable(LEGACY_SERVICE)
            self.operator.services.start(LEGACY_SERVICE)
            self.operator._wait_service(LEGACY_SERVICE, active=True)
            self.operator._verify_legacy_health()
            return WriterAuthority.LEGACY
        except Exception:
            try:
                if self.operator.services.observe(LEGACY_SERVICE).active:
                    self.operator.services.stop(LEGACY_SERVICE)
                    self.operator._wait_service(LEGACY_SERVICE, active=False)
                self.operator.services.disable(LEGACY_SERVICE)
            except Exception:
                pass
            return WriterAuthority.NONE

    def upgrade(self, target_sha: str, *, execute: bool = False) -> RoutineUpgradeReport:
        plan = self.preflight(target_sha)
        if plan.disposition is not RoutineUpgradeDisposition.READY or not execute:
            return plan
        if self.operator.geteuid() != 0:
            return RoutineUpgradeReport(
                RoutineUpgradeDisposition.BLOCKED,
                plan.authority,
                ("live routine upgrade requires a root operator",),
                plan.previous_head_sha,
                plan.prior_accepted_main_sha,
                plan.target_sha,
                activation_template_path=str(self.activation_template),
            )

        baseline = load_portable_activation_baseline(self.activation_evidence)
        checkpoint = self._checkpoint(
            baseline=baseline,
            previous_head=plan.previous_head_sha,
            target_sha=plan.target_sha,
        )
        events: list[RoutineUpgradeEvent] = []
        self._record(events, "UPGRADE_CHECKPOINT", str(checkpoint))

        try:
            rollback = self.operator.rollback(execute=True)
            if rollback.disposition is not OperatorDisposition.ROLLBACK_COMPLETE:
                return RoutineUpgradeReport(
                    RoutineUpgradeDisposition.BLOCKED,
                    rollback.authority,
                    tuple(f"rollback: {item}" for item in rollback.blockers),
                    plan.previous_head_sha,
                    baseline.accepted_main_sha,
                    plan.target_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_template),
                )
            self._record(events, "ROLLBACK_TO_LEGACY_COMPLETE")

            blockers = self._legacy_projection_blockers()
            if blockers:
                return RoutineUpgradeReport(
                    RoutineUpgradeDisposition.FAILED_SAFE_LEGACY,
                    WriterAuthority.LEGACY,
                    tuple(blockers),
                    plan.previous_head_sha,
                    baseline.accepted_main_sha,
                    plan.target_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_template),
                )

            self._git("switch", "--detach", plan.target_sha)
            self._record(events, "CHECKOUT_UPDATED", plan.target_sha)

            self._restart_legacy_at_target()
            self._record(events, "LEGACY_RESTARTED_AT_TARGET", plan.target_sha)

            blockers = self._legacy_projection_blockers()
            if blockers:
                return RoutineUpgradeReport(
                    RoutineUpgradeDisposition.FAILED_SAFE_LEGACY,
                    WriterAuthority.LEGACY,
                    tuple(blockers),
                    plan.previous_head_sha,
                    baseline.accepted_main_sha,
                    plan.target_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_template),
                )

            template = build_routine_upgrade_template(
                baseline=baseline,
                target_sha=plan.target_sha,
                legacy_state=self._legacy_state(),
            )
            _atomic_write_json(self.activation_template, template)
            self._record(
                events,
                "ACTIVATION_TEMPLATE_REFRESHED",
                str(self.activation_template),
            )

            cutover = self.operator.cutover(execute=True)
            if cutover.disposition is not OperatorDisposition.CUTOVER_COMPLETE:
                raise RuntimeError(
                    "re-cutover failed: " + "; ".join(cutover.blockers or (cutover.disposition.value,))
                )
            self._record(events, "RECUTOVER_COMPLETE", plan.target_sha)

            legacy = self.operator.services.observe(LEGACY_SERVICE)
            v2 = self.operator.services.observe(V2_SERVICE)
            if self._head() != plan.target_sha:
                raise RuntimeError("final checkout HEAD differs from routine upgrade target")
            if legacy.active or legacy.enabled or not v2.active or not v2.enabled:
                raise RuntimeError("final service state does not prove exclusive V2 authority")

            return RoutineUpgradeReport(
                RoutineUpgradeDisposition.COMPLETE,
                WriterAuthority.V2,
                (),
                plan.previous_head_sha,
                baseline.accepted_main_sha,
                plan.target_sha,
                tuple(events),
                str(checkpoint),
                str(self.activation_template),
            )
        except Exception as exc:
            authority = self._recover_legacy(plan.previous_head_sha)
            disposition = (
                RoutineUpgradeDisposition.FAILED_SAFE_LEGACY
                if authority is WriterAuthority.LEGACY
                else RoutineUpgradeDisposition.FAILED_SAFE_NONE
            )
            self._record(events, "UPGRADE_FAILED", f"{type(exc).__name__}: {exc}")
            return RoutineUpgradeReport(
                disposition,
                authority,
                (f"{type(exc).__name__}: {exc}",),
                plan.previous_head_sha,
                baseline.accepted_main_sha,
                plan.target_sha,
                tuple(events),
                str(checkpoint),
                str(self.activation_template),
            )
