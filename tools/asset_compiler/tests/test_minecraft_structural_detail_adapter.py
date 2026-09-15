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
DISTANCES = frozenset(str(i) for i in range(8))


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


def _scaffolding_capability() -> BlockCapability:
    return BlockCapability(
        name="example:metal_scaffolding",
        families=frozenset({"access", "industrial_metal"}),
        capabilities=frozenset({"scaffolding", "climbable", "neighbor_sensitive"}),
        properties=(
            ("bottom", BOOLS),
            ("distance", DISTANCES),
            ("waterlogged", BOOLS),
        ),
        defaults=(
            ("bottom", "false"),
            ("distance", "7"),
            ("waterlogged", "false"),
        ),
    )


def _adapter() -> StructuralDetailMinecraftAdapter:
    registry = vanilla_1_21_1_registry()
    registry["example:iron_bars"] = _bars_capability()
    registry["example:metal_ladder"] = _ladder_capability()
    registry["example:metal_scaffolding"] = _scaffolding_capability()
    return StructuralDetailMinecraftAdapter(registry=registry)


def _compiled(model: VoxelModel) -> CompiledAsset:
    return CompiledAsset(
        {
            "validation": {"passed": True},
            "layout": {
                "bounds": {
                    "min": [-2, -1, -2],
                    "max": [3, 3, 2],
                    "size": [6, 5, 5],
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

    def test_scaffolding_solves_vertical_and_horizontal_support_distance(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(0, -1, 0, "foundation", BlockState.of("minecraft:stone_bricks"))
        model.set(0, 0, 0, "access", BlockState.of("example:metal_scaffolding"))
        model.set(0, 1, 0, "access", BlockState.of("example:metal_scaffolding"))
        model.set(1, 0, 0, "access", BlockState.of("example:metal_scaffolding"))
        model.set(2, 0, 0, "access", BlockState.of("example:metal_scaffolding"))

        realized, report = adapter.adapt(_compiled(model))
        states = {
            pos: realized.model.cells[pos].state.property_dict()
            for pos in ((0, 0, 0), (0, 1, 0), (1, 0, 0), (2, 0, 0))
        }
        self.assertEqual(states[(0, 0, 0)]["distance"], "0")
        self.assertEqual(states[(0, 1, 0)]["distance"], "0")
        self.assertEqual(states[(1, 0, 0)]["distance"], "1")
        self.assertEqual(states[(2, 0, 0)]["distance"], "2")
        self.assertEqual(states[(0, 0, 0)]["bottom"], "false")
        self.assertEqual(states[(0, 1, 0)]["bottom"], "false")
        self.assertEqual(states[(1, 0, 0)]["bottom"], "true")
        self.assertEqual(states[(2, 0, 0)]["bottom"], "true")
        self.assertGreaterEqual(report["connectivityStateChanges"], 4)

    def test_scaffolding_derived_state_overrides_legal_authored_state(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(0, -1, 0, "foundation", BlockState.of("minecraft:stone_bricks"))
        model.set(
            0,
            0,
            0,
            "access",
            BlockState.of("example:metal_scaffolding", bottom=True, distance=6),
        )

        realized, _report = adapter.adapt(_compiled(model))
        props = realized.model.cells[(0, 0, 0)].state.property_dict()
        self.assertEqual(props["bottom"], "false")
        self.assertEqual(props["distance"], "0")

    def test_scaffolding_rejects_cluster_without_portable_support_path(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(0, 0, 0, "access", BlockState.of("example:metal_scaffolding"))
        model.set(0, 1, 0, "access", BlockState.of("example:metal_scaffolding"))
        with self.assertRaisesRegex(SpecError, "portable support path"):
            adapter.adapt(_compiled(model))

    def test_scaffolding_has_distinct_partial_neighbor_dependent_shapes(self):
        adapter = _adapter()
        normal = adapter.shape_descriptor(
            BlockState.of("example:metal_scaffolding", bottom=False, distance=0)
        )
        bottom = adapter.shape_descriptor(
            BlockState.of("example:metal_scaffolding", bottom=True, distance=1)
        )
        self.assertEqual(normal.shape_class, "scaffolding")
        self.assertEqual(bottom.shape_class, "scaffolding_bottom")
        for shape in (normal, bottom):
            self.assertTrue(shape.partial_collision)
            self.assertTrue(shape.neighbor_dependent)
            self.assertEqual(shape.support_faces, frozenset())
        self.assertGreater(bottom.collision_volume_fraction, normal.collision_volume_fraction)

    def test_scaffolding_and_climbable_capabilities_survive_semantic_roundtrip(self):
        adapter = _adapter()
        cell = Cell(
            "access",
            BlockState.of("example:metal_scaffolding"),
            "synthetic_scaffold",
        )
        intent = adapter.intent_from_cell(cell)
        self.assertIn("scaffolding", intent.required_capabilities)
        self.assertIn("climbable", intent.required_capabilities)
        self.assertIn("neighbor_sensitive", intent.required_capabilities)
        self.assertNotIn("full_cube", intent.required_capabilities)
        self.assertEqual(adapter.resolve_intent(intent).name, "example:metal_scaffolding")


if __name__ == "__main__":
    unittest.main()
