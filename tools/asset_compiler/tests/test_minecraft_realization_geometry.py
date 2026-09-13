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
from minecraft_adapter import MinecraftAdapter
from minecraft_guild_profile import guild_v014_adapter
from minecraft_realization_geometry import enforce_realized_geometry_correctness
from minecraft_target_lowering import realize_intent_model
from model import BlockState, CompiledAsset, SpecError, VoxelModel

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"


def asset(model: VoxelModel) -> CompiledAsset:
    return CompiledAsset(
        {
            "validation": {"passed": True},
            "layout": {"bounds": {"min": [-8, 0, -8], "max": [32, 16, 32], "size": [41, 17, 41]}},
        },
        model,
    )


class MinecraftRealizationGeometryTests(unittest.TestCase):
    def test_recessed_window_adds_jamb_backed_return_and_reconnects_pane(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(3, 3, 1, "window", BlockState.of("minecraft:glass_pane"), "fp_north_window_pane")
        model.set(4, 3, 0, "structural_frame", BlockState.of("minecraft:dark_oak_log", axis="y"), "fp_north_window_jamb_0")
        realized, report = enforce_realized_geometry_correctness(asset(model), adapter)
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(report["apertureReturnRepairCount"], 1)
        added = realized.model.cells[(4, 3, 1)]
        self.assertEqual(added.state.name, "minecraft:dark_oak_log")
        self.assertEqual(added.state.property_dict()["axis"], "y")
        self.assertEqual(added.module, "minecraft_adapter_aperture_return")
        pane = realized.model.cells[(3, 3, 1)].state.property_dict()
        self.assertEqual(pane["east"], "true")

    def test_freight_side_margin_removes_only_working_partition_column(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        for z, hinge in ((7, "left"), (8, "right")):
            model.set(23, 2, z, "door", BlockState.of("minecraft:dark_oak_door", facing="east", half="lower", hinge=hinge), "fp_freight_door")
            model.set(23, 3, z, "door", BlockState.of("minecraft:dark_oak_door", facing="east", half="upper", hinge=hinge), "fp_freight_door")
        for y in (2, 3, 4):
            model.set(22, y, 6, "wall_infill", BlockState.of("minecraft:calcite"), "fp_working_partition")
        realized, report = enforce_realized_geometry_correctness(asset(model), adapter)
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(report["operationalClearanceRepairCount"], 3)
        for y in (2, 3, 4):
            self.assertNotIn((22, y, 6), realized.model.cells)
        self.assertIn((23, 2, 7), realized.model.cells)
        self.assertIn((23, 2, 8), realized.model.cells)

    def test_nonrepairable_direct_portal_blocker_fails_closed(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(23, 2, 7, "door", BlockState.of("minecraft:dark_oak_door", facing="east", half="lower", hinge="left"), "fp_freight_door")
        model.set(23, 3, 7, "door", BlockState.of("minecraft:dark_oak_door", facing="east", half="upper", hinge="left"), "fp_freight_door")
        model.set(22, 2, 7, "storage", BlockState.of("minecraft:barrel"), "fixture")
        with self.assertRaises(SpecError):
            enforce_realized_geometry_correctness(asset(model), adapter)

    def test_unsupported_waiting_back_fence_is_omitted_not_left_floating(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(2, 1, 9, "floor", BlockState.of("minecraft:spruce_planks"), "fixture_floor")
        model.set(2, 2, 9, "seating", BlockState.of("minecraft:spruce_slab", type="bottom"), "fp_waiting_bench")
        model.set(2, 3, 9, "seating_detail", BlockState.of("minecraft:dark_oak_fence"), "fp14_waiting_back")
        realized, report = enforce_realized_geometry_correctness(asset(model), adapter)
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(report["isolatedDetailRepairCount"], 1)
        self.assertNotIn((2, 3, 9), realized.model.cells)
        self.assertIn((2, 2, 9), realized.model.cells)

    def test_ambiguous_isolated_structural_slab_fails_closed(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(0, 5, 0, "structural_frame", BlockState.of("minecraft:dark_oak_slab", type="bottom"), "mystery_detail")
        with self.assertRaises(SpecError):
            enforce_realized_geometry_correctness(asset(model), adapter)

    def test_canonical_v014_repairs_human_gate_defects_without_touching_architecture_digest(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        compiled = compile_guild_branch_first_principles_detail(copy.deepcopy(spec))
        digest = compiled.summary["digestSha256"]
        intents = project_guild_v014_realization_ir(compiled)
        realized, report = realize_intent_model(compiled.summary, intents, guild_v014_adapter())
        geometry = report["realizedGeometry"]
        self.assertTrue(report["passed"], report["issues"])
        self.assertTrue(geometry["passed"], geometry["issues"])
        self.assertEqual(realized.summary["digestSha256"], digest)
        self.assertEqual(report["adapterVersion"], "minecraft-adapter-0.5")
        self.assertGreaterEqual(geometry["apertureReturnRepairCount"], 20)
        self.assertEqual(geometry["operationalClearanceRepairCount"], 3)
        self.assertEqual(geometry["isolatedDetailRepairCount"], 6)
        self.assertGreater(geometry["partialDetailContactChecks"], 0)
        self.assertGreater(geometry["workingPortalChecks"], 0)


if __name__ == "__main__":
    unittest.main()
