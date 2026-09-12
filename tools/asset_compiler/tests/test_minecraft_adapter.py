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
from model import BlockState, Cell, CompiledAsset, SpecError, VoxelModel

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
                families=("dark_timber", "structural_timber"),
                required_capabilities=frozenset({"axis_orientable"}),
                properties=(("axis", "x"),),
            )
        )
        self.assertEqual(state.name, "minecraft:dark_oak_log")
        self.assertEqual(state.property_dict()["axis"], "x")

    def test_default_properties_become_explicit(self):
        adapter = MinecraftAdapter()
        state = adapter.normalize_defaults(BlockState.of("minecraft:deepslate_tile_slab"))
        self.assertEqual(state.property_dict()["type"], "bottom")
        self.assertEqual(state.property_dict()["waterlogged"], "false")

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
        self.assertGreater(report["connectivityStateChanges"], 0)
        self.assertEqual(realized.model.cells[(0, 0, 0)].state.property_dict()["east"], "true")
        self.assertEqual(realized.model.cells[(1, 0, 0)].state.property_dict()["west"], "true")

    def test_stair_corner_shape_is_derived_from_neighbor_geometry(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        model.set(
            0, 0, 0, "roof",
            BlockState.of("minecraft:deepslate_tile_stairs", facing="north", half="bottom"),
        )
        model.set(
            0, 0, -1, "roof",
            BlockState.of("minecraft:deepslate_tile_stairs", facing="west", half="bottom"),
        )
        compiled = CompiledAsset(
            {"validation": {"passed": True}, "layout": {"bounds": {"min": [0, 0, -1], "max": [0, 0, 0], "size": [1, 1, 2]}}},
            model,
        )
        realized, report = adapter.adapt(compiled)
        self.assertEqual(realized.model.cells[(0, 0, 0)].state.property_dict()["shape"], "outer_left")
        self.assertGreaterEqual(report["stairShapeStateChanges"], 1)

    def test_shape_model_distinguishes_full_partial_and_support_faces(self):
        adapter = MinecraftAdapter()
        cube = adapter.shape_descriptor(BlockState.of("minecraft:stone_bricks"))
        bottom_slab = adapter.shape_descriptor(BlockState.of("minecraft:dark_oak_slab", type="bottom"))
        carpet = adapter.shape_descriptor(BlockState.of("minecraft:blue_carpet"))
        self.assertEqual(cube.collision_volume_fraction, 1.0)
        self.assertFalse(cube.partial_collision)
        self.assertIn("up", cube.support_faces)
        self.assertTrue(bottom_slab.partial_collision)
        self.assertIn("down", bottom_slab.support_faces)
        self.assertNotIn("up", bottom_slab.support_faces)
        self.assertLess(carpet.collision_volume_fraction, bottom_slab.collision_volume_fraction)

    def test_hanging_lantern_requires_actual_support_face(self):
        adapter = MinecraftAdapter()
        model = VoxelModel()
        # A top slab has no sturdy downward face at the block boundary in this target model.
        model.set(0, 1, 0, "structural_frame", BlockState.of("minecraft:dark_oak_slab", type="top"))
        model.set(0, 0, 0, "lighting", BlockState.of("minecraft:lantern", hanging=True))
        compiled = CompiledAsset(
            {"validation": {"passed": True}, "layout": {"bounds": {"min": [0, 0, 0], "max": [0, 1, 0], "size": [1, 2, 1]}}},
            model,
        )
        with self.assertRaises(SpecError):
            adapter.adapt(compiled)

    def test_semantic_reresolution_does_not_need_concrete_name(self):
        adapter = MinecraftAdapter()
        cell = Cell("structural_frame", BlockState.of("minecraft:dark_oak_log", axis="x"), "beam")
        intent = adapter.intent_from_cell(cell)
        self.assertEqual(intent.preferred_blocks, ())
        resolved = adapter.resolve_intent(intent)
        self.assertEqual(resolved.name, "minecraft:dark_oak_log")
        self.assertEqual(resolved.property_dict()["axis"], "x")

    def test_v014_palette_states_support_and_door_pairs_pass_adapter(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        compiled = compile_guild_branch_first_principles_detail(spec)
        realized, report = MinecraftAdapter().adapt(compiled)
        self.assertTrue(report["passed"], report["issues"])
        self.assertEqual(len(realized.model.cells), len(compiled.model.cells))
        self.assertGreater(report["validatedDoorPairs"], 0)
        self.assertGreater(report["validatedAttachmentBlocks"], 0)
        self.assertGreater(report["neighborSensitiveStateChanges"], 0)
        self.assertGreater(report["shapeModel"]["partialCollisionBlockCount"], 0)
        self.assertEqual(report["semanticReResolution"]["concreteNameChanges"], 0)
        self.assertFalse(report["semanticReResolution"]["resolverUsedConcreteBlockNames"])
        self.assertTrue(report["adapterIsExportAuthority"])
        self.assertEqual(report["adapterVersion"], "minecraft-adapter-0.2")


if __name__ == "__main__":
    unittest.main()
