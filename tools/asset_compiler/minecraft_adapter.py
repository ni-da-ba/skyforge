from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from model import BlockState, Cell, CompiledAsset, SpecError, VoxelModel

CARDINALS = {
    "north": (0, 0, -1),
    "east": (1, 0, 0),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
}


@dataclass(frozen=True)
class BlockCapability:
    name: str
    families: frozenset[str]
    capabilities: frozenset[str]
    properties: tuple[tuple[str, frozenset[str]], ...] = ()

    def property_map(self) -> dict[str, frozenset[str]]:
        return dict(self.properties)


@dataclass(frozen=True)
class BlockIntent:
    families: tuple[str, ...]
    required_capabilities: frozenset[str] = frozenset()
    properties: tuple[tuple[str, str], ...] = ()
    preferred_blocks: tuple[str, ...] = ()


def _cap(
    name: str,
    families: Iterable[str],
    capabilities: Iterable[str] = (),
    **properties: Iterable[str],
) -> BlockCapability:
    return BlockCapability(
        name=name,
        families=frozenset(families),
        capabilities=frozenset(capabilities),
        properties=tuple(
            sorted((key, frozenset(str(v).lower() for v in values)) for key, values in properties.items())
        ),
    )


def vanilla_1_21_1_registry() -> dict[str, BlockCapability]:
    """Bounded Minecraft 1.21.1 capability registry for the accepted Guild palette.

    This is deliberately explicit rather than pretending to be a complete vanilla registry. Unknown
    blocks fail closed when strict adaptation is requested. Later versions should generate this data
    from authoritative game registries/datagen and allow mod capability providers to extend it.
    """
    bools = ("true", "false")
    facings = ("north", "east", "south", "west")
    registry = [
        _cap("minecraft:stone_bricks", ("masonry", "foundation"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_log", ("dark_timber", "structural_timber"), ("full_cube", "solid_support", "axis_orientable"), axis=("x", "y", "z")),
        _cap("minecraft:calcite", ("pale_masonry", "wall_infill"), ("full_cube", "solid_support")),
        _cap("minecraft:spruce_planks", ("timber", "floor"), ("full_cube", "solid_support")),
        _cap("minecraft:deepslate_tiles", ("dark_roof", "masonry"), ("full_cube", "solid_support")),
        _cap("minecraft:deepslate_tile_stairs", ("dark_roof",), ("stair", "directional", "neighbor_sensitive"), facing=facings, half=("top", "bottom"), shape=("straight", "inner_left", "inner_right", "outer_left", "outer_right"), waterlogged=bools),
        _cap("minecraft:deepslate_tile_slab", ("dark_roof",), ("slab",), type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:glass_pane", ("glazing",), ("pane", "neighbor_sensitive", "thin"), north=bools, east=bools, south=bools, west=bools, waterlogged=bools),
        _cap("minecraft:spruce_door", ("timber_door", "public_door"), ("door", "directional", "two_block"), facing=facings, half=("upper", "lower"), hinge=("left", "right"), open=bools, powered=bools),
        _cap("minecraft:dark_oak_door", ("dark_timber", "working_door"), ("door", "directional", "two_block"), facing=facings, half=("upper", "lower"), hinge=("left", "right"), open=bools, powered=bools),
        _cap("minecraft:polished_andesite", ("masonry", "hardware"), ("full_cube", "solid_support")),
        _cap("minecraft:lantern", ("lighting",), ("light", "attachment_sensitive"), hanging=bools, waterlogged=bools),
        _cap("minecraft:blue_wool", ("guild_blue", "textile"), ("full_cube", "solid_support")),
        _cap("minecraft:yellow_terracotta", ("warm_hardware", "brass_surrogate"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_slab", ("dark_timber",), ("slab",), type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:spruce_slab", ("timber", "seating"), ("slab",), type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:bookshelf", ("records", "timber"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_planks", ("dark_timber",), ("full_cube", "solid_support")),
        _cap("minecraft:barrel", ("storage",), ("full_cube", "block_entity", "container", "directional"), facing=("up", "down", *facings), open=bools),
        _cap("minecraft:smithing_table", ("workbench",), ("full_cube", "solid_support")),
        _cap("minecraft:chest", ("storage",), ("block_entity", "container", "directional"), facing=facings, type=("single", "left", "right"), waterlogged=bools),
        _cap("minecraft:stone_brick_slab", ("masonry",), ("slab",), type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:dark_oak_fence", ("dark_timber",), ("fence", "neighbor_sensitive", "thin"), north=bools, east=bools, south=bools, west=bools, waterlogged=bools),
        _cap("minecraft:polished_diorite", ("masonry", "history_masonry"), ("full_cube", "solid_support")),
        _cap("minecraft:blue_carpet", ("guild_blue", "textile"), ("carpet", "thin")),
        _cap("minecraft:lightning_rod", ("hardware", "metal"), ("directional", "attachment_sensitive"), facing=("up", "down", *facings), powered=bools, waterlogged=bools),
    ]
    return {entry.name: entry for entry in registry}


class MinecraftAdapter:
    """Translate/validate backend-neutral intent against bounded Minecraft capabilities.

    v0.1 is intentionally transitional: existing Guild compilers still emit concrete BlockStates.
    The adapter validates those states, normalizes neighbor-sensitive states where deterministic,
    checks selected runtime support invariants, and exposes a semantic intent resolver for the next
    compiler lowering stage.
    """

    def __init__(self, registry: dict[str, BlockCapability] | None = None) -> None:
        self.registry = dict(registry or vanilla_1_21_1_registry())

    def capability(self, block_name: str) -> BlockCapability:
        try:
            return self.registry[block_name]
        except KeyError as exc:
            raise SpecError(f"Minecraft adapter has no capability record for {block_name}") from exc

    def validate_state(self, state: BlockState) -> None:
        cap = self.capability(state.name)
        allowed = cap.property_map()
        for key, value in state.properties:
            if key not in allowed:
                raise SpecError(f"{state.name} does not support block-state property {key}")
            if value not in allowed[key]:
                values = ", ".join(sorted(allowed[key]))
                raise SpecError(f"{state.name}[{key}={value}] is illegal; expected one of {values}")

    def resolve_intent(self, intent: BlockIntent) -> BlockState:
        requested_families = set(intent.families)
        preferred = {name: i for i, name in enumerate(intent.preferred_blocks)}
        candidates: list[tuple[tuple[int, int, str], BlockCapability]] = []
        for cap in self.registry.values():
            family_overlap = len(requested_families & cap.families)
            if requested_families and family_overlap == 0:
                continue
            if not intent.required_capabilities.issubset(cap.capabilities):
                continue
            rank = preferred.get(cap.name, len(preferred) + 100)
            capability_slack = len(cap.capabilities - intent.required_capabilities)
            candidates.append(((rank, capability_slack, cap.name), cap))
        if not candidates:
            raise SpecError(
                "Minecraft adapter could not realize intent "
                f"families={intent.families} capabilities={sorted(intent.required_capabilities)}"
            )
        cap = min(candidates, key=lambda item: item[0])[1]
        state = BlockState.of(cap.name, **dict(intent.properties))
        self.validate_state(state)
        return state

    def _connective_state(self, model: VoxelModel, pos: tuple[int, int, int], cell: Cell) -> BlockState:
        cap = self.capability(cell.state.name)
        if not ({"pane", "fence"} & cap.capabilities):
            return cell.state
        props = cell.state.property_dict()
        x, y, z = pos
        for direction, (dx, dy, dz) in CARDINALS.items():
            neighbor = model.cells.get((x + dx, y + dy, z + dz))
            connected = False
            if neighbor is not None:
                ncap = self.capability(neighbor.state.name)
                if "pane" in cap.capabilities:
                    connected = bool({"pane", "full_cube", "solid_support"} & ncap.capabilities)
                else:
                    connected = bool({"fence", "full_cube", "solid_support"} & ncap.capabilities)
            props[direction] = str(connected).lower()
        return BlockState.of(cell.state.name, **props)

    def _validate_doors(self, model: VoxelModel, issues: list[str]) -> int:
        pairs = 0
        for pos, cell in sorted(model.cells.items()):
            cap = self.capability(cell.state.name)
            if "door" not in cap.capabilities:
                continue
            props = cell.state.property_dict()
            half = props.get("half")
            x, y, z = pos
            mate_pos = (x, y + 1, z) if half == "lower" else (x, y - 1, z) if half == "upper" else None
            if mate_pos is None:
                issues.append(f"door at {pos} lacks explicit half property")
                continue
            mate = model.cells.get(mate_pos)
            if mate is None or mate.state.name != cell.state.name:
                issues.append(f"door at {pos} has no matching {cell.state.name} mate at {mate_pos}")
                continue
            mate_props = mate.state.property_dict()
            expected = "upper" if half == "lower" else "lower"
            if mate_props.get("half") != expected:
                issues.append(f"door pair {pos}/{mate_pos} has inconsistent half states")
                continue
            for key in ("facing", "hinge"):
                if props.get(key) != mate_props.get(key):
                    issues.append(f"door pair {pos}/{mate_pos} disagrees on {key}")
            if half == "lower":
                pairs += 1
        return pairs

    def _validate_attachments(self, model: VoxelModel, issues: list[str]) -> int:
        checked = 0
        occupied = set(model.cells)
        for pos, cell in sorted(model.cells.items()):
            cap = self.capability(cell.state.name)
            if "attachment_sensitive" not in cap.capabilities:
                continue
            props = cell.state.property_dict()
            x, y, z = pos
            if cell.state.name == "minecraft:lantern":
                hanging = props.get("hanging", "false") == "true"
                support_pos = (x, y + 1, z) if hanging else (x, y - 1, z)
                if support_pos not in occupied:
                    issues.append(f"lantern at {pos} lacks {'ceiling' if hanging else 'floor'} support at {support_pos}")
                checked += 1
        return checked

    def adapt(self, compiled: CompiledAsset) -> tuple[CompiledAsset, dict]:
        model = VoxelModel(cells=dict(compiled.model.cells))
        issues: list[str] = []
        state_errors: list[str] = []
        state_changes = 0

        for pos, cell in sorted(list(model.cells.items())):
            try:
                self.validate_state(cell.state)
            except SpecError as exc:
                state_errors.append(f"{pos}: {exc}")
                continue
            realized = self._connective_state(model, pos, cell)
            if realized != cell.state:
                model.set(pos[0], pos[1], pos[2], cell.role, realized, cell.module)
                state_changes += 1

        issues.extend(state_errors)
        if not state_errors:
            door_pairs = self._validate_doors(model, issues)
            attachment_checks = self._validate_attachments(model, issues)
        else:
            door_pairs = 0
            attachment_checks = 0

        palette = sorted({cell.state.name for cell in model.cells.values()})
        block_entity_blocks = sum(
            1
            for cell in model.cells.values()
            if cell.state.name in self.registry and "block_entity" in self.registry[cell.state.name].capabilities
        )
        report = {
            "adapterVersion": "minecraft-adapter-0.1",
            "target": "minecraft-java-1.21.1",
            "passed": not issues,
            "issues": issues,
            "registeredPaletteSize": len(self.registry),
            "realizedPaletteSize": len(palette),
            "realizedBlocks": palette,
            "neighborSensitiveStateChanges": state_changes,
            "validatedDoorPairs": door_pairs,
            "validatedAttachmentBlocks": attachment_checks,
            "blockEntityBlockCount": block_entity_blocks,
            "blockEntityPolicy": "default_empty_runtime_state_only_v0.1",
            "semanticIntentResolverAvailable": True,
            "architectureStillEmitsConcreteStates": True,
        }
        if issues:
            raise SpecError("Minecraft adapter rejected compiled asset: " + "; ".join(issues[:8]))
        return CompiledAsset(compiled.summary, model), report
