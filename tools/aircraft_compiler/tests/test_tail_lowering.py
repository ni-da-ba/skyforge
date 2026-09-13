from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tail_lowering import lower_tail_junction


class TailLoweringTests(unittest.TestCase):
    def test_vertical_shape_translation_preserves_count_x_moment_and_attachment(self):
        target = {"schemaVersion":"aircraft-minecraft-realization-preflight-ir-0.4","digestSha256":"t","placements":[
            {"lattice":[15,3,0],"roles":["horizontal_tail_surface_intent","vertical_tail_surface_intent"],"providerId":"sail","resourceId":"create:white_sail"},
            {"lattice":[15,4,0],"roles":["vertical_tail_surface_intent"],"providerId":"sail","resourceId":"create:white_sail"},
            {"lattice":[15,3,1],"roles":["horizontal_tail_surface_intent"],"providerId":"sail","resourceId":"create:white_sail"}
        ]}
        surface = {"schemaVersion":"aircraft-surface-state-resolution-ir-0.6","assetId":"a.v0_6_surface_state","digestSha256":"s","resolvedPlacements":[{"lattice":[15,4,0],"roles":["vertical_tail_surface_intent"],"blockState":{"facing":"south"}},{"lattice":[15,3,1],"roles":["horizontal_tail_surface_intent"],"blockState":{"facing":"up"}}],"conflicts":[{"lattice":[15,3,0]}],"readiness":{"staticBlockers":["unresolved_surface_state_conflicts"],"runtimeBlockers":["runtime"]}}
        profile = {"schemaVersion":"aircraft-tail-lowering-profile-0.7","profileId":"p","providerId":"sail","resourceId":"create:white_sail","stateProperty":"facing","translatedRole":"vertical_tail_surface_intent","junctionRetainedRole":"horizontal_tail_surface_intent","translatedState":"south","junctionRetainedState":"up","translationBlocks":[0,1,0]}
        result = lower_tail_junction(target, surface, profile)
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["verticalTailCellCount"], 2)
        self.assertEqual(result["metrics"]["verticalXFirstMomentBeforeBlocks"], result["metrics"]["verticalXFirstMomentAfterBlocks"])
        self.assertTrue(result["topologyChecks"]["verticalRootFaceAttachedToHorizontalTail"])
        self.assertTrue(result["readiness"]["surfaceStateResolutionComplete"])


if __name__ == "__main__":
    unittest.main()
