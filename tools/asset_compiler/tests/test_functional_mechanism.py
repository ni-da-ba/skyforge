from __future__ import annotations

import copy
import gzip
import hashlib
import json
import subprocess
import sys
import tempfile
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
        self.mech002_spec_path = ASSET_COMPILER / "specimens" / "mech_002_waterwheel_airflow_bench.json"
        self.ledger_path = ROOT / "docs" / "agent-state" / "COMPILER_INTEGRATION_CAPABILITIES.json"
        self.spec = json.loads(self.spec_path.read_text(encoding="utf-8"))
        self.mech002_spec = json.loads(self.mech002_spec_path.read_text(encoding="utf-8"))
        self.ledger = json.loads(self.ledger_path.read_text(encoding="utf-8"))

    def compile(self) -> dict:
        return compile_functional_mechanism(self.spec, self.ledger)

    def compile_mech002(self) -> dict:
        return compile_functional_mechanism(self.mech002_spec, self.ledger)

    def test_semantic_spec_contains_no_concrete_create_resource_ids(self) -> None:
        self.assertNotIn("create:", self.spec_path.read_text(encoding="utf-8"))
        mech002_text = self.mech002_spec_path.read_text(encoding="utf-8")
        self.assertNotIn("create:", mech002_text)
        self.assertNotIn("minecraft:water", mech002_text)

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


    def test_mech002_compiles_qualified_environmental_source_exactly(self) -> None:
        first = self.compile_mech002()
        second = self.compile_mech002()
        self.assertEqual(first, second)
        self.assertEqual(
            first["digestSha256"],
            "5e78c14a2632d75255e18c06da261b8365a9ffe5623c0c371b7b54184cdc697d",
        )
        self.assertEqual(first["compilerVersion"], "mech-0.2-fixed-world")
        self.assertEqual(first["envelope"]["size"], [7, 4, 5])
        self.assertEqual(len(first["placements"]), 29)
        self.assertEqual(first["requiredPlatformCapability"], "CREATE_WATER_WHEEL_SOURCE_LIFECYCLE")
        self.assertEqual(first["sourcePolicy"], "qualified_environmental_source_candidate_not_geography_canon")
        by_id = {placement["id"]: placement for placement in first["placements"]}
        self.assertEqual(
            by_id["source"]["blockState"],
            {"name": "create:water_wheel", "properties": {"facing": "east"}},
        )
        self.assertEqual(
            by_id["source_flow_0"]["blockState"],
            {"name": "minecraft:water", "properties": {"level": "8"}},
        )
        self.assertEqual(first["environmentalEnvelope"]["disableCell"], "source_flow_0")
        cell = first["environmentalEnvelope"]["requiredCells"][0]
        self.assertEqual(cell["offsetFromSource"], [0, 0, -1])
        self.assertEqual(cell["expectedFlowVector"], [0.0, -1.0, 0.0])
        self.assertEqual(first["runtimeExpectations"]["active"]["endpointSpeed"], -8.0)

    def test_mech002_structure_export_is_deterministic(self) -> None:
        encoded = encode_mechanism_structure_nbt(self.compile_mech002())
        self.assertEqual(
            hashlib.sha256(encoded).hexdigest(),
            "834322cc5fcdc40ba1c7e9a6d51bf6091a66e34edb4187f8cf9f7242eef864a2",
        )
        raw = gzip.decompress(encoded)
        self.assertIn(b"create:water_wheel", raw)
        self.assertIn(b"minecraft:water", raw)
        self.assertIn(b"create:shaft", raw)
        self.assertIn(b"create:encased_fan", raw)

    def test_mech002_platform_authority_and_environment_fail_closed(self) -> None:
        ledger = copy.deepcopy(self.ledger)
        capability = ledger["capabilities"]["CREATE_WATER_WHEEL_SOURCE_LIFECYCLE"]
        capability["production_authority_for_agents"]["C"] = False
        with self.assertRaisesRegex(SpecError, "Agent C lacks platform authority"):
            compile_functional_mechanism(self.mech002_spec, ledger)

        spec = copy.deepcopy(self.mech002_spec)
        spec["mechanism"]["sourceEnvironmentRole"] = "generic_water"
        with self.assertRaisesRegex(SpecError, "bounded_falling_water"):
            compile_functional_mechanism(spec, self.ledger)

    def test_mech002_environmental_envelope_rejects_tampering(self) -> None:
        plan = self.compile_mech002()
        bad_offset = copy.deepcopy(plan)
        bad_offset["environmentalEnvelope"]["requiredCells"][0]["offsetFromSource"] = [0, 0, 1]
        with self.assertRaisesRegex(SpecError, "offsetFromSource does not match"):
            compiled_asset_for_mechanism_structure(bad_offset)

        bad_control = copy.deepcopy(plan)
        bad_control["environmentalEnvelope"]["disableCell"] = "relay_0"
        with self.assertRaisesRegex(SpecError, "disableCell must reference a required cell"):
            compiled_asset_for_mechanism_structure(bad_control)

    def test_cli_derives_structure_name_from_spec_filename(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            renamed_spec = temp / "bounded_fixture_name.json"
            renamed_spec.write_text(self.spec_path.read_text(encoding="utf-8"), encoding="utf-8")
            output_dir = temp / "out"
            completed = subprocess.run(
                [
                    sys.executable,
                    str(ASSET_COMPILER / "compile_mechanism.py"),
                    str(renamed_spec),
                    "--capability-ledger",
                    str(self.ledger_path),
                    "--out",
                    str(output_dir),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            summary = json.loads(completed.stdout)
            expected_structure = output_dir / "bounded_fixture_name.nbt"
            self.assertEqual(Path(summary["minecraftStructure"]), expected_structure)
            self.assertTrue(expected_structure.is_file())
            self.assertFalse((output_dir / "mech_001_airflow_bench.nbt").exists())

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

    def test_boolean_integer_fields_fail_closed(self) -> None:
        spec = copy.deepcopy(self.spec)
        spec["seed"] = True
        with self.assertRaisesRegex(SpecError, "integer seed is required"):
            compile_functional_mechanism(spec, self.ledger)

        ledger = copy.deepcopy(self.ledger)
        ledger["capabilities"]["CREATE_KINETIC_NETWORK_LIFECYCLE"]["latest_accepted_evidence"]["workflow_run"] = True
        with self.assertRaisesRegex(SpecError, "workflow_run is missing"):
            compile_functional_mechanism(self.spec, ledger)

    def test_structure_export_rejects_valid_shape_with_stale_digest(self) -> None:
        plan = self.compile()
        plan["placements"][0]["blockState"] = {"name": "minecraft:cobblestone"}
        with self.assertRaisesRegex(SpecError, "digest mismatch"):
            compiled_asset_for_mechanism_structure(plan)

    def test_structure_export_requires_nonempty_resource_and_role(self) -> None:
        plan = self.compile()
        empty_resource = copy.deepcopy(plan)
        empty_resource["placements"][0]["blockState"]["name"] = ""
        with self.assertRaisesRegex(SpecError, "missing blockState.name"):
            compiled_asset_for_mechanism_structure(empty_resource)

        bad_role = copy.deepcopy(plan)
        bad_role["placements"][0]["mechanicalRole"] = 7
        with self.assertRaisesRegex(SpecError, "mechanicalRole must be a non-empty string"):
            compiled_asset_for_mechanism_structure(bad_role)


if __name__ == "__main__":
    unittest.main()
