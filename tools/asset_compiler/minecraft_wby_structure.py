from __future__ import annotations

from pathlib import Path

from guild_realization_intent import project_guild_v014_realization_ir
from minecraft_block_entities import block_entity_payload_for
from minecraft_structure import MINECRAFT_1_21_1_DATA_VERSION, write_structure_nbt
from minecraft_target_lowering import realize_intent_model
from minecraft_wby_profile import guild_v014_wby_adapter
from model import CompiledAsset, SpecError


def _block_entity_payload_count(compiled: CompiledAsset) -> int:
    return sum(
        1
        for cell in compiled.model.cells.values()
        if block_entity_payload_for(cell.state) is not None
    )


def realize_wby_target(compiled: CompiledAsset) -> tuple[CompiledAsset, dict]:
    """Lower current Guild architecture through the explicit WBY Minecraft profile."""
    if str(compiled.summary.get("compilerVersion", "")) != "0.14-first-principles-detail":
        raise SpecError(
            "WBY structure realization is currently bounded to Guild compiler "
            "0.14-first-principles-detail"
        )

    intent_model = project_guild_v014_realization_ir(compiled)
    realized, report = realize_intent_model(
        compiled.summary,
        intent_model,
        guild_v014_wby_adapter(),
    )

    expected = int(report.get("blockEntityBlockCount", 0))
    encoded = _block_entity_payload_count(realized)
    if expected != encoded:
        raise SpecError(
            "WBY Minecraft target block-entity coverage mismatch: "
            f"adapter reports {expected} block-entity blocks but exporter has payload policy for {encoded}"
        )

    out_report = dict(report)
    out_report["minecraftProfile"] = "wby-c1-create"
    out_report["blockEntityNbtCount"] = encoded
    out_report["blockEntityPolicy"] = "explicit_empty_container_nbt_v0.4"
    return realized, out_report


def write_wby_structure_nbt(
    path: Path,
    compiled: CompiledAsset,
    *,
    data_version: int = MINECRAFT_1_21_1_DATA_VERSION,
) -> dict:
    """Write one WBY-aware structure template without altering the default vanilla exporter."""
    realized, report = realize_wby_target(compiled)
    write_structure_nbt(
        path,
        realized,
        data_version=data_version,
        use_adapter=False,
    )
    return report
