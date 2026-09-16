from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_adapter import vanilla_1_21_1_registry

MANIFEST = ROOT / "minecraft_data" / "java_1_21_1_guild_capabilities.json"


class MinecraftCapabilityManifestTests(unittest.TestCase):
    def test_manifest_matches_python_capability_registry(self):
        doc = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(doc["target"], "minecraft-java-1.21.1")
        self.assertEqual(doc["dataVersion"], 3955)

        registry = vanilla_1_21_1_registry()
        declared = {entry["name"]: entry for entry in doc["blocks"]}
        self.assertEqual(set(declared), set(registry))

        for name, cap in sorted(registry.items()):
            with self.subTest(block=name):
                expected_properties = {
                    key: sorted(values)
                    for key, values in cap.property_map().items()
                }
                expected_defaults = dict(sorted(cap.default_map().items()))
                self.assertEqual(declared[name]["properties"], expected_properties)
                self.assertEqual(declared[name]["defaults"], expected_defaults)


if __name__ == "__main__":
    unittest.main()
