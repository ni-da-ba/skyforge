from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]

class PlatformV2OperabilityAuditTest(unittest.TestCase):
    def test_units_make_dual_writer_service_ownership_fail_closed(self):
        v2 = (ROOT / "deploy/orchestrator/skyforge-orchestrator-v2.service.in").read_text()
        legacy = (ROOT / "deploy/orchestrator/skyforge-orchestrator.service.in").read_text()
        self.assertIn("Conflicts=skyforge-orchestrator.service", v2)
        self.assertIn("--enable-production-execution", v2)
        self.assertIn("--activation-evidence", v2)
        self.assertIn("--require-startup-reconcile-success", legacy)
        self.assertIn("--port 3000", v2)
        self.assertIn("--port 3000", legacy)

    def test_staging_is_exact_head_clean_and_nonactivating(self):
        stage = (ROOT / "scripts/orchestrator/stage_platform_v2_cutover.sh").read_text()
        self.assertIn('HEAD_SHA="$(git rev-parse HEAD)"', stage)
        self.assertIn('HEAD_SHA" != "$SKYFORGE_ACCEPTED_MAIN_SHA', stage)
        self.assertIn("git status --porcelain --untracked-files=no", stage)
        self.assertIn("systemctl disable skyforge-orchestrator-v2.service", stage)
        self.assertNotIn("systemctl start skyforge-orchestrator-v2.service", stage)
        self.assertNotIn("systemctl restart skyforge-orchestrator.service", stage)

    def test_evidence_preserves_deferred_gate_and_legacy_authority(self):
        evidence = json.loads((ROOT / "docs/operations/evidence/platform_v2_r5c28_operability_audit.json").read_text())
        self.assertEqual(evidence["dr70"], "DEFERRED_NOT_PASSED")
        self.assertEqual(evidence["read_only_live_observation"]["production_writer_authority"], "LEGACY")
        self.assertFalse(evidence["production_mutation_performed"])
        self.assertEqual(evidence["disposition"], "READY_FOR_FINAL_NONPRODUCTION_ACCEPTANCE_REHEARSAL")

if __name__ == "__main__":
    unittest.main()
