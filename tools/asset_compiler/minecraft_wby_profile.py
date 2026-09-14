from __future__ import annotations

from dataclasses import replace

from minecraft_adapter import BlockCapability, BlockIntent, MinecraftAdapter, vanilla_1_21_1_registry
from minecraft_guild_profile import guild_v014_intent
from model import Cell

_PROFILE_NAME = "guild-v0.14-wby-c1-create"


def _simple_full_cube(name: str, families: tuple[str, ...]) -> BlockCapability:
    return BlockCapability(
        name=name,
        families=frozenset(families),
        capabilities=frozenset({"full_cube", "solid_support"}),
    )


def wby_c1_create_registry() -> dict[str, BlockCapability]:
    """Bounded WBY C1 overlay on the ordinary Java 1.21.1 Guild registry.

    The overlay is intentionally small. Only simple Create casing blocks whose state-free
    blockstate form has been audited are admitted here. Stateful panes, machinery, block entities,
    aircraft parts, and addon-specific detail remain outside this automatic realization profile
    until their exact runtime state/capability contract is independently verified.
    """
    registry = vanilla_1_21_1_registry()
    additions = (
        _simple_full_cube("create:andesite_casing", ("hardware", "masonry")),
        _simple_full_cube("create:brass_casing", ("warm_hardware", "brass_surrogate")),
        _simple_full_cube("create:copper_casing", ("hardware", "copper_detail")),
    )
    registry.update((entry.name, entry) for entry in additions)
    return registry


def _prepend_preferences(intent: BlockIntent, *names: str) -> BlockIntent:
    ordered: list[str] = []
    for name in (*names, *intent.preferred_blocks):
        if name not in ordered:
            ordered.append(name)
    return replace(intent, preferred_blocks=tuple(ordered))


def guild_v014_wby_intent(cell: Cell) -> BlockIntent:
    """Apply WBY realization preferences without changing architectural semantics."""
    intent = guild_v014_intent(cell)
    family_set = set(intent.families)

    if cell.role == "hardware" and {"warm_hardware", "brass_surrogate"}.issubset(family_set):
        return _prepend_preferences(intent, "create:brass_casing")

    if cell.role == "hardware" and {"hardware", "masonry"}.issubset(family_set):
        return _prepend_preferences(intent, "create:andesite_casing")

    return intent


def guild_v014_wby_adapter() -> MinecraftAdapter:
    return MinecraftAdapter(
        registry=wby_c1_create_registry(),
        intent_provider=guild_v014_wby_intent,
        intent_profile_name=_PROFILE_NAME,
    )
