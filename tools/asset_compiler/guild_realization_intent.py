from __future__ import annotations

from asset_realization_ir import RealizationIntent, RealizationIntentModel, project_voxel_intents
from minecraft_guild_profile import guild_v014_intent
from model import Cell, CompiledAsset, SpecError


def guild_v014_realization_intent(cell: Cell) -> RealizationIntent:
    """Project one v0.14 Guild architectural cell into target-independent realization intent.

    The existing Guild profile is the semantic authority for this bounded specimen. Its Minecraft
    `BlockIntent` carrier is immediately copied into the backend-neutral IR; the source block resource
    name is never retained.
    """
    bounded = guild_v014_intent(cell)
    return RealizationIntent(
        families=bounded.families,
        required_capabilities=bounded.required_capabilities,
        properties=bounded.properties,
        preferred_targets=bounded.preferred_blocks,
    )


def project_guild_v014_realization_ir(compiled: CompiledAsset) -> RealizationIntentModel:
    if str(compiled.summary.get("compilerVersion", "")) != "0.14-first-principles-detail":
        raise SpecError("Guild realization IR projection currently supports only v0.14")
    return project_voxel_intents(compiled.model, guild_v014_realization_intent)
