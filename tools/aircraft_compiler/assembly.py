from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from typing import Any


class AssemblyPlanError(ValueError):
    pass


def _coord(cell: dict[str, Any]) -> tuple[int, int, int]:
    try:
        return (int(cell["x"]), int(cell["y"]), int(cell["z"]))
    except (KeyError, TypeError, ValueError) as exc:
        raise AssemblyPlanError(f"invalid block-space cell: {cell!r}") from exc


def plan_assembly(blockspace: dict[str, Any]) -> dict[str, Any]:
    """Coalesce semantic cell records into coordinate-unique assembly sites.

    The v0.2 block-space IR permits multiple semantic role records at one lattice
    coordinate because role participates in Cell identity. A realizable block-world
    plan cannot: one lattice position can contain at most one placed block. v0.3
    therefore makes coordinates authoritative and preserves all roles/capabilities
    as provenance attached to that single site.
    """
    if blockspace.get("schemaVersion") != "aircraft-blockspace-ir-0.2":
        raise AssemblyPlanError("v0.3 requires aircraft-blockspace-ir-0.2")
    if not blockspace.get("validation", {}).get("passed", False):
        raise AssemblyPlanError("refusing to plan assembly from invalid block-space IR")

    role_contract = blockspace.get("capabilityContract", {}).get("roles")
    if not isinstance(role_contract, dict) or not role_contract:
        raise AssemblyPlanError("block-space IR has no capability role contract")

    grouped: dict[tuple[int, int, int], set[str]] = defaultdict(set)
    for cell in blockspace.get("cells", []):
        role = cell.get("role")
        if not isinstance(role, str) or not role:
            raise AssemblyPlanError(f"cell has no semantic role: {cell!r}")
        if role not in role_contract:
            raise AssemblyPlanError(f"cell role is absent from capability contract: {role}")
        grouped[_coord(cell)].add(role)

    if not grouped:
        raise AssemblyPlanError("block-space IR contains no occupied cells")

    sites: list[dict[str, Any]] = []
    overlap_sites: list[dict[str, Any]] = []
    for (x, y, z), roles in sorted(grouped.items()):
        ordered_roles = sorted(roles)
        capabilities = sorted(
            {
                capability
                for role in ordered_roles
                for capability in role_contract[role]
            }
        )
        site = {
            "x": x,
            "y": y,
            "z": z,
            "roles": ordered_roles,
            "capabilities": capabilities,
        }
        sites.append(site)
        if len(ordered_roles) > 1:
            overlap_sites.append({"x": x, "y": y, "z": z, "roles": ordered_roles})

    anchor_capabilities = {
        name: sorted(role_contract[name])
        for name in ("propeller_axis", "pilot_station", "cargo_station")
        if name in role_contract
    }
    stations = []
    for name, anchor in sorted(blockspace.get("anchors", {}).items()):
        lattice = anchor.get("lattice")
        if not isinstance(lattice, list) or len(lattice) != 3:
            raise AssemblyPlanError(f"anchor {name} has invalid lattice coordinate")
        stations.append(
            {
                "name": name,
                "lattice": [int(value) for value in lattice],
                "continuousM": list(anchor.get("continuousM", [])),
                "capabilities": anchor_capabilities.get(name, []),
                "placementSemantics": "anchor_requirement_not_occupied_site",
            }
        )

    input_records = len(blockspace.get("cells", []))
    unique_sites = len(sites)
    collapsed_records = input_records - unique_sites
    if collapsed_records < 0:
        raise AssemblyPlanError("unique site count exceeded source cell record count")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-assembly-plan-ir-0.3",
        "assetId": str(blockspace["assetId"]).replace("v0_2_blockspace", "v0_3_assembly"),
        "sourceBlockspaceAssetId": blockspace["assetId"],
        "sourceBlockspaceDigestSha256": blockspace["digestSha256"],
        "compilerVersion": "aircraft-assembly-plan-0.3",
        "coordinateSystem": dict(blockspace["coordinateSystem"]),
        "siteSemantics": {
            "coordinateAuthority": "one_assembly_site_per_integer_lattice_coordinate",
            "roleComposition": "union_all_source_roles_at_coordinate",
            "capabilityComposition": "set_union_of_role_capabilities",
            "targetResourceIdentity": "deferred_pending_exact_released_artifact_and_runtime_evidence",
        },
        "sites": sites,
        "stations": stations,
        "metrics": {
            "inputCellRecordCount": input_records,
            "uniqueAssemblySiteCount": unique_sites,
            "collapsedRoleRecordCount": collapsed_records,
            "multiRoleSiteCount": len(overlap_sites),
            "coordinateUniquenessSatisfied": len({(s["x"], s["y"], s["z"]) for s in sites}) == unique_sites,
            "allSourceRolesPreserved": sum(len(s["roles"]) for s in sites)
            == sum(len(roles) for roles in grouped.values()),
        },
        "overlapSites": overlap_sites,
        "validation": {
            "passed": True,
            "scope": "coordinate_unique_semantic_assembly_planning_only",
            "doesNotProve": [
                "Create Aeronautics concrete block legality",
                "Create Aeronautics assembly connectivity",
                "Create Aeronautics in-engine center of mass",
                "Create Aeronautics aerodynamic forces",
                "propulsion performance",
                "flight stability or control authority",
                "structural strength",
            ],
        },
    }
    out["validation"]["passed"] = bool(
        out["metrics"]["coordinateUniquenessSatisfied"]
        and out["metrics"]["allSourceRolesPreserved"]
    )
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
