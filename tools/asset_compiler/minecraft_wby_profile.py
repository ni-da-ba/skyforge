from __future__ import annotations

import json
from dataclasses import replace
from pathlib import Path

from minecraft_adapter import BlockCapability, BlockIntent, MinecraftAdapter, vanilla_1_21_1_registry
from minecraft_guild_profile import guild_v014_intent
from model import Cell, SpecError

_PROFILE_NAME = "guild-v0.14-wby-c1-create"
_CATALOG = Path(__file__).with_name("minecraft_data") / "wby_c1_create_6_0_10_capabilities.json"
_ALLOWED_CATALOG_STATUSES = frozenset({"active", "cataloged"})


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


def _load_catalog() -> dict:
    try:
        document = json.loads(_CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SpecError(f"could not read WBY capability catalog {_CATALOG}: {exc}") from exc

    blocks = document.get("blocks")
    if not isinstance(blocks, list):
        raise SpecError("WBY capability catalog must contain a blocks list")

    seen: set[str] = set()
    for raw in blocks:
        if not isinstance(raw, dict):
            raise SpecError(f"invalid WBY capability catalog entry: {raw!r}")
        status = raw.get("status")
        if status not in _ALLOWED_CATALOG_STATUSES:
            raise SpecError(
                f"WBY capability catalog entry {raw.get('name')!r} has unsupported status {status!r}; "
                f"expected one of {sorted(_ALLOWED_CATALOG_STATUSES)}"
            )
        cap = _capability_from_catalog(raw)
        if cap.name in seen:
            raise SpecError(f"duplicate WBY capability catalog entry {cap.name}")
        seen.add(cap.name)

        property_map = cap.property_map()
        default_map = cap.default_map()
        unknown_defaults = sorted(set(default_map) - set(property_map))
        if unknown_defaults:
            raise SpecError(
                f"WBY capability catalog entry {cap.name} has defaults for undeclared properties "
                f"{unknown_defaults}"
            )
        invalid_defaults = sorted(
            key for key, value in default_map.items() if value not in property_map[key]
        )
        if invalid_defaults:
            raise SpecError(
                f"WBY capability catalog entry {cap.name} has defaults outside property domains "
                f"{invalid_defaults}"
            )

    return document


def wby_c1_create_registry() -> dict[str, BlockCapability]:
    """Compose the active WBY C1 overlay over the vanilla Guild registry.

    Catalog membership and automatic placement authority are deliberately separate. Every catalog
    entry is parsed and validated, but only entries carrying ``status: active`` enter the resolver
    registry. ``cataloged`` entries therefore cannot become selectable merely because their
    capabilities happen to match a future intent.
    """
    document = _load_catalog()
    registry = vanilla_1_21_1_registry()
    for raw in document["blocks"]:
        if raw["status"] != "active":
            continue
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
