import json
from pathlib import Path
import tempfile
import unittest

from v2.objective_intake import (
    ObjectiveCompileDisposition,
    ObjectiveIntent,
    compile_continue_objective,
    compile_objective,
    load_manifest,
    parse_objective,
)
from v2.roadmap_shadow import ShadowRoadmapState


REPO_ROOT = Path(__file__).resolve().parents[2]


class ObjectiveIntakeTest(unittest.TestCase):
    def test_supported_intents_are_typed_and_non_authoritative(self):
        cases = {
            "Investigate river frequency": (ObjectiveIntent.INVESTIGATE, ObjectiveCompileDisposition.READ_ONLY),
            "Fix weird waterfalls": (ObjectiveIntent.FIX, ObjectiveCompileDisposition.NEEDS_SCOPING),
            "Develop jungle music": (ObjectiveIntent.DEVELOP, ObjectiveCompileDisposition.NEEDS_SCOPING),
            "Continue DR-70": (ObjectiveIntent.CONTINUE, ObjectiveCompileDisposition.NEEDS_SCOPING),
            "Show current blocker": (ObjectiveIntent.SHOW, ObjectiveCompileDisposition.READ_ONLY),
        }
        for text, (intent, disposition) in cases.items():
            with self.subTest(text=text):
                result = parse_objective(text)
                self.assertEqual(result.request.intent, intent)
                self.assertEqual(result.disposition, disposition)
                self.assertFalse(result.as_dict()["executable_task_authority"])

    def test_unsupported_free_text_fails_closed(self):
        result = parse_objective("make Skyforge better")
        self.assertEqual(result.disposition, ObjectiveCompileDisposition.AMBIGUOUS)
        self.assertIsNone(result.request)

    def test_continue_skyforge_is_admitted_to_opt6_without_task_authority(self):
        manifest = load_manifest(REPO_ROOT)
        state = ShadowRoadmapState(
            roadmap_id=manifest.roadmap_id,
            manifest_fingerprint=manifest.fingerprint,
            completed_runs=(),
            blocked_nodes=(),
            active=None,
        )
        parsed = parse_objective("Continue Skyforge").request
        result = compile_continue_objective(parsed, manifest=manifest, state=state)
        self.assertEqual(
            result.disposition,
            ObjectiveCompileDisposition.PROGRAM_CONTINUE,
        )
        self.assertIn("OPT-6", result.reason)
        self.assertIsNone(result.candidate_task)
        self.assertIsNone(result.human_gate)
        self.assertFalse(result.as_dict()["executable_task_authority"])

    def test_continue_dr70_resolves_latest_reached_human_rereview_gate(self):
        manifest = load_manifest(REPO_ROOT)
        completed = tuple(
            (node.node_id, node.max_runs)
            for node in manifest.nodes
            if node.kind.value == "task"
            and node.node_id != "dr-human-exploration-rereview"
        )
        state = ShadowRoadmapState(
            roadmap_id=manifest.roadmap_id,
            manifest_fingerprint=manifest.fingerprint,
            completed_runs=completed,
            blocked_nodes=("dr-human-exploration-review", "dr-human-exploration-rereview"),
            active=None,
        )
        request = parse_objective("Continue DR-70").request
        result = compile_continue_objective(request, manifest=manifest, state=state)
        self.assertEqual(result.disposition, ObjectiveCompileDisposition.HUMAN_GATE)
        self.assertEqual(result.human_gate.node_id, "dr-human-exploration-rereview")
        self.assertIn("tall vegetation no longer clips", result.human_gate.message)
        self.assertFalse(result.as_dict()["executable_task_authority"])

    def test_continue_candidate_task_is_proposal_not_authority(self):
        manifest = load_manifest(REPO_ROOT)
        completed = (
            ("dr-00-canonical-specimen-lock", 1),
            ("dr-10-starting-cluster-closure", 1),
            ("dr-20-visible-hydrology", 1),
            ("dr-30-structure-reintegration", 1),
            ("dr-40-production-ecology", 1),
            ("dr-50-integrated-dressed-region", 1),
            ("dr-60-exploration-review-packet", 1),
        )
        state = ShadowRoadmapState(
            roadmap_id=manifest.roadmap_id,
            manifest_fingerprint=manifest.fingerprint,
            completed_runs=completed,
            blocked_nodes=("dr-human-exploration-review",),
            active=None,
        )
        request = parse_objective("Continue DR70").request
        result = compile_continue_objective(request, manifest=manifest, state=state)
        self.assertEqual(result.disposition, ObjectiveCompileDisposition.CANDIDATE_TASK)
        self.assertEqual(result.candidate_task.node_id, "dr-65-canonical-hydrology-authorship")
        self.assertEqual(result.candidate_task.issue_number, 756)
        self.assertFalse(result.as_dict()["executable_task_authority"])

    def test_cli_loader_prefers_v2_roadmap_state_when_present(self):
        manifest = load_manifest(REPO_ROOT)
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "docs/agent-state").mkdir(parents=True)
            (root / ".skyforge-platform-v2").mkdir(parents=True)
            (root / "docs/agent-state/ORCHESTRATOR_ROADMAP.json").write_text(
                (REPO_ROOT / "docs/agent-state/ORCHESTRATOR_ROADMAP.json").read_text(),
                encoding="utf-8",
            )
            completed = {
                node.node_id: node.max_runs
                for node in manifest.nodes
                if node.kind.value == "task"
            }
            (root / ".skyforge-platform-v2/roadmap-authority.json").write_text(
                json.dumps({
                    "schema_version": 1,
                    "roadmap_id": manifest.roadmap_id,
                    "manifest_fingerprint": manifest.fingerprint,
                    "completed_runs": completed,
                    "blocked_nodes": [
                        {
                            "node_id": "dr-human-exploration-rereview",
                            "reason": "human rereview required",
                        }
                    ],
                    "active": None,
                    "claims_day": None,
                    "claims_today": 0,
                    "human_gate_records": [],
                }),
                encoding="utf-8",
            )
            result = compile_objective("Continue DR-70", root=root)
            self.assertEqual(result.disposition, ObjectiveCompileDisposition.HUMAN_GATE)
            self.assertEqual(result.human_gate.node_id, "dr-human-exploration-rereview")


if __name__ == "__main__":
    unittest.main()
