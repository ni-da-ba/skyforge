from __future__ import annotations

import hashlib
import json
from collections import deque
from typing import Any


class AssemblyFixtureError(ValueError):
    pass


def _coord(p: dict[str, Any]) -> tuple[int, int, int]:
    values = p["lattice"]
    if len(values) != 3:
        raise AssemblyFixtureError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _neighbors(c: tuple[int, int, int]):
    x, y, z = c
    return ((x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1))


def plan_assembly_fixture(manifest: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise AssemblyFixtureError("v0.10 requires probe manifest v0.9")
    if profile.get("schemaVersion") != "aircraft-assembly-fixture-profile-0.10":
        raise AssemblyFixtureError("v0.10 requires aircraft-assembly-fixture-profile-0.10")
    if not manifest.get("readiness", {}).get("probePlacementManifestReady", False):
        raise AssemblyFixtureError("refusing fixture planning from non-ready probe manifest")

    main_kinds = set(str(v) for v in profile["mainBodyKinds"])
    child_kinds = set(str(v) for v in profile["nestedChildKinds"])
    if main_kinds & child_kinds:
        raise AssemblyFixtureError("mainBodyKinds and nestedChildKinds must be disjoint")

    placements = list(manifest.get("placements", []))
    main = [p for p in placements if p["kind"] in main_kinds]
    child = [p for p in placements if p["kind"] in child_kinds]
    unclassified = [p for p in placements if p["kind"] not in main_kinds | child_kinds]
    if unclassified:
        raise AssemblyFixtureError(f"unclassified placement kinds: {sorted({p['kind'] for p in unclassified})}")

    manifest_main_coords = {_coord(p) for p in main}
    child_coords = {_coord(p) for p in child}
    if manifest_main_coords & child_coords:
        raise AssemblyFixtureError("main and child bodies overlap")

    seed = tuple(int(v) for v in profile["seedCoordinate"])
    assembler_offset = tuple(int(v) for v in profile["assemblerOffsetFromSeed"])
    if assembler_offset != (0, -1, 0):
        raise AssemblyFixtureError("bounded v0.10 requires assembler one block below seed")
    assembler_coord = tuple(seed[i] + assembler_offset[i] for i in range(3))
    assembler = profile["physicsAssembler"]
    if str(assembler["resourceId"]) != "simulated:physics_assembler":
        raise AssemblyFixtureError("bounded v0.10 requires source-verified simulated:physics_assembler")
    state = dict(assembler["blockState"])
    if state.get("face") != "ceiling":
        raise AssemblyFixtureError("assembler must use face=ceiling so sticky facing is UP")
    if state.get("facing") not in {"north", "south", "east", "west"}:
        raise AssemblyFixtureError("assembler horizontal facing must be legal")

    assembler_coordinate_free = assembler_coord not in manifest_main_coords and assembler_coord not in child_coords
    if not assembler_coordinate_free:
        raise AssemblyFixtureError("Physics Assembler fixture coordinate collides with probe manifest")

    # The Physics Assembler is not part of v0.9's aircraft placement manifest, but it MUST become
    # part of the moving Sable body. PhysicsAssemblerBlockEntity's disassembly path discovers its
    # containing sublevel; PhysicsAssemblerBlock.afterMove repairs the parent relationship after a
    # move. Leaving the assembler in the world would therefore create an assemble-only fixture that
    # cannot exercise the intended disassembly/reassembly lifecycle. Treat it as an explicit v0.10
    # main-body fixture placement and glue it to the seed.
    main_coords = set(manifest_main_coords)
    main_coords.add(assembler_coord)

    # A face-adjacent spanning tree is a conservative adhesion intent graph. It does NOT claim
    # these edges have been encoded as Super Glue entities yet.
    parent: dict[tuple[int,int,int], tuple[int,int,int] | None] = {}
    if seed in main_coords:
        parent[seed] = None
        q = deque([seed])
        while q:
            c = q.popleft()
            for n in sorted(_neighbors(c)):
                if n in main_coords and n not in parent:
                    parent[n] = c
                    q.append(n)
    reachable = set(parent)
    unreachable = sorted(main_coords - reachable)
    edges = sorted((min(c,p), max(c,p)) for c,p in parent.items() if p is not None)

    bearing_coords = {_coord(p) for p in main if p["kind"] == "propeller_bearing"}
    child_hub_coords = {_coord(p) for p in child if p["kind"] == "propeller_hub"}
    child_sail_coords = {_coord(p) for p in child if p["kind"] == "propeller_sail"}
    bearing_child_face_adjacencies = sorted(
        (b, h) for b in bearing_coords for h in child_hub_coords if h in set(_neighbors(b))
    )

    checks = {
        "seedIsMainBodyPlacement": seed in manifest_main_coords,
        "assemblerCoordinateFree": assembler_coordinate_free,
        "assemblerCeilingStickyFaceSeedsUpward": assembler_coord == (seed[0], seed[1]-1, seed[2]) and state.get("face") == "ceiling",
        "physicsAssemblerIncludedInMovingMainBody": assembler_coord in main_coords,
        "physicsAssemblerHasAdhesionEdgeToSeed": (min(assembler_coord, seed), max(assembler_coord, seed)) in set(edges),
        "mainBodyAdjacencyGraphConnected": len(reachable) == len(main_coords) and bool(main_coords),
        "spanningTreeEdgeCountIsNMinusOne": len(edges) == max(0, len(main_coords)-1),
        "nestedChildExcludedFromMainAdhesionGraph": not any(a in child_coords or b in child_coords for a,b in edges),
        "exactlyOnePropellerBearingOnMainBody": len(bearing_coords) == 1,
        "propellerChildContainsHubAndSails": len(child_hub_coords) == 1 and len(child_sail_coords) > 0,
        "bearingFaceAdjacentToChildHub": len(bearing_child_face_adjacencies) == 1,
    }
    topology_passed = all(checks.values())

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_ids = {"physics_assembler_capture_probe", "adhesion_application_probe", "nested_propeller_capture_probe"}
    if required_ids - {v.get("id") for v in runtime_obligations}:
        raise AssemblyFixtureError("v0.10 profile missing required runtime obligations")

    mechanical_blockers = []
    if topology_passed:
        mechanical_blockers.append("adhesion_application_encoding_unresolved")
        mechanical_blockers.append("control_surface_child_body_topology_unresolved")
    else:
        mechanical_blockers.append("assembly_fixture_topology_failed")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-assembly-fixture-ir-0.10",
        "assetId": str(manifest["assetId"]).replace("v0_9_probe_manifest", "v0_10_assembly_fixture"),
        "compilerVersion": "aircraft-assembly-fixture-planner-0.10.1",
        "sourceProbeManifestDigestSha256": manifest["digestSha256"],
        "profileId": profile["profileId"],
        "physicsAssemblerPlacement": {
            "lattice": list(assembler_coord),
            "resourceId": assembler["resourceId"],
            "blockState": state,
            "seedLattice": list(seed),
            "movingBodyMembership": "required",
            "sourceContract": "face=ceiling seeds UP; afterMove repairs assembler parent; disassembly path requires assembler to reside in the Sable sublevel",
        },
        "mainBody": {
            "placementCount": len(main_coords),
            "manifestPlacementCount": len(manifest_main_coords),
            "fixturePlacementCount": 1,
            "coordinates": [list(c) for c in sorted(main_coords)],
            "seedCoordinate": list(seed),
            "reachableCount": len(reachable),
            "unreachableCoordinates": [list(c) for c in unreachable],
        },
        "nestedPropellerChild": {
            "placementCount": len(child),
            "coordinates": [list(c) for c in sorted(child_coords)],
            "bearingToHubFaceAdjacency": [[list(a), list(b)] for a,b in bearing_child_face_adjacencies],
        },
        "adhesionIntent": {
            "policy": "face_adjacent_spanning_tree_over_moving_main_body_including_physics_assembler",
            "edgeCount": len(edges),
            "edges": [{"a": list(a), "b": list(b)} for a,b in edges],
            "encodingStatus": "logical_graph_only_super_glue_entity_encoding_unresolved",
        },
        "topologyChecks": checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "manifestPlacementCount": len(placements),
            "fixturePlacementCount": len(placements) + 1,
            "mainBodyManifestPlacementCount": len(manifest_main_coords),
            "mainBodyPlacementCount": len(main_coords),
            "nestedChildPlacementCount": len(child),
            "adhesionIntentEdgeCount": len(edges),
            "mainBodyUnreachableCount": len(unreachable),
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "assemblyFixtureTopologyPassed": topology_passed,
            "physicsAssemblerPlacementResolved": checks["seedIsMainBodyPlacement"] and checks["assemblerCoordinateFree"] and checks["assemblerCeilingStickyFaceSeedsUpward"] and checks["physicsAssemblerIncludedInMovingMainBody"] and checks["physicsAssemblerHasAdhesionEdgeToSeed"],
            "mainBodyAdhesionGraphResolved": checks["mainBodyAdjacencyGraphConnected"] and checks["spanningTreeEdgeCountIsNMinusOne"],
            "adhesionApplicationEncodingReady": False,
            "physicsAssemblyProbeReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "mechanicalBlockers": sorted(set(mechanical_blockers)),
        },
        "validation": {
            "passed": topology_passed,
            "scope": "physics_assembler_seed_and_moving_body_membership_plus_main_body_face_adjacency_adhesion_intent_topology",
            "doesNotProve": [
                "Super Glue entity/selection encoding",
                "actual Physics Assembler capture in the exact runtime",
                "nested Propeller Bearing contraption capture",
                "control-surface child-body topology",
                "runtime mass or center of mass",
                "runtime force sign or magnitude",
                "flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
