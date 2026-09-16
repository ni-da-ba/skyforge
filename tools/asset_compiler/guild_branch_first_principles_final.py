from __future__ import annotations

import hashlib
import json

from architectural_math import (
    closeness_centrality,
    path_stretch,
    reachable_distances,
    shortest_path_length,
    visibility_count,
    visible_2d,
)
from guild_branch_first_principles import compile_guild_branch_first_principles
from model import CompiledAsset


def compile_guild_branch_first_principles_final(spec: dict) -> CompiledAsset:
    """Finalize the clean-room generator with a semantic public-domain space analysis.

    The core first-principles compiler deliberately constructs public, staff and working domains from
    one semantic program. Space-syntax metrics for the *public* gate should not treat isolated staff
    niches as failed public circulation, so this stage evaluates exactly the public side of the service
    counter while preserving the independently generated geometry unchanged.
    """

    compiled = compile_guild_branch_first_principles(spec)
    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    hall = layout["volumes"]["publicHall"]
    hall_x0, _hall_y0, hall_z0 = map(int, hall["min"])
    hall_x1, _hall_y1, hall_z1 = map(int, hall["max"])
    counter_z = int(layout["resolvedParameters"]["counterZ"])
    entrance = layout["anchors"]["PUBLIC_ENTRANCE"]
    service = layout["anchors"]["SERVICE_COUNTER"]
    entrance_node = (int(entrance[0]), int(entrance[2]))
    service_node = (int(service[0]), int(service[2]))

    public_walkable: set[tuple[int, int]] = set()
    for x in range(hall_x0 + 1, hall_x1):
        for z in range(counter_z + 1, hall_z1):
            if all(model.cells.get((x, y, z)) is None for y in (2, 3)):
                public_walkable.add((x, z))
    public_walkable.add(entrance_node)
    public_walkable.add(service_node)

    distances = reachable_distances(public_walkable, entrance_node)
    service_distance = shortest_path_length(public_walkable, entrance_node, service_node)
    opaque = {
        (x, z)
        for (x, y, z), cell in model.cells.items()
        if y == 2
        and hall_x0 <= x <= hall_x1
        and counter_z <= z <= hall_z1
        and cell.role in {
            "wall_infill",
            "structural_frame",
            "counter",
            "records",
            "storage",
            "workbench",
            "tool_storage",
        }
    }
    sees_service = visible_2d(opaque, entrance_node, service_node)
    space = {
        "domain": "public_side_of_service_counter",
        "walkableNodeCount": len(public_walkable),
        "entranceReachableCount": len(distances),
        "entranceConnectedFraction": len(distances) / max(len(public_walkable), 1),
        "entranceToServiceDistance": service_distance,
        "entranceToServicePathStretch": path_stretch(service_distance, entrance_node, service_node),
        "entranceCloseness": closeness_centrality(public_walkable, entrance_node),
        "serviceCloseness": closeness_centrality(public_walkable, service_node),
        "entranceVisibilityCount": visibility_count(public_walkable, opaque, entrance_node),
        "entranceSeesService": sees_service,
    }
    layout["firstPrinciples"]["spaceGraph"] = space

    issues = [
        issue
        for issue in summary["validation"]["issues"]
        if issue not in {
            "public walkable graph is not fully entrance-connected",
            "public entrance cannot reach service counter",
            "public entrance does not have line of sight to service counter",
        }
    ]
    if service_distance is None:
        issues.append("public entrance cannot reach service counter")
    if len(distances) != len(public_walkable):
        issues.append("public walkable graph is not fully entrance-connected")
    if spec.get("program", {}).get("entranceSeesService", True) and not sees_service:
        issues.append("public entrance does not have line of sight to service counter")

    canonical = json.dumps(
        {
            "assetId": summary["assetId"],
            "layout": layout,
            "cells": [
                {"pos": [x, y, z], **cell.to_dict()}
                for (x, y, z), cell in sorted(model.cells.items())
            ],
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    summary["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    summary["validation"] = {"passed": not issues, "issues": issues}
    return CompiledAsset(summary, model)
