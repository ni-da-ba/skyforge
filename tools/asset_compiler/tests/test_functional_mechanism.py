from __future__ import annotations

import copy
import gzip
import hashlib
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSET_COMPILER = ROOT / "tools" / "asset_compiler"
if str(ASSET_COMPILER) not in sys.path:
    sys.path.insert(0, str(ASSET_COMPILER))

from functional_mechanism import compile_functional_mechanism
from functional_mechanism_structure import compiled_asset_for_mechanism_structure, encode_mechanism_structure_nbt
from model import SpecError


class FunctionalMechanismCompilerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.spec_path = ASSET_COMPILER / "specimens" / "mech_001_airflow_bench.json"
        self.ledger_path = ROOT / "docs" / "agent-state" / "COMPILER_INTEGRATION_CAPABILITIES.json"
        self.spec = json.loads(self.spec_path.read_text(encoding="utf-8"))
        self.ledger = json.loads(self.ledger_path.read_text(encoding="utf-8"))

    def compile(self) -> dict:
        return compile_functional_mechanism(self.spec, self.ledger)

    def test_semantic_spec_contains_no_concrete_create_resource_ids(self) -> None:
        self.assertNotIn("create:", self.spec_path.read_text(encoding="utf-8"))

    def test_compiler_emits_deterministic_bounded_plan(self) -> None:
        first = self.compile()
        second = self.compile()
        self.assertEqual(first, second)
        self.assertEqual(
            first["digestSha256"],
            "da905478f5f3e42e197bd0ef1a38baba848c8ce65c2a911d7153743597098ce1",
        )
        self.assertEqual(first["envelope"]["size"], [6, 2, 3])
        self.assertEqual(len(first["placements"]), 12)
        self.assertEqual(first["connectivity"]["severNode"], "relay_0")
        self.assertEqual(first["clearanceCells"], [[3, 1, 1], [4, 1, 1], [5, 1, 1]])

    def test_target_lowering_is_exact_and_source_policy_is_not_gameplay_canon(self) -> None:
        plan = self.compile()
        by_id = {placement["id"]: placement for placement in plan["placements"]}
        self.assertEqual(
            by_id["source"]["blockState"],
            {"name": "create:creative_motor", "properties": {"facing": "east"}},
        )
        self.assertEqual(
            by_id["relay_0"]["blockState"],
            {"name": "create:shaft", "properties": {"axis": "x"}},
        )
        self.assertEqual(
            by_id["endpoint"]["blockState"],
            {"name": "create:encased_fan", "properties": {"facing": "east"}},
        )
        self.assertEqual(plan["sourcePolicy"], "qualified_test_source_not_gameplay_canon")

    def test_support_and_clearance_do_not_overlap_functional_cells(self) -> None:
        plan = self.compile()
        occupied = {tuple(p["pos"]) for p in plan["placements"]}
        for requirement in plan["supportRequirements"]:
            self.assertIn(tuple(requirement["supportBelow"]), occupied)
        self.assertTrue(occupied.isdisjoint(map(tuple, plan["clearanceCells"])))


    def test_structure_export_is_deterministic_and_contains_exact_target_states(self) -> None:
        encoded = encode_mechanism_structure_nbt(self.compile())
        self.assertEqual(
            hashlib.sha256(encoded).hexdigest(),
            "dee1563daa925034f13a9481744bbf2da9b0bb36937ae8cb6a7192a1b45e9b84",
        )
        raw = gzip.decompress(encoded)
        self.assertIn(b"create:creative_motor", raw)
        self.assertIn(b"create:shaft", raw)
        self.assertIn(b"create:encased_fan", raw)
        self.assertIn(b"minecraft:stone_bricks", raw)

    def test_platform_authority_is_required_fail_closed(self) -> None:
        ledger = copy.deepcopy(self.ledger)
        capability = ledger["capabilities"]["CREATE_KINETIC_NETWORK_LIFECYCLE"]
        capability["production_authority_for_agents"]["C"] = False
        with self.assertRaisesRegex(SpecError, "Agent C lacks platform authority"):
            compile_functional_mechanism(self.spec, ledger)

        ledger = copy.deepcopy(self.ledger)
        ledger["capabilities"]["CREATE_KINETIC_NETWORK_LIFECYCLE"]["status"] = "qualification_pending"
        with self.assertRaisesRegex(SpecError, "is not accepted"):
            compile_functional_mechanism(self.spec, ledger)

    def test_unsupported_geometry_fails_closed(self) -> None:
        spec = copy.deepcopy(self.spec)
        spec["mechanism"]["orientation"] = "north"
        with self.assertRaisesRegex(SpecError, "supports only east orientation"):
            compile_functional_mechanism(spec, self.ledger)

        spec = copy.deepcopy(self.spec)
        spec["mechanism"]["transmissionLength"] = 2
        with self.assertRaisesRegex(SpecError, "exactly one relay"):
            compile_functional_mechanism(spec, self.ledger)

    def test_unknown_semantic_vocabulary_fails_before_target_lowering(self) -> None:
        spec = copy.deepcopy(self.spec)
        spec["mechanism"]["waterWheelRadius"] = 2
        with self.assertRaisesRegex(SpecError, "unsupported mechanism fields"):
            compile_functional_mechanism(spec, self.ledger)

    def test_structure_export_rejects_duplicate_identity_and_occupancy(self) -> None:
        plan = self.compile()
        duplicate_id = copy.deepcopy(plan)
        duplicate_id["placements"][1]["id"] = duplicate_id["placements"][0]["id"]
        with self.assertRaisesRegex(SpecError, "duplicate placement IDs"):
            compiled_asset_for_mechanism_structure(duplicate_id)

        overlapping = copy.deepcopy(plan)
        overlapping["placements"][1]["pos"] = overlapping["placements"][0]["pos"]
        with self.assertRaisesRegex(SpecError, "overlapping placements"):
            compiled_asset_for_mechanism_structure(overlapping)

    def test_structure_export_rejects_invalid_envelope_and_graph_references(self) -> None:
        plan = self.compile()
        outside = copy.deepcopy(plan)
        outside["placements"][0]["pos"] = [99, 0, 0]
        with self.assertRaisesRegex(SpecError, "outside mechanism envelope"):
            compiled_asset_for_mechanism_structure(outside)

        bad_graph = copy.deepcopy(plan)
        bad_graph["connectivity"]["nodes"].append("missing_node")
        with self.assertRaisesRegex(SpecError, "unknown placements"):
            compiled_asset_for_mechanism_structure(bad_graph)

    def test_structure_export_rejects_support_and_clearance_tampering(self) -> None:
        plan = self.compile()
        unsupported = copy.deepcopy(plan)
        unsupported["supportRequirements"][0]["supportBelow"] = [5, 0, 2]
        with self.assertRaisesRegex(SpecError, "unoccupied cell"):
            compiled_asset_for_mechanism_structure(unsupported)

        blocked_clearance = copy.deepcopy(plan)
        blocked_clearance["clearanceCells"][0] = [2, 1, 1]
        with self.assertRaisesRegex(SpecError, "overlaps a mechanism placement"):
            compiled_asset_for_mechanism_structure(blocked_clearance)


if __name__ == "__main__":
    unittest.main()
