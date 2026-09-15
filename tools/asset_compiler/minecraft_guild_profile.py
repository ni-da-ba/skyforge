from __future__ import annotations

from minecraft_adapter import BlockIntent, MinecraftAdapter
from model import Cell, SpecError

_PROFILE_NAME = "guild-v0.14-semantic-material-profile"


def _props(cell: Cell, *keys: str) -> tuple[tuple[str, str], ...]:
    source = cell.state.property_dict()
    return tuple(sorted((key, source[key]) for key in keys if key in source))


def _slab_props(cell: Cell, default_type: str) -> tuple[tuple[str, str], ...]:
    source = cell.state.property_dict()
    return (("type", source.get("type", default_type)),)


def guild_v014_intent(cell: Cell) -> BlockIntent:
    """Lower Guild architecture without consulting the source Minecraft resource name.

    `cell.state` is used only as a carrier for target-neutral geometric orientation values such as
    axis/facing/half/hinge/type/hanging. Its resource location is deliberately ignored.
    """
    role = cell.role
    module = cell.module or ""

    if role == "foundation":
        if "plinth" in module or "sill" in module:
            return BlockIntent(("masonry",), frozenset({"slab"}), _slab_props(cell, "bottom"))
        return BlockIntent(("foundation", "masonry"), frozenset({"full_cube", "solid_support"}))

    if role == "floor":
        return BlockIntent(("floor", "timber"), frozenset({"full_cube", "solid_support"}))

    if role == "wall_infill":
        if "controlled_history" in module:
            return BlockIntent(("history_masonry", "masonry"), frozenset({"full_cube", "solid_support"}))
        return BlockIntent(("wall_infill", "pale_masonry"), frozenset({"full_cube", "solid_support"}))

    if role == "structural_frame":
        if "bracket" in module:
            return BlockIntent(("dark_timber",), frozenset({"fence", "neighbor_sensitive", "thin"}))
        if any(token in module for token in ("canopy", "hood", "_cap", "eave_fascia", "service_shelf")):
            default_type = "top" if any(token in module for token in ("hood", "_cap", "service_shelf")) else "bottom"
            return BlockIntent(("dark_timber",), frozenset({"slab"}), _slab_props(cell, default_type))
        return BlockIntent(
            ("dark_timber", "structural_timber"),
            frozenset({"full_cube", "solid_support", "axis_orientable"}),
            _props(cell, "axis"),
        )

    if role == "roof":
        if "weather_skin" in module:
            return BlockIntent(("dark_roof", "masonry"), frozenset({"full_cube", "solid_support"}))
        if module.endswith("_eave"):
            return BlockIntent(
                ("dark_roof",),
                frozenset({"stair", "directional", "neighbor_sensitive"}),
                _props(cell, "facing", "half"),
            )
        if "ridge" in module:
            return BlockIntent(("dark_roof",), frozenset({"slab"}), _slab_props(cell, "bottom"))
        raise SpecError(f"Guild Minecraft profile has no roof intent for module {module!r}")

    if role == "window":
        return BlockIntent(("glazing",), frozenset({"pane", "neighbor_sensitive", "thin"}))

    if role == "door":
        public = module == "fp_public_entrance"
        return BlockIntent(
            ("public_door", "timber_door") if public else ("working_door", "dark_timber"),
            frozenset({"door", "directional", "two_block"}),
            _props(cell, "facing", "half", "hinge"),
        )

    if role == "lighting":
        return BlockIntent(("lighting",), frozenset({"light", "attachment_sensitive"}), _props(cell, "hanging"))

    if role == "institutional_accent":
        return BlockIntent(("guild_blue", "textile"), frozenset({"full_cube", "solid_support"}))

    if role == "hardware":
        if "compact_roof_signal" in module:
            return BlockIntent(("hardware", "metal"), frozenset({"directional", "attachment_sensitive"}), _props(cell, "facing"))
        if any(token in module for token in ("service_marker", "guild_identity", "service_counter_end")):
            return BlockIntent(("warm_hardware", "brass_surrogate"), frozenset({"full_cube", "solid_support"}))
        if any(token in module for token in ("manifest", "tool_panel")):
            return BlockIntent(("dark_timber",), frozenset({"full_cube", "solid_support"}))
        return BlockIntent(("hardware", "masonry"), frozenset({"full_cube", "solid_support"}))

    if role in {"board", "counter"}:
        return BlockIntent(("dark_timber",), frozenset({"full_cube", "solid_support"}))

    if role in {"counter_detail", "records_detail"}:
        return BlockIntent(("dark_timber",), frozenset({"slab"}), _slab_props(cell, "bottom"))

    if role == "desk":
        return BlockIntent(("dark_timber",), frozenset({"slab"}), _slab_props(cell, "top"))

    if role == "seating":
        return BlockIntent(("seating", "timber"), frozenset({"slab"}), _slab_props(cell, "bottom"))

    if role == "seating_detail":
        return BlockIntent(("dark_timber",), frozenset({"fence", "neighbor_sensitive", "thin"}))

    if role == "records":
        return BlockIntent(("records", "timber"), frozenset({"full_cube", "solid_support"}))

    if role == "storage":
        return BlockIntent(
            ("storage", "freight_storage"),
            frozenset({"full_cube", "block_entity", "container", "directional"}),
        )

    if role == "tool_storage":
        return BlockIntent(("storage", "tool_storage"), frozenset({"block_entity", "container", "directional"}))

    if role == "workbench":
        return BlockIntent(("workbench",), frozenset({"full_cube", "solid_support"}))

    if role == "floor_detail_passable":
        return BlockIntent(("guild_blue", "textile"), frozenset({"carpet", "thin"}))

    raise SpecError(f"Guild Minecraft profile has no semantic material intent for role={role!r} module={module!r}")


def guild_v014_adapter() -> MinecraftAdapter:
    return MinecraftAdapter(intent_provider=guild_v014_intent, intent_profile_name=_PROFILE_NAME)
