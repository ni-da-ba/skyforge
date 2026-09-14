from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_guild_profile import guild_v014_adapter
from minecraft_wby_profile import (
    guild_v014_wby_adapter,
    guild_v014_wby_intent,
    wby_c1_create_registry,
)
from model import BlockState, Cell, SpecError

CATALOG = ROOT / "minecraft_data" / "wby_c1_create_6_0_10_capabilities.json"


class MinecraftWbyProfileTests(unittest.TestCase):
    def test_wby_registry_contains_only_active_catalog_entries(self):
        registry = wby_c1_create_registry()
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        self.assertEqual(doc["createVersion"], "6.0.10+mc1.21.1")
        self.assertEqual(doc["createCoordinate"], "maven.modrinth:LNytGWDc:UjX6dr61")
        self.assertEqual(
            doc["validation"]["createSourceCommit"],
            "79b5d3b37e2d1970818dd97ca460b649cd0a456c",
        )

        declared = {entry["name"]: entry for entry in doc["blocks"]}
        active = {name for name, entry in declared.items() if entry["status"] == "active"}
        cataloged = {name for name, entry in declared.items() if entry["status"] == "cataloged"}

        self.assertEqual(active, {"create:andesite_casing", "create:brass_casing"})
        self.assertEqual(
            cataloged,
            {
                "create:copper_casing",
                "create:industrial_iron_block",
                "create:weathered_iron_block",
                "create:framed_glass_pane",
                "create:industrial_iron_window_pane",
                "create:ornate_iron_window_pane",
            },
        )

        for name in active:
            entry = declared[name]
            cap = registry[name]
            self.assertEqual(sorted(cap.families), sorted(entry["families"]))
            self.assertEqual(sorted(cap.capabilities), sorted(entry["capabilities"]))
            self.assertEqual(cap.property_map(), entry["properties"])
            self.assertEqual(cap.default_map(), entry["defaults"])

        for name in cataloged:
            self.assertNotIn(name, registry)

        self.assertIn("minecraft:stone_bricks", registry)
        self.assertIn("minecraft:glass_pane", registry)

    def test_cataloged_stateful_glazing_contract_is_explicit_but_not_selectable(self):
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        declared = {entry["name"]: entry for entry in doc["blocks"]}
        pane = declared["create:framed_glass_pane"]
        self.assertEqual(pane["status"], "cataloged")
        self.assertEqual(
            pane["properties"],
            {
                "east": ["false", "true"],
                "north": ["false", "true"],
                "south": ["false", "true"],
                "waterlogged": ["false", "true"],
                "west": ["false", "true"],
            },
        )
        self.assertEqual(set(pane["defaults"].values()), {"false"})

        cell = Cell("window", BlockState.of("example:ignored"), "fp_public_window")
        intent = guild_v014_wby_intent(cell)
        self.assertEqual(guild_v014_wby_adapter().resolve_intent(intent).name, "minecraft:glass_pane")

    def _write_bad_catalog(self, document: dict, tmp: str) -> Path:
        path = Path(tmp) / "bad.json"
        path.write_text(json.dumps(document), encoding="utf-8")
        return path

    def test_catalog_status_is_mandatory_and_fail_closed(self):
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        doc["blocks"][0]["status"] = "research-only"
        with tempfile.TemporaryDirectory() as tmp:
            with patch("minecraft_wby_profile._CATALOG", self._write_bad_catalog(doc, tmp)):
                with self.assertRaisesRegex(SpecError, "unsupported status"):
                    wby_c1_create_registry()

    def test_duplicate_catalog_resource_names_fail_closed(self):
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        doc["blocks"].append(dict(doc["blocks"][0]))
        with tempfile.TemporaryDirectory() as tmp:
            with patch("minecraft_wby_profile._CATALOG", self._write_bad_catalog(doc, tmp)):
                with self.assertRaisesRegex(SpecError, "duplicate WBY capability"):
                    wby_c1_create_registry()

    def test_catalog_defaults_must_belong_to_declared_property_domains(self):
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        pane = next(entry for entry in doc["blocks"] if entry["name"] == "create:framed_glass_pane")
        pane["defaults"]["east"] = "invalid"
        with tempfile.TemporaryDirectory() as tmp:
            with patch("minecraft_wby_profile._CATALOG", self._write_bad_catalog(doc, tmp)):
                with self.assertRaisesRegex(SpecError, "defaults outside property domains"):
                    wby_c1_create_registry()

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
        self.assertNotIn("create:copper_casing", wby_c1_create_registry())

    def test_resource_name_does_not_enter_wby_semantic_intent(self):
        left = Cell("hardware", BlockState.of("minecraft:yellow_terracotta"), "service_marker")
        right = Cell("hardware", BlockState.of("other_backend:anything"), "service_marker")
        self.assertEqual(guild_v014_wby_intent(left), guild_v014_wby_intent(right))


if __name__ == "__main__":
    unittest.main()
