from __future__ import annotations

from minecraft_adapter import (
    CARDINALS,
    REALIZATION_CAPABILITIES,
    BlockIntent,
    MinecraftAdapter,
    ShapeDescriptor,
)
from model import BlockState, Cell, VoxelModel

DETAIL_REALIZATION_CAPABILITIES = REALIZATION_CAPABILITIES | frozenset({"bars"})


class StructuralDetailMinecraftAdapter(MinecraftAdapter):
    """Extend Minecraft realization with backend-neutral structural-detail primitives.

    This adapter deliberately defines geometry/topology rather than concrete mod selection. Target
    profiles may register a bars-capable resource only after their own resource/state validation.
    """

    def intent_from_cell(self, cell: Cell) -> BlockIntent:
        cap = self.capability(cell.state.name)
        required = frozenset(cap.capabilities & DETAIL_REALIZATION_CAPABILITIES)
        return BlockIntent(
            families=tuple(sorted(cap.families)),
            required_capabilities=required,
            properties=cell.state.properties,
        )

    def shape_descriptor(self, state: BlockState) -> ShapeDescriptor:
        normalized = self.normalize_defaults(state)
        cap = self.capability(normalized.name)
        if "bars" not in cap.capabilities:
            return super().shape_descriptor(normalized)

        props = normalized.property_dict()
        arms = sum(props[direction] == "true" for direction in CARDINALS)
        return ShapeDescriptor(
            "bars",
            min(0.40, 0.125 + 0.0625 * arms),
            frozenset(),
            True,
            True,
        )

    def _connective_state(
        self,
        model: VoxelModel,
        pos: tuple[int, int, int],
        cell: Cell,
    ) -> BlockState:
        state = self.normalize_defaults(cell.state)
        cap = self.capability(state.name)
        if "bars" not in cap.capabilities:
            return super()._connective_state(model, pos, cell)

        props = state.property_dict()
        x, y, z = pos
        for direction, (dx, dy, dz) in CARDINALS.items():
            neighbor = model.cells.get((x + dx, y + dy, z + dz))
            connected = False
            if neighbor is not None:
                neighbor_cap = self.capability(neighbor.state.name)
                connected = bool(
                    {"bars", "full_cube", "solid_support"} & neighbor_cap.capabilities
                )
            props[direction] = str(connected).lower()
        return BlockState.of(state.name, **props)
