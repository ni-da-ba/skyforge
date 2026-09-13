from __future__ import annotations

import hashlib
import json
from collections import deque
from typing import Any


class YawControlCorrectionError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise YawControlCorrectionError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _bounds(a: tuple[int, int, int], b: tuple[int, int, int]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    return tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3))


def _contains(lo: tuple[int, int, int], hi: tuple[int, int, int], p: tuple[int, int, int]) -> bool:
    return all(lo[i] <= p[i] <= hi[i] for i in range(3))


def _neighbors(c: tuple[int, int, int]):
    x, y, z = c
    return ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))


def _state_matches(actual: dict[str, Any], expected: dict[str, Any]) -> bool:
    return all(str(actual.get(k)) == str(v) for k, v in expected.items())


def _domain_record(name: str, start: tuple[int, int, int], end: tuple[int, int, int], classification: str) -> dict[str, Any]:
    lo, hi = _bounds(start, end)
    return {
        "name": name,
        "from": list(start),
        "to": list(end),
        "classification": classification,
        "selectionCellBounds": {"min": list(lo), "max": list(hi)},
        "selectionSizeBlocks": [hi[i] - lo[i] + 1 for i in range(3)],
    }


def lower_yaw_control_correction(
    manifest: dict[str, Any],
    glue: dict[str, Any],
    powertrain: dict[str, Any],
    prior_yaw: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise YawControlCorrectionError("v0.13.1 requires probe manifest v0.9")
    if glue.get("schemaVersion") != "aircraft-glue-encoding-ir-0.11":
        raise YawControlCorrectionError("v0.13.1 requires glue encoding IR v0.11")
    if powertrain.get("schemaVersion") != "aircraft-powertrain-ir-0.12":
        raise YawControlCorrectionError("v0.13.1 requires powertrain IR v0.12")
    if prior_yaw.get("schemaVersion") != "aircraft-yaw-control-ir-0.13":
        raise YawControlCorrectionError("v0.13.1 requires the failed v0.13 yaw-control IR as lineage evidence")
    if profile.get("schemaVersion") != "aircraft-yaw-control-correction-profile-0.13.1":
        raise YawControlCorrectionError("v0.13.1 requires aircraft-yaw-control-correction-profile-0.13.1")
    if not prior_yaw.get("validation", {}).get("passed", False):
        raise YawControlCorrectionError("v0.13.1 requires a statically valid v0.13 predecessor")

    effective: dict[tuple[int, int, int], dict[str, Any]] = {}
    propeller_child: set[tuple[int, int, int]] = set()
    for raw in manifest.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        if c in effective:
            raise YawControlCorrectionError(f"duplicate v0.9 coordinate: {c!r}")
        effective[c] = p
        if p.get("kind") in {"propeller_hub", "propeller_sail"}:
            propeller_child.add(c)

    for raw in powertrain.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        mode = str(p.get("mode", ""))
        if mode == "replace":
            if c not in effective:
                raise YawControlCorrectionError(f"v0.12 replacement source missing: {c!r}")
            effective[c] = dict(p, kind="powertrain_main_body")
        elif mode == "add":
            if c in effective:
                raise YawControlCorrectionError(f"v0.12 addition collision while reconstructing: {c!r}")
            effective[c] = dict(p, kind="powertrain_main_body")
        else:
            raise YawControlCorrectionError(f"unsupported v0.12 placement mode: {mode!r}")

    main_coords = set(effective) - propeller_child
    prior_main_with_assembler = int(powertrain.get("metrics", {}).get("resultingMovingMainBodyPlacementCount", 0))
    if prior_main_with_assembler != len(main_coords) + 1:
        raise YawControlCorrectionError(
            f"v0.12 main metric mismatch: metric={prior_main_with_assembler} reconstructed={len(main_coords)+1}"
        )
    if int(powertrain.get("metrics", {}).get("nestedPropellerChildPlacementCount", 0)) != len(propeller_child):
        raise YawControlCorrectionError("v0.12 propeller-child metric mismatch")

    expected_prior_digest = str(profile.get("supersededYawDigestSha256", ""))
    if expected_prior_digest and str(prior_yaw.get("digestSha256")) != expected_prior_digest:
        raise YawControlCorrectionError(
            f"v0.13 predecessor digest mismatch: actual={prior_yaw.get('digestSha256')} expected={expected_prior_digest}"
        )

    declared = list(profile.get("placements", []))
    if not declared:
        raise YawControlCorrectionError("v0.13.1 profile requires placements")
    roles: dict[str, dict[str, Any]] = {}
    emitted: set[tuple[int, int, int]] = set()
    additions_main: list[dict[str, Any]] = []
    removals_main: list[dict[str, Any]] = []
    additions_child: list[dict[str, Any]] = []
    control_child: set[tuple[int, int, int]] = set()

    def require_source(p: dict[str, Any], c: tuple[int, int, int]) -> dict[str, Any]:
        existing = effective.get(c)
        if existing is None:
            raise YawControlCorrectionError(f"required source coordinate is empty: {c!r}")
        expected = dict(p.get("replaces", {}))
        if str(existing.get("resourceId")) != str(expected.get("resourceId")):
            raise YawControlCorrectionError(
                f"source resource mismatch at {c!r}: actual={existing.get('resourceId')} expected={expected.get('resourceId')}"
            )
        if expected.get("kind") is not None and str(existing.get("kind")) != str(expected.get("kind")):
            raise YawControlCorrectionError(
                f"source kind mismatch at {c!r}: actual={existing.get('kind')} expected={expected.get('kind')}"
            )
        if not _state_matches(dict(existing.get("blockState", {})), dict(expected.get("blockState", {}))):
            raise YawControlCorrectionError(f"source blockstate mismatch at {c!r}")
        return existing

    for raw in declared:
        p = dict(raw)
        role = str(p.get("role", "")).strip()
        if not role or role in roles:
            raise YawControlCorrectionError(f"placement requires unique non-empty role: {role!r}")
        roles[role] = p
        c = _coord(p["lattice"])
        if c in emitted:
            raise YawControlCorrectionError(f"duplicate v0.13.1 coordinate: {c!r}")
        emitted.add(c)
        if c in propeller_child:
            raise YawControlCorrectionError(f"yaw correction crosses propeller child: {c!r}")
        mode = str(p.get("mode", ""))
        if mode == "add_main":
            if c in effective:
                raise YawControlCorrectionError(f"add_main collides with v0.12 placement: {c!r}")
            effective[c] = p
            main_coords.add(c)
            additions_main.append(p)
        elif mode == "remove_main":
            require_source(p, c)
            if c not in main_coords:
                raise YawControlCorrectionError(f"remove_main source is not parent main: {c!r}")
            main_coords.remove(c)
            effective.pop(c)
            removals_main.append(p)
        elif mode == "add_control_child":
            if c in effective:
                raise YawControlCorrectionError(f"add_control_child collides with v0.12 placement: {c!r}")
            effective[c] = p
            control_child.add(c)
            additions_child.append(p)
        else:
            raise YawControlCorrectionError(f"unsupported v0.13.1 placement mode: {mode!r}")

    required_roles = {"yaw_swivel_bearing", "yaw_air_gap", "rudder_seed", "rudder_1", "rudder_2", "rudder_3"}
    if required_roles - set(roles):
        raise YawControlCorrectionError(f"missing roles: {sorted(required_roles - set(roles))}")

    bearing = _coord(roles["yaw_swivel_bearing"]["lattice"])
    bearing_state = dict(roles["yaw_swivel_bearing"].get("blockState", {}))
    rudder_seed = _coord(roles["rudder_seed"]["lattice"])
    gap_cells = {_coord(v) for v in profile.get("airGapCoordinates", [])}
    fixed_fin = {_coord(v) for v in profile.get("fixedFinCoordinates", [])}
    if len(gap_cells) != 4 or len(fixed_fin) != 7:
        raise YawControlCorrectionError("v0.13.1 bounded topology requires four gap cells and seven fixed-fin cells")

    for c in fixed_fin:
        p = effective.get(c)
        if p is None or p.get("resourceId") != "create:white_sail" or not _state_matches(dict(p.get("blockState", {})), {"facing": "south"}):
            raise YawControlCorrectionError(f"fixed-fin source contract failed at {c!r}: {p!r}")
    if any(c in effective for c in gap_cells):
        raise YawControlCorrectionError(f"air-gap cells are not empty after correction: {sorted(c for c in gap_cells if c in effective)}")

    child_reachable: set[tuple[int, int, int]] = set()
    if rudder_seed in control_child:
        q = deque([rudder_seed])
        child_reachable.add(rudder_seed)
        while q:
            c = q.popleft()
            for n in _neighbors(c):
                if n in control_child and n not in child_reachable:
                    child_reachable.add(n)
                    q.append(n)
    all_child_symmetric_z = all(
        effective[c].get("resourceId") == "simulated:white_symmetric_sail"
        and _state_matches(dict(effective[c].get("blockState", {})), {"axis": "z"})
        for c in control_child
    )

    topology_checks = {
        "exactlyOneBearingAddedToParent": len(additions_main) == 1,
        "exactlyOneOldTrailingFinCellRemoved": len(removals_main) == 1,
        "fourNewRudderChildCells": len(additions_child) == 4 and len(control_child) == 4,
        "bearingResourceExact": roles["yaw_swivel_bearing"].get("resourceId") == "simulated:swivel_bearing",
        "bearingFacesUp": bearing_state.get("facing") == "up",
        "bearingStartsUnassembled": str(bearing_state.get("assembled")) == "false",
        "bearingStartsUnpowered": str(bearing_state.get("powered")) == "false",
        "bearingSeedsRudderExactly": rudder_seed == (bearing[0], bearing[1] + 1, bearing[2]),
        "rudderChildConnected": child_reachable == control_child,
        "rudderChildUsesSymmetricSailsWithZNormalAxis": all_child_symmetric_z,
        "fourCellAirGapIsEmpty": not any(c in effective for c in gap_cells),
        "airGapSeparatesFixedFinFromRudder": max(c[0] for c in fixed_fin) + 2 == min(c[0] for c in control_child),
        "controlChildDisjointFromParentMain": not bool(control_child & main_coords),
        "controlChildDisjointFromPropellerChild": not bool(control_child & propeller_child),
    }

    superseded_name = str(profile.get("supersededGlueDomainName", "vertical_tail"))
    retained = [d for d in glue.get("glueDomains", []) if str(d.get("name")) != superseded_name]
    if len(retained) != len(glue.get("glueDomains", [])) - 1:
        raise YawControlCorrectionError(f"expected exactly one superseded glue domain {superseded_name!r}")

    max_dimension = int(glue.get("encodingPolicy", {}).get("maxSelectionDimensionBlocks", 24))
    parent_domains: list[dict[str, Any]] = []
    for d in retained:
        b = d.get("selectionCellBounds", {})
        lo, hi = _coord(b["min"]), _coord(b["max"])
        parent_domains.append(_domain_record(str(d.get("name", "")), lo, hi, "parent_main"))
    power_glue = dict(powertrain.get("powerplantGlueDomain", {}))
    parent_domains.append(_domain_record(str(power_glue.get("name", "powerplant")), _coord(power_glue["from"]), _coord(power_glue["to"]), "parent_main"))

    fixed_raw = dict(profile["fixedFinParentGlueDomain"])
    mount_raw = dict(profile["bearingMountGlueDomain"])
    child_raw = dict(profile["controlChildGlueDomain"])
    fixed_domain = _domain_record(str(fixed_raw["name"]), _coord(fixed_raw["from"]), _coord(fixed_raw["to"]), "parent_main")
    mount_domain = _domain_record(str(mount_raw["name"]), _coord(mount_raw["from"]), _coord(mount_raw["to"]), "parent_main")
    child_domain = _domain_record(str(child_raw["name"]), _coord(child_raw["from"]), _coord(child_raw["to"]), "yaw_control_child")
    parent_domains.extend([fixed_domain, mount_domain])
    runtime_domains = parent_domains + [child_domain]

    def domain_bounds(d: dict[str, Any]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
        b = d["selectionCellBounds"]
        return _coord(b["min"]), _coord(b["max"])

    child_lo, child_hi = domain_bounds(child_domain)
    mount_lo, mount_hi = domain_bounds(mount_domain)
    fixed_lo, fixed_hi = domain_bounds(fixed_domain)
    glue_checks = {
        "sevenRuntimeGlueDomains": len(runtime_domains) == 7,
        "allRuntimeGlueDomainsWithinSelectionLimit": all(all(v <= max_dimension for v in d["selectionSizeBlocks"]) for d in runtime_domains),
        "noParentGlueDomainContainsRudderChild": all(not any(_contains(*domain_bounds(d), c) for c in control_child) for d in parent_domains),
        "noGlueDomainContainsAnyAirGapCell": all(not any(_contains(*domain_bounds(d), c) for c in gap_cells) for d in runtime_domains),
        "fixedFinGlueContainsAllSevenFixedCells": all(_contains(fixed_lo, fixed_hi, c) for c in fixed_fin),
        "bearingMountGlueContainsBearing": _contains(mount_lo, mount_hi, bearing),
        "bearingMountGlueContainsParentAnchor": _contains(mount_lo, mount_hi, (bearing[0] - 1, bearing[1], bearing[2])),
        "childGlueContainsAllRudderCells": all(_contains(child_lo, child_hi, c) for c in control_child),
        "childGlueExcludesBearing": not _contains(child_lo, child_hi, bearing),
    }

    resulting_main = prior_main_with_assembler + len(additions_main) - len(removals_main)
    resulting_manifest = int(powertrain.get("metrics", {}).get("resultingManifestPlacementCount", 0)) + len(additions_main) - len(removals_main) + len(additions_child)
    expected_primary = resulting_main + len(propeller_child) + len(control_child)
    count_checks = {
        "parentMainCountPreservedByOneAddOneRemove": resulting_main == prior_main_with_assembler,
        "manifestCountIs130": resulting_manifest == 130,
        "primaryTransferIs131": expected_primary == 131,
    }

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required = {
        "yaw_v0131_primary_sable_recapture_probe",
        "rudder_swivel_child_capture_probe",
        "rudder_neutral_constraint_probe",
        "rudder_actuation_probe",
        "rudder_yaw_force_probe",
    }
    if required - {str(v.get("id")) for v in runtime_obligations}:
        raise YawControlCorrectionError("v0.13.1 profile missing required runtime obligations")

    passed = all(topology_checks.values()) and all(glue_checks.values()) and all(count_checks.values())
    out: dict[str, Any] = {
        "schemaVersion": "aircraft-yaw-control-ir-0.13.1",
        "assetId": str(powertrain["assetId"]).replace("v0_12_powertrain", "v0_13_1_yaw_control"),
        "compilerVersion": "aircraft-yaw-control-lowerer-0.13.1",
        "sourceProbeManifestDigestSha256": manifest["digestSha256"],
        "sourceGlueEncodingDigestSha256": glue["digestSha256"],
        "sourcePowertrainDigestSha256": powertrain["digestSha256"],
        "supersededYawDigestSha256": prior_yaw["digestSha256"],
        "correctionReason": str(profile.get("correctionReason", "")),
        "profileId": profile["profileId"],
        "placements": sorted(declared, key=lambda p: tuple(p["lattice"])),
        "fixedFinCoordinates": [list(c) for c in sorted(fixed_fin)],
        "airGapCoordinates": [list(c) for c in sorted(gap_cells)],
        "rudderChildCoordinates": [list(c) for c in sorted(control_child)],
        "swivelBearingCoordinate": list(bearing),
        "rudderSeedCoordinate": list(rudder_seed),
        "runtimeGlueDomains": runtime_domains,
        "topologyChecks": topology_checks,
        "glueChecks": glue_checks,
        "countChecks": count_checks,
        "runtimeObligations": runtime_obligations,
        "aerodynamicAccounting": {
            "fixedVerticalTailRegularSailCells": len(fixed_fin),
            "horizontalTailRegularSailCells": 19,
            "rudderSymmetricSailCells": len(control_child),
            "airGapCells": len(gap_cells),
            "note": "runtime-driven v0.13.1 engineering-mule correction; analytical v0.1 evidence is not retuned to match this discrete mechanism",
        },
        "metrics": {
            "v012MovingMainBodyPlacementCount": prior_main_with_assembler,
            "v0131MovingParentMainBodyPlacementCount": resulting_main,
            "nestedPropellerChildPlacementCount": len(propeller_child),
            "yawControlChildPlacementCount": len(control_child),
            "resultingManifestPlacementCount": resulting_manifest,
            "expectedPrimarySableTransferCount": expected_primary,
            "parentMainAdditionCount": len(additions_main),
            "parentMainRemovalCount": len(removals_main),
            "controlChildAdditionCount": len(additions_child),
            "runtimeGlueDomainCount": len(runtime_domains),
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "yawControlStaticTopologyPassed": passed,
            "rudderChildCaptureProbeReady": passed,
            "controlActuationProbeReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "v0131_primary_sable_recapture_unverified",
                "rudder_swivel_child_capture_unverified",
                "rudder_neutral_constraint_unverified",
                "rudder_actuation_unverified",
                "rudder_yaw_force_unverified",
                "pilot_control_binding_unverified",
            ],
        },
        "validation": {
            "passed": passed,
            "scope": "runtime_corrected_one_axis_yaw_topology_with_seven_cell_fixed_fin_one_block_air_hinge_gap_aft_up_facing_swivel_bearing_and_four_cell_symmetric_sail_rudder",
            "doesNotProve": [
                "that the corrected 131-block primary fixture reassembles into one Sable sublevel",
                "that the aft Swivel Bearing captures exactly four rudder cells",
                "that the rotary constraint begins neutral",
                "signed rudder actuation or return behavior",
                "runtime rudder side force or yaw moment",
                "pilot input binding",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
