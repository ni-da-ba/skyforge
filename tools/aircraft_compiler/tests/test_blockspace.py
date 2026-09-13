from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from blockspace import BlockspaceError, transcribe
from solver import solve

DESIGN_SPEC = ROOT / "specimens" / "guild_utility_monoplane_v0.1.json"
BLOCKSPACE_SPEC = ROOT / "specimens" / "guild_utility_monoplane_blockspace_v0.2.json"


class BlockspaceTests(unittest.TestCase):
    def design(self):
        return solve(json.loads(DESIGN_SPEC.read_text(encoding="utf-8")))

    def config(self):
        return json.loads(BLOCKSPACE_SPEC.read_text(encoding="utf-8"))

    def test_transcription_is_deterministic(self):
        design = self.design()
        config = self.config()
        a = transcribe(copy.deepcopy(design), copy.deepcopy(config))
        b = transcribe(copy.deepcopy(design), copy.deepcopy(config))
        self.assertEqual(a, b)
        self.assertEqual(a["digestSha256"], b["digestSha256"])

    def test_bounded_discrete_invariants_pass(self):
        result = transcribe(self.design(), self.config())
        metrics = result["metrics"]
        limits = self.config()["validation"]
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(metrics["connectedComponents6Neighbor"], 1)
        self.assertTrue(metrics["mirrorSymmetrySatisfied"])
        self.assertTrue(metrics["propellerDiskClear"])
        self.assertGreater(metrics["cellCount"], 50)
        self.assertLessEqual(
            metrics["cgStationQuantizationErrorBlocks"],
            limits["maxCgStationErrorBlocks"],
        )
        for error in metrics["dimensionErrorBlocks"].values():
            self.assertLessEqual(error, limits["maxDimensionErrorBlocks"])

    def test_target_boundary_remains_capability_only(self):
        result = transcribe(self.design(), self.config())
        text = json.dumps(result, sort_keys=True)
        self.assertNotIn("minecraft:", text)
        self.assertNotIn("create:", text)
        self.assertNotIn("aeronautics:", text)
        self.assertTrue(result["declaredTranscriptionAssumptions"]["noMinecraftBlockIdentity"])
        self.assertEqual(
            result["capabilityContract"]["concreteResourceResolution"],
            "deferred_pending_exact_target_api_and_runtime_evidence",
        )

    def test_propeller_clearance_guard_can_fail(self):
        config = self.config()
        config["propellerHubRadiusM"] = 0.3
        result = transcribe(self.design(), config)
        self.assertFalse(result["validation"]["passed"])
        self.assertFalse(result["metrics"]["propellerDiskClear"])
        self.assertGreater(result["metrics"]["propellerDiskViolationCount"], 0)

    def test_source_identity_mismatch_is_rejected(self):
        config = self.config()
        config["sourceAssetId"] = "wrong.asset"
        with self.assertRaises(BlockspaceError):
            transcribe(self.design(), config)


if __name__ == "__main__":
    unittest.main()
