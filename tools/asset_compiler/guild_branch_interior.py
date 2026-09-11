from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from guild_branch_detail import compile_guild_branch_v03
from model import BlockState, CompiledAsset, SpecError, Volume


def _material(materials: dict[str, str], role: str, fallback: str) -> str:
    value = materials.get(role) or materials.get(fallback)
    if not value:
        raise SpecError(f"materialRoles.{role} or fallback {fallback} is required")
    return value


def _state(materials: dict[str, str], role: str, fallback: str, **properties: Any) -> BlockState:
    return BlockState.of(_material(materials, role, fallback), **properties)


def _inside(volume: dict[str, Any], pos: list[int]) -> bool:
    return all(volume["min"][i] <= pos[i] <= volume["max"][i] for i in range(3))


def compile_guild_branch_v04(spec: dict[str, Any]) -> CompiledAsset:
    """Interior/furnishing lowering layered over the v0.3 exterior/detail compiler.

    v0.4 deliberately treats furnishings as semantic modules, not arbitrary clutter. It corrects the
    public-service circulation exposed by the v0.3 floorplan, places bounded furnishing modules, emits
    interior anchors/volumes, and keeps a clear entrance -> service counter sight/circulation axis.
    """
    if spec.get("schemaVersion") != "0.4":
        raise SpecError("v0.4 interior compiler requires schemaVersion=0.4")

    interior = spec.get("interior", {})
    if interior.get("enabled", True) is not True:
        raise SpecError("v0.4 requires interior.enabled=true")

    materials = spec.get("materialRoles", {})
    required = {
        "seating",
        "recordsShelf",
        "publicBoard",
        "freightContainer",
        "repairBench",
        "toolStorage",
        "desk",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.4 interior material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.3"
    base_spec["requestedAnchors"] = [
        name for name in base_spec.get("requestedAnchors", []) if name != "BACK_OFFICE_WORK"
    ]
    compiled = compile_guild_branch_v03(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]

    hall_w = int(rp["hallWidth"])
    wall_h = int(rp["wallHeight"])
    depth = int(rp["hallDepth"])
    counter_z = int(rp["counterZ"])
    divider_z = int(rp["dividerZ"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])
    public_center = (door_x0 + door_x1) // 2
    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1

    wing = layout["volumes"]["workingWing"]
    wing_x0, _wy0, wing_z0 = wing["min"]
    wing_x1, _wy1, wing_z1 = wing["max"]
    warehouse = layout["volumes"]["warehouse"]
    repair = layout["volumes"]["lightRepair"]

    for pos, cell in list(model.cells.items()):
        if cell.module == "back_office":
            model.clear(*pos)

    layout["anchors"]["SERVICE_COUNTER"] = [public_center, 1, counter_z + 1]
    layout["anchors"]["NPC_WORK_POINT"] = [public_center, 1, counter_z - 1]
    layout["anchors"]["BACK_OFFICE_WORK"] = [public_center, 1, hall_z0 + 2]

    public_wait_z0 = counter_z + 1
    public_wait_z1 = hall_z1 - 2
    staff_z0 = hall_z0 + 1
    staff_z1 = counter_z - 1
    if public_wait_z0 > public_wait_z1 or staff_z0 > staff_z1:
        raise SpecError("v0.4 interior has insufficient depth for public/staff zoning")

    layout["volumes"]["publicWaiting"] = Volume(
        (1, 1, public_wait_z0), (hall_w - 2, min(4, wall_h - 1), public_wait_z1), "public_service"
    ).to_dict()
    layout["volumes"]["clerkWork"] = Volume(
        (max(1, door_x0 - 2), 1, counter_z - 2),
        (min(hall_w - 2, door_x1 + 2), min(4, wall_h - 1), counter_z - 1),
        "administrative",
    ).to_dict()
    layout["volumes"]["backOffice"] = Volume(
        (1, 1, staff_z0), (hall_w - 2, min(4, wall_h - 1), max(staff_z0, counter_z - 3)), "administrative"
    ).to_dict()

    seating = _state(materials, "seating", "floor", type="bottom")
    shelf = _state(materials, "recordsShelf", "structuralFrame")
    board = _state(materials, "publicBoard", "structuralFrame")
    freight = _state(materials, "freightContainer", "hardware")
    repair_bench = _state(materials, "repairBench", "hardware")
    tools = _state(materials, "toolStorage", "hardware")
    desk = _state(materials, "desk", "structuralFrame", type="bottom")
    lantern = BlockState.of(materials["lighting"], hanging=True)

    contract_x = max(1, door_x0 - 3)
    route_x = min(hall_w - 2, door_x1 + 3)
    board_z = hall_z1 - 1
    model.set(contract_x, 3, board_z, "board", board, "contract_board")
    model.set(route_x, 3, board_z, "board", board, "route_info_panel")
    layout["anchors"]["CONTRACT_BOARD"] = [contract_x, 1, board_z - 1]
    layout["anchors"]["ROUTE_INFO"] = [route_x, 1, board_z - 1]

    bench_z = min(public_wait_z1, hall_z1 - 3)
    for x in range(2, min(5, hall_w - 2)):
        model.set(x, 2, bench_z, "seating", seating, "waiting_bench")
    for x in range(max(hall_w - 5, 1), hall_w - 2):
        model.set(x, 2, bench_z, "seating", seating, "waiting_bench")

    backbar_z = max(hall_z0 + 2, counter_z - 3)
    for x in range(max(2, door_x0 - 2), min(hall_w - 3, door_x1 + 2) + 1):
        model.set(x, 2, backbar_z, "records", shelf, "clerk_backbar")
        if x % 2 == 0:
            model.set(x, 3, backbar_z, "records", shelf, "clerk_backbar")

    rear_z = hall_z0 + 1
    for x in range(2, min(5, hall_w - 2)):
        model.set(x, 2, rear_z, "desk", desk, "back_office_desk")
    for x in range(max(hall_w - 4, 1), hall_w - 1):
        model.set(x, 2, rear_z, "records", shelf, "records_shelves")
        model.set(x, 3, rear_z, "records", shelf, "records_shelves")

    for x in (max(2, door_x0 - 2), min(hall_w - 3, door_x1 + 2)):
        model.set(x, wall_h - 1, counter_z + 2, "lighting", lantern, "public_interior_lighting")
    model.set(public_center, wall_h - 1, counter_z - 2, "lighting", lantern, "staff_interior_lighting")

    freight_pick = layout["anchors"]["FREIGHT_PICKUP"]
    freight_lane_z = freight_pick[2]
    for z in range(warehouse["min"][2], warehouse["max"][2] + 1):
        if abs(z - freight_lane_z) <= 1:
            continue
        for x in range(warehouse["min"][0], min(warehouse["min"][0] + 2, warehouse["max"][0] + 1)):
            model.set(x, 2, z, "storage", freight, "freight_stack")
            if (z - warehouse["min"][2]) % 3 == 0:
                model.set(x, 3, z, "storage", freight, "freight_stack")

    repair_z0 = repair["min"][2]
    repair_z1 = repair["max"][2]
    bench_x = repair["min"][0]
    for z in range(repair_z0 + 1, max(repair_z0 + 2, repair_z1)):
        if z >= repair_z1:
            break
        model.set(bench_x, 2, z, "workbench", repair_bench, "repair_workbench")
    model.set(bench_x + 1, 2, repair_z0 + 1, "tool_storage", tools, "repair_tools")
    model.set(bench_x + 1, 3, repair_z0 + 1, "tool_storage", tools, "repair_tools")

    for z in range(counter_z + 1, hall_z1):
        for y in (2, 3):
            cell = model.cells.get((public_center, y, z))
            if cell and cell.module not in {"public_entrance", "service_counter"}:
                model.clear(public_center, y, z)
    for anchor_name in ("SERVICE_COUNTER", "NPC_WORK_POINT", "BACK_OFFICE_WORK"):
        ax, _ay, az = layout["anchors"][anchor_name]
        for y in (2, 3):
            cell = model.cells.get((ax, y, az))
            if cell and cell.module not in {"service_counter"}:
                model.clear(ax, y, az)

    layout["resolvedParameters"]["interiorVersion"] = "0.4"
    layout["resolvedParameters"]["publicWaitingZ"] = [public_wait_z0, public_wait_z1]
    layout["resolvedParameters"]["staffZoneZ"] = [staff_z0, staff_z1]

    issues: list[str] = []
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")

    required_modules = {
        "contract_board",
        "route_info_panel",
        "waiting_bench",
        "clerk_backbar",
        "back_office_desk",
        "records_shelves",
        "freight_stack",
        "repair_workbench",
        "repair_tools",
    }
    present = {cell.module for cell in model.cells.values() if cell.module}
    for module in sorted(required_modules - present):
        issues.append(f"missing interior module: {module}")

    sc = layout["anchors"]["SERVICE_COUNTER"]
    npc = layout["anchors"]["NPC_WORK_POINT"]
    if not sc[2] > counter_z:
        issues.append("SERVICE_COUNTER is not on public/south side of counter")
    if not npc[2] < counter_z:
        issues.append("NPC_WORK_POINT is not on staff/north side of counter")
    if not _inside(layout["volumes"]["backOffice"], layout["anchors"]["BACK_OFFICE_WORK"]):
        issues.append("BACK_OFFICE_WORK outside backOffice volume")

    for z in range(counter_z + 1, hall_z1):
        for y in (2, 3):
            cell = model.cells.get((public_center, y, z))
            if cell and cell.module not in {"public_entrance", "service_counter"}:
                issues.append(f"public circulation blocked at {(public_center, y, z)} by {cell.module or cell.role}")

    for anchor, volume in (
        ("FREIGHT_PICKUP", "warehouse"),
        ("FREIGHT_DROPOFF", "warehouse"),
        ("REPAIR_BAY", "lightRepair"),
    ):
        if not _inside(layout["volumes"][volume], layout["anchors"][anchor]):
            issues.append(f"{anchor} outside {volume} volume")

    for anchor_name in (
        "SERVICE_COUNTER",
        "NPC_WORK_POINT",
        "BACK_OFFICE_WORK",
        "FREIGHT_PICKUP",
        "FREIGHT_DROPOFF",
        "REPAIR_BAY",
    ):
        x, _y, z = layout["anchors"][anchor_name]
        for y in (2, 3):
            cell = model.cells.get((x, y, z))
            if cell and cell.module not in {"service_counter"}:
                issues.append(f"{anchor_name} obstructed at y={y} by {cell.module or cell.role}")

    min_x = min(x for x, _y, _z in model.cells)
    min_y = min(y for _x, y, _z in model.cells)
    min_z = min(z for _x, _y, z in model.cells)
    max_x = max(x for x, _y, _z in model.cells)
    max_y = max(y for _x, y, _z in model.cells)
    max_z = max(z for _x, _y, z in model.cells)
    layout["bounds"] = {
        "min": [min_x, min_y, min_z],
        "max": [max_x, max_y, max_z],
        "size": [max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1],
    }

    canonical = json.dumps(
        {
            "assetId": spec["assetId"],
            "layout": layout,
            "cells": [
                {"pos": [x, y, z], **cell.to_dict()}
                for (x, y, z), cell in sorted(model.cells.items())
            ],
        },
        sort_keys=True,
        separators=(",", ":"),
    )

    summary.update(
        {
            "schemaVersion": "0.4",
            "compilerVersion": "0.4",
            "assetId": spec["assetId"],
            "layout": layout,
            "blockCount": len(model.cells),
            "materialCounts": dict(
                sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())
            ),
            "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
            "moduleBlockCounts": dict(
                sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())
            ),
            "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
            "validation": {"passed": not issues, "issues": issues},
        }
    )
    return CompiledAsset(summary, model)
