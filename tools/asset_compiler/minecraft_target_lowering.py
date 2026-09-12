from __future__ import annotations

from asset_realization_ir import RealizationIntentModel
from minecraft_adapter import BlockIntent, MinecraftAdapter
from model import CompiledAsset, VoxelModel


def realize_intent_model(
    summary: dict,
    intent_model: RealizationIntentModel,
    adapter: MinecraftAdapter,
) -> tuple[CompiledAsset, dict]:
    """Lower a resource-name-free realization IR into a validated Minecraft model.

    The first pass resolves every semantic intent into a concrete target state. The ordinary adapter
    pass then owns target defaults, neighbor topology, support checks and bounded repairs. Any concrete
    resource names observed in that second pass were created by Minecraft target resolution itself,
    not supplied by the architectural compiler.
    """
    initial = VoxelModel()
    for (x, y, z), cell in sorted(intent_model.cells.items()):
        intent = cell.intent
        state = adapter.resolve_intent(
            BlockIntent(
                families=intent.families,
                required_capabilities=intent.required_capabilities,
                properties=intent.properties,
                preferred_blocks=intent.preferred_targets,
            )
        )
        initial.set(x, y, z, cell.role, state, cell.module)

    realized, report = adapter.adapt(CompiledAsset(summary, initial))
    report = dict(report)
    report["realizationIntentIr"] = {
        "cellCount": len(intent_model.cells),
        "containsConcreteResourceNames": False,
        "architectureConcreteResourceNamesConsumed": False,
        "targetConcreteStatesCreatedBeforeTopologyPass": True,
    }
    return realized, report
