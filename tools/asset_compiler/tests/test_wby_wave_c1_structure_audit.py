from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

AUDIT = ROOT / "minecraft_data" / "wby_wave_c1_structure_audit.json"
LOCK = ROOT.parents[1] / "skyforge-neoforge-1211" / "wave-c1-mods.properties"


def _load_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


class WbyWaveC1StructureAuditTests(unittest.TestCase):
    def test_audit_exactly_covers_locked_wave_c1_mods(self):
        doc = json.loads(AUDIT.read_text(encoding="utf-8"))
        pins = _load_properties(LOCK)
        mods = {entry["id"]: entry for entry in doc["mods"]}
        expected = {
            key[:-8]
            for key in pins
            if key.endswith(".version") and key not in {"minecraft.version", "neoforge.version"}
        }
        self.assertEqual(set(mods), expected)
        self.assertEqual(doc["minecraftVersion"], pins["minecraft.version"])
        self.assertEqual(doc["neoforgeVersion"], pins["neoforge.version"])

        for mod_id, entry in mods.items():
            self.assertEqual(entry["version"], pins[f"{mod_id}.version"])
            self.assertEqual(entry["coordinate"], pins[f"{mod_id}.coordinate"])

    def test_only_create_has_automatic_structure_authority(self):
        doc = json.loads(AUDIT.read_text(encoding="utf-8"))
        owners = {
            entry["id"]
            for entry in doc["mods"]
            if entry["automaticStructureResources"]
        }
        self.assertEqual(owners, {"create"})
        self.assertEqual(doc["policy"]["activeCatalogOwners"], ["create"])

        create = next(entry for entry in doc["mods"] if entry["id"] == "create")
        self.assertEqual(create["disposition"], "architectural-catalog")
        self.assertEqual(
            create["capabilityCatalog"],
            "minecraft_data/wby_c1_create_6_0_10_capabilities.json",
        )

    def test_aircraft_owned_mods_cannot_claim_structure_resources(self):
        doc = json.loads(AUDIT.read_text(encoding="utf-8"))
        mods = {entry["id"]: entry for entry in doc["mods"]}
        aircraft_owned = set(doc["policy"]["aircraftOwnedMods"])
        self.assertEqual(aircraft_owned, {"sable", "aeronautics", "createpropulsion"})
        for mod_id in aircraft_owned:
            self.assertEqual(mods[mod_id]["disposition"], "aircraft-owned-deferred")
            self.assertFalse(mods[mod_id]["automaticStructureResources"])
            self.assertNotIn("capabilityCatalog", mods[mod_id])

    def test_dispositions_are_closed_set_and_deferred_mods_have_no_catalog(self):
        doc = json.loads(AUDIT.read_text(encoding="utf-8"))
        allowed = {
            "architectural-catalog",
            "deferred-no-structure-contract",
            "deferred-functional-content",
            "aircraft-owned-deferred",
        }
        for entry in doc["mods"]:
            self.assertIn(entry["disposition"], allowed)
            if entry["id"] != "create":
                self.assertFalse(entry["automaticStructureResources"])
                self.assertNotIn("capabilityCatalog", entry)


if __name__ == "__main__":
    unittest.main()
