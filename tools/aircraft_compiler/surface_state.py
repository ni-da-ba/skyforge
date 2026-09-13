from __future__ import annotations

import hashlib
import json
from typing import Any


class SurfaceStateError(ValueError):
    pass


def resolve_surface_states(
    target_preflight: dict[str, Any],
    propulsion: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if target_preflight.get("schemaVersion") != "aircraft-minecraft-realization-preflight-ir-0.4":
        raise SurfaceStateError("v0.6 requires aircraft-minecraft-realization-preflight-ir-0.4")
    if propulsion.get("schemaVersion") != "aircraft-propulsion-realization-ir-0.5":
        raise SurfaceStateError("v0.6 requires aircraft-propulsion-realization-ir-0.5")
    if profile.get("schemaVersion") != "aircraft-surface-state-profile-0.6":
        raise SurfaceStateError("v0.6 requires aircraft-surface-state-profile-0.6")

    provider_id = str(profile["providerId"])
    axis_property = str(profile.get("axisProperty", "axis"))
    role_axes = {str(k): str(v) for k, v in profile.get("roleAxes", {}).items()}
    legal_axes = set(str(v) for v in profile.get("legalAxes", ["x", "y", "z"]))
    if not role_axes or not set(role_axes.values()) <= legal_axes:
        raise SurfaceStateError("roleAxes must map to declared legalAxes")

    resolved: list[dict[str, Any]] = []
    conflicts: list[dict[str, Any]] = []
    provider_placements = [p for p in target_preflight.get("placements", []) if p.get("providerId") == provider_id]
    for placement in provider_placements:
        roles = list(placement.get("roles", []))
        demanded = sorted({role_axes[role] for role in roles if role in role_axes})
        base = {
            "lattice": list(placement["lattice"]),
            "roles": roles,
            "resourceId": placement["resourceId"],
            "providerId": provider_id,
            "demandedAxes": demanded,
        }
        if len(demanded) == 1:
            resolved.append({**base, "blockState": {axis_property: demanded[0]}, "status": "resolved"})
        elif len(demanded) > 1:
            conflicts.append({
                **base,
                "status": "conflict",
                "reason": "one_lattice_coordinate_cannot_realize_multiple_symmetric_sail_axes",
            })
        else:
            conflicts.append({
                **base,
                "status": "conflict",
                "reason": "aerodynamic_provider_has_no_declared_surface_role_axis",
            })

    resolved.sort(key=lambda p: tuple(p["lattice"]))
    conflicts.sort(key=lambda p: tuple(p["lattice"]))
    input_unresolved = int(target_preflight.get("metrics", {}).get("unresolvedStateOrResourceCount", 0))
    resolved_count = len(resolved)
    remaining = max(input_unresolved - resolved_count, 0)
    complete = len(conflicts) == 0 and remaining == 0

    inherited_static_blockers = [
        b for b in propulsion.get("readiness", {}).get("blockers", [])
        if b != "propulsion_runtime_obligations_unverified"
    ]
    effective_static_blockers = []
    for blocker in inherited_static_blockers:
        if blocker == "unresolved_blockstate_or_resource_rules":
            if conflicts or remaining:
                effective_static_blockers.append("unresolved_surface_state_conflicts")
        else:
            effective_static_blockers.append(blocker)
    if conflicts and "unresolved_surface_state_conflicts" not in effective_static_blockers:
        effective_static_blockers.append("unresolved_surface_state_conflicts")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-surface-state-resolution-ir-0.6",
        "assetId": str(propulsion["assetId"]).replace("v0_5_propulsion", "v0_6_surface_state"),
        "compilerVersion": "aircraft-surface-state-resolver-0.6",
        "sourceTargetPreflightDigestSha256": target_preflight["digestSha256"],
        "sourcePropulsionDigestSha256": propulsion["digestSha256"],
        "profileId": profile["profileId"],
        "providerId": provider_id,
        "resolvedPlacements": resolved,
        "conflicts": conflicts,
        "metrics": {
            "providerPlacementCount": len(provider_placements),
            "stateResolvedCount": resolved_count,
            "stateConflictCount": len(conflicts),
            "inputUnresolvedStateOrResourceCount": input_unresolved,
            "remainingUnresolvedStateOrResourceCount": remaining,
        },
        "readiness": {
            "surfaceStateResolutionComplete": complete,
            "probeSchematicEmissionReady": bool(
                propulsion.get("readiness", {}).get("propulsionStaticTopologyPassed", False)
                and complete
                and not effective_static_blockers
            ),
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "staticBlockers": sorted(set(effective_static_blockers)),
            "runtimeBlockers": ["propulsion_runtime_obligations_unverified", "aircraft_runtime_obligations_unverified"],
        },
        "validation": {
            "passed": True,
            "scope": "source_backed_symmetric_sail_axis_lowering_and_conflict_detection",
            "doesNotProve": [
                "live Sable force sign for the emitted axis state",
                "contraption capture or persistence",
                "runtime mass or center of mass",
                "control binding",
                "stable flight",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
