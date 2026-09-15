from __future__ import annotations

from heapq import heappop, heappush

from minecraft_adapter import (
    CARDINALS,
    OPPOSITE,
    REALIZATION_CAPABILITIES,
    BlockIntent,
    MinecraftAdapter,
    ShapeDescriptor,
)
from model import BlockState, Cell, SpecError, VoxelModel

DETAIL_REALIZATION_CAPABILITIES = REALIZATION_CAPABILITIES | frozenset(
    {"bars", "ladder", "climbable", "scaffolding"}
)


class StructuralDetailMinecraftAdapter(MinecraftAdapter):
    """Extend Minecraft realization with backend-neutral structural-detail primitives.

    This adapter deliberately defines geometry/topology rather than concrete mod selection. Target
    profiles may register structural-detail resources only after their own resource/state
    validation.
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
        if "bars" in cap.capabilities:
            props = normalized.property_dict()
            arms = sum(props[direction] == "true" for direction in CARDINALS)
            return ShapeDescriptor(
                "bars",
                min(0.40, 0.125 + 0.0625 * arms),
                frozenset(),
                True,
                True,
            )
        if "ladder" in cap.capabilities:
            return ShapeDescriptor(
                "ladder",
                3.0 / 16.0,
                frozenset(),
                True,
                False,
            )
        if "scaffolding" in cap.capabilities:
            bottom = normalized.property_dict()["bottom"] == "true"
            return ShapeDescriptor(
                "scaffolding_bottom" if bottom else "scaffolding",
                0.50 if bottom else 0.25,
                frozenset(),
                True,
                True,
            )
        return super().shape_descriptor(normalized)

    def _is_scaffolding_cell(self, model: VoxelModel, pos: tuple[int, int, int]) -> bool:
        cell = model.cells.get(pos)
        return cell is not None and "scaffolding" in self.capability(cell.state.name).capabilities

    def _scaffolding_distance_map(
        self,
        model: VoxelModel,
    ) -> dict[tuple[int, int, int], int]:
        scaffolds = {
            pos
            for pos, cell in model.cells.items()
            if "scaffolding" in self.capability(cell.state.name).capabilities
        }
        distances = {pos: 7 for pos in scaffolds}
        queue: list[tuple[int, tuple[int, int, int]]] = []

        for pos in scaffolds:
            x, y, z = pos
            below = (x, y - 1, z)
            if below not in scaffolds and self._supports_face(model, below, "up"):
                distances[pos] = 0
                heappush(queue, (0, pos))

        while queue:
            distance, pos = heappop(queue)
            if distance != distances[pos]:
                continue
            x, y, z = pos

            above = (x, y + 1, z)
            if above in scaffolds and distance < distances[above]:
                distances[above] = distance
                heappush(queue, (distance, above))

            horizontal_distance = min(7, distance + 1)
            for dx, _dy, dz in CARDINALS.values():
                neighbor = (x + dx, y, z + dz)
                if neighbor in scaffolds and horizontal_distance < distances[neighbor]:
                    distances[neighbor] = horizontal_distance
                    heappush(queue, (horizontal_distance, neighbor))

        return distances

    def _scaffolding_bottom(self, model: VoxelModel, pos: tuple[int, int, int]) -> bool:
        x, y, z = pos
        below = (x, y - 1, z)
        return not self._is_scaffolding_cell(model, below) and not self._supports_face(
            model, below, "up"
        )

    def _connective_state(
        self,
        model: VoxelModel,
        pos: tuple[int, int, int],
        cell: Cell,
    ) -> BlockState:
        state = self.normalize_defaults(cell.state)
        cap = self.capability(state.name)
        if "bars" in cap.capabilities:
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

        if "scaffolding" in cap.capabilities:
            distance = self._scaffolding_distance_map(model)[pos]
            if distance >= 7:
                raise SpecError(
                    f"scaffolding at {pos} has no portable support path within distance 6"
                )
            props = state.property_dict()
            props["distance"] = str(distance)
            props["bottom"] = str(self._scaffolding_bottom(model, pos)).lower()
            return BlockState.of(state.name, **props)

        return super()._connective_state(model, pos, cell)

    def _validate_attachments(
        self,
        model: VoxelModel,
        issues: list[str],
    ) -> tuple[int, list[dict]]:
        checked, repairs = super()._validate_attachments(model, issues)
        for pos, cell in sorted(model.cells.items()):
            cap = self.capability(cell.state.name)
            if "ladder" not in cap.capabilities:
                continue
            props = self.normalize_defaults(cell.state).property_dict()
            facing = props["facing"]
            backing_pos = self._relative(pos, OPPOSITE[facing])
            if not self._supports_face(model, backing_pos, facing):
                issues.append(
                    f"ladder at {pos} lacks Minecraft-style {facing} backing support at {backing_pos}"
                )
            checked += 1
        return checked, repairs
