from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from sibling_roi import flatten_config, module_family

BASE_SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"
SIBLING_SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_sibling_working_heavy.json"


class GuildBranchV14SiblingRoiTests(unittest.TestCase):
    def _load(self, path: Path):
        return json.loads(path.read_text(encoding="utf-8"))

    def test_working_heavy_sibling_reuses_compiler_and_solves_distinct_geometry(self):
        base_spec = self._load(BASE_SPEC)
        sibling_spec = self._load(SIBLING_SPEC)
        base = compile_guild_branch_first_principles_detail(base_spec)
        sibling = compile_guild_branch_first_principles_detail(sibling_spec)

        self.assertTrue(sibling.summary["validation"]["passed"], sibling.summary["validation"]["issues"])
        self.assertEqual(base.summary["compilerVersion"], sibling.summary["compilerVersion"])
        self.assertNotEqual(base.summary["digestSha256"], sibling.summary["digestSha256"])

        chosen = sibling.summary["layout"]["firstPrinciples"]["optimization"]["chosen"]
        self.assertEqual(chosen["bayCount"], 5)
        self.assertEqual(chosen["bayWidth"], 5)
        self.assertEqual(chosen["hallDepth"], 18)
        self.assertEqual(chosen["workingWingWidth"], 9)

    def test_sibling_has_high_configuration_and_module_reuse(self):
        base_spec = self._load(BASE_SPEC)
        sibling_spec = self._load(SIBLING_SPEC)
        base_fields = flatten_config(base_spec)
        sibling_fields = flatten_config(sibling_spec)
        union = set(base_fields) | set(sibling_fields)
        unchanged = {p for p in set(base_fields) & set(sibling_fields) if base_fields[p] == sibling_fields[p]}
        changed = sorted(p for p in set(base_fields) & set(sibling_fields) if base_fields[p] != sibling_fields[p])

        self.assertGreaterEqual(len(unchanged) / len(union), 0.90)
        self.assertEqual(
            changed,
            [
                "assetId",
                "family.scale",
                "program.maxFootprintArea",
                "program.publicHallMinArea",
                "program.repairMinArea",
                "program.warehouseMinArea",
                "seed",
            ],
        )

        base = compile_guild_branch_first_principles_detail(base_spec)
        sibling = compile_guild_branch_first_principles_detail(sibling_spec)
        base_modules = set(base.summary["moduleBlockCounts"])
        sibling_modules = set(sibling.summary["moduleBlockCounts"])
        # Raw module IDs include deterministic per-window instance suffixes, so a larger five-bay
        # sibling legitimately creates new IDs without creating new grammar vocabulary. Measure both.
        self.assertGreaterEqual(len(base_modules & sibling_modules) / len(sibling_modules), 0.85)
        base_families = {module_family(name) for name in base_modules}
        sibling_families = {module_family(name) for name in sibling_modules}
        self.assertGreaterEqual(len(base_families & sibling_families) / len(sibling_families), 0.95)

    def test_sibling_preserves_accepted_neutral_and_usability_invariants(self):
        sibling = compile_guild_branch_first_principles_detail(self._load(SIBLING_SPEC))
        modules = {cell.module for cell in sibling.model.cells.values()}
        names = {cell.state.name for cell in sibling.model.cells.values()}
        for absent in ("fp_working_partition", "fp_freight_transom", "fp_repair_transom", "fp14_public_rug"):
            self.assertNotIn(absent, modules)
        for absent in ("minecraft:blue_wool", "minecraft:yellow_terracotta", "minecraft:blue_carpet"):
            self.assertNotIn(absent, names)

        shelves = [pos for pos, cell in sibling.model.cells.items() if cell.module == "fp_repair_service_shelf"]
        self.assertGreater(len(shelves), 0)
        for x, y, z in shelves:
            self.assertIn((x - 1, y, z), sibling.model.cells, (x, y, z))

        fp = sibling.summary["layout"]["firstPrinciples"]
        self.assertEqual(fp["spaceGraph"]["entranceConnectedFraction"], 1.0)
        self.assertTrue(fp["spaceGraph"]["entranceSeesService"])
        self.assertEqual(fp["structuralGraph"]["unsupportedTargets"], [])


if __name__ == "__main__":
    unittest.main()
