from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import roadmap_policy as legacy_policy
import platform_v2_roadmap_shadow as live
from v2.roadmap_shadow import (
    RoadmapControlObservation,
    RoadmapIssueTruth,
    RoadmapShadowDisposition,
    ShadowRoadmapManifest,
    ShadowRoadmapState,
    evaluate_roadmap_shadow,
    select_shadow_next_node,
)


def task(node_id, issue, priority, prereq=(), max_runs=1):
    return {
        "id": node_id,
        "kind": "task",
        "lane": "Implementation",
        "issue_number": issue,
        "priority": priority,
        "max_runs": max_runs,
        "prerequisites": list(prereq),
        "objective_hint": f"do {node_id}",
        "stop_boundary": f"stop {node_id}",
    }


def gate(node_id, priority, prereq=()):
    return {
        "id": node_id,
        "kind": "gate",
        "lane": "Implementation",
        "priority": priority,
        "max_runs": 1,
        "prerequisites": list(prereq),
        "human_message": f"review {node_id}",
    }


def payload(nodes):
    return {
        "schema_version": 1,
        "roadmap_id": "test-roadmap",
        "enabled": True,
        "max_auto_claims_per_utc_day": 6,
        "nodes": nodes,
    }


def state_for(manifest, *, completed=None, blocked=None, active=None):
    return ShadowRoadmapState.from_legacy(
        {
            "roadmap_id": manifest.roadmap_id,
            "manifest_fingerprint": manifest.fingerprint,
            "completed_runs": completed or {},
            "blocked_nodes": {
                node: {"reason": "blocked"} for node in (blocked or [])
            },
            "active": active,
        },
        manifest,
    )


class PureRoadmapShadowTest(unittest.TestCase):
    def test_selection_matches_legacy_policy(self) -> None:
        raw = payload([
            task("a", 1, 100),
            task("b", 2, 90, ("a",)),
            task("c", 3, 95),
        ])
        legacy = legacy_policy.parse_manifest(raw)
        shadow = ShadowRoadmapManifest.from_mapping(raw)
        cases = [
            ({}, set()),
            ({"a": 1}, set()),
            ({"a": 1}, {"c"}),
        ]
        for completed, blocked in cases:
            with self.subTest(completed=completed, blocked=blocked):
                left = legacy_policy.select_next_node(
                    legacy,
                    completed_runs=completed,
                    blocked_nodes=blocked,
                )
                right = select_shadow_next_node(
                    shadow,
                    completed_runs=completed,
                    blocked_nodes=set(blocked),
                )
                self.assertEqual(
                    left.node_id if left else None,
                    right.node_id if right else None,
                )

    def test_closed_blocked_task_retires_to_max_runs_then_gate_is_selected(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(
            payload([
                task("repair", 10, 100, max_runs=2),
                gate("review", 90, ("repair",)),
            ])
        )
        state = state_for(manifest, blocked={"repair"})
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state,
            issue_truth={10: RoadmapIssueTruth.CLOSED},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.HUMAN_GATE)
        self.assertEqual(decision.selected_node_id, "review")
        self.assertEqual(decision.retired_closed_blocked, ("repair",))
        self.assertIn(("repair", 2), decision.projected_completed_runs)

    def test_blocked_gate_is_never_auto_retired(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(
            payload([gate("review", 100)])
        )
        state = state_for(manifest, blocked={"review"})
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state,
            issue_truth={},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.EXHAUSTED)
        self.assertEqual(decision.retired_closed_blocked, ())
        self.assertEqual(decision.projected_blocked_nodes, ("review",))

    def test_selected_closed_task_is_skipped_and_selection_continues(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(
            payload([
                task("a", 1, 100),
                task("b", 2, 90, ("a",)),
            ])
        )
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state_for(manifest),
            issue_truth={1: RoadmapIssueTruth.CLOSED, 2: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.TASK_ELIGIBLE)
        self.assertEqual(decision.selected_node_id, "b")
        self.assertEqual(decision.skipped_closed_selected, ("a",))

    def test_open_task_is_eligible_without_no_change_blocker(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(payload([task("a", 1, 100)]))
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state_for(manifest),
            issue_truth={1: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.TASK_ELIGIBLE)

    def test_no_change_blocker_is_conservative_block(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(payload([task("a", 1, 100)]))
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state_for(manifest),
            issue_truth={1: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(no_change_blocked_issues=(1,)),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.BLOCK)

    def test_unknown_selected_issue_truth_blocks(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(payload([task("a", 1, 100)]))
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=state_for(manifest),
            issue_truth={1: RoadmapIssueTruth.UNKNOWN},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(decision.disposition, RoadmapShadowDisposition.BLOCK)

    def test_state_manifest_mismatch_fails_closed(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(payload([task("a", 1, 100)]))
        with self.assertRaises(ValueError):
            ShadowRoadmapState.from_legacy(
                {
                    "roadmap_id": manifest.roadmap_id,
                    "manifest_fingerprint": "0" * 64,
                    "completed_runs": {},
                    "blocked_nodes": {},
                    "active": None,
                },
                manifest,
            )


class LiveRoadmapCollectorTest(unittest.TestCase):
    def test_issue_command_allowlist_is_exact_and_manifest_scoped(self) -> None:
        valid = [
            "gh", "issue", "view", "10",
            "--repo", "ni-da-ba/skyforge",
            "--json", live.ISSUE_JSON_FIELDS,
            "--jq=.",
        ]
        self.assertEqual(
            live.validate_readonly_issue_command(
                valid,
                repo="ni-da-ba/skyforge",
                allowed_issue_numbers={10, 11},
            ),
            tuple(valid),
        )
        forbidden = [
            ["gh", "issue", "close", "10", "--repo", "ni-da-ba/skyforge"],
            ["gh", "issue", "view", "99", "--repo", "ni-da-ba/skyforge", "--json", live.ISSUE_JSON_FIELDS, "--jq=."],
            ["gh", "issue", "view", "10", "--repo", "other/repo", "--json", live.ISSUE_JSON_FIELDS, "--jq=."],
            ["gh", "issue", "view", "10", "--repo", "ni-da-ba/skyforge", "--json", "state,body", "--jq=."],
        ]
        for command in forbidden:
            with self.subTest(command=command), self.assertRaises(ValueError):
                live.validate_readonly_issue_command(
                    command,
                    repo="ni-da-ba/skyforge",
                    allowed_issue_numbers={10, 11},
                )

    def test_collector_uses_code_root_manifest_and_state_root_state(self) -> None:
        raw = payload([
            task("repair", 10, 100, max_runs=2),
            gate("review", 90, ("repair",)),
        ])
        manifest = ShadowRoadmapManifest.from_mapping(raw)
        with tempfile.TemporaryDirectory() as code_td, tempfile.TemporaryDirectory() as state_td:
            code_root = Path(code_td)
            state_root = Path(state_td)
            manifest_path = code_root / live.MANIFEST_RELATIVE_PATH
            manifest_path.parent.mkdir(parents=True)
            manifest_path.write_text(json.dumps(raw), encoding="utf-8")
            state_dir = state_root / ".skyforge-orchestrator"
            state_dir.mkdir()
            state = {
                "roadmap": {
                    "roadmap_id": manifest.roadmap_id,
                    "manifest_fingerprint": manifest.fingerprint,
                    "completed_runs": {},
                    "blocked_nodes": {"repair": {"reason": "blocked"}},
                    "active": None,
                },
                "managed": {},
                "pending_events": [],
                "pending_decision": None,
                "pending_worker": None,
                "blocked_kind": None,
                "paused": False,
                "task_no_change_blockers": {},
            }
            (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")

            def gh(args, **kwargs):
                return subprocess.CompletedProcess(
                    args, 0, stdout=json.dumps({"state": "CLOSED", "title": "done"}), stderr=""
                )

            value = live.collect_roadmap_shadow(
                state_root=state_root,
                code_root=code_root,
                repo="ni-da-ba/skyforge",
                gh_runner=gh,
            )
        self.assertEqual(value["decision"]["disposition"], "HUMAN_GATE")
        self.assertEqual(value["decision"]["selected_node_id"], "review")
        self.assertEqual(value["decision"]["retired_closed_blocked"], ["repair"])

    def test_task_owned_managed_pr_truth_is_not_guessed(self) -> None:
        raw = payload([task("a", 10, 100)])
        manifest = ShadowRoadmapManifest.from_mapping(raw)
        with tempfile.TemporaryDirectory() as code_td, tempfile.TemporaryDirectory() as state_td:
            code_root = Path(code_td)
            state_root = Path(state_td)
            manifest_path = code_root / live.MANIFEST_RELATIVE_PATH
            manifest_path.parent.mkdir(parents=True)
            manifest_path.write_text(json.dumps(raw), encoding="utf-8")
            state_dir = state_root / ".skyforge-orchestrator"
            state_dir.mkdir()
            state = {
                "roadmap": {
                    "roadmap_id": manifest.roadmap_id,
                    "manifest_fingerprint": manifest.fingerprint,
                    "completed_runs": {},
                    "blocked_nodes": {},
                    "active": None,
                },
                "managed": {
                    "Implementation": {
                        "authority_key": "task:10",
                        "pr_number": 20,
                    }
                },
                "pending_events": [],
                "pending_decision": None,
                "pending_worker": None,
                "blocked_kind": None,
                "paused": False,
                "task_no_change_blockers": {},
            }
            (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")

            def gh(args, **kwargs):
                return subprocess.CompletedProcess(
                    args, 0, stdout=json.dumps({"state": "OPEN", "title": "open"}), stderr=""
                )

            value = live.collect_roadmap_shadow(
                state_root=state_root,
                code_root=code_root,
                repo="ni-da-ba/skyforge",
                gh_runner=gh,
            )
        self.assertEqual(value["decision"]["disposition"], "BLOCK")
        self.assertEqual(value["task_owned_managed_prs"], [20])

    def test_collector_source_has_no_mutating_surface(self) -> None:
        source = Path(live.__file__).read_text(encoding="utf-8")
        for token in (
            "write_text",
            "write_bytes",
            "gh issue close",
            "gh issue comment",
            "_roadmap_maybe_advance",
            "_post_gate",
            "enqueue(",
            "openai_codex",
            "WriterFence",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
