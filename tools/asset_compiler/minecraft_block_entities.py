from __future__ import annotations

from dataclasses import dataclass

from model import BlockState, SpecError


@dataclass(frozen=True)
class BlockEntityPayload:
    """Bounded target-side block-entity payload for structure-template export.

    Minecraft structure templates attach block-entity NBT to the corresponding block entry.  The
    current Guild specimen only needs empty vanilla containers; inventory population/loot authority
    remains a later semantic content stage.
    """

    block_entity_id: str
    empty_items: bool = False


_EMPTY_CONTAINER_IDS = {
    "minecraft:barrel": "minecraft:barrel",
    "minecraft:chest": "minecraft:chest",
}


def block_entity_payload_for(state: BlockState) -> BlockEntityPayload | None:
    block_entity_id = _EMPTY_CONTAINER_IDS.get(state.name)
    if block_entity_id is None:
        return None
    return BlockEntityPayload(block_entity_id=block_entity_id, empty_items=True)


def require_block_entity_payload(state: BlockState) -> BlockEntityPayload:
    payload = block_entity_payload_for(state)
    if payload is None:
        raise SpecError(f"no bounded Minecraft block-entity payload policy for {state.name}")
    return payload
