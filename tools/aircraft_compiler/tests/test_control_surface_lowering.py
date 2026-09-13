from __future__ import annotations

import copy
import unittest

from control_surface_lowering import YawControlLoweringError, lower_yaw_control


class YawControlLoweringTests(unittest.TestCase):
    def setUp(self) -> None:
        placements = []
        for y in range(4, 8):
            placements.append({"lattice": [15, y, 0], "kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}})
        for y in range(4, 7):
            placements.append({"lattice": [16, y, 0], "kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}})
        placements.extend([
            {"lattice": [17, 4, 0], "kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}},
            {"lattice": [17, 3, 0], "kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "up"}},
            {"lattice": [2, 2, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}},
            {"lattice": [0, 3, 0], "kind": "propeller_bearing", "resourceId": "aeronautics:propeller_bearing", "blockState": {"facing": "west"}},
            {"lattice": [-1, 3, 0], "kind": "propeller_hub", "resourceId": "minecraft:spruce_planks", "blockState": {}},
        ])
        self.manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_9_probe_manifest",
            "digestSha256": "manifest",
            "placements": placements,
        }
        self.glue = {
            "schemaVersion": "aircraft-glue-encoding-ir-0.11",
            "digestSha256": "glue",
            "encodingPolicy": {"maxSelectionDimensionBlocks": 24},
            "glueDomains": [
                {"name": "fuselage_core", "from": [0, 1, 0], "to": [18, 3, 0], "selectionCellBounds": {"min": [0, 1, 0], "max": [18, 3, 0]}},
                {"name": "wing", "from": [7, 3, -10], "to": [10, 4, 10], "selectionCellBounds": {"min": [7, 3, -10], "max": [10, 4, 10]}},
                {"name": "horizontal_tail", "from": [15, 3, -3], "to": [17, 3, 3], "selectionCellBounds": {"min": [15, 3, -3], "max": [17, 3, 3]}},
                {"name": "vertical_tail", "from": [15, 3, 0], "to": [17, 7, 0], "selectionCellBounds": {"min": [15, 3, 0], "max": [17, 7, 0]}},
            ],
        }
        self.powertrain = {
            "schemaVersion": "aircraft-powertrain-ir-0.12",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_12_powertrain",
            "digestSha256": "powertrain",
            "validation": {"passed": True},
            "placements": [
                {"role": "engine_port", "mode": "add", "lattice": [2, 2, -1], "resourceId": "simulated:red_portable_engine", "blockState": {"facing": "south"}},
                {"role": "engine_starboard", "mode": "add", "lattice": [2, 2, 1], "resourceId": "simulated:red_portable_engine", "blockState": {"facing": "north"}},
                {"role": "governor", "mode": "replace", "lattice": [2, 2, 0], "resourceId": "create:rotation_speed_controller", "blockState": {"axis": "z"}},
                {"role": "governor_cog", "mode": "add", "lattice": [2, 3, 0], "resourceId": "create:large_cogwheel", "blockState": {"axis": "x"}},
                {"role": "prop_shaft", "mode": "add", "lattice": [1, 3, 0], "resourceId": "create:shaft", "blockState": {"axis": "x"}},
            ],
            "powerplantGlueDomain": {"name": "powerplant", "from": [1, 2, -1], "to": [2, 3, 1]},
            "metrics": {
                "resultingManifestPlacementCount": 16,
                "resultingMovingMainBodyPlacementCount": 16,
                "nestedPropellerChildPlacementCount": 1,
                "expectedPrimarySableTransferCount": 17,
            },
        }
        self.profile = {
            "schemaVersion": "aircraft-yaw-control-profile-0.13",
            "profileId": "test",
            "supersededGlueDomainName": "vertical_tail",
            "bearingParentGlueDomainName": "horizontal_tail",
            "fixedFinCoordinates": [[15, y, 0] for y in range(4, 8)],
            "placements": [
                {"role": "yaw_swivel_bearing", "mode": "replace_main", "lattice": [17, 3, 0], "kind": "yaw_control_hinge", "resourceId": "simulated:swivel_bearing", "blockState": {"assembled": "false", "facing": "up", "powered": "false"}, "replaces": {"kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "up"}}},
                {"role": "yaw_hinge_spar_0", "mode": "replace_main", "lattice": [16, 4, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}, "replaces": {"kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}}},
                {"role": "yaw_hinge_spar_1", "mode": "replace_main", "lattice": [16, 5, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}, "replaces": {"kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}}},
                {"role": "yaw_hinge_spar_2", "mode": "replace_main", "lattice": [16, 6, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}, "replaces": {"kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}}},
                {"role": "rudder_seed", "mode": "reclassify_main_to_control_child", "lattice": [17, 4, 0], "kind": "yaw_control_surface", "resourceId": "simulated:white_symmetric_sail", "blockState": {"axis": "z"}, "replaces": {"kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}}},
                {"role": "rudder_1", "mode": "add_control_child", "lattice": [17, 5, 0], "kind": "yaw_control_surface", "resourceId": "simulated:white_symmetric_sail", "blockState": {"axis": "z"}},
                {"role": "rudder_2", "mode": "add_control_child", "lattice": [17, 6, 0], "kind": "yaw_control_surface", "resourceId": "simulated:white_symmetric_sail", "blockState": {"axis": "z"}},
                {"role": "rudder_3", "mode": "add_control_child", "lattice": [17, 7, 0], "kind": "yaw_control_surface", "resourceId": "simulated:white_symmetric_sail", "blockState": {"axis": "z"}},
            ],
            "replacementParentGlueDomain": {"name": "vertical_tail_fixed_hinge", "from": [15, 3, 0], "to": [16, 7, 0]},
            "controlChildGlueDomain": {"name": "rudder_child", "from": [17, 4, 0], "to": [17, 7, 0]},
            "runtimeObligations": [
                {"id": "yaw_primary_sable_recapture_probe"},
                {"id": "rudder_swivel_child_capture_probe"},
                {"id": "rudder_neutral_constraint_probe"},
                {"id": "rudder_actuation_probe"},
                {"id": "rudder_yaw_force_probe"},
            ],
        }

    def test_source_constrained_yaw_topology_is_child_capture_probe_ready(self) -> None:
        out = lower_yaw_control(self.manifest, self.glue, self.powertrain, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertTrue(out["readiness"]["yawControlStaticTopologyPassed"])
        self.assertTrue(out["readiness"]["rudderChildCaptureProbeReady"])
        self.assertFalse(out["readiness"]["controlActuationProbeReady"])
        self.assertEqual(4, out["metrics"]["yawControlChildPlacementCount"])
        self.assertEqual(15, out["metrics"]["v013MovingParentMainBodyPlacementCount"])
        self.assertEqual(20, out["metrics"]["expectedPrimarySableTransferCount"])
        self.assertEqual(6, out["metrics"]["runtimeGlueDomainCount"])
        self.assertEqual([17, 4, 0], out["rudderSeedCoordinate"])
        self.assertEqual(4, out["aerodynamicAccounting"]["resultingFixedVerticalTailRegularSailCells"])
        self.assertEqual(18, out["aerodynamicAccounting"]["resultingHorizontalTailRegularSailCells"])

    def test_regular_sail_source_mismatch_is_rejected(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        for p in manifest["placements"]:
            if p["lattice"] == [16, 5, 0]:
                p["resourceId"] = "minecraft:spruce_planks"
        with self.assertRaisesRegex(YawControlLoweringError, "source resource mismatch"):
            lower_yaw_control(manifest, self.glue, self.powertrain, self.profile)

    def test_control_child_addition_collision_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        next(p for p in profile["placements"] if p["role"] == "rudder_1")["lattice"] = [15, 4, 0]
        with self.assertRaisesRegex(YawControlLoweringError, "control-child addition collides"):
            lower_yaw_control(self.manifest, self.glue, self.powertrain, profile)

    def test_superseded_vertical_tail_glue_domain_is_required(self) -> None:
        glue = copy.deepcopy(self.glue)
        next(d for d in glue["glueDomains"] if d["name"] == "vertical_tail")["name"] = "wrong_name"
        with self.assertRaisesRegex(YawControlLoweringError, "superseded glue domain"):
            lower_yaw_control(self.manifest, glue, self.powertrain, self.profile)

    def test_horizontal_tail_must_retain_bearing_on_parent(self) -> None:
        glue = copy.deepcopy(self.glue)
        next(d for d in glue["glueDomains"] if d["name"] == "horizontal_tail")["selectionCellBounds"] = {"min": [15, 3, -3], "max": [16, 3, 3]}
        out = lower_yaw_control(self.manifest, glue, self.powertrain, self.profile)
        self.assertFalse(out["validation"]["passed"])
        self.assertFalse(out["glueChecks"]["horizontalTailGlueRetainsBearingOnParent"])

    def test_parent_glue_cannot_enclose_rudder_child(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["replacementParentGlueDomain"]["to"] = [17, 7, 0]
        out = lower_yaw_control(self.manifest, self.glue, self.powertrain, profile)
        self.assertFalse(out["validation"]["passed"])
        self.assertFalse(out["glueChecks"]["replacementParentGlueExcludesRudderChild"])

    def test_child_glue_cannot_enclose_swivel_bearing(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["controlChildGlueDomain"]["from"] = [17, 3, 0]
        out = lower_yaw_control(self.manifest, self.glue, self.powertrain, profile)
        self.assertFalse(out["validation"]["passed"])
        self.assertFalse(out["glueChecks"]["rudderChildGlueExcludesBearing"])


if __name__ == "__main__":
    unittest.main()
