from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from guild_realization_intent import project_guild_v014_realization_ir
from minecraft_guild_profile import guild_v014_adapter
from minecraft_target_lowering import realize_intent_model
from model import BlockState, CompiledAsset, VoxelModel

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"


class AssetRealizationIrTests(unittest.TestCase):
    def compiled_v14(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        return compile_guild_branch_first_principles_detail(copy.deepcopy(spec))

    def test_projection_contains_no_concrete_target_resource_names(self):
        compiled = self.compiled_v14()
        intent_model = project_guild_v014_realization_ir(compiled)
        self.assertEqual(len(compiled.model.cells), len(intent_model.cells))
        serialized = json.dumps(intent_model.to_dict(), sort_keys=True)
        self.assertNotIn("minecraft:", serialized)
        self.assertNotIn("ignored:", serialized)

    def test_projection_survives_erased_architecture_resource_names(self):
        compiled = self.compiled_v14()
        stripped = VoxelModel()
        for pos, cell in compiled.model.cells.items():
            stripped.set(
                pos[0], pos[1], pos[2], cell.role,
                BlockState("ignored:architecture_preview_only", cell.state.properties),
                cell.module,
            )
        stripped_asset = CompiledAsset(compiled.summary, stripped)
        intent_model = project_guild_v014_realization_ir(stripped_asset)
        realized, report = realize_intent_model(compiled.summary, intent_model, guild_v014_adapter())
        geometry = report["realizedGeometry"]
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(report["adapterVersion"], "minecraft-adapter-0.5")
        self.assertEqual(report["stateAdapterVersion"], "minecraft-adapter-0.3")
        self.assertTrue(geometry["passed"], geometry["issues"])
        self.assertEqual(len(realized.model.cells), geometry["targetCellCountAfter"])
        self.assertEqual(geometry["targetCellCountBefore"], len(compiled.model.cells))
        self.assertGreater(geometry["apertureReturnRepairCount"], 0)
        self.assertGreater(geometry["operationalClearanceRepairCount"], 0)
        self.assertGreater(geometry["isolatedDetailRepairCount"], 0)
        self.assertFalse(report["realizationIntentIr"]["containsConcreteResourceNames"])
        self.assertFalse(report["realizationIntentIr"]["architectureConcreteResourceNamesConsumed"])
        self.assertEqual(len(intent_model.cells), report["realizationIntentIr"]["cellCount"])


if __name__ == "__main__":
    unittest.main()
