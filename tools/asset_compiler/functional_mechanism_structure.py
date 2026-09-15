from __future__ import annotations

from typing import Any

from minecraft_structure import encode_structure_nbt
from model import BlockState, CompiledAsset, SpecError, VoxelModel

EXPECTED_SCHEMA = "skyforge.functional-mechanism-plan.v1"


def compiled_asset_for_mechanism_structure(plan: dict[str, Any]) -> CompiledAsset:
    if plan.get("schema") != EXPECTED_SCHEMA:
        raise SpecError(f"expected mechanism plan schema {EXPECTED_SCHEMA}")
    if plan.get("validation", {}).get("passed") is not True:
        raise SpecError("refusing structure export for an invalid mechanism plan")

    envelope = plan.get("envelope")
    if not isinstance(envelope, dict):
        raise SpecError("mechanism plan has no envelope")
    minimum = envelope.get("min")
    maximum = envelope.get("max")
    size = envelope.get("size")
    if not all(isinstance(value, list) and len(value) == 3 for value in (minimum, maximum, size)):
        raise SpecError("mechanism envelope must provide three-axis min/max/size")

    model = VoxelModel()
    placements = plan.get("placements")
    if not isinstance(placements, list) or not placements:
        raise SpecError("mechanism plan has no placements")

    for placement in placements:
        if not isinstance(placement, dict):
            raise SpecError("mechanism placement must be an object")
        pos = placement.get("pos")
        if not isinstance(pos, list) or len(pos) != 3 or not all(isinstance(v, int) for v in pos):
            raise SpecError("mechanism placement position must be an integer triple")
        state_data = placement.get("blockState")
        if not isinstance(state_data, dict) or not isinstance(state_data.get("name"), str):
            raise SpecError("mechanism placement is missing blockState.name")
        properties = state_data.get("properties", {})
        if not isinstance(properties, dict):
            raise SpecError("mechanism block-state properties must be an object")
        state = BlockState.of(state_data["name"], **properties)
        model.set(
            pos[0],
            pos[1],
            pos[2],
            str(placement.get("mechanicalRole", "mechanism")),
            state,
            str(placement.get("id", "mechanism")),
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
