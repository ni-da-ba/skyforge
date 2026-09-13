from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from surface_state import resolve_surface_states


class SurfaceStateTests(unittest.TestCase):
    def test_incompatible_tail_facings_are_compile_visible(self):
        target = {
            "schemaVersion": "aircraft-minecraft-realization-preflight-ir-0.4", "digestSha256": "target", "metrics": {"unresolvedStateOrResourceCount": 3},
            "placements": [
                {"lattice": [1,2,0], "roles": ["wing_surface_intent"], "providerId": "sail", "resourceId": "create:white_sail"},
                {"lattice": [2,2,0], "roles": ["horizontal_tail_surface_intent"], "providerId": "sail", "resourceId": "create:white_sail"},
                {"lattice": [3,2,0], "roles": ["horizontal_tail_surface_intent","vertical_tail_surface_intent"], "providerId": "sail", "resourceId": "create:white_sail"}
            ]
        }
        propulsion = {"schemaVersion": "aircraft-propulsion-realization-ir-0.5", "assetId": "test.v0_5_propulsion", "digestSha256": "prop", "readiness": {"propulsionStaticTopologyPassed": True, "blockers": ["unresolved_blockstate_or_resource_rules", "propulsion_runtime_obligations_unverified"]}}
        profile = {"schemaVersion": "aircraft-surface-state-profile-0.6", "profileId": "state-test", "providerId": "sail", "stateProperty": "facing", "legalStates": ["up","south"], "roleStates": {"wing_surface_intent": "up", "horizontal_tail_surface_intent": "up", "vertical_tail_surface_intent": "south"}}
        result = resolve_surface_states(target, propulsion, profile)
        self.assertEqual(result["metrics"]["stateResolvedCount"], 2)
        self.assertEqual(result["metrics"]["stateConflictCount"], 1)
        self.assertEqual(result["resolvedPlacements"][0]["blockState"], {"facing": "up"})
        self.assertEqual(result["conflicts"][0]["demandedStates"], ["south", "up"])
        self.assertFalse(result["readiness"]["surfaceStateResolutionComplete"])
        self.assertIn("unresolved_surface_state_conflicts", result["readiness"]["staticBlockers"])


if __name__ == "__main__":
    unittest.main()
