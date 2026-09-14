from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_guild_profile import guild_v014_adapter
from minecraft_wby_profile import (
    guild_v014_wby_adapter,
    guild_v014_wby_intent,
    wby_c1_create_registry,
)
from model import BlockState, Cell

CATALOG = ROOT / "minecraft_data" / "wby_c1_create_6_0_10_capabilities.json"


class MinecraftWbyProfileTests(unittest.TestCase):
    def test_wby_registry_is_vanilla_superset_with_bounded_create_catalog(self):
        registry = wby_c1_create_registry()
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        self.assertEqual(doc["createVersion"], "6.0.10+mc1.21.1")
        self.assertEqual(doc["createCoordinate"], "maven.modrinth:LNytGWDc:UjX6dr61")

        declared = {entry["name"]: entry for entry in doc["blocks"]}
        self.assertEqual(
            set(declared),
            {"create:andesite_casing", "create:brass_casing", "create:copper_casing"},
        )
        for name, entry in declared.items():
            cap = registry[name]
            self.assertEqual(sorted(cap.families), sorted(entry["families"]))
            self.assertEqual(sorted(cap.capabilities), sorted(entry["capabilities"]))
            self.assertEqual(cap.property_map(), entry["properties"])
            self.assertEqual(cap.default_map(), entry["defaults"])

        self.assertIn("minecraft:stone_bricks", registry)
        self.assertIn("minecraft:glass_pane", registry)

    def test_warm_guild_hardware_uses_brass_casing_only_in_wby_profile(self):
        cell = Cell("hardware", BlockState.of("example:ignored"), "service_marker")
        intent = guild_v014_wby_intent(cell)
        self.assertEqual(intent.preferred_blocks[0], "create:brass_casing")
        self.assertEqual(guild_v014_wby_adapter().resolve_intent(intent).name, "create:brass_casing")

        vanilla_intent = guild_v014_adapter().intent_provider(cell)
        self.assertEqual(guild_v014_adapter().resolve_intent(vanilla_intent).name, "minecraft:yellow_terracotta")

    def test_generic_guild_hardware_uses_andesite_casing_and_copper_remains_catalog_only(self):
        cell = Cell("hardware", BlockState.of("example:ignored"), "working_face_fastener")
        intent = guild_v014_wby_intent(cell)
        realized = guild_v014_wby_adapter().resolve_intent(intent)
        self.assertEqual(intent.preferred_blocks[0], "create:andesite_casing")
        self.assertEqual(realized.name, "create:andesite_casing")
        self.assertNotEqual(realized.name, "create:copper_casing")

    def test_resource_name_does_not_enter_wby_semantic_intent(self):
        left = Cell("hardware", BlockState.of("minecraft:yellow_terracotta"), "service_marker")
        right = Cell("hardware", BlockState.of("other_backend:anything"), "service_marker")
        self.assertEqual(guild_v014_wby_intent(left), guild_v014_wby_intent(right))


if __name__ == "__main__":
    unittest.main()
