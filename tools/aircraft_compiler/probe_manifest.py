from __future__ import annotations

import hashlib
import json
from typing import Any


class ProbeManifestError(ValueError):
    pass


def _coord(p: dict[str, Any]) -> tuple[int, int, int]:
    values = p["lattice"]
    if len(values) != 3:
        raise ProbeManifestError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _placement(kind: str, lattice: list[int], resource_id: str, block_state: dict[str, Any], source: str) -> dict[str, Any]:
    if ":" not in resource_id:
        raise ProbeManifestError(f"resourceId must be namespaced: {resource_id!r}")
    return {"kind": kind, "lattice": [int(v) for v in lattice], "resourceId": resource_id, "blockState": dict(block_state), "sourceLayer": source}


def emit_probe_manifest(target: dict[str, Any], propulsion: dict[str, Any], tail: dict[str, Any], pilot: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if target.get("schemaVersion") != "aircraft-minecraft-realization-preflight-ir-0.4":
        raise ProbeManifestError("v0.9 requires target preflight v0.4")
    if propulsion.get("schemaVersion") != "aircraft-propulsion-realization-ir-0.5":
        raise ProbeManifestError("v0.9 requires propulsion realization v0.5")
    if tail.get("schemaVersion") != "aircraft-tail-lowering-ir-0.7":
        raise ProbeManifestError("v0.9 requires tail lowering v0.7")
    if pilot.get("schemaVersion") != "aircraft-pilot-station-realization-ir-0.8":
        raise ProbeManifestError("v0.9 requires pilot station v0.8")
    if profile.get("schemaVersion") != "aircraft-probe-manifest-profile-0.9":
        raise ProbeManifestError("v0.9 requires aircraft-probe-manifest-profile-0.9")
    if not pilot.get("readiness", {}).get("probeSchematicEmissionReady", False):
        raise ProbeManifestError("pilot station has not cleared static probe emission")

    aero_provider = str(profile["airframeAerodynamicProviderId"])
    placements: list[dict[str, Any]] = []
    for p in target.get("placements", []):
        if p.get("providerId") == aero_provider:
            continue
        placements.append(_placement("airframe_structure", list(p["lattice"]), str(p["resourceId"]), {}, "target_preflight_v0.4"))
    for p in tail.get("resolvedAerodynamicPlacements", []):
        placements.append(_placement("airframe_aerodynamic_surface", list(p["lattice"]), str(p["resourceId"]), dict(p.get("blockState", {})), "tail_lowering_v0.7"))
    placements.append(_placement("propeller_bearing", list(propulsion["bearing"]["lattice"]), str(propulsion["bearing"]["resourceId"]), dict(propulsion["bearing"].get("blockState", {})), "propulsion_v0.5"))
    placements.append(_placement("propeller_hub", list(propulsion["hub"]["lattice"]), str(propulsion["hub"]["resourceId"]), dict(propulsion["hub"].get("blockState", {})), "propulsion_v0.5"))
    for p in propulsion.get("sails", []):
        placements.append(_placement("propeller_sail", list(p["lattice"]), str(p["resourceId"]), dict(p.get("blockState", {})), "propulsion_v0.5"))
    pp = pilot["placement"]
    placements.append(_placement("pilot_occupancy_station", list(pp["lattice"]), str(pp["resourceId"]), dict(pp.get("blockState", {})), "pilot_station_v0.8"))

    placements.sort(key=lambda p: (tuple(p["lattice"]), p["kind"], p["resourceId"]))
    coords = [_coord(p) for p in placements]
    counts: dict[tuple[int, int, int], int] = {}
    for c in coords:
        counts[c] = counts.get(c, 0) + 1
    duplicates = sorted(c for c, n in counts.items() if n > 1)
    xs, ys, zs = [c[0] for c in coords], [c[1] for c in coords], [c[2] for c in coords]
    bounds = {"min": [min(xs), min(ys), min(zs)], "max": [max(xs), max(ys), max(zs)], "sizeBlocks": [max(xs)-min(xs)+1, max(ys)-min(ys)+1, max(zs)-min(zs)+1]}
    resource_ids = sorted({p["resourceId"] for p in placements})
    kind_counts: dict[str, int] = {}
    for p in placements:
        kind_counts[p["kind"]] = kind_counts.get(p["kind"], 0) + 1
    checks = {
        "allPlacementsCoordinateUnique": not duplicates,
        "allResourceIdsNamespaced": all(":" in p["resourceId"] for p in placements),
        "allBlockStatesExplicitObjects": all(isinstance(p["blockState"], dict) for p in placements),
        "tailSurfaceCountPreserved": kind_counts.get("airframe_aerodynamic_surface", 0) == int(tail["metrics"]["v07AerodynamicPlacementCount"]),
        "propulsionPlacementCountPreserved": kind_counts.get("propeller_bearing", 0) + kind_counts.get("propeller_hub", 0) + kind_counts.get("propeller_sail", 0) == int(propulsion["metrics"]["generatedPlacementCount"]),
        "pilotPlacementCountIsOne": kind_counts.get("pilot_occupancy_station", 0) == 1,
    }
    static_ready = all(checks.values())
    out: dict[str, Any] = {
        "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
        "assetId": str(pilot["assetId"]).replace("v0_8_pilot_station", "v0_9_probe_manifest"),
        "compilerVersion": "aircraft-probe-manifest-emitter-0.9",
        "sourceTargetPreflightDigestSha256": target["digestSha256"],
        "sourcePropulsionDigestSha256": propulsion["digestSha256"],
        "sourceTailLoweringDigestSha256": tail["digestSha256"],
        "sourcePilotStationDigestSha256": pilot["digestSha256"],
        "profileId": profile["profileId"],
        "originContract": profile["originContract"],
        "placements": placements,
        "bounds": bounds,
        "resourceIds": resource_ids,
        "kindCounts": kind_counts,
        "duplicateCoordinates": [list(c) for c in duplicates],
        "validationChecks": checks,
        "readiness": {
            "probePlacementManifestReady": static_ready,
            "probePlacementCommandsReady": static_ready,
            "physicsAssemblyProbeReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "staticBlockers": [] if static_ready else ["probe_manifest_static_validation_failed"],
            "mechanicalBlockers": ["physics_assembler_placement_unresolved", "airframe_adhesion_graph_unresolved", "control_surface_child_body_topology_unresolved"],
            "runtimeBlockers": sorted(set(pilot.get("readiness", {}).get("runtimeBlockers", []))),
        },
        "validation": {"passed": static_ready, "scope": "coordinate_unique_exact_resource_and_blockstate_probe_placement_manifest", "doesNotProve": ["Physics Assembler capture of the intended airframe", "Super Glue or other adhesion completeness", "nested propeller capture", "moving control-surface topology", "runtime force sign or magnitude", "pilot occupancy on a Sable body", "control binding or authority", "flight qualification"]},
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
