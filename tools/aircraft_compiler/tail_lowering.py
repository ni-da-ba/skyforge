from __future__ import annotations

import hashlib
import json
from collections import deque
from typing import Any


class TailLoweringError(ValueError):
    pass


def _coord(p: dict[str, Any]) -> tuple[int, int, int]:
    return tuple(int(v) for v in p["lattice"])


def _connected(coords: set[tuple[int, int, int]]) -> bool:
    if not coords:
        return False
    unseen = set(coords)
    q = deque([unseen.pop()])
    while q:
        x, y, z = q.popleft()
        for n in ((x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1)):
            if n in unseen:
                unseen.remove(n)
                q.append(n)
    return not unseen


def lower_tail_junction(target: dict[str, Any], surface: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if target.get("schemaVersion") != "aircraft-minecraft-realization-preflight-ir-0.4":
        raise TailLoweringError("v0.7 requires target preflight v0.4")
    if surface.get("schemaVersion") != "aircraft-surface-state-resolution-ir-0.6":
        raise TailLoweringError("v0.7 requires surface state IR v0.6")
    if profile.get("schemaVersion") != "aircraft-tail-lowering-profile-0.7":
        raise TailLoweringError("v0.7 requires aircraft-tail-lowering-profile-0.7")

    vertical_role = str(profile["translatedRole"])
    horizontal_role = str(profile["junctionRetainedRole"])
    shift = tuple(int(v) for v in profile["translationBlocks"])
    if shift != (0, 1, 0):
        raise TailLoweringError("bounded v0.7 proof requires one-block upward fin translation")
    provider_id = str(profile["providerId"])
    state_property = str(profile["stateProperty"])
    vertical_state = str(profile["translatedState"])
    horizontal_state = str(profile["junctionRetainedState"])

    aero = [p for p in target.get("placements", []) if p.get("providerId") == provider_id]
    vertical_sources = [p for p in aero if vertical_role in p.get("roles", [])]
    if not vertical_sources:
        raise TailLoweringError("no vertical tail cells found")
    conflict_coords = {_coord(p) for p in surface.get("conflicts", [])}
    expected_conflicts = {_coord(p) for p in vertical_sources if horizontal_role in p.get("roles", [])}
    if conflict_coords != expected_conflicts:
        raise TailLoweringError("surface conflicts do not exactly match horizontal/vertical tail intersections")

    vertical_only_coords = {_coord(p) for p in vertical_sources if horizontal_role not in p.get("roles", [])}
    immutable_coords = {_coord(p) for p in target.get("placements", [])} - vertical_only_coords
    shifted_coords = {tuple(c[i] + shift[i] for i in range(3)) for c in map(_coord, vertical_sources)}
    collisions = sorted(shifted_coords & (immutable_coords - conflict_coords))

    original_vertical = sorted(_coord(p) for p in vertical_sources)
    shifted_vertical = sorted(shifted_coords)
    x_moment_before = sum(c[0] for c in original_vertical)
    x_moment_after = sum(c[0] for c in shifted_vertical)
    y_moment_before = sum(c[1] for c in original_vertical)
    y_moment_after = sum(c[1] for c in shifted_vertical)
    shape_before = sorted((x-original_vertical[0][0], y-original_vertical[0][1], z-original_vertical[0][2]) for x,y,z in original_vertical)
    shape_after = sorted((x-shifted_vertical[0][0], y-shifted_vertical[0][1], z-shifted_vertical[0][2]) for x,y,z in shifted_vertical)

    horizontal_coords = {_coord(p) for p in aero if horizontal_role in p.get("roles", [])}
    root_y = min(c[1] for c in shifted_vertical)
    root = {c for c in shifted_vertical if c[1] == root_y}
    root_face_contacts = sorted(c for c in root if (c[0], c[1]-1, c[2]) in horizontal_coords)

    resolved = []
    for p in aero:
        roles = list(p.get("roles", []))
        c = _coord(p)
        if vertical_role in roles and horizontal_role not in roles:
            continue
        state = horizontal_state if horizontal_role in roles else next((r["blockState"][state_property] for r in surface.get("resolvedPlacements", []) if _coord(r) == c), None)
        if vertical_role in roles and horizontal_role in roles:
            roles = [r for r in roles if r != vertical_role]
        if state is None:
            raise TailLoweringError(f"missing resolved state at {c}")
        resolved.append({"lattice": list(c), "roles": roles, "resourceId": p["resourceId"], "providerId": provider_id, "blockState": {state_property: state}})
    for c in shifted_vertical:
        resolved.append({"lattice": list(c), "roles": [vertical_role], "resourceId": profile["resourceId"], "providerId": provider_id, "blockState": {state_property: vertical_state}})
    resolved.sort(key=lambda p: tuple(p["lattice"]))

    unique = len({_coord(p) for p in resolved}) == len(resolved)
    topology_checks = {
        "allSurfacePlacementsCoordinateUnique": unique,
        "noImmutablePlacementCollision": not collisions,
        "verticalCellCountPreserved": len(original_vertical) == len(shifted_vertical),
        "verticalLongitudinalFirstMomentPreserved": x_moment_before == x_moment_after,
        "verticalRelativeShapePreserved": shape_before == shape_after,
        "verticalTailInternallyConnected": _connected(set(shifted_vertical)),
        "verticalRootFaceAttachedToHorizontalTail": len(root_face_contacts) > 0,
        "allV06ConflictsResolved": len(conflict_coords) > 0,
    }
    passed = all(topology_checks.values())
    static_blockers = [b for b in surface.get("readiness", {}).get("staticBlockers", []) if b != "unresolved_surface_state_conflicts"]
    if not passed:
        static_blockers.append("tail_junction_lowering_failed")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-tail-lowering-ir-0.7",
        "assetId": str(surface["assetId"]).replace("v0_6_surface_state", "v0_7_tail_lowering"),
        "compilerVersion": "aircraft-tail-junction-lowerer-0.7",
        "sourceTargetPreflightDigestSha256": target["digestSha256"],
        "sourceSurfaceStateDigestSha256": surface["digestSha256"],
        "profileId": profile["profileId"],
        "translationBlocks": list(shift),
        "resolvedAerodynamicPlacements": resolved,
        "translatedVerticalTail": [{"from": list(a), "to": list(b)} for a,b in zip(original_vertical, shifted_vertical)],
        "rootFaceContacts": [list(c) for c in root_face_contacts],
        "collisions": [list(c) for c in collisions],
        "topologyChecks": topology_checks,
        "metrics": {
            "v06AerodynamicPlacementCount": len(aero),
            "v07AerodynamicPlacementCount": len(resolved),
            "verticalTailCellCount": len(shifted_vertical),
            "horizontalTailCellCount": len(horizontal_coords),
            "resolvedFormerConflictCount": len(conflict_coords),
            "verticalXFirstMomentBeforeBlocks": x_moment_before,
            "verticalXFirstMomentAfterBlocks": x_moment_after,
            "verticalYFirstMomentBeforeBlocks": y_moment_before,
            "verticalYFirstMomentAfterBlocks": y_moment_after,
            "verticalCentroidShiftBlocks": [0.0, 1.0, 0.0],
        },
        "readiness": {
            "tailJunctionLoweringPassed": passed,
            "surfaceStateResolutionComplete": passed,
            "probeSchematicEmissionReady": bool(passed and not static_blockers),
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "staticBlockers": sorted(set(static_blockers)),
            "runtimeBlockers": list(surface.get("readiness", {}).get("runtimeBlockers", [])),
        },
        "validation": {
            "passed": passed,
            "scope": "discrete_tail_junction_translation_with_cell_count_shape_and_longitudinal_moment_preservation",
            "doesNotProve": ["Create/Sable attachment semantics at the fin root", "runtime side-force sign or magnitude", "dynamic stability", "control authority", "flight qualification"],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
