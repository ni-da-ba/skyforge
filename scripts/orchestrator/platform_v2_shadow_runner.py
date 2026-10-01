"""Read-only executable boundary for Platform v2 shadow evaluation.

Input is a prepared observation snapshot. This runner does not collect live truth and has
no repository/provider/workspace mutation capability.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Mapping

from v2.core import ControllerState, ManagedPRObservation
from v2.domain import CIState, PRClass
from v2.effects import (
    EffectKind,
    EffectStatus,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
)
from v2.fairness import PendingTimerObservation
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision
from v2.shadow import OfflineShadowInput, evaluate_offline_shadow


SNAPSHOT_SCHEMA_VERSION = 1


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


def _optional_mapping(value: Any, label: str) -> Mapping[str, Any] | None:
    if value is None:
        return None
    return _mapping(value, label)


def _parse_managed(raw: Mapping[str, Any]) -> tuple[ControllerState, ManagedPRObservation]:
    state_raw = _mapping(raw.get("state"), "managed.state")
    observation_raw = _mapping(raw.get("observation"), "managed.observation")
    try:
        ci_state = CIState(str(observation_raw.get("ci_state")))
        pr_class = PRClass(str(observation_raw.get("pr_class", PRClass.DELIVERY.value)))
    except ValueError as exc:
        raise ValueError("managed observation contains unknown enum value") from exc

    state = ControllerState.from_legacy_mapping(state_raw)
    observation = ManagedPRObservation(
        lane=str(observation_raw.get("lane") or ""),
        pr_number=observation_raw.get("pr_number"),
        active_pr=observation_raw.get("active_pr"),
        base_branch=str(observation_raw.get("base_branch") or ""),
        head_branch=str(observation_raw.get("head_branch") or ""),
        current_head_sha=str(observation_raw.get("current_head_sha") or ""),
        evidence_sha=str(observation_raw.get("evidence_sha") or ""),
        reviewed_sha=str(observation_raw.get("reviewed_sha") or ""),
        task_spec_hash=str(observation_raw.get("task_spec_hash") or ""),
        accepted_task_spec_hash=str(observation_raw.get("accepted_task_spec_hash") or ""),
        ci_state=ci_state,
        pr_class=pr_class,
        human_gate_pending=observation_raw.get("human_gate_pending", False),
        review_required=observation_raw.get("review_required", False),
    )
    return state, observation


def _parse_quota(raw: Mapping[str, Any]) -> tuple[ProviderQuotaDecision | None, LocalBudgetObservation]:
    provider_raw = raw.get("provider")
    provider = ProviderQuotaDecision.from_mapping(provider_raw)
    if provider_raw is not None and provider is None:
        raise ValueError("quota.provider is malformed")
    local_raw = _mapping(raw.get("local"), "quota.local")
    local = LocalBudgetObservation(
        calls_used=local_raw.get("calls_used"),
        daily_limit=local_raw.get("daily_limit"),
    )
    return provider, local


def _parse_effect(raw: Mapping[str, Any]) -> tuple[RemoteEffectRecord, RemoteEffectObservation]:
    identity_raw = _mapping(raw.get("identity"), "effect.identity")
    try:
        kind = EffectKind(str(identity_raw.get("kind")))
        status = EffectStatus(str(raw.get("status")))
        presence = RemoteEffectPresence(str(_mapping(raw.get("observation"), "effect.observation").get("presence")))
    except ValueError as exc:
        raise ValueError("effect contains unknown enum value") from exc

    identity = RemoteEffectIdentity.create(
        attempt_id=str(identity_raw.get("attempt_id") or ""),
        kind=kind,
        subject=str(identity_raw.get("subject") or ""),
    )
    record = RemoteEffectRecord(
        identity=identity,
        status=status,
        remote_identity=str(raw.get("remote_identity") or ""),
    )
    observation_raw = _mapping(raw.get("observation"), "effect.observation")
    observation = RemoteEffectObservation(
        presence=presence,
        remote_identity=str(observation_raw.get("remote_identity") or ""),
    )
    return record, observation


def _parse_timer(raw: Mapping[str, Any]) -> PendingTimerObservation:
    return PendingTimerObservation(
        now_epoch=raw.get("now_epoch"),
        requested_delay_seconds=raw.get("requested_delay_seconds"),
        existing_due_epoch=raw.get("existing_due_epoch"),
        existing_timer_alive=raw.get("existing_timer_alive", False),
    )


def shadow_input_from_mapping(value: Any) -> OfflineShadowInput:
    root = _mapping(value, "shadow snapshot")
    if root.get("schema_version") != SNAPSHOT_SCHEMA_VERSION:
        raise ValueError(f"unsupported shadow snapshot schema_version: {root.get('schema_version')!r}")

    allowed = {"schema_version", "managed", "quota", "effect", "timer"}
    unknown = sorted(set(root) - allowed)
    if unknown:
        raise ValueError(f"unsupported shadow snapshot fields: {unknown}")

    managed_state = None
    managed_observation = None
    managed_raw = _optional_mapping(root.get("managed"), "managed")
    if managed_raw is not None:
        managed_state, managed_observation = _parse_managed(managed_raw)

    quota_provider = None
    quota_local = None
    quota_raw = _optional_mapping(root.get("quota"), "quota")
    if quota_raw is not None:
        quota_provider, quota_local = _parse_quota(quota_raw)

    effect_record = None
    effect_observation = None
    effect_raw = _optional_mapping(root.get("effect"), "effect")
    if effect_raw is not None:
        effect_record, effect_observation = _parse_effect(effect_raw)

    timer_observation = None
    timer_raw = _optional_mapping(root.get("timer"), "timer")
    if timer_raw is not None:
        timer_observation = _parse_timer(timer_raw)

    return OfflineShadowInput(
        managed_state=managed_state,
        managed_observation=managed_observation,
        quota_provider=quota_provider,
        quota_local=quota_local,
        effect_record=effect_record,
        effect_observation=effect_observation,
        timer_observation=timer_observation,
    )


def render_report(snapshot: Any) -> str:
    report = evaluate_offline_shadow(shadow_input_from_mapping(snapshot))
    payload = {"schema_version": 1, "report": report.as_dict(), "digest": report.digest}
    return json.dumps(payload, sort_keys=True, separators=(",", ":"))


def _read_snapshot(path: str | None) -> Any:
    if path is None:
        return json.load(sys.stdin)
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate prepared Platform v2 shadow observations read-only.")
    parser.add_argument("--input", help="Prepared JSON snapshot path; omit to read stdin.")
    args = parser.parse_args(argv)
    try:
        snapshot = _read_snapshot(args.input)
        output = render_report(snapshot)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"shadow input rejected: {exc}", file=sys.stderr)
        return 2
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
