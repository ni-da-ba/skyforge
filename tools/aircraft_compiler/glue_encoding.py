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


def _bounds(a: tuple[int, int, int], b: tuple[int, int, int]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    return tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3))


def _contains(bounds_min: tuple[int, int, int], bounds_max: tuple[int, int, int], p: tuple[int, int, int]) -> bool:
    return all(bounds_min[i] <= p[i] <= bounds_max[i] for i in range(3))


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
    if encoding.get("policy") != "bounded_super_glue_domain_cover_of_main_body_tree":
        raise GlueEncodingError("v0.11 requires bounded Super Glue domain cover encoding")
    if encoding.get("commandRoot") != "create glue":
        raise GlueEncodingError("v0.11 requires Create 6.0.10 /create glue command")
    if int(encoding.get("requiredPermissionLevel", -1)) != 2:
        raise GlueEncodingError("Create glue command requires permission level 2")
    max_dimension = int(encoding.get("maxSelectionDimensionBlocks", 0))
    if max_dimension <= 0:
        raise GlueEncodingError("bounded glue encoding requires a positive maxSelectionDimensionBlocks")

    main_coords = {_coord(v) for v in fixture.get("mainBody", {}).get("coordinates", [])}
    child_coords = {_coord(v) for v in fixture.get("nestedPropellerChild", {}).get("coordinates", [])}
    raw_edges = fixture.get("adhesionIntent", {}).get("edges", [])
    if not main_coords:
        raise GlueEncodingError("fixture has no main-body coordinates")

    tree_edges: list[tuple[tuple[int, int, int], tuple[int, int, int]]] = []
    seen_edges: set[tuple[tuple[int, int, int], tuple[int, int, int]]] = set()
    for raw in raw_edges:
        a = _coord(raw["a"])
        b = _coord(raw["b"])
        edge = (min(a, b), max(a, b))
        if edge in seen_edges:
            raise GlueEncodingError(f"duplicate adhesion edge: {edge!r}")
        seen_edges.add(edge)
        if _manhattan(a, b) != 1:
            raise GlueEncodingError(f"non-face-adjacent adhesion proof edge: {a!r} -> {b!r}")
        if a not in main_coords or b not in main_coords:
            raise GlueEncodingError(f"adhesion proof edge endpoint not on main body: {a!r} -> {b!r}")
        if a in child_coords or b in child_coords:
            raise GlueEncodingError(f"adhesion proof edge crosses nested-child boundary: {a!r} -> {b!r}")
        tree_edges.append(edge)

    expected_edge_count = int(fixture.get("adhesionIntent", {}).get("edgeCount", len(raw_edges)))
    tree_is_spanning = len(tree_edges) == expected_edge_count == max(0, len(main_coords) - 1)
    if not tree_is_spanning:
        raise GlueEncodingError("v0.10 adhesion proof is not an N-1 main-body spanning tree")

    forbidden = {
        (min(_coord(v[0]), _coord(v[1])), max(_coord(v[0]), _coord(v[1])))
        for v in profile.get("forbiddenGlueEdges", [])
    }

    domain_records: list[dict[str, Any]] = []
    commands: list[str] = []
    covered_edges: set[tuple[tuple[int, int, int], tuple[int, int, int]]] = set()
    seen_names: set[str] = set()
    all_within_limit = True
    no_child_containment = True
    forbidden_clear = True

    for raw in profile.get("glueDomains", []):
        name = str(raw.get("name", "")).strip()
        if not name or name in seen_names:
            raise GlueEncodingError(f"glue domain requires a unique non-empty name: {name!r}")
        seen_names.add(name)
        start = _coord(raw["from"])
        end = _coord(raw["to"])
        bounds_min, bounds_max = _bounds(start, end)
        size = tuple(bounds_max[i] - bounds_min[i] + 1 for i in range(3))
        within_limit = all(v <= max_dimension for v in size)
        all_within_limit &= within_limit
        if not within_limit:
            raise GlueEncodingError(f"glue domain {name!r} exceeds {max_dimension}-block selection bound: {size!r}")

        contained_children = sorted(p for p in child_coords if _contains(bounds_min, bounds_max, p))
        no_child_containment &= not contained_children
        if contained_children:
            raise GlueEncodingError(f"glue domain {name!r} contains nested propeller payload cells: {contained_children!r}")

        crossed_forbidden = sorted(
            edge for edge in forbidden
            if _contains(bounds_min, bounds_max, edge[0]) and _contains(bounds_min, bounds_max, edge[1])
        )
        forbidden_clear &= not crossed_forbidden
        if crossed_forbidden:
            raise GlueEncodingError(f"glue domain {name!r} crosses forbidden dynamic boundary: {crossed_forbidden!r}")

        domain_covered = sorted(
            edge for edge in tree_edges
            if _contains(bounds_min, bounds_max, edge[0]) and _contains(bounds_min, bounds_max, edge[1])
        )
        if not domain_covered:
            raise GlueEncodingError(f"glue domain {name!r} covers no accepted adhesion proof edge")
        covered_edges.update(domain_covered)

        command = f"create glue {_rel_coord(start)} {_rel_coord(end)}"
        commands.append(command)
        domain_records.append({
            "name": name,
            "from": list(start),
            "to": list(end),
            "command": command,
            "selectionCellBounds": {"min": list(bounds_min), "max": list(bounds_max)},
            "selectionSizeBlocks": list(size),
            "coveredAdhesionEdgeCount": len(domain_covered),
            "coveredAdhesionEdges": [[list(a), list(b)] for a, b in domain_covered],
        })

    if not domain_records:
        raise GlueEncodingError("v0.11 requires at least one bounded glue domain")
    uncovered = sorted(set(tree_edges) - covered_edges)
    all_edges_covered = not uncovered
    if uncovered:
        raise GlueEncodingError(f"bounded glue domains leave adhesion proof edges uncovered: {uncovered!r}")

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

    checks = {
        "domainCoverEncoding": len(commands) == len(domain_records),
        "mainBodyConnectivityProofRetained": tree_is_spanning,
        "allAdhesionIntentEdgesCovered": all_edges_covered,
        "allGlueDomainsWithinSelectionLimit": all_within_limit,
        "noGlueDomainContainsNestedChild": no_child_containment,
        "forbiddenDynamicBoundariesClear": forbidden_clear,
        "physicsAssemblerCommandEncoded": assembler_command.startswith("setblock "),
        "createGlueCommandSourceBacked": encoding.get("sourceContract") == "Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span",
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
        "compilerVersion": "aircraft-glue-domain-encoder-0.11.1",
        "sourceAssemblyFixtureDigestSha256": fixture["digestSha256"],
        "profileId": profile["profileId"],
        "encodingPolicy": {
            "policy": encoding["policy"],
            "commandRoot": encoding["commandRoot"],
            "requiredPermissionLevel": 2,
            "maxSelectionDimensionBlocks": max_dimension,
            "idempotent": False,
            "rerunRule": "remove_prior_fixture_glue_before_reapplying; do not accumulate duplicate SuperGlueEntity instances",
            "rationale": "retain the exact N-1 face-adjacent tree as the connectivity proof, then lower it to bounded runtime-backed Super Glue domains that cover every proof edge without enclosing dynamic child cells",
        },
        "physicsAssemblerCommand": assembler_command,
        "glueCommands": commands,
        "glueDomains": domain_records,
        "adhesionProof": {
            "edgeCount": len(tree_edges),
            "coveredEdgeCount": len(covered_edges),
            "uncoveredEdges": [[list(a), list(b)] for a, b in uncovered],
        },
        "forbiddenGlueEdges": [[list(a), list(b)] for a, b in sorted(forbidden)],
        "checks": checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "mainBodyPlacementCount": len(main_coords),
            "nestedChildPlacementCount": len(child_coords),
            "adhesionIntentEdgeCount": len(tree_edges),
            "coveredAdhesionIntentEdgeCount": len(covered_edges),
            "glueDomainCount": len(domain_records),
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
            "scope": "source_and_runtime_backed_bounded_create_super_glue_domain_cover_of_the_exact_main_body_connectivity_proof",
            "doesNotProve": [
                "that all four emitted SuperGlueEntity domains survive placement and assembly",
                "that Physics Assembler transfers the exact intended primary Sable payload",
                "that the Propeller Bearing subsequently re-forms its nine-block child contraption inside the Sable body",
                "pilot occupancy on the assembled Sable body",
                "control authority",
                "runtime aerodynamic or propulsive force magnitude/sign",
                "flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
