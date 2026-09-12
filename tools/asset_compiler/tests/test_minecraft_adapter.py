from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from minecraft_adapter import BlockIntent, MinecraftAdapter
from model import BlockState, CompiledAsset, SpecError, VoxelModel

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"


class MinecraftAdapterTests(unittest.TestCase):
    def test_illegal_property_fails_closed(self):
        adapter = MinecraftAdapter()
        with self.assertRaises(SpecError):
            adapter.validate_state(BlockState.of("minecraft:stone_bricks", axis="x"))

    def test_semantic_intent_resolves_by_capability_not_name_guess(self):
        adapter = MinecraftAdapter()
        state = adapter.resolve_intent(
            BlockIntent(
                families=("dark_timber",),
                required_capabilities=frozenset({"axis_orientable"}),
                properties=(("axis", "x"),),
            )
        )
        self.assertEqual(state.name, "minecraft:dark_oak_log")
        self.assertEqual(state.property_dict()["axis"], "x")

    def test_neighbor_sensitive_panes_are_realized_explicitly(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(0, 0, 0, "window", BlockState.of("minecraft:glass_pane"))
        model.set(1, 0, 0, "window", BlockState.of("minecraft:glass_pane"))
        compiled = CompiledAsset(
            {"validation": {"passed": True}, "layout": {"bounds": {"min": [0, 0, 0], "max": [1, 0, 0], "size": [2, 1, 1]}}},
            model,
        )
        realized, report = adapter.adapt(compiled)
        self.assertTrue(report["passed"])
        self.assertGreater(report["neighborSensitiveStateChanges"], 0)
        self.assertEqual(realized.model.cells[(0, 0, 0)].state.property_dict()["east"], "true")
        self.assertEqual(realized.model.cells[(1, 0, 0)].state.property_dict()["west"], "true")

    def test_v014_palette_states_support_and_door_pairs_pass_adapter(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        compiled = compile_guild_branch_first_principles_detail(spec)
        realized, report = MinecraftAdapter().adapt(compiled)
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(len(realized.model.cells), len(compiled.model.cells))
        self.assertGreater(report["validatedDoorPairs"], 0)
        self.assertGreater(report["validatedAttachmentBlocks"], 0)
        self.assertGreater(report["neighborSensitiveStateChanges"], 0)
        self.assertEqual(report["adapterVersion"], "minecraft-adapter-0.1")


if __name__ == "__main__":
    unittest.main()
