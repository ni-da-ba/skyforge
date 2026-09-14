from __future__ import annotations

import json
from dataclasses import replace
from pathlib import Path

from minecraft_adapter import BlockCapability, BlockIntent, MinecraftAdapter, vanilla_1_21_1_registry
from minecraft_guild_profile import guild_v014_intent
from model import Cell, SpecError

_PROFILE_NAME = "guild-v0.14-wby-c1-create"
_CATALOG = Path(__file__).with_name("minecraft_data") / "wby_c1_create_6_0_10_capabilities.json"


def _capability_from_catalog(entry: dict) -> BlockCapability:
    try:
        name = str(entry["name"])
        families = frozenset(str(value) for value in entry["families"])
        capabilities = frozenset(str(value) for value in entry["capabilities"])
        properties = tuple(
            sorted(
                (
                    str(key),
                    frozenset(str(value).lower() for value in values),
                )
                for key, values in entry.get("properties", {}).items()
            )
        )
        defaults = tuple(
            sorted((str(key), str(value).lower()) for key, value in entry.get("defaults", {}).items())
        )
    except (KeyError, TypeError) as exc:
        raise SpecError(f"invalid WBY capability catalog entry: {entry!r}") from exc
    return BlockCapability(
        name=name,
        families=families,
        capabilities=capabilities,
        properties=properties,
        defaults=defaults,
    )


def wby_c1_create_registry() -> dict[str, BlockCapability]:
    """Load the bounded WBY C1 overlay and compose it over the vanilla Guild registry.

    The catalog is data-driven so additional audited blocks do not require resolver edits. Admission
    to the catalog is still conservative: stateful, functional, block-entity, aircraft, and addon
    content must carry an explicit verified contract before it is added.
    """
    try:
        document = json.loads(_CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SpecError(f"could not read WBY capability catalog {_CATALOG}: {exc}") from exc

    registry = vanilla_1_21_1_registry()
    for raw in document.get("blocks", []):
        cap = _capability_from_catalog(raw)
        if cap.name in registry:
            raise SpecError(f"WBY capability catalog may not replace baseline registry entry {cap.name}")
        registry[cap.name] = cap
    return registry


def _prepend_preferences(intent: BlockIntent, *names: str) -> BlockIntent:
    ordered: list[str] = []
    for name in (*names, *intent.preferred_blocks):
        if name not in ordered:
            ordered.append(name)
    return replace(intent, preferred_blocks=tuple(ordered))


def wby_c1_target_intent(intent: BlockIntent) -> BlockIntent:
    """Add WBY target preferences using only target-neutral semantic intent."""
    family_set = set(intent.families)

    if {"warm_hardware", "brass_surrogate"}.issubset(family_set):
        return _prepend_preferences(intent, "create:brass_casing")

    if {"hardware", "masonry"}.issubset(family_set):
        return _prepend_preferences(intent, "create:andesite_casing")

    return intent


def guild_v014_wby_intent(cell: Cell) -> BlockIntent:
    """Apply the same WBY preference policy to direct Guild adapter passes."""
    return wby_c1_target_intent(guild_v014_intent(cell))


class WbyC1MinecraftAdapter(MinecraftAdapter):
    """Minecraft resolver that overlays WBY choices at the concrete target boundary."""

    def resolve_intent(self, intent: BlockIntent):
        return super().resolve_intent(wby_c1_target_intent(intent))


def guild_v014_wby_adapter() -> MinecraftAdapter:
    return WbyC1MinecraftAdapter(
        registry=wby_c1_create_registry(),
        intent_provider=guild_v014_wby_intent,
        intent_profile_name=_PROFILE_NAME,
    )
