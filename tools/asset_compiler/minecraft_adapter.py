from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from typing import Callable, Iterable

from model import BlockState, Cell, CompiledAsset, SpecError, VoxelModel

CARDINALS = {
    "north": (0, 0, -1),
    "east": (1, 0, 0),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
COUNTERCLOCKWISE = {"north": "west", "west": "south", "south": "east", "east": "north"}
AXIS = {"north": "z", "south": "z", "east": "x", "west": "x"}

REALIZATION_CAPABILITIES = frozenset({
    "full_cube", "solid_support", "axis_orientable", "slab", "stair", "pane", "fence",
    "door", "two_block", "light", "attachment_sensitive", "carpet", "thin", "block_entity",
    "container", "directional", "neighbor_sensitive",
})


@dataclass(frozen=True)
class BlockCapability:
    name: str
    families: frozenset[str]
    capabilities: frozenset[str]
    properties: tuple[tuple[str, frozenset[str]], ...] = ()
    defaults: tuple[tuple[str, str], ...] = ()

    def property_map(self) -> dict[str, frozenset[str]]:
        return dict(self.properties)

    def default_map(self) -> dict[str, str]:
        return dict(self.defaults)


@dataclass(frozen=True)
class BlockIntent:
    """Target-neutral material/shape request."""

    families: tuple[str, ...]
    required_capabilities: frozenset[str] = frozenset()
    properties: tuple[tuple[str, str], ...] = ()
    preferred_blocks: tuple[str, ...] = ()


@dataclass(frozen=True)
class ShapeDescriptor:
    shape_class: str
    collision_volume_fraction: float
    support_faces: frozenset[str]
    partial_collision: bool
    neighbor_dependent: bool

    def to_dict(self) -> dict:
        return {
            "shapeClass": self.shape_class,
            "collisionVolumeFraction": self.collision_volume_fraction,
            "supportFaces": sorted(self.support_faces),
            "partialCollision": self.partial_collision,
            "neighborDependent": self.neighbor_dependent,
        }


def _cap(
    name: str,
    families: Iterable[str],
    capabilities: Iterable[str] = (),
    *,
    defaults: dict[str, str] | None = None,
    **properties: Iterable[str],
) -> BlockCapability:
    return BlockCapability(
        name=name,
        families=frozenset(families),
        capabilities=frozenset(capabilities),
        properties=tuple(sorted((key, frozenset(str(v).lower() for v in values)) for key, values in properties.items())),
        defaults=tuple(sorted((str(k), str(v).lower()) for k, v in (defaults or {}).items())),
    )


def vanilla_1_21_1_registry() -> dict[str, BlockCapability]:
    """Bounded Java 1.21.1 capability registry for the current Guild palette."""
    bools = ("true", "false")
    facings = ("north", "east", "south", "west")
    registry = [
        _cap("minecraft:stone_bricks", ("masonry", "foundation"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_log", ("dark_timber", "structural_timber"), ("full_cube", "solid_support", "axis_orientable"), defaults={"axis": "y"}, axis=("x", "y", "z")),
        _cap("minecraft:calcite", ("pale_masonry", "wall_infill"), ("full_cube", "solid_support")),
        _cap("minecraft:spruce_planks", ("timber", "floor"), ("full_cube", "solid_support")),
        _cap("minecraft:deepslate_tiles", ("dark_roof", "masonry"), ("full_cube", "solid_support")),
        _cap("minecraft:deepslate_tile_stairs", ("dark_roof",), ("stair", "directional", "neighbor_sensitive"), defaults={"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"}, facing=facings, half=("top", "bottom"), shape=("straight", "inner_left", "inner_right", "outer_left", "outer_right"), waterlogged=bools),
        _cap("minecraft:deepslate_tile_slab", ("dark_roof",), ("slab",), defaults={"type": "bottom", "waterlogged": "false"}, type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:glass_pane", ("glazing",), ("pane", "neighbor_sensitive", "thin"), defaults={"north": "false", "east": "false", "south": "false", "west": "false", "waterlogged": "false"}, north=bools, east=bools, south=bools, west=bools, waterlogged=bools),
        _cap("minecraft:spruce_door", ("timber_door", "public_door"), ("door", "directional", "two_block"), defaults={"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"}, facing=facings, half=("upper", "lower"), hinge=("left", "right"), open=bools, powered=bools),
        _cap("minecraft:dark_oak_door", ("dark_timber", "working_door"), ("door", "directional", "two_block"), defaults={"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"}, facing=facings, half=("upper", "lower"), hinge=("left", "right"), open=bools, powered=bools),
        _cap("minecraft:polished_andesite", ("masonry", "hardware"), ("full_cube", "solid_support")),
        _cap("minecraft:lantern", ("lighting",), ("light", "attachment_sensitive"), defaults={"hanging": "false", "waterlogged": "false"}, hanging=bools, waterlogged=bools),
        _cap("minecraft:blue_wool", ("guild_blue", "textile"), ("full_cube", "solid_support")),
        _cap("minecraft:yellow_terracotta", ("warm_hardware", "brass_surrogate"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_slab", ("dark_timber",), ("slab",), defaults={"type": "bottom", "waterlogged": "false"}, type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:spruce_slab", ("timber", "seating"), ("slab",), defaults={"type": "bottom", "waterlogged": "false"}, type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:bookshelf", ("records", "timber"), ("full_cube", "solid_support")),
        _cap("minecraft:dark_oak_planks", ("dark_timber",), ("full_cube", "solid_support")),
        _cap("minecraft:barrel", ("storage", "freight_storage"), ("full_cube", "block_entity", "container", "directional"), defaults={"facing": "north", "open": "false"}, facing=("up", "down", *facings), open=bools),
        _cap("minecraft:smithing_table", ("workbench",), ("full_cube", "solid_support")),
        _cap("minecraft:chest", ("storage", "tool_storage"), ("block_entity", "container", "directional"), defaults={"facing": "north", "type": "single", "waterlogged": "false"}, facing=facings, type=("single", "left", "right"), waterlogged=bools),
        _cap("minecraft:stone_brick_slab", ("masonry",), ("slab",), defaults={"type": "bottom", "waterlogged": "false"}, type=("top", "bottom", "double"), waterlogged=bools),
        _cap("minecraft:dark_oak_fence", ("dark_timber",), ("fence", "neighbor_sensitive", "thin"), defaults={"north": "false", "east": "false", "south": "false", "west": "false", "waterlogged": "false"}, north=bools, east=bools, south=bools, west=bools, waterlogged=bools),
        _cap("minecraft:polished_diorite", ("masonry", "history_masonry"), ("full_cube", "solid_support")),
        _cap("minecraft:blue_carpet", ("guild_blue", "textile"), ("carpet", "thin")),
        _cap("minecraft:lightning_rod", ("hardware", "metal"), ("directional", "attachment_sensitive"), defaults={"facing": "up", "powered": "false", "waterlogged": "false"}, facing=("up", "down", *facings), powered=bools, waterlogged=bools),
    ]
    return {entry.name: entry for entry in registry}


class MinecraftAdapter:
    """Minecraft target-realization authority for bounded compiled architecture."""

    def __init__(self, registry: dict[str, BlockCapability] | None = None, *, intent_provider: Callable[[Cell], BlockIntent] | None = None, intent_profile_name: str = "capability-roundtrip-v0.2") -> None:
        self.registry = dict(registry or vanilla_1_21_1_registry())
        self.intent_provider = intent_provider
        self.intent_profile_name = str(intent_profile_name)

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

    def normalize_defaults(self, state: BlockState) -> BlockState:
        cap = self.capability(state.name)
        props = cap.default_map()
        props.update(state.property_dict())
        normalized = BlockState.of(state.name, **props)
        self.validate_state(normalized)
        return normalized

    def resolve_intent(self, intent: BlockIntent) -> BlockState:
        requested_families = set(intent.families)
        preferred = {name: i for i, name in enumerate(intent.preferred_blocks)}
        candidates: list[tuple[tuple[int, int, int, int, str], BlockCapability]] = []
        for cap in self.registry.values():
            overlap = len(requested_families & cap.families)
            if requested_families and overlap == 0:
                continue
            if not intent.required_capabilities.issubset(cap.capabilities):
                continue
            missing_families = len(requested_families - cap.families)
            extra_families = len(cap.families - requested_families)
            pref_rank = preferred.get(cap.name, len(preferred) + 100)
            cap_slack = len(cap.capabilities - intent.required_capabilities)
            candidates.append(((missing_families, extra_families, pref_rank, cap_slack, cap.name), cap))
        if not candidates:
            raise SpecError("Minecraft adapter could not realize intent " f"families={intent.families} capabilities={sorted(intent.required_capabilities)}")
        cap = min(candidates, key=lambda item: item[0])[1]
        props = cap.default_map()
        props.update(dict(intent.properties))
        state = BlockState.of(cap.name, **props)
        self.validate_state(state)
        return state

    def intent_from_cell(self, cell: Cell) -> BlockIntent:
        cap = self.capability(cell.state.name)
        required = frozenset(cap.capabilities & REALIZATION_CAPABILITIES)
        return BlockIntent(families=tuple(sorted(cap.families)), required_capabilities=required, properties=cell.state.properties)

    def shape_descriptor(self, state: BlockState) -> ShapeDescriptor:
        state = self.normalize_defaults(state)
        cap = self.capability(state.name)
        props = state.property_dict()
        if "full_cube" in cap.capabilities:
            return ShapeDescriptor("full_cube", 1.0, frozenset({"up", "down", "north", "east", "south", "west"}), False, False)
        if "slab" in cap.capabilities:
            slab_type = props["type"]
            if slab_type == "double":
                return ShapeDescriptor("double_slab", 1.0, frozenset({"up", "down", "north", "east", "south", "west"}), False, False)
            support = frozenset({"up"}) if slab_type == "top" else frozenset({"down"})
            return ShapeDescriptor(f"{slab_type}_slab", 0.5, support, True, False)
        if "stair" in cap.capabilities:
            half = props["half"]
            support = frozenset({"up"}) if half == "top" else frozenset({"down"})
            return ShapeDescriptor(f"{half}_stair_{props['shape']}", 0.75, support, True, True)
        if "pane" in cap.capabilities:
            arms = sum(props[d] == "true" for d in CARDINALS)
            return ShapeDescriptor("pane", min(0.40, 0.125 + 0.0625 * arms), frozenset(), True, True)
        if "fence" in cap.capabilities:
            arms = sum(props[d] == "true" for d in CARDINALS)
            return ShapeDescriptor("fence", min(0.50, 0.25 + 0.0625 * arms), frozenset(), True, True)
        if "door" in cap.capabilities:
            return ShapeDescriptor("door", 3.0 / 16.0, frozenset(), True, False)
        if "carpet" in cap.capabilities:
            return ShapeDescriptor("carpet", 1.0 / 16.0, frozenset(), True, False)
        if state.name == "minecraft:lantern":
            return ShapeDescriptor("lantern", 0.20, frozenset(), True, False)
        if state.name == "minecraft:lightning_rod":
            return ShapeDescriptor("lightning_rod", 0.05, frozenset(), True, False)
        if state.name == "minecraft:chest":
            return ShapeDescriptor("chest", 0.84, frozenset({"up"}), True, False)
        return ShapeDescriptor("bounded_unknown_shape", 1.0, frozenset(), False, "neighbor_sensitive" in cap.capabilities)

    @staticmethod
    def _relative(pos: tuple[int, int, int], direction: str) -> tuple[int, int, int]:
        dx, dy, dz = CARDINALS[direction]
        return pos[0] + dx, pos[1] + dy, pos[2] + dz

    def _can_take_stair_shape(self, model: VoxelModel, pos: tuple[int, int, int], state: BlockState, side: str) -> bool:
        neighbor = model.cells.get(self._relative(pos, side))
        if neighbor is None or "stair" not in self.capability(neighbor.state.name).capabilities:
            return True
        p = self.normalize_defaults(state).property_dict()
        q = self.normalize_defaults(neighbor.state).property_dict()
        return q["facing"] != p["facing"] or q["half"] != p["half"]

    def _derive_stair_state(self, model: VoxelModel, pos: tuple[int, int, int], cell: Cell) -> BlockState:
        state = self.normalize_defaults(cell.state)
        cap = self.capability(state.name)
        if "stair" not in cap.capabilities:
            return state
        props = state.property_dict()
        facing = props["facing"]
        half = props["half"]
        front = model.cells.get(self._relative(pos, facing))
        if front is not None and "stair" in self.capability(front.state.name).capabilities:
            fp = self.normalize_defaults(front.state).property_dict()
            ff = fp["facing"]
            if fp["half"] == half and AXIS[ff] != AXIS[facing] and self._can_take_stair_shape(model, pos, state, OPPOSITE[ff]):
                props["shape"] = "outer_left" if ff == COUNTERCLOCKWISE[facing] else "outer_right"
                return BlockState.of(state.name, **props)
        back = model.cells.get(self._relative(pos, OPPOSITE[facing]))
        if back is not None and "stair" in self.capability(back.state.name).capabilities:
            bp = self.normalize_defaults(back.state).property_dict()
            bf = bp["facing"]
            if bp["half"] == half and AXIS[bf] != AXIS[facing] and self._can_take_stair_shape(model, pos, state, bf):
                props["shape"] = "inner_left" if bf == COUNTERCLOCKWISE[facing] else "inner_right"
                return BlockState.of(state.name, **props)
        props["shape"] = "straight"
        return BlockState.of(state.name, **props)

    def _connective_state(self, model: VoxelModel, pos: tuple[int, int, int], cell: Cell) -> BlockState:
        state = self.normalize_defaults(cell.state)
        cap = self.capability(state.name)
        if not ({"pane", "fence"} & cap.capabilities):
            return state
        props = state.property_dict()
        x, y, z = pos
        for direction, (dx, dy, dz) in CARDINALS.items():
            neighbor = model.cells.get((x + dx, y + dy, z + dz))
            connected = False
            if neighbor is not None:
                ncap = self.capability(neighbor.state.name)
                connected = bool(({"pane", "full_cube", "solid_support"} if "pane" in cap.capabilities else {"fence", "full_cube", "solid_support"}) & ncap.capabilities)
            props[direction] = str(connected).lower()
        return BlockState.of(state.name, **props)

    def _supports_face(self, model: VoxelModel, pos: tuple[int, int, int], face: str) -> bool:
        cell = model.cells.get(pos)
        return cell is not None and face in self.shape_descriptor(cell.state).support_faces

    def _validate_doors(self, model: VoxelModel, issues: list[str]) -> int:
        pairs = 0
        for pos, cell in sorted(model.cells.items()):
            if "door" not in self.capability(cell.state.name).capabilities:
                continue
            props = self.normalize_defaults(cell.state).property_dict()
            half = props["half"]
            x, y, z = pos
            mate_pos = (x, y + 1, z) if half == "lower" else (x, y - 1, z)
            mate = model.cells.get(mate_pos)
            if mate is None or mate.state.name != cell.state.name:
                issues.append(f"door at {pos} has no matching {cell.state.name} mate at {mate_pos}")
                continue
            mate_props = self.normalize_defaults(mate.state).property_dict()
            expected = "upper" if half == "lower" else "lower"
            if mate_props["half"] != expected:
                issues.append(f"door pair {pos}/{mate_pos} has inconsistent half states")
                continue
            for key in ("facing", "hinge", "open", "powered"):
                if props[key] != mate_props[key]:
                    issues.append(f"door pair {pos}/{mate_pos} disagrees on {key}")
            if half == "lower":
                below = (x, y - 1, z)
                if not self._supports_face(model, below, "up"):
                    issues.append(f"door lower half at {pos} lacks sturdy top support at {below}")
                pairs += 1
        return pairs

    def _promote_roof_support(self, model: VoxelModel, support_pos: tuple[int, int, int], required_face: str) -> dict | None:
        cell = model.cells.get(support_pos)
        if cell is None or cell.role != "roof":
            return None
        before = self.normalize_defaults(cell.state)
        cap = self.capability(before.name)
        candidates: list[tuple[tuple[int, int, str], BlockState, str]] = []
        if "slab" in cap.capabilities and "double" in cap.property_map().get("type", frozenset()):
            props = before.property_dict()
            props["type"] = "double"
            candidate = BlockState.of(before.name, **props)
            if required_face in self.shape_descriptor(candidate).support_faces:
                candidates.append(((1, 0, candidate.canonical()), candidate, "promote_slab_to_double"))
        intent = BlockIntent(families=tuple(sorted(cap.families)), required_capabilities=frozenset({"full_cube", "solid_support"}))
        try:
            full = self.resolve_intent(intent)
        except SpecError:
            full = None
        if full is not None and required_face in self.shape_descriptor(full).support_faces:
            candidates.append(((2, 1, full.canonical()), full, "replace_with_same_family_full_support"))
        if not candidates:
            return None
        _cost, chosen, mode = min(candidates, key=lambda item: item[0])
        model.set(support_pos[0], support_pos[1], support_pos[2], cell.role, chosen, cell.module)
        return {"position": list(support_pos), "requiredFace": required_face, "mode": mode, "before": before.canonical(), "after": chosen.canonical()}

    def _validate_attachments(self, model: VoxelModel, issues: list[str]) -> tuple[int, list[dict]]:
        checked = 0
        repairs: list[dict] = []
        for pos, cell in sorted(list(model.cells.items())):
            if "attachment_sensitive" not in self.capability(cell.state.name).capabilities:
                continue
            props = self.normalize_defaults(cell.state).property_dict()
            x, y, z = pos
            if cell.state.name == "minecraft:lantern":
                hanging = props["hanging"] == "true"
                support_pos = (x, y + 1, z) if hanging else (x, y - 1, z)
                support_face = "down" if hanging else "up"
                if not self._supports_face(model, support_pos, support_face):
                    issues.append(f"lantern at {pos} lacks Minecraft-style {support_face} support at {support_pos}")
                checked += 1
            elif cell.state.name == "minecraft:lightning_rod":
                facing = props["facing"]
                if facing in CARDINALS:
                    dx, dy, dz = CARDINALS[OPPOSITE[facing]]
                    support_pos = (x + dx, y + dy, z + dz)
                    support_face = facing
                elif facing == "up":
                    support_pos = (x, y - 1, z)
                    support_face = "up"
                else:
                    support_pos = (x, y + 1, z)
                    support_face = "down"
                if not self._supports_face(model, support_pos, support_face):
                    repair = self._promote_roof_support(model, support_pos, support_face)
                    if repair is not None:
                        repairs.append(repair)
                if not self._supports_face(model, support_pos, support_face):
                    issues.append(f"lightning rod at {pos} lacks Minecraft-style {support_face} support at {support_pos}")
                checked += 1
        return checked, repairs

    def _semantic_reresolve(self, source: VoxelModel) -> tuple[VoxelModel, int, int]:
        realized = VoxelModel()
        changed_names = 0
        normalized_states = 0
        for pos, cell in sorted(source.cells.items()):
            if self.intent_provider is None:
                self.validate_state(cell.state)
                intent = self.intent_from_cell(cell)
            else:
                intent = self.intent_provider(cell)
            state = self.resolve_intent(intent)
            if state.name != cell.state.name:
                changed_names += 1
            if state != cell.state:
                normalized_states += 1
            realized.set(pos[0], pos[1], pos[2], cell.role, state, cell.module)
        return realized, changed_names, normalized_states

    def adapt(self, compiled: CompiledAsset) -> tuple[CompiledAsset, dict]:
        issues: list[str] = []
        state_errors: list[str] = []
        try:
            model, semantic_name_changes, default_state_changes = self._semantic_reresolve(compiled.model)
        except SpecError as exc:
            raise SpecError(f"Minecraft semantic lowering failed: {exc}") from exc

        connectivity_changes = 0
        stair_shape_changes = 0
        for pos, cell in sorted(list(model.cells.items())):
            try:
                connected = self._connective_state(model, pos, cell)
                realized = self._derive_stair_state(model, pos, Cell(cell.role, connected, cell.module))
                self.validate_state(realized)
            except SpecError as exc:
                state_errors.append(f"{pos}: {exc}")
                continue
            if connected != cell.state:
                connectivity_changes += 1
            if realized.property_dict().get("shape") != connected.property_dict().get("shape"):
                stair_shape_changes += 1
            if realized != cell.state:
                model.set(pos[0], pos[1], pos[2], cell.role, realized, cell.module)

        issues.extend(state_errors)
        if not state_errors:
            door_pairs = self._validate_doors(model, issues)
            attachment_checks, support_repairs = self._validate_attachments(model, issues)
        else:
            door_pairs = 0
            attachment_checks = 0
            support_repairs = []

        palette = sorted({cell.state.name for cell in model.cells.values()})
        block_entity_blocks = sum(1 for cell in model.cells.values() if "block_entity" in self.capability(cell.state.name).capabilities)
        shape_counts = Counter(self.shape_descriptor(cell.state).shape_class for cell in model.cells.values())
        partial_collision_blocks = sum(1 for cell in model.cells.values() if self.shape_descriptor(cell.state).partial_collision)
        explicit_support_blocks = sum(1 for cell in model.cells.values() if self.shape_descriptor(cell.state).support_faces)

        report = {
            "adapterVersion": "minecraft-adapter-0.3",
            "target": "minecraft-java-1.21.1",
            "passed": not issues,
            "issues": issues,
            "registeredPaletteSize": len(self.registry),
            "realizedPaletteSize": len(palette),
            "realizedBlocks": palette,
            "semanticReResolution": {
                "cellCount": len(model.cells),
                "concreteNameChanges": semantic_name_changes,
                "defaultStateNormalizations": default_state_changes,
                "resolverUsedConcreteBlockNames": False,
                "sourceConcreteNamesReadForIntent": self.intent_provider is None,
                "intentProfile": self.intent_profile_name,
            },
            "neighborSensitiveStateChanges": connectivity_changes + stair_shape_changes,
            "connectivityStateChanges": connectivity_changes,
            "stairShapeStateChanges": stair_shape_changes,
            "validatedDoorPairs": door_pairs,
            "validatedAttachmentBlocks": attachment_checks,
            "supportRepairs": support_repairs,
            "supportRepairPolicy": "roof_attachment_minimum_edit_only_v0.2",
            "shapeModel": {
                "kind": "coarse_collision_and_face_support_v0.2",
                "shapeClassCounts": dict(sorted(shape_counts.items())),
                "partialCollisionBlockCount": partial_collision_blocks,
                "explicitSupportBlockCount": explicit_support_blocks,
            },
            "blockEntityBlockCount": block_entity_blocks,
            "blockEntityPolicy": "default_empty_runtime_state_only_v0.3",
            "semanticIntentResolverAvailable": True,
            "architectureStillEmitsConcreteStates": True,
            "adapterIsExportAuthority": True,
        }
        if issues:
            raise SpecError("Minecraft adapter rejected compiled asset: " + "; ".join(issues[:8]))
        return CompiledAsset(compiled.summary, model), report
