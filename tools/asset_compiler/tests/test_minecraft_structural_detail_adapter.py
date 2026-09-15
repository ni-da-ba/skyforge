from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_adapter import BlockCapability, vanilla_1_21_1_registry
from minecraft_structural_detail_adapter import StructuralDetailMinecraftAdapter
from model import BlockState, Cell, CompiledAsset, SpecError, VoxelModel

BOOLS = frozenset({"false", "true"})
FACINGS = frozenset({"north", "east", "south", "west"})


def _bars_capability() -> BlockCapability:
    return BlockCapability(
        name="example:iron_bars",
        families=frozenset({"metal", "railing"}),
        capabilities=frozenset({"bars", "neighbor_sensitive", "thin"}),
        properties=tuple(
            sorted(
                (key, BOOLS)
                for key in ("east", "north", "south", "waterlogged", "west")
            )
        ),
        defaults=tuple(
            sorted(
                (key, "false")
                for key in ("east", "north", "south", "waterlogged", "west")
            )
        ),
    )


def _ladder_capability() -> BlockCapability:
    return BlockCapability(
        name="example:metal_ladder",
        families=frozenset({"access", "industrial_metal"}),
        capabilities=frozenset(
            {"ladder", "climbable", "directional", "attachment_sensitive", "thin"}
        ),
        properties=(
            ("facing", FACINGS),
            ("waterlogged", BOOLS),
        ),
        defaults=(
            ("facing", "north"),
            ("waterlogged", "false"),
        ),
    )


def _adapter() -> StructuralDetailMinecraftAdapter:
    registry = vanilla_1_21_1_registry()
    registry["example:iron_bars"] = _bars_capability()
    registry["example:metal_ladder"] = _ladder_capability()
    return StructuralDetailMinecraftAdapter(registry=registry)


def _compiled(model: VoxelModel) -> CompiledAsset:
    return CompiledAsset(
        {
            "validation": {"passed": True},
            "layout": {
                "bounds": {
                    "min": [-1, 0, -1],
                    "max": [1, 0, 1],
                    "size": [3, 1, 3],
                }
            },
        },
        model,
    )


class MinecraftStructuralDetailAdapterTests(unittest.TestCase):
    def test_bars_connect_to_bars_and_solid_support_but_not_glazing(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(0, 0, 0, "railing", BlockState.of("example:iron_bars"))
        model.set(1, 0, 0, "railing", BlockState.of("example:iron_bars"))
        model.set(-1, 0, 0, "wall", BlockState.of("minecraft:stone_bricks"))
        model.set(0, 0, -1, "window", BlockState.of("minecraft:glass_pane"))

        realized, report = adapter.adapt(_compiled(model))
        props = realized.model.cells[(0, 0, 0)].state.property_dict()
        self.assertEqual(props["west"], "true")
        self.assertEqual(props["east"], "true")
        self.assertEqual(props["north"], "false")
        self.assertEqual(props["south"], "false")
        self.assertGreaterEqual(report["connectivityStateChanges"], 1)

    def test_bars_have_distinct_partial_neighbor_dependent_shape(self):
        adapter = _adapter()
        state = BlockState.of("example:iron_bars", east=True, west=True)
        shape = adapter.shape_descriptor(state)
        self.assertEqual(shape.shape_class, "bars")
        self.assertTrue(shape.partial_collision)
        self.assertTrue(shape.neighbor_dependent)
        self.assertEqual(shape.support_faces, frozenset())
        self.assertLess(shape.collision_volume_fraction, 0.5)

    def test_bars_capability_survives_semantic_roundtrip(self):
        adapter = _adapter()
        cell = Cell("railing", BlockState.of("example:iron_bars"), "synthetic_railing")
        intent = adapter.intent_from_cell(cell)
        self.assertIn("bars", intent.required_capabilities)
        self.assertNotIn("pane", intent.required_capabilities)
        self.assertNotIn("fence", intent.required_capabilities)
        self.assertEqual(adapter.resolve_intent(intent).name, "example:iron_bars")

    def test_ladder_requires_backing_support_opposite_its_facing(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(
            0,
            0,
            0,
            "access",
            BlockState.of("example:metal_ladder", facing="north"),
        )
        model.set(0, 0, 1, "wall", BlockState.of("minecraft:stone_bricks"))

        realized, report = adapter.adapt(_compiled(model))
        self.assertEqual(
            realized.model.cells[(0, 0, 0)].state.property_dict()["facing"],
            "north",
        )
        self.assertGreaterEqual(report["validatedAttachmentBlocks"], 1)

    def test_ladder_rejects_missing_backing_support(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(
            0,
            0,
            0,
            "access",
            BlockState.of("example:metal_ladder", facing="north"),
        )
        with self.assertRaisesRegex(SpecError, "ladder at .* backing support"):
            adapter.adapt(_compiled(model))

    def test_ladder_rejects_support_on_wrong_side(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(
            0,
            0,
            0,
            "access",
            BlockState.of("example:metal_ladder", facing="north"),
        )
        model.set(0, 0, -1, "wall", BlockState.of("minecraft:stone_bricks"))
        with self.assertRaisesRegex(SpecError, "backing support"):
            adapter.adapt(_compiled(model))

    def test_ladder_has_distinct_partial_non_neighbor_dependent_shape(self):
        adapter = _adapter()
        shape = adapter.shape_descriptor(BlockState.of("example:metal_ladder"))
        self.assertEqual(shape.shape_class, "ladder")
        self.assertTrue(shape.partial_collision)
        self.assertFalse(shape.neighbor_dependent)
        self.assertEqual(shape.support_faces, frozenset())
        self.assertLess(shape.collision_volume_fraction, 0.25)

    def test_ladder_and_climbable_capabilities_survive_semantic_roundtrip(self):
        adapter = _adapter()
        cell = Cell(
            "access",
            BlockState.of("example:metal_ladder", facing="east"),
            "synthetic_access",
        )
        intent = adapter.intent_from_cell(cell)
        self.assertIn("ladder", intent.required_capabilities)
        self.assertIn("climbable", intent.required_capabilities)
        self.assertIn("attachment_sensitive", intent.required_capabilities)
        self.assertEqual(adapter.resolve_intent(intent).name, "example:metal_ladder")


if __name__ == "__main__":
    unittest.main()
