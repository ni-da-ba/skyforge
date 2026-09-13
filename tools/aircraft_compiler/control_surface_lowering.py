from __future__ import annotations

import hashlib
import json
from collections import deque
from typing import Any


class YawControlLoweringError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise YawControlLoweringError(f"expected 3-vector, got {values!r}")
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


def lower_yaw_control(
    manifest: dict[str, Any],
    glue: dict[str, Any],
    powertrain: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise YawControlLoweringError("v0.13 requires probe manifest v0.9")
    if glue.get("schemaVersion") != "aircraft-glue-encoding-ir-0.11":
        raise YawControlLoweringError("v0.13 requires glue encoding IR v0.11")
    if powertrain.get("schemaVersion") != "aircraft-powertrain-ir-0.12":
        raise YawControlLoweringError("v0.13 requires powertrain IR v0.12")
    if profile.get("schemaVersion") != "aircraft-yaw-control-profile-0.13":
        raise YawControlLoweringError("v0.13 requires aircraft-yaw-control-profile-0.13")
    if not powertrain.get("validation", {}).get("passed", False):
        raise YawControlLoweringError("refusing yaw-control lowering from failed v0.12 powertrain IR")

    manifest_placements = list(manifest.get("placements", []))
    effective: dict[tuple[int, int, int], dict[str, Any]] = {}
    propeller_child_coords: set[tuple[int, int, int]] = set()
    for raw in manifest_placements:
        p = dict(raw)
        c = _coord(p["lattice"])
        if c in effective:
            raise YawControlLoweringError(f"duplicate v0.9 coordinate: {c!r}")
        effective[c] = p
        if p.get("kind") in {"propeller_hub", "propeller_sail"}:
            propeller_child_coords.add(c)

    # Reconstruct the exact v0.12 effective placement map rather than assuming the old manifest
    # still describes the powered specimen.
    for raw in powertrain.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        mode = str(p.get("mode", ""))
        if mode == "replace":
            if c not in effective:
                raise YawControlLoweringError(f"v0.12 replacement missing source coordinate: {c!r}")
            effective[c] = dict(p, kind="powertrain_main_body")
        elif mode == "add":
            if c in effective:
                raise YawControlLoweringError(f"v0.12 addition collides while reconstructing effective map: {c!r}")
            effective[c] = dict(p, kind="powertrain_main_body")
        else:
            raise YawControlLoweringError(f"unsupported v0.12 placement mode while reconstructing map: {mode!r}")

    main_manifest_coords = set(effective) - propeller_child_coords
    prior_main_with_assembler = int(powertrain.get("metrics", {}).get("resultingMovingMainBodyPlacementCount", 0))
    if prior_main_with_assembler != len(main_manifest_coords) + 1:
        raise YawControlLoweringError(
            "v0.12 moving-main metric disagrees with reconstructed effective map: "
            f"metric={prior_main_with_assembler} reconstructed={len(main_manifest_coords) + 1}"
        )
    nested_propeller_count = int(powertrain.get("metrics", {}).get("nestedPropellerChildPlacementCount", 0))
    if nested_propeller_count != len(propeller_child_coords):
        raise YawControlLoweringError(
            "v0.12 nested-propeller metric disagrees with manifest classification: "
            f"metric={nested_propeller_count} reconstructed={len(propeller_child_coords)}"
        )

    declared = list(profile.get("placements", []))
    if not declared:
        raise YawControlLoweringError("v0.13 profile requires declared placements")
    roles: dict[str, dict[str, Any]] = {}
    emitted_coords: set[tuple[int, int, int]] = set()
    replace_main: list[dict[str, Any]] = []
    reclassify: list[dict[str, Any]] = []
    add_child: list[dict[str, Any]] = []

    def require_source(p: dict[str, Any], c: tuple[int, int, int]) -> dict[str, Any]:
        existing = effective.get(c)
        if existing is None:
            raise YawControlLoweringError(f"v0.13 replacement/reclassification coordinate is empty: {c!r}")
        expected = dict(p.get("replaces", {}))
        if str(existing.get("resourceId")) != str(expected.get("resourceId")):
            raise YawControlLoweringError(
                f"v0.13 source resource mismatch at {c!r}: actual={existing.get('resourceId')} expected={expected.get('resourceId')}"
            )
        if expected.get("kind") is not None and str(existing.get("kind")) != str(expected.get("kind")):
            raise YawControlLoweringError(
                f"v0.13 source kind mismatch at {c!r}: actual={existing.get('kind')} expected={expected.get('kind')}"
            )
        expected_state = dict(expected.get("blockState", {}))
        if not _state_matches(dict(existing.get("blockState", {})), expected_state):
            raise YawControlLoweringError(
                f"v0.13 source blockstate mismatch at {c!r}: actual={existing.get('blockState')} expectedSubset={expected_state}"
            )
        return existing

    main_coords = set(main_manifest_coords)
    control_child_coords: set[tuple[int, int, int]] = set()
    for raw in declared:
        p = dict(raw)
        role = str(p.get("role", "")).strip()
        if not role or role in roles:
            raise YawControlLoweringError(f"v0.13 placement requires unique non-empty role: {role!r}")
        roles[role] = p
        c = _coord(p["lattice"])
        if c in emitted_coords:
            raise YawControlLoweringError(f"duplicate v0.13 coordinate: {c!r}")
        emitted_coords.add(c)
        if c in propeller_child_coords:
            raise YawControlLoweringError(f"yaw-control placement crosses propeller-child boundary: {c!r}")

        mode = str(p.get("mode", ""))
        if mode == "replace_main":
            require_source(p, c)
            if c not in main_coords:
                raise YawControlLoweringError(f"replace_main coordinate is not on parent main body: {c!r}")
            effective[c] = p
            replace_main.append(p)
        elif mode == "reclassify_main_to_control_child":
            require_source(p, c)
            if c not in main_coords:
                raise YawControlLoweringError(f"reclassified control-child coordinate is not on parent main body: {c!r}")
            main_coords.remove(c)
            control_child_coords.add(c)
            effective[c] = p
            reclassify.append(p)
        elif mode == "add_control_child":
            if c in effective:
                raise YawControlLoweringError(f"control-child addition collides with effective v0.12 placement: {c!r}")
            control_child_coords.add(c)
            effective[c] = p
            add_child.append(p)
        else:
            raise YawControlLoweringError(f"unsupported v0.13 placement mode: {mode!r}")

    required_roles = {
        "yaw_swivel_bearing",
        "yaw_hinge_spar_0",
        "yaw_hinge_spar_1",
        "yaw_hinge_spar_2",
        "rudder_seed",
        "rudder_1",
        "rudder_2",
        "rudder_3",
    }
    if required_roles - set(roles):
        raise YawControlLoweringError(f"missing required yaw-control roles: {sorted(required_roles - set(roles))}")

    bearing_coord = _coord(roles["yaw_swivel_bearing"]["lattice"])
    bearing_state = dict(roles["yaw_swivel_bearing"].get("blockState", {}))
    rudder_seed = _coord(roles["rudder_seed"]["lattice"])
    spar_coords = {_coord(roles[f"yaw_hinge_spar_{i}"]["lattice"]) for i in range(3)}
    fixed_fin_coords = {_coord(v) for v in profile.get("fixedFinCoordinates", [])}

    if len(fixed_fin_coords) != 4:
        raise YawControlLoweringError("bounded v0.13 requires exactly four fixed-fin cells")
    for c in fixed_fin_coords:
        p = effective.get(c)
        if p is None or p.get("resourceId") != "create:white_sail" or not _state_matches(dict(p.get("blockState", {})), {"facing": "south"}):
            raise YawControlLoweringError(f"fixed-fin source contract failed at {c!r}: {p!r}")

    # Child must be one exact vertical column seeded by the UP-facing bearing.
    child_reachable: set[tuple[int, int, int]] = set()
    if rudder_seed in control_child_coords:
        q = deque([rudder_seed])
        child_reachable.add(rudder_seed)
        while q:
            c = q.popleft()
            for n in _neighbors(c):
                if n in control_child_coords and n not in child_reachable:
                    child_reachable.add(n)
                    q.append(n)

    all_child_symmetric_z = all(
        effective[c].get("resourceId") == "simulated:white_symmetric_sail"
        and _state_matches(dict(effective[c].get("blockState", {})), {"axis": "z"})
        for c in control_child_coords
    )
    all_spar_structure = all(effective[c].get("resourceId") == "minecraft:spruce_planks" for c in spar_coords)

    topology_checks = {
        "exactlyOneMainToChildReclassification": len(reclassify) == 1,
        "exactlyThreeNewControlChildCells": len(add_child) == 3,
        "fourCellRudderChild": len(control_child_coords) == 4,
        "rudderChildConnected": child_reachable == control_child_coords,
        "bearingResourceExact": roles["yaw_swivel_bearing"].get("resourceId") == "simulated:swivel_bearing",
        "bearingFacesUp": bearing_state.get("facing") == "up",
        "bearingStartsUnassembled": str(bearing_state.get("assembled")) == "false",
        "bearingStartsUnpowered": str(bearing_state.get("powered")) == "false",
        "bearingSeedsRudderExactly": rudder_seed == (bearing_coord[0], bearing_coord[1] + 1, bearing_coord[2]),
        "rudderChildUsesSymmetricSailsWithZNormalAxis": all_child_symmetric_z,
        "threeCellStructuralHingeSpar": len(spar_coords) == 3 and all_spar_structure,
        "fixedFinRetainsFourRegularSouthFacingSails": len(fixed_fin_coords) == 4,
        "hingeSparSeparatesFixedFinAndRudderInX": all(
            f[0] + 1 == s[0] and s[0] + 1 == r[0]
            for f in fixed_fin_coords
            for s in spar_coords
            for r in control_child_coords
            if f[1:] == s[1:] == r[1:]
        ),
        "controlChildDisjointFromParentMain": not bool(control_child_coords & main_coords),
        "controlChildDisjointFromPropellerChild": not bool(control_child_coords & propeller_child_coords),
    }

    max_dimension = int(glue.get("encodingPolicy", {}).get("maxSelectionDimensionBlocks", 24))
    superseded_name = str(profile.get("supersededGlueDomainName", ""))
    bearing_parent_name = str(profile.get("bearingParentGlueDomainName", ""))
    input_domains = list(glue.get("glueDomains", []))
    superseded = [d for d in input_domains if str(d.get("name")) == superseded_name]
    if len(superseded) != 1:
        raise YawControlLoweringError(f"expected exactly one superseded glue domain {superseded_name!r}")
    retained_domains = [d for d in input_domains if str(d.get("name")) != superseded_name]
    bearing_parent_domains = [d for d in retained_domains if str(d.get("name")) == bearing_parent_name]
    if len(bearing_parent_domains) != 1:
        raise YawControlLoweringError(f"expected exactly one bearing-parent glue domain {bearing_parent_name!r}")

    parent_raw = dict(profile.get("replacementParentGlueDomain", {}))
    child_raw = dict(profile.get("controlChildGlueDomain", {}))
    parent_start, parent_end = _coord(parent_raw["from"]), _coord(parent_raw["to"])
    child_start, child_end = _coord(child_raw["from"]), _coord(child_raw["to"])
    parent_domain = _domain_record(str(parent_raw["name"]), parent_start, parent_end, "parent_main")
    child_domain = _domain_record(str(child_raw["name"]), child_start, child_end, "yaw_control_child")
    parent_lo, parent_hi = _bounds(parent_start, parent_end)
    child_lo, child_hi = _bounds(child_start, child_end)

    def domain_bounds(d: dict[str, Any]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
        if "selectionCellBounds" in d:
            b = d["selectionCellBounds"]
            return _coord(b["min"]), _coord(b["max"])
        return _bounds(_coord(d["from"]), _coord(d["to"]))

    parent_runtime_domains: list[dict[str, Any]] = []
    for d in retained_domains:
        lo, hi = domain_bounds(d)
        parent_runtime_domains.append(_domain_record(str(d.get("name", "")), lo, hi, "parent_main"))
    power_glue = dict(powertrain.get("powerplantGlueDomain", {}))
    power_from, power_to = _coord(power_glue["from"]), _coord(power_glue["to"])
    parent_runtime_domains.append(_domain_record(str(power_glue.get("name", "powerplant")), power_from, power_to, "parent_main"))
    parent_runtime_domains.append(parent_domain)

    all_runtime_domains = parent_runtime_domains + [child_domain]
    all_within_limit = all(all(v <= max_dimension for v in d["selectionSizeBlocks"]) for d in all_runtime_domains)
    no_parent_domain_contains_child = all(
        not any(_contains(*domain_bounds(d), c) for c in control_child_coords)
        for d in parent_runtime_domains
    )
    child_contains_all_rudder = all(_contains(child_lo, child_hi, c) for c in control_child_coords)
    child_excludes_bearing = not _contains(child_lo, child_hi, bearing_coord)
    child_excludes_spar_and_fixed = not any(_contains(child_lo, child_hi, c) for c in spar_coords | fixed_fin_coords)
    bearing_parent_lo, bearing_parent_hi = domain_bounds(bearing_parent_domains[0])
    bearing_attached_to_parent = _contains(bearing_parent_lo, bearing_parent_hi, bearing_coord)
    parent_domain_contains_fixed_and_spar = all(_contains(parent_lo, parent_hi, c) for c in fixed_fin_coords | spar_coords)
    parent_domain_excludes_child = not any(_contains(parent_lo, parent_hi, c) for c in control_child_coords)

    glue_checks = {
        "supersededVerticalTailGlueRemoved": len(superseded) == 1,
        "allRuntimeGlueDomainsWithinSelectionLimit": all_within_limit,
        "noParentGlueDomainContainsRudderChild": no_parent_domain_contains_child,
        "rudderChildGlueContainsAllFourChildCells": child_contains_all_rudder,
        "rudderChildGlueExcludesBearing": child_excludes_bearing,
        "rudderChildGlueExcludesHingeSparAndFixedFin": child_excludes_spar_and_fixed,
        "horizontalTailGlueRetainsBearingOnParent": bearing_attached_to_parent,
        "replacementParentGlueContainsFixedFinAndHingeSpar": parent_domain_contains_fixed_and_spar,
        "replacementParentGlueExcludesRudderChild": parent_domain_excludes_child,
    }

    resulting_main_with_assembler = prior_main_with_assembler - len(reclassify)
    resulting_manifest_count = int(powertrain.get("metrics", {}).get("resultingManifestPlacementCount", 0)) + len(add_child)
    expected_primary_transfer = resulting_main_with_assembler + nested_propeller_count + len(control_child_coords)

    count_checks = {
        "mainBodyDropsOnlyReclassifiedRudderSeed": resulting_main_with_assembler == prior_main_with_assembler - 1,
        "manifestCountAddsOnlyThreeNewRudderCells": resulting_manifest_count == int(powertrain.get("metrics", {}).get("resultingManifestPlacementCount", 0)) + 3,
        "primaryTransferPartitionsExactly": expected_primary_transfer
        == resulting_main_with_assembler + nested_propeller_count + len(control_child_coords),
    }

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_obligations = {
        "yaw_primary_sable_recapture_probe",
        "rudder_swivel_child_capture_probe",
        "rudder_neutral_constraint_probe",
        "rudder_actuation_probe",
        "rudder_yaw_force_probe",
    }
    if required_obligations - {str(v.get("id")) for v in runtime_obligations}:
        raise YawControlLoweringError("v0.13 profile missing required runtime obligations")

    passed = all(topology_checks.values()) and all(glue_checks.values()) and all(count_checks.values())
    out: dict[str, Any] = {
        "schemaVersion": "aircraft-yaw-control-ir-0.13",
        "assetId": str(powertrain["assetId"]).replace("v0_12_powertrain", "v0_13_yaw_control"),
        "compilerVersion": "aircraft-yaw-control-lowerer-0.13.0",
        "sourceProbeManifestDigestSha256": manifest["digestSha256"],
        "sourceGlueEncodingDigestSha256": glue["digestSha256"],
        "sourcePowertrainDigestSha256": powertrain["digestSha256"],
        "profileId": profile["profileId"],
        "placements": sorted(declared, key=lambda p: tuple(p["lattice"])),
        "fixedFinCoordinates": [list(c) for c in sorted(fixed_fin_coords)],
        "hingeSparCoordinates": [list(c) for c in sorted(spar_coords)],
        "rudderChildCoordinates": [list(c) for c in sorted(control_child_coords)],
        "swivelBearingCoordinate": list(bearing_coord),
        "rudderSeedCoordinate": list(rudder_seed),
        "runtimeGlueDomains": all_runtime_domains,
        "supersededGlueDomainName": superseded_name,
        "topologyChecks": topology_checks,
        "glueChecks": glue_checks,
        "countChecks": count_checks,
        "runtimeObligations": runtime_obligations,
        "aerodynamicAccounting": {
            "priorFixedVerticalTailRegularSailCells": 8,
            "resultingFixedVerticalTailRegularSailCells": 4,
            "priorHorizontalTailRegularSailCells": 19,
            "resultingHorizontalTailRegularSailCells": 18,
            "rudderSymmetricSailCells": len(control_child_coords),
            "note": "v0.13 is an engineering-mule control experiment; these geometry changes require later stability/flight requalification and do not retroactively alter v0.1 analytical evidence",
        },
        "metrics": {
            "v012MovingMainBodyPlacementCount": prior_main_with_assembler,
            "v013MovingParentMainBodyPlacementCount": resulting_main_with_assembler,
            "nestedPropellerChildPlacementCount": nested_propeller_count,
            "yawControlChildPlacementCount": len(control_child_coords),
            "resultingManifestPlacementCount": resulting_manifest_count,
            "expectedPrimarySableTransferCount": expected_primary_transfer,
            "mainReplacementCount": len(replace_main),
            "mainToControlChildReclassificationCount": len(reclassify),
            "controlChildAdditionCount": len(add_child),
            "runtimeGlueDomainCount": len(all_runtime_domains),
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
                "v013_primary_sable_recapture_unverified",
                "rudder_swivel_child_capture_unverified",
                "rudder_neutral_constraint_unverified",
                "rudder_actuation_unverified",
                "rudder_yaw_force_unverified",
                "pilot_control_binding_unverified",
            ],
        },
        "validation": {
            "passed": passed,
            "scope": "source_constrained_one_axis_yaw_control_topology_with_fixed_fin_structural_hinge_spar_up_facing_swivel_bearing_and_four_cell_symmetric_sail_rudder",
            "doesNotProve": [
                "that the modified 130-block primary fixture reassembles into one Sable sublevel",
                "that the Swivel Bearing captures exactly four rudder cells",
                "that the initial rotary constraint is neutral",
                "signed rudder actuation angle or return behavior",
                "runtime rudder side force or yaw moment",
                "pilot input binding",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
