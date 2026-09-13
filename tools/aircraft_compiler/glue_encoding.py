from __future__ import annotations

import hashlib
import json
from typing import Any


class GlueEncodingError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise GlueEncodingError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _manhattan(a: tuple[int, int, int], b: tuple[int, int, int]) -> int:
    return sum(abs(a[i] - b[i]) for i in range(3))


def _rel_scalar(value: int) -> str:
    return "~" if value == 0 else f"~{value}"


def _rel_coord(coord: tuple[int, int, int]) -> str:
    return " ".join(_rel_scalar(v) for v in coord)


def encode_glue_application(fixture: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if fixture.get("schemaVersion") != "aircraft-assembly-fixture-ir-0.10":
        raise GlueEncodingError("v0.11 requires aircraft assembly fixture IR v0.10")
    if profile.get("schemaVersion") != "aircraft-glue-encoding-profile-0.11":
        raise GlueEncodingError("v0.11 requires aircraft-glue-encoding-profile-0.11")
    readiness = fixture.get("readiness", {})
    if not readiness.get("assemblyFixtureTopologyPassed", False):
        raise GlueEncodingError("refusing glue encoding from failed v0.10 topology")
    if not readiness.get("mainBodyAdhesionGraphResolved", False):
        raise GlueEncodingError("refusing glue encoding from unresolved v0.10 adhesion graph")

    encoding = profile.get("encoding", {})
    if encoding.get("policy") != "one_super_glue_entity_per_main_body_tree_edge":
        raise GlueEncodingError("bounded v0.11 requires edge-exact Super Glue encoding")
    if encoding.get("commandRoot") != "create glue":
        raise GlueEncodingError("bounded v0.11 requires Create 6.0.10 /create glue command")
    if int(encoding.get("requiredPermissionLevel", -1)) != 2:
        raise GlueEncodingError("Create glue command requires permission level 2")

    main_coords = {_coord(v) for v in fixture.get("mainBody", {}).get("coordinates", [])}
    child_coords = {_coord(v) for v in fixture.get("nestedPropellerChild", {}).get("coordinates", [])}
    raw_edges = fixture.get("adhesionIntent", {}).get("edges", [])
    if not main_coords:
        raise GlueEncodingError("fixture has no main-body coordinates")

    commands: list[str] = []
    records: list[dict[str, Any]] = []
    seen_edges: set[tuple[tuple[int, int, int], tuple[int, int, int]]] = set()
    all_adjacent = True
    all_main = True
    child_free = True

    for raw in raw_edges:
        a = _coord(raw["a"])
        b = _coord(raw["b"])
        edge = (min(a, b), max(a, b))
        if edge in seen_edges:
            raise GlueEncodingError(f"duplicate adhesion edge: {edge!r}")
        seen_edges.add(edge)
        adjacent = _manhattan(a, b) == 1
        endpoints_main = a in main_coords and b in main_coords
        touches_child = a in child_coords or b in child_coords
        all_adjacent &= adjacent
        all_main &= endpoints_main
        child_free &= not touches_child
        if not adjacent:
            raise GlueEncodingError(f"non-face-adjacent adhesion edge cannot be encoded conservatively: {a!r} -> {b!r}")
        if not endpoints_main:
            raise GlueEncodingError(f"adhesion edge endpoint not on main body: {a!r} -> {b!r}")
        if touches_child:
            raise GlueEncodingError(f"adhesion edge crosses nested-child boundary: {a!r} -> {b!r}")
        command = f"create glue {_rel_coord(a)} {_rel_coord(b)}"
        commands.append(command)
        records.append({
            "a": list(a),
            "b": list(b),
            "command": command,
            "manhattanDistanceBlocks": 1,
            "selectionCellBounds": {
                "min": [min(a[i], b[i]) for i in range(3)],
                "max": [max(a[i], b[i]) for i in range(3)],
            },
        })

    expected_edge_count = int(fixture.get("adhesionIntent", {}).get("edgeCount", len(raw_edges)))
    edge_count_matches = len(records) == expected_edge_count == max(0, len(main_coords) - 1)

    assembler = fixture.get("physicsAssemblerPlacement", {})
    assembler_coord = _coord(assembler.get("lattice", []))
    assembler_resource = str(assembler.get("resourceId", ""))
    assembler_state = dict(assembler.get("blockState", {}))
    if assembler_resource != "simulated:physics_assembler":
        raise GlueEncodingError("unexpected Physics Assembler resource")
    if assembler_state.get("face") != "ceiling":
        raise GlueEncodingError("v0.11 requires the accepted face=ceiling assembler fixture")
    state_suffix = ""
    if assembler_state:
        state_suffix = "[" + ",".join(f"{k}={assembler_state[k]}" for k in sorted(assembler_state)) + "]"
    assembler_command = f"setblock {_rel_coord(assembler_coord)} {assembler_resource}{state_suffix} replace"

    forbidden = {
        (min(_coord(v[0]), _coord(v[1])), max(_coord(v[0]), _coord(v[1])))
        for v in profile.get("forbiddenGlueEdges", [])
    }
    encoded_edges = set(seen_edges)
    forbidden_clear = not bool(encoded_edges & forbidden)
    if not forbidden_clear:
        raise GlueEncodingError("encoded glue includes an explicitly forbidden dynamic boundary")

    checks = {
        "edgeExactEncoding": len(commands) == len(records) == len(seen_edges),
        "allGlueEdgesFaceAdjacent": all_adjacent,
        "allGlueEndpointsMainBody": all_main,
        "noGlueEndpointOnNestedChild": child_free,
        "adhesionEdgeCountMatchesMainBodySpanningTree": edge_count_matches,
        "forbiddenDynamicBoundariesClear": forbidden_clear,
        "physicsAssemblerCommandEncoded": assembler_command.startswith("setblock "),
        "createGlueCommandSourceBacked": encoding.get("sourceContract") == "Create-6.0.10-AllCommands/GlueCommand",
    }
    passed = all(checks.values())

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_ids = {
        "create_glue_command_registry_probe",
        "super_glue_entity_realization_probe",
        "physics_assembler_capture_probe",
        "glue_persistence_after_sublevel_move",
    }
    if required_ids - {str(v.get("id")) for v in runtime_obligations}:
        raise GlueEncodingError("v0.11 profile missing required runtime obligations")

    remaining_mechanical = [] if passed else ["adhesion_application_encoding_failed"]
    remaining_mechanical.append("control_surface_child_body_topology_unresolved")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-glue-encoding-ir-0.11",
        "assetId": str(fixture["assetId"]).replace("v0_10_assembly_fixture", "v0_11_glue_encoding"),
        "compilerVersion": "aircraft-glue-command-encoder-0.11",
        "sourceAssemblyFixtureDigestSha256": fixture["digestSha256"],
        "profileId": profile["profileId"],
        "encodingPolicy": {
            "policy": encoding["policy"],
            "commandRoot": encoding["commandRoot"],
            "requiredPermissionLevel": 2,
            "idempotent": False,
            "rerunRule": "remove_prior_fixture_glue_before_reapplying; do not accumulate duplicate SuperGlueEntity instances",
            "rationale": "one face-adjacent glue AABB per spanning-tree edge exactly realizes the accepted graph without broad selections crossing dynamic boundaries",
        },
        "physicsAssemblerCommand": assembler_command,
        "glueCommands": commands,
        "glueEntities": records,
        "forbiddenGlueEdges": [[list(a), list(b)] for a, b in sorted(forbidden)],
        "checks": checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "mainBodyPlacementCount": len(main_coords),
            "nestedChildPlacementCount": len(child_coords),
            "glueCommandCount": len(commands),
            "assemblerPlacementCommandCount": 1,
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "adhesionApplicationEncodingReady": passed,
            "mainBodyPhysicsAssemblyProbeReady": passed,
            "physicsAssemblyProbeReady": passed,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "remainingMechanicalBlockers": sorted(set(remaining_mechanical)),
            "runtimeBlockers": [
                "exact_stack_glue_realization_unverified",
                "physics_assembler_capture_unverified",
                "nested_propeller_capture_unverified",
                "pilot_occupancy_runtime_unverified",
                "control_binding_runtime_unverified",
                "aircraft_runtime_forces_unverified",
            ],
        },
        "validation": {
            "passed": passed,
            "scope": "source_backed_edge_exact_create_super_glue_command_encoding_for_main_body_probe_fixture",
            "doesNotProve": [
                "that /create glue is registered in the installed exact-stack runtime",
                "that all emitted SuperGlueEntity instances survive placement and assembly",
                "that Physics Assembler captures exactly the intended main body",
                "that nested Propeller Bearing capture succeeds",
                "pilot occupancy on the assembled Sable body",
                "control authority",
                "runtime mass or center of mass",
                "runtime aerodynamic or propulsive force magnitude/sign",
                "flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
