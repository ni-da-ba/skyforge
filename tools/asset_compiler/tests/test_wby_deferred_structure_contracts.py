from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_wby_profile import wby_c1_create_registry

CATALOG = ROOT / "minecraft_data" / "wby_c1_create_6_0_10_capabilities.json"
BOOLS = {"false", "true"}
CARDINALS = {"north", "east", "south", "west"}


class WbyDeferredStructureContractTests(unittest.TestCase):
    def _contracts(self) -> dict[str, dict]:
        doc = json.loads(CATALOG.read_text(encoding="utf-8"))
        self.assertEqual(
            doc["validation"]["deferredStructuralRuntimeObservation"],
            "passed-wave-c1-exact-create-runtime-10-resources",
        )
        contracts = doc["deferredStateContracts"]
        self.assertEqual(len(contracts), 10)
        return {entry["name"]: entry for entry in contracts}

    def test_exact_runtime_observed_resource_set_is_frozen_and_non_selectable(self):
        contracts = self._contracts()
        self.assertEqual(
            set(contracts),
            {
                "create:andesite_bars",
                "create:brass_bars",
                "create:copper_bars",
                "create:andesite_ladder",
                "create:brass_ladder",
                "create:copper_ladder",
                "create:andesite_scaffolding",
                "create:brass_scaffolding",
                "create:copper_scaffolding",
                "create:metal_girder",
            },
        )
        registry = wby_c1_create_registry()
        for name, contract in contracts.items():
            self.assertNotIn(name, registry)
            self.assertTrue(contract["semanticGate"].strip())

    def test_bars_match_live_iron_bars_contract(self):
        contracts = self._contracts()
        for material in ("andesite", "brass", "copper"):
            contract = contracts[f"create:{material}_bars"]
            self.assertEqual(contract["runtimeClass"], "net.minecraft.world.level.block.IronBarsBlock")
            self.assertEqual(set(contract["properties"]), {*CARDINALS, "waterlogged"})
            for values in contract["properties"].values():
                self.assertEqual(set(values), BOOLS)
            self.assertEqual(set(contract["defaults"].values()), {"false"})

    def test_ladders_match_live_metal_ladder_contract(self):
        contracts = self._contracts()
        for material in ("andesite", "brass", "copper"):
            contract = contracts[f"create:{material}_ladder"]
            self.assertEqual(
                contract["runtimeClass"],
                "com.simibubi.create.content.decoration.MetalLadderBlock",
            )
            self.assertEqual(set(contract["properties"]["facing"]), CARDINALS)
            self.assertEqual(set(contract["properties"]["waterlogged"]), BOOLS)
            self.assertEqual(contract["defaults"], {"facing": "north", "waterlogged": "false"})

    def test_scaffolding_matches_live_metal_scaffolding_contract(self):
        contracts = self._contracts()
        for material in ("andesite", "brass", "copper"):
            contract = contracts[f"create:{material}_scaffolding"]
            self.assertEqual(
                contract["runtimeClass"],
                "com.simibubi.create.content.decoration.MetalScaffoldingBlock",
            )
            self.assertEqual(set(contract["properties"]["bottom"]), BOOLS)
            self.assertEqual(set(contract["properties"]["waterlogged"]), BOOLS)
            self.assertEqual(set(contract["properties"]["distance"]), {str(i) for i in range(8)})
            self.assertEqual(
                contract["defaults"],
                {"bottom": "false", "distance": "7", "waterlogged": "false"},
            )

    def test_girder_matches_live_girder_contract(self):
        contract = self._contracts()["create:metal_girder"]
        self.assertEqual(
            contract["runtimeClass"],
            "com.simibubi.create.content.decoration.girder.GirderBlock",
        )
        self.assertEqual(set(contract["properties"]["axis"]), {"x", "y", "z"})
        for key in ("bottom", "top", "waterlogged", "x", "z"):
            self.assertEqual(set(contract["properties"][key]), BOOLS)
        self.assertEqual(
            contract["defaults"],
            {
                "axis": "y",
                "bottom": "false",
                "top": "false",
                "waterlogged": "false",
                "x": "false",
                "z": "false",
            },
        )


if __name__ == "__main__":
    unittest.main()
