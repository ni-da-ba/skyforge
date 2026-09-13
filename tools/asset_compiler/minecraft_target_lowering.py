from __future__ import annotations

from asset_realization_ir import RealizationIntentModel
from minecraft_adapter import BlockIntent, MinecraftAdapter
from minecraft_realization_geometry import enforce_realized_geometry_correctness
from model import CompiledAsset, VoxelModel


def realize_intent_model(
    summary: dict,
    intent_model: RealizationIntentModel,
    adapter: MinecraftAdapter,
) -> tuple[CompiledAsset, dict]:
    """Lower a resource-name-free realization IR into a validated Minecraft model.

    The first pass resolves every semantic intent into a concrete target state. The ordinary adapter
    pass then owns target defaults, neighbor topology, support checks and bounded repairs. The v0.5
    realized-geometry pass then checks the target medium itself for aperture closure, operational
    clearance and visually isolated partial detail. Any concrete resource names observed after the IR
    boundary were created by Minecraft target resolution, not supplied by the architectural compiler.
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
    realized, geometry_report = enforce_realized_geometry_correctness(realized, adapter)
    report = dict(report)
    report["stateAdapterVersion"] = report.get("adapterVersion")
    report["adapterVersion"] = "minecraft-adapter-0.5"
    report["realizedGeometry"] = geometry_report
    report["realizationIntentIr"] = {
        "cellCount": len(intent_model.cells),
        "containsConcreteResourceNames": False,
        "architectureConcreteResourceNamesConsumed": False,
        "targetConcreteStatesCreatedBeforeTopologyPass": True,
    }
    return realized, report
