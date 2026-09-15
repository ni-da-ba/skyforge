from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_adapter import BlockCapability, vanilla_1_21_1_registry
from minecraft_structural_detail_adapter import StructuralDetailMinecraftAdapter
from model import BlockState, CompiledAsset, VoxelModel

BOOLS = frozenset({"false", "true"})


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


def _adapter() -> StructuralDetailMinecraftAdapter:
    registry = vanilla_1_21_1_registry()
    registry["example:iron_bars"] = _bars_capability()
    return StructuralDetailMinecraftAdapter(registry=registry)


class MinecraftStructuralDetailAdapterTests(unittest.TestCase):
    def test_bars_connect_to_bars_and_solid_support_but_not_glazing(self):
        adapter = _adapter()
        model = VoxelModel()
        model.set(0, 0, 0, "railing", BlockState.of("example:iron_bars"))
        model.set(1, 0, 0, "railing", BlockState.of("example:iron_bars"))
        model.set(-1, 0, 0, "wall", BlockState.of("minecraft:stone_bricks"))
        model.set(0, 0, -1, "window", BlockState.of("minecraft:glass_pane"))
        compiled = CompiledAsset(
            {
                "validation": {"passed": True},
                "layout": {"bounds": {"min": [-1, 0, -1], "max": [1, 0, 0], "size": [3, 1, 2]}},
            },
            model,
        )

        realized, report = adapter.adapt(compiled)
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
        from model import Cell

        cell = Cell("railing", BlockState.of("example:iron_bars"), "synthetic_railing")
        intent = adapter.intent_from_cell(cell)
        self.assertIn("bars", intent.required_capabilities)
        self.assertNotIn("pane", intent.required_capabilities)
        self.assertNotIn("fence", intent.required_capabilities)
        self.assertEqual(adapter.resolve_intent(intent).name, "example:iron_bars")


if __name__ == "__main__":
    unittest.main()
