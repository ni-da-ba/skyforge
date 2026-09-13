from __future__ import annotations

import copy
import unittest

from control_surface_correction import YawControlCorrectionError, lower_yaw_control_correction


class YawControlCorrectionTests(unittest.TestCase):
    def setUp(self) -> None:
        placements = []
        fixed = [(15,4,0),(15,5,0),(15,6,0),(15,7,0),(16,4,0),(16,5,0),(16,6,0)]
        for c in fixed + [(17,4,0)]:
            placements.append({
                "lattice": list(c),
                "kind": "airframe_aerodynamic_surface",
                "resourceId": "create:white_sail",
                "blockState": {"facing": "south"},
            })
        used = {tuple(p["lattice"]) for p in placements}
        x = 0
        while len([p for p in placements if p["kind"] != "propeller_hub" and p["kind"] != "propeller_sail"]) < 117:
            c = (x % 30, 20 + x // 30, 10)
            x += 1
            if c in used or c in {(18,3,0),(18,4,0),(18,5,0),(18,6,0),(18,7,0),(17,5,0),(17,6,0),(17,7,0)}:
                continue
            used.add(c)
            placements.append({"lattice": list(c), "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}})
        for i in range(9):
            placements.append({
                "lattice": [-10 - i, 3, 0],
                "kind": "propeller_hub" if i == 0 else "propeller_sail",
                "resourceId": "minecraft:spruce_planks" if i == 0 else "simulated:white_symmetric_sail",
                "blockState": {} if i == 0 else {"axis": "x"},
            })
        self.manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "digestSha256": "manifest",
            "placements": placements,
        }
        self.glue = {
            "schemaVersion": "aircraft-glue-encoding-ir-0.11",
            "digestSha256": "glue",
            "encodingPolicy": {"maxSelectionDimensionBlocks": 24},
            "glueDomains": [
                {"name": "fuselage", "selectionCellBounds": {"min": [0,0,0], "max": [1,1,1]}},
                {"name": "wing", "selectionCellBounds": {"min": [2,0,0], "max": [3,1,1]}},
                {"name": "horizontal_tail", "selectionCellBounds": {"min": [15,3,-3], "max": [17,3,3]}},
                {"name": "vertical_tail", "selectionCellBounds": {"min": [15,3,0], "max": [17,7,0]}},
            ],
        }
        self.powertrain = {
            "schemaVersion": "aircraft-powertrain-ir-0.12",
            "digestSha256": "power",
            "placements": [],
            "powerplantGlueDomain": {"name": "powerplant", "from": [4,0,0], "to": [5,1,1]},
            "metrics": {
                "resultingMovingMainBodyPlacementCount": 118,
                "nestedPropellerChildPlacementCount": 9,
                "resultingManifestPlacementCount": 126,
            },
        }
        self.prior_yaw = {
            "schemaVersion": "aircraft-yaw-control-ir-0.13",
            "digestSha256": "59f451bb7bb71433016af249f5ded7a87aed47b69b2a26215b4f573322f2d951",
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-yaw-control-correction-profile-0.13.1",
            "profileId": "test",
            "supersededYawDigestSha256": self.prior_yaw["digestSha256"],
            "correctionReason": "runtime disproved spruce separator",
            "supersededGlueDomainName": "vertical_tail",
            "fixedFinCoordinates": [list(v) for v in fixed],
            "airGapCoordinates": [[17,4,0],[17,5,0],[17,6,0],[17,7,0]],
            "placements": [
                {"role":"yaw_swivel_bearing","mode":"add_main","lattice":[18,3,0],"kind":"yaw_control_hinge","resourceId":"simulated:swivel_bearing","blockState":{"assembled":"false","facing":"up","powered":"false"}},
                {"role":"yaw_air_gap","mode":"remove_main","lattice":[17,4,0],"kind":"yaw_control_air_gap","resourceId":"minecraft:air","blockState":{},"replaces":{"kind":"airframe_aerodynamic_surface","resourceId":"create:white_sail","blockState":{"facing":"south"}}},
                {"role":"rudder_seed","mode":"add_control_child","lattice":[18,4,0],"kind":"yaw_control_surface","resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_1","mode":"add_control_child","lattice":[18,5,0],"kind":"yaw_control_surface","resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_2","mode":"add_control_child","lattice":[18,6,0],"kind":"yaw_control_surface","resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_3","mode":"add_control_child","lattice":[18,7,0],"kind":"yaw_control_surface","resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
            ],
            "fixedFinParentGlueDomain": {"name":"vertical_tail_fixed","from":[15,3,0],"to":[16,7,0]},
            "bearingMountGlueDomain": {"name":"yaw_bearing_mount","from":[17,3,0],"to":[18,3,0]},
            "controlChildGlueDomain": {"name":"rudder_child","from":[18,4,0],"to":[18,7,0]},
            "runtimeObligations": [
                {"id":"yaw_v0131_primary_sable_recapture_probe"},
                {"id":"rudder_swivel_child_capture_probe"},
                {"id":"rudder_neutral_constraint_probe"},
                {"id":"rudder_actuation_probe"},
                {"id":"rudder_yaw_force_probe"},
            ],
        }

    def test_runtime_corrected_air_gap_topology_is_probe_ready(self) -> None:
        out = lower_yaw_control_correction(self.manifest, self.glue, self.powertrain, self.prior_yaw, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertEqual(118, out["metrics"]["v0131MovingParentMainBodyPlacementCount"])
        self.assertEqual(9, out["metrics"]["nestedPropellerChildPlacementCount"])
        self.assertEqual(4, out["metrics"]["yawControlChildPlacementCount"])
        self.assertEqual(130, out["metrics"]["resultingManifestPlacementCount"])
        self.assertEqual(131, out["metrics"]["expectedPrimarySableTransferCount"])
        self.assertEqual(7, out["metrics"]["runtimeGlueDomainCount"])
        self.assertTrue(out["readiness"]["rudderChildCaptureProbeReady"])
        self.assertFalse(out["readiness"]["controlActuationProbeReady"])

    def test_predecessor_digest_mismatch_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["supersededYawDigestSha256"] = "wrong"
        with self.assertRaisesRegex(YawControlCorrectionError, "predecessor digest mismatch"):
            lower_yaw_control_correction(self.manifest, self.glue, self.powertrain, self.prior_yaw, profile)

    def test_removed_gap_source_must_match_regular_fin_sail(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        for p in manifest["placements"]:
            if p["lattice"] == [17,4,0]:
                p["resourceId"] = "minecraft:spruce_planks"
        with self.assertRaisesRegex(YawControlCorrectionError, "source resource mismatch"):
            lower_yaw_control_correction(manifest, self.glue, self.powertrain, self.prior_yaw, self.profile)

    def test_aft_bearing_coordinate_must_be_free(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["placements"][0] = {"lattice":[18,3,0],"kind":"airframe_structure","resourceId":"minecraft:spruce_planks","blockState":{}}
        with self.assertRaisesRegex(YawControlCorrectionError, "add_main collides"):
            lower_yaw_control_correction(manifest, self.glue, self.powertrain, self.prior_yaw, self.profile)

    def test_air_gap_cannot_be_reintroduced_by_existing_geometry(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["placements"].append({"lattice":[17,5,0],"kind":"airframe_structure","resourceId":"minecraft:spruce_planks","blockState":{}})
        self.powertrain["metrics"]["resultingMovingMainBodyPlacementCount"] = 119
        self.powertrain["metrics"]["resultingManifestPlacementCount"] = 127
        with self.assertRaisesRegex(YawControlCorrectionError, "air-gap cells are not empty"):
            lower_yaw_control_correction(manifest, self.glue, self.powertrain, self.prior_yaw, self.profile)


if __name__ == "__main__":
    unittest.main()
