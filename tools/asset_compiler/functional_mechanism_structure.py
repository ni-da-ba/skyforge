from __future__ import annotations

from typing import Any

from minecraft_structure import encode_structure_nbt
from model import BlockState, CompiledAsset, SpecError, VoxelModel

EXPECTED_SCHEMA = "skyforge.functional-mechanism-plan.v1"


def _int_triple(value: Any, label: str) -> tuple[int, int, int]:
    if (
        not isinstance(value, list)
        or len(value) != 3
        or not all(isinstance(component, int) and not isinstance(component, bool) for component in value)
    ):
        raise SpecError(f"{label} must be an integer triple")
    return value[0], value[1], value[2]


def _validate_plan_geometry(plan: dict[str, Any]) -> tuple[list[int], list[int], list[int]]:
    envelope = plan.get("envelope")
    if not isinstance(envelope, dict):
        raise SpecError("mechanism plan has no envelope")
    minimum = list(_int_triple(envelope.get("min"), "mechanism envelope min"))
    maximum = list(_int_triple(envelope.get("max"), "mechanism envelope max"))
    size = list(_int_triple(envelope.get("size"), "mechanism envelope size"))
    if any(lo > hi for lo, hi in zip(minimum, maximum)):
        raise SpecError("mechanism envelope min exceeds max")
    expected_size = [maximum[i] - minimum[i] + 1 for i in range(3)]
    if size != expected_size:
        raise SpecError("mechanism envelope size does not match min/max")
    return minimum, maximum, size


def _inside(pos: tuple[int, int, int], minimum: list[int], maximum: list[int]) -> bool:
    return all(minimum[i] <= pos[i] <= maximum[i] for i in range(3))


def _validate_plan_references(
    plan: dict[str, Any],
    placement_ids: set[str],
    occupied: set[tuple[int, int, int]],
    minimum: list[int],
    maximum: list[int],
) -> None:
    connectivity = plan.get("connectivity")
    if not isinstance(connectivity, dict):
        raise SpecError("mechanism plan has no connectivity")
    nodes = connectivity.get("nodes")
    if not isinstance(nodes, list) or not nodes or not all(isinstance(node, str) and node for node in nodes):
        raise SpecError("mechanism connectivity nodes must be non-empty placement IDs")
    if len(set(nodes)) != len(nodes):
        raise SpecError("mechanism connectivity contains duplicate nodes")
    unknown_nodes = sorted(set(nodes) - placement_ids)
    if unknown_nodes:
        raise SpecError(f"mechanism connectivity references unknown placements: {unknown_nodes}")

    edges = connectivity.get("edges")
    if not isinstance(edges, list):
        raise SpecError("mechanism connectivity edges must be a list")
    node_set = set(nodes)
    for edge in edges:
        if not isinstance(edge, list) or len(edge) != 2 or not all(isinstance(node, str) for node in edge):
            raise SpecError("mechanism connectivity edge must contain two node IDs")
        if edge[0] not in node_set or edge[1] not in node_set:
            raise SpecError("mechanism connectivity edge references an unknown node")
    sever_node = connectivity.get("severNode")
    if sever_node not in node_set:
        raise SpecError("mechanism severNode must reference a connectivity node")

    requirements = plan.get("supportRequirements")
    if not isinstance(requirements, list):
        raise SpecError("mechanism supportRequirements must be a list")
    for requirement in requirements:
        if not isinstance(requirement, dict):
            raise SpecError("mechanism support requirement must be an object")
        placement_id = requirement.get("placementId")
        if placement_id not in placement_ids:
            raise SpecError("support requirement references unknown placement")
        support = _int_triple(requirement.get("supportBelow"), "supportBelow")
        if support not in occupied:
            raise SpecError("support requirement references an unoccupied cell")

    clearance_cells = plan.get("clearanceCells")
    if not isinstance(clearance_cells, list):
        raise SpecError("mechanism clearanceCells must be a list")
    clearance: set[tuple[int, int, int]] = set()
    for cell in clearance_cells:
        position = _int_triple(cell, "clearance cell")
        if not _inside(position, minimum, maximum):
            raise SpecError("clearance cell lies outside mechanism envelope")
        if position in occupied:
            raise SpecError("clearance cell overlaps a mechanism placement")
        if position in clearance:
            raise SpecError("mechanism clearanceCells contains duplicates")
        clearance.add(position)


def compiled_asset_for_mechanism_structure(plan: dict[str, Any]) -> CompiledAsset:
    if plan.get("schema") != EXPECTED_SCHEMA:
        raise SpecError(f"expected mechanism plan schema {EXPECTED_SCHEMA}")
    if plan.get("validation", {}).get("passed") is not True:
        raise SpecError("refusing structure export for an invalid mechanism plan")

    minimum, maximum, size = _validate_plan_geometry(plan)
    placements = plan.get("placements")
    if not isinstance(placements, list) or not placements:
        raise SpecError("mechanism plan has no placements")

    model = VoxelModel()
    placement_ids: set[str] = set()
    occupied: set[tuple[int, int, int]] = set()
    validated: list[tuple[dict[str, Any], tuple[int, int, int], BlockState]] = []
    for placement in placements:
        if not isinstance(placement, dict):
            raise SpecError("mechanism placement must be an object")
        placement_id = placement.get("id")
        if not isinstance(placement_id, str) or not placement_id:
            raise SpecError("mechanism placement id must be a non-empty string")
        if placement_id in placement_ids:
            raise SpecError("mechanism plan contains duplicate placement IDs")
        placement_ids.add(placement_id)

        pos = _int_triple(placement.get("pos"), "mechanism placement position")
        if not _inside(pos, minimum, maximum):
            raise SpecError("mechanism placement lies outside mechanism envelope")
        if pos in occupied:
            raise SpecError("mechanism plan contains overlapping placements")
        occupied.add(pos)

        state_data = placement.get("blockState")
        if not isinstance(state_data, dict) or not isinstance(state_data.get("name"), str):
            raise SpecError("mechanism placement is missing blockState.name")
        properties = state_data.get("properties", {})
        if not isinstance(properties, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in properties.items()):
            raise SpecError("mechanism block-state properties must be a string object")
        state = BlockState.of(state_data["name"], **properties)
        validated.append((placement, pos, state))

    _validate_plan_references(plan, placement_ids, occupied, minimum, maximum)

    for placement, pos, state in validated:
        model.set(
            pos[0],
            pos[1],
            pos[2],
            str(placement.get("mechanicalRole", "mechanism")),
            state,
            str(placement["id"]),
        )

    summary = {
        "assetId": plan.get("assetId"),
        "compilerVersion": plan.get("compilerVersion"),
        "validation": plan["validation"],
        "layout": {
            "bounds": {
                "min": minimum,
                "max": maximum,
                "size": size,
            }
        },
    }
    return CompiledAsset(summary=summary, model=model)


def encode_mechanism_structure_nbt(plan: dict[str, Any]) -> bytes:
    """Encode the exact compiled mechanism as a deterministic Minecraft structure template."""
    return encode_structure_nbt(compiled_asset_for_mechanism_structure(plan))
