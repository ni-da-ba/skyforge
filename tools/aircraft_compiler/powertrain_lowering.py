from __future__ import annotations

import hashlib
import json
from typing import Any


class PowertrainLoweringError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise PowertrainLoweringError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _contains(lo: tuple[int, int, int], hi: tuple[int, int, int], p: tuple[int, int, int]) -> bool:
    return all(lo[i] <= p[i] <= hi[i] for i in range(3))


def lower_powertrain(manifest: dict[str, Any], glue: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise PowertrainLoweringError("v0.12 requires probe manifest v0.9")
    if glue.get("schemaVersion") != "aircraft-glue-encoding-ir-0.11":
        raise PowertrainLoweringError("v0.12 requires glue encoding IR v0.11")
    if profile.get("schemaVersion") != "aircraft-powertrain-profile-0.12":
        raise PowertrainLoweringError("v0.12 requires aircraft-powertrain-profile-0.12")
    if not glue.get("readiness", {}).get("physicsAssemblyProbeReady", False):
        raise PowertrainLoweringError("refusing powertrain lowering from non-ready glue IR")

    placements = list(manifest.get("placements", []))
    by_coord: dict[tuple[int, int, int], dict[str, Any]] = {}
    for p in placements:
        c = _coord(p["lattice"])
        if c in by_coord:
            raise PowertrainLoweringError(f"duplicate manifest coordinate: {c!r}")
        by_coord[c] = p

    child_coords = {
        _coord(p["lattice"])
        for p in placements
        if p.get("kind") in {"propeller_hub", "propeller_sail"}
    }

    declared = list(profile.get("placements", []))
    if not declared:
        raise PowertrainLoweringError("v0.12 profile requires powertrain placements")

    replacements: list[dict[str, Any]] = []
    additions: list[dict[str, Any]] = []
    emitted_coords: set[tuple[int, int, int]] = set()
    for p in declared:
        c = _coord(p["lattice"])
        if c in emitted_coords:
            raise PowertrainLoweringError(f"duplicate powertrain coordinate: {c!r}")
        emitted_coords.add(c)
        mode = str(p.get("mode", ""))
        if c in child_coords:
            raise PowertrainLoweringError(f"powertrain placement crosses propeller child boundary: {c!r}")
        existing = by_coord.get(c)
        if mode == "replace":
            if existing is None:
                raise PowertrainLoweringError(f"replacement coordinate is empty in v0.9 manifest: {c!r}")
            expected = p.get("replaces", {})
            if str(existing.get("resourceId")) != str(expected.get("resourceId")) or str(existing.get("kind")) != str(expected.get("kind")):
                raise PowertrainLoweringError(
                    f"replacement source mismatch at {c!r}: actual={existing.get('kind')}/{existing.get('resourceId')} expected={expected}"
                )
            replacements.append(dict(p))
        elif mode == "add":
            if existing is not None:
                raise PowertrainLoweringError(f"addition collides with manifest at {c!r}: {existing!r}")
            additions.append(dict(p))
        else:
            raise PowertrainLoweringError(f"unsupported powertrain placement mode {mode!r}")

    roles = {str(p.get("role")): p for p in declared}
    required_roles = {"engine_port", "engine_starboard", "governor", "governor_cog", "prop_shaft"}
    if required_roles - set(roles):
        raise PowertrainLoweringError(f"missing required powertrain roles: {sorted(required_roles - set(roles))}")

    engine_port = _coord(roles["engine_port"]["lattice"])
    engine_starboard = _coord(roles["engine_starboard"]["lattice"])
    governor = _coord(roles["governor"]["lattice"])
    cog = _coord(roles["governor_cog"]["lattice"])
    shaft = _coord(roles["prop_shaft"]["lattice"])
    bearing = tuple(int(v) for v in profile["existingPropellerBearingCoordinate"])

    topology_checks = {
        "enginesOpposeAcrossGovernor": engine_port == (governor[0], governor[1], governor[2] - 1)
        and engine_starboard == (governor[0], governor[1], governor[2] + 1),
        "governorCogDirectlyAbove": cog == (governor[0], governor[1] + 1, governor[2]),
        "propShaftCollinearWithCogAndBearing": shaft[1:] == cog[1:] == bearing[1:]
        and abs(shaft[0] - cog[0]) == 1
        and abs(shaft[0] - bearing[0]) == 1,
        "governorReplacesExactlyOneStructuralCell": len(replacements) == 1 and roles["governor"].get("mode") == "replace",
        "allOtherPowertrainCellsAreAdditions": len(additions) == len(declared) - 1,
        "noPowertrainCellCrossesPropellerChild": not bool(emitted_coords & child_coords),
    }

    target_rpm = int(profile["governorTargetRpm"])
    if target_rpm != 128:
        raise PowertrainLoweringError("bounded v0.12 accepts only the first 128-RPM governor point")
    source_rpm = int(profile["portableEngineSourceRpm"])
    if source_rpm != 32:
        raise PowertrainLoweringError("v0.12 source contract requires 32-RPM Portable Engines")

    power_glue = profile.get("powerplantGlueDomain", {})
    lo = _coord(power_glue["from"])
    hi = _coord(power_glue["to"])
    lo = tuple(min(lo[i], hi[i]) for i in range(3))
    hi = tuple(max(_coord(power_glue["from"])[i], _coord(power_glue["to"])[i]) for i in range(3))
    domain_size = tuple(hi[i] - lo[i] + 1 for i in range(3))
    max_dim = int(glue.get("encodingPolicy", {}).get("maxSelectionDimensionBlocks", 24))
    glue_checks = {
        "powerplantGlueWithinCreateSelectionLimit": all(v <= max_dim for v in domain_size),
        "powerplantGlueContainsAllPowertrainCells": all(_contains(lo, hi, c) for c in emitted_coords),
        "powerplantGlueExcludesPropellerChild": not any(_contains(lo, hi, c) for c in child_coords),
        "powerplantGlueOverlapsAcceptedMainGlue": any(
            not (
                hi[i] < _coord(d["selectionCellBounds"]["min"])[i]
                or lo[i] > _coord(d["selectionCellBounds"]["max"])[i]
            )
            for d in glue.get("glueDomains", [])
            for i in range(3)
        ),
    }
    if not all(glue_checks.values()):
        raise PowertrainLoweringError(f"powerplant glue validation failed: {glue_checks}")

    resulting_manifest_count = len(placements) + len(additions)
    prior_main = int(glue.get("metrics", {}).get("mainBodyPlacementCount", 0))
    resulting_main = prior_main + len(additions)
    nested_child = int(glue.get("metrics", {}).get("nestedChildPlacementCount", 0))
    expected_primary_transfer = resulting_main + nested_child

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_obligations = {
        "powertrain_primary_sable_recapture_probe",
        "portable_engine_shared_network_probe",
        "governor_128_rpm_probe",
        "kinetic_stress_margin_probe",
        "propeller_thrust_sign_magnitude_probe",
    }
    if required_obligations - {str(v.get("id")) for v in runtime_obligations}:
        raise PowertrainLoweringError("v0.12 profile missing required runtime obligations")

    passed = all(topology_checks.values()) and all(glue_checks.values())
    out: dict[str, Any] = {
        "schemaVersion": "aircraft-powertrain-ir-0.12",
        "assetId": str(manifest["assetId"]).replace("v0_9_probe_manifest", "v0_12_powertrain"),
        "compilerVersion": "aircraft-powertrain-lowerer-0.12.0",
        "sourceProbeManifestDigestSha256": manifest["digestSha256"],
        "sourceGlueEncodingDigestSha256": glue["digestSha256"],
        "profileId": profile["profileId"],
        "governor": {
            "sourceRpmPerPortableEngine": source_rpm,
            "firstAcceptedTargetRpm": target_rpm,
            "higherRpmPointsStatus": "unaccepted_until_128_rpm_runtime_gate_passes",
            "higherRpmPoints": list(profile.get("laterGovernorRpmPoints", [])),
        },
        "placements": sorted(declared, key=lambda p: tuple(p["lattice"])),
        "replacementCount": len(replacements),
        "additionCount": len(additions),
        "powerplantGlueDomain": {
            "name": str(power_glue.get("name", "powerplant")),
            "from": list(_coord(power_glue["from"])),
            "to": list(_coord(power_glue["to"])),
            "selectionCellBounds": {"min": list(lo), "max": list(hi)},
            "selectionSizeBlocks": list(domain_size),
        },
        "topologyChecks": topology_checks,
        "glueChecks": glue_checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "inputManifestPlacementCount": len(placements),
            "resultingManifestPlacementCount": resulting_manifest_count,
            "replacementCount": len(replacements),
            "additionCount": len(additions),
            "priorMovingMainBodyPlacementCount": prior_main,
            "resultingMovingMainBodyPlacementCount": resulting_main,
            "nestedPropellerChildPlacementCount": nested_child,
            "expectedPrimarySableTransferCount": expected_primary_transfer,
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "powertrainStaticTopologyPassed": passed,
            "powertrainPlacementPatchReady": passed,
            "governor128RuntimeProbeReady": passed,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "powertrain_primary_sable_recapture_unverified",
                "portable_engine_shared_network_unverified",
                "governor_128_rpm_unverified",
                "kinetic_stress_margin_unverified",
                "propeller_thrust_sign_magnitude_unverified",
                "pilot_occupancy_runtime_unverified",
                "control_binding_runtime_unverified",
            ],
        },
        "validation": {
            "passed": passed,
            "scope": "source_constrained_dual_portable_engine_rotation_speed_controller_powertrain_topology_and_manifest_patch_only",
            "doesNotProve": [
                "that the modified 127-block fixture reassembles into one Sable sublevel",
                "that both Portable Engines join one live kinetic network",
                "that the Rotation Speed Controller realizes 128 RPM at the Propeller Bearing",
                "kinetic stress sufficiency",
                "propeller thrust sign or magnitude",
                "post-powertrain runtime mass or center of mass",
                "stable flight or control authority",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
