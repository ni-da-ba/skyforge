from __future__ import annotations

import copy
import unittest

from powertrain_lowering import PowertrainLoweringError, lower_powertrain


class PowertrainLoweringTests(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_9_probe_manifest",
            "digestSha256": "manifest",
            "placements": [
                {"lattice": [2, 2, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks"},
                {"lattice": [0, 3, 0], "kind": "propeller_bearing", "resourceId": "aeronautics:propeller_bearing"},
                {"lattice": [-1, 3, 0], "kind": "propeller_hub", "resourceId": "minecraft:spruce_planks"},
            ],
        }
        self.glue = {
            "schemaVersion": "aircraft-glue-encoding-ir-0.11",
            "digestSha256": "glue",
            "readiness": {"physicsAssemblyProbeReady": True},
            "encodingPolicy": {"maxSelectionDimensionBlocks": 24},
            "metrics": {"mainBodyPlacementCount": 114, "nestedChildPlacementCount": 9},
            "glueDomains": [
                {"selectionCellBounds": {"min": [0, 1, 0], "max": [18, 3, 0]}}
            ],
        }
        self.profile = {
            "schemaVersion": "aircraft-powertrain-profile-0.12",
            "profileId": "test",
            "portableEngineSourceRpm": 32,
            "governorTargetRpm": 128,
            "laterGovernorRpmPoints": [160, 192, 224, 256],
            "existingPropellerBearingCoordinate": [0, 3, 0],
            "placements": [
                {"role": "engine_port", "mode": "add", "lattice": [2, 2, -1], "resourceId": "simulated:red_portable_engine", "blockState": {"facing": "south"}},
                {"role": "engine_starboard", "mode": "add", "lattice": [2, 2, 1], "resourceId": "simulated:red_portable_engine", "blockState": {"facing": "north"}},
                {"role": "governor", "mode": "replace", "lattice": [2, 2, 0], "resourceId": "create:rotation_speed_controller", "blockState": {"axis": "z"}, "replaces": {"kind": "airframe_structure", "resourceId": "minecraft:spruce_planks"}},
                {"role": "governor_cog", "mode": "add", "lattice": [2, 3, 0], "resourceId": "create:large_cogwheel", "blockState": {"axis": "x"}},
                {"role": "prop_shaft", "mode": "add", "lattice": [1, 3, 0], "resourceId": "create:shaft", "blockState": {"axis": "x"}},
            ],
            "powerplantGlueDomain": {"name": "powerplant", "from": [1, 2, -1], "to": [2, 3, 1]},
            "runtimeObligations": [
                {"id": "powertrain_primary_sable_recapture_probe"},
                {"id": "portable_engine_shared_network_probe"},
                {"id": "governor_128_rpm_probe"},
                {"id": "kinetic_stress_margin_probe"},
                {"id": "propeller_thrust_sign_magnitude_probe"},
            ],
        }

    def test_source_constrained_powertrain_patch_is_probe_ready(self) -> None:
        out = lower_powertrain(self.manifest, self.glue, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertEqual(1, out["replacementCount"])
        self.assertEqual(4, out["additionCount"])
        self.assertEqual(118, out["metrics"]["resultingMovingMainBodyPlacementCount"])
        self.assertEqual(127, out["metrics"]["expectedPrimarySableTransferCount"])
        self.assertEqual(128, out["governor"]["firstAcceptedTargetRpm"])
        self.assertTrue(out["readiness"]["governor128RuntimeProbeReady"])
        self.assertFalse(out["readiness"]["runtimeQualificationReady"])

    def test_replacement_must_match_existing_structural_cell(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["placements"][2]["replaces"]["resourceId"] = "minecraft:stone"
        with self.assertRaisesRegex(PowertrainLoweringError, "replacement source mismatch"):
            lower_powertrain(self.manifest, self.glue, profile)

    def test_addition_collision_is_rejected(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["placements"].append({"lattice": [2, 2, -1], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks"})
        with self.assertRaisesRegex(PowertrainLoweringError, "addition collides"):
            lower_powertrain(manifest, self.glue, self.profile)

    def test_powerplant_glue_cannot_contain_propeller_child(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["powerplantGlueDomain"] = {"name": "bad", "from": [-1, 2, -1], "to": [2, 3, 1]}
        with self.assertRaisesRegex(PowertrainLoweringError, "powerplant glue validation failed"):
            lower_powertrain(self.manifest, self.glue, profile)

    def test_powerplant_glue_must_overlap_existing_domain_in_all_three_axes(self) -> None:
        glue = copy.deepcopy(self.glue)
        glue["glueDomains"] = [
            {"selectionCellBounds": {"min": [0, 1, 100], "max": [18, 3, 100]}}
        ]
        with self.assertRaisesRegex(PowertrainLoweringError, "powerplant glue validation failed"):
            lower_powertrain(self.manifest, glue, self.profile)

    def test_only_first_128_rpm_point_is_statically_accepted(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["governorTargetRpm"] = 160
        with self.assertRaisesRegex(PowertrainLoweringError, "only the first 128-RPM"):
            lower_powertrain(self.manifest, self.glue, profile)


if __name__ == "__main__":
    unittest.main()
