from __future__ import annotations

import json
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from minecraft_wby_structure import realize_wby_target, write_wby_structure_nbt

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"


class MinecraftWbyStructureTests(unittest.TestCase):
    def _compile(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        return compile_guild_branch_first_principles_detail(spec)

    def test_wby_target_realization_uses_only_active_create_detail_after_resource_free_ir_boundary(self):
        realized, report = realize_wby_target(self._compile())
        names = Counter(cell.state.name for cell in realized.model.cells.values())

        # The canonical v0.14 specimen contains warm/brass hardware but currently no generic
        # hardware+masonry cells. Generic andesite-casing selection is covered independently by
        # the profile-level semantic-intent test rather than inventing fixture geometry here.
        self.assertGreater(names["create:brass_casing"], 0)
        self.assertEqual(names["create:copper_casing"], 0)
        self.assertEqual(names["create:industrial_iron_block"], 0)
        self.assertEqual(names["create:weathered_iron_block"], 0)
        self.assertEqual(names["create:framed_glass_pane"], 0)
        self.assertEqual(names["create:industrial_iron_window_pane"], 0)
        self.assertEqual(names["create:ornate_iron_window_pane"], 0)
        self.assertGreater(names["minecraft:glass_pane"], 0)
        self.assertEqual(report["minecraftProfile"], "wby-c1-create")
        self.assertFalse(report["realizationIntentIr"]["containsConcreteResourceNames"])
        self.assertFalse(report["realizationIntentIr"]["architectureConcreteResourceNamesConsumed"])

    def test_wby_structure_export_is_deterministic(self):
        compiled = self._compile()
        with tempfile.TemporaryDirectory() as tmp:
            first = Path(tmp) / "first.nbt"
            second = Path(tmp) / "second.nbt"
            report_a = write_wby_structure_nbt(first, compiled)
            report_b = write_wby_structure_nbt(second, compiled)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(report_a, report_b)
            self.assertGreater(first.stat().st_size, 100)


if __name__ == "__main__":
    unittest.main()
