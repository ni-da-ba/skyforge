from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
from solver import solve

SPEC=ROOT/"specimens"/"guild_utility_monoplane_v0.1.json"


class SolverTests(unittest.TestCase):
    def specimen(self): return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_deterministic(self):
        a=solve(copy.deepcopy(self.specimen())); b=solve(copy.deepcopy(self.specimen()))
        self.assertEqual(a,b)
        self.assertEqual(a["digestSha256"],b["digestSha256"])

    def test_target_neutral_and_validation_scope_explicit(self):
        r=solve(self.specimen()); text=json.dumps(r,sort_keys=True)
        self.assertNotIn("minecraft:",text)
        self.assertNotIn("create:",text)
        self.assertFalse(r["targetBoundary"]["createAeronauticsAdapterApplied"])
        self.assertIn("dynamic stability",r["validation"]["doesNotProve"])

    def test_selected_candidate_satisfies_declared_bounds(self):
        r=solve(self.specimen()); m=r["metrics"]; c=self.specimen()["constraints"]
        self.assertLessEqual(m["cruiseLiftResidualFraction"],c["maxCruiseLiftResidualFraction"])
        self.assertGreaterEqual(m["wingAspectRatio"],c["aspectRatioRange"][0])
        self.assertLessEqual(m["wingAspectRatio"],c["aspectRatioRange"][1])
        self.assertGreaterEqual(m["cgMacFraction"],c["specimenCgMacFractionRange"][0])
        self.assertLessEqual(m["cgMacFraction"],c["specimenCgMacFractionRange"][1])
        self.assertLessEqual(m["horizontalTailReferenceErrorFraction"],c["maxHorizontalTailVolumeReferenceErrorFraction"])
        self.assertLessEqual(m["verticalTailReferenceErrorFraction"],c["maxVerticalTailVolumeReferenceErrorFraction"])

    def test_mass_ledger_closes_exactly(self):
        r=solve(self.specimen())
        self.assertAlmostEqual(sum(x["massKg"] for x in r["massLedger"]),r["mission"]["grossMassKg"],places=10)

    def test_solver_is_actual_search_not_single_hardcoded_design(self):
        r=solve(self.specimen())
        self.assertGreater(r["solver"]["candidateCountFeasible"],10)
        self.assertTrue(r["solver"]["rejectionCounts"])


if __name__ == "__main__": unittest.main()
