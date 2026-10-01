from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]

class FinalRehearsalEvidenceTest(unittest.TestCase):
    def test_evidence_freezes_code_without_claiming_production_cutover(self):
        value = json.loads((ROOT / "docs/operations/evidence/platform_v2_r5c29_final_rehearsal.json").read_text())
        self.assertEqual(value["process_rehearsal"]["forward_sequence"], ["LEGACY", "NONE", "V2"])
        self.assertEqual(value["process_rehearsal"]["rollback_sequence"], ["V2", "NONE", "LEGACY"])
        self.assertTrue(value["process_rehearsal"]["no_overlap_proven"])
        self.assertEqual(value["dr70"], "DEFERRED_NOT_PASSED")
        self.assertFalse(value["live_production_mutation_performed"])
        self.assertEqual(value["migration_code_disposition"], "FREEZE_AFTER_R5C29_ACCEPTANCE_UNLESS_DEFECT_FOUND")

if __name__ == "__main__":
    unittest.main()
