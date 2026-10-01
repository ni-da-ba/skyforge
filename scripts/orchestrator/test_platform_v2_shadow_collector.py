from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_shadow_collector as collector
import platform_v2_shadow_runner as shadow_runner


def write_state(root: Path, *, gate_token: str | None = None, pending_worker: bool = False) -> None:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir()
    gate_records = {}
    if gate_token is not None:
        gate_records["pr:900:implementation"] = {
            "token": gate_token,
            "target": "900",
            "seeded_from_github": False,
        }
    state = {
        "managed": {
            "Implementation": {
                "branch": "codex/task-900",
                "pr_number": 900,
                "authority_key": "task:900",
                "expected_head": "a" * 40,
                "auto_merge_eligible": True,
                "changed_paths": ["scripts/orchestrator/v2/core.py"],
            }
        },
        "human_gate_records": gate_records,
        "pending_worker": (
            {"branch": "codex/task-901", "authority_key": "task:901"}
            if pending_worker
            else None
        ),
        "paused": False,
    }
    (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")


def fake_gh(pr: dict):
    def run(args, **kwargs):
        return subprocess.CompletedProcess(args, 0, stdout=json.dumps(pr), stderr="")
    return run


def live_pr(*, state="OPEN", head=None, review="", checks=None):
    return {
        "state": state,
        "headRefName": "codex/task-900",
        "headRefOid": head or ("a" * 40),
        "baseRefName": "main",
        "reviewDecision": review,
        "statusCheckRollup": checks if checks is not None else [
            {"status": "COMPLETED", "conclusion": "SUCCESS", "name": "build"}
        ],
        "isDraft": False,
        "mergeStateStatus": "CLEAN",
    }


class ReadOnlyCollectorTest(unittest.TestCase):
    def collect(self, root: Path, pr: dict):
        return collector.collect_managed_snapshot(
            root=root,
            repo="ni-da-ba/skyforge",
            lane="Implementation",
            gh_runner=fake_gh(pr),
        )

    def test_green_live_pr_projects_without_inventing_review_or_task_identity(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            snapshot = self.collect(root, live_pr())
        observation = snapshot["managed"]["observation"]
        self.assertEqual(observation["ci_state"], "PASS")
        self.assertEqual(observation["evidence_sha"], "a" * 40)
        self.assertEqual(observation["reviewed_sha"], "")
        self.assertEqual(observation["task_spec_hash"], "")
        self.assertEqual(observation["accepted_task_spec_hash"], "")

        report = json.loads(shadow_runner.render_report(snapshot))
        entry = report["report"]["entries"][0]
        self.assertEqual(entry["policy"], "managed_pr")
        self.assertEqual(entry["disposition"], "RECONCILE")

    def test_pending_and_failed_ci_match_legacy_machine_green_semantics(self) -> None:
        pending_checks = [
            {"status": "IN_PROGRESS", "conclusion": "", "name": "build"},
            {"status": "COMPLETED", "conclusion": "SUCCESS", "name": "smoke"},
        ]
        failed_checks = [
            {"status": "COMPLETED", "conclusion": "SUCCESS", "name": "build"},
            {"status": "COMPLETED", "conclusion": "FAILURE", "name": "smoke"},
        ]
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            pending = self.collect(root, live_pr(checks=pending_checks))
            failed = self.collect(root, live_pr(checks=failed_checks))

        self.assertEqual(pending["managed"]["observation"]["ci_state"], "PENDING")
        self.assertEqual(failed["managed"]["observation"]["ci_state"], "FAIL")
        self.assertEqual(
            json.loads(shadow_runner.render_report(pending))["report"]["entries"][0]["disposition"],
            "WAIT",
        )
        self.assertEqual(
            json.loads(shadow_runner.render_report(failed))["report"]["entries"][0]["disposition"],
            "REPAIR_ELIGIBLE",
        )

    def test_skipped_or_neutral_only_is_not_machine_green(self) -> None:
        checks = [
            {"status": "COMPLETED", "conclusion": "SKIPPED", "name": "optional"},
            {"status": "COMPLETED", "conclusion": "NEUTRAL", "name": "advisory"},
        ]
        self.assertEqual(collector._ci_state(checks), "UNKNOWN")

    def test_current_gate_token_sets_human_gate_but_stale_token_does_not(self) -> None:
        head = "a" * 40
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, gate_token=f"{head}:OPEN")
            current = self.collect(root, live_pr(head=head))
        self.assertTrue(current["managed"]["observation"]["human_gate_pending"])
        self.assertEqual(current["managed"]["observation"]["pr_class"], "HUMAN_GATE")

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, gate_token=f"{'b' * 40}:OPEN")
            stale = self.collect(root, live_pr(head=head))
        self.assertFalse(stale["managed"]["observation"]["human_gate_pending"])
        self.assertEqual(stale["managed"]["observation"]["pr_class"], "DELIVERY")

    def test_github_review_requirement_is_human_gate(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            snapshot = self.collect(root, live_pr(review="CHANGES_REQUESTED"))
        observation = snapshot["managed"]["observation"]
        self.assertTrue(observation["review_required"])
        self.assertEqual(observation["pr_class"], "HUMAN_GATE")
        self.assertEqual(
            json.loads(shadow_runner.render_report(snapshot))["report"]["entries"][0]["disposition"],
            "HUMAN_GATE",
        )

    def test_global_pending_worker_remains_visible_to_v2_core(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, pending_worker=True)
            snapshot = self.collect(root, live_pr())
        self.assertIsNotNone(snapshot["managed"]["state"]["pending_worker"])
        self.assertEqual(
            json.loads(shadow_runner.render_report(snapshot))["report"]["entries"][0]["disposition"],
            "WAIT",
        )

    def test_missing_or_malformed_state_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(ValueError):
                collector.read_legacy_state(Path(td))
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            state_dir = root / ".skyforge-orchestrator"
            state_dir.mkdir()
            (state_dir / "state.json").write_text("[]", encoding="utf-8")
            with self.assertRaises(ValueError):
                collector.read_legacy_state(root)

    def test_readonly_command_allowlist_rejects_mutation_and_broader_reads(self) -> None:
        valid = [
            "gh", "pr", "view", "900",
            "--repo", "ni-da-ba/skyforge",
            "--json", collector.PR_JSON_FIELDS,
            "--jq=.",
        ]
        self.assertEqual(collector.validate_readonly_gh_command(valid), tuple(valid))
        forbidden = [
            ["gh", "pr", "merge", "900", "--repo", "ni-da-ba/skyforge"],
            ["gh", "pr", "ready", "900", "--repo", "ni-da-ba/skyforge"],
            ["gh", "issue", "comment", "900", "--repo", "ni-da-ba/skyforge"],
            ["gh", "api", "repos/ni-da-ba/skyforge/pulls/900"],
            ["gh", "pr", "diff", "900", "--repo", "ni-da-ba/skyforge"],
        ]
        for command in forbidden:
            with self.subTest(command=command), self.assertRaises(ValueError):
                collector.validate_readonly_gh_command(command)

    def test_exact_branch_and_pr_identity_are_not_normalized_away(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            pr = live_pr()
            pr["headRefName"] = "unexpected/branch"
            snapshot = self.collect(root, pr)
        report = json.loads(shadow_runner.render_report(snapshot))
        self.assertEqual(report["report"]["entries"][0]["disposition"], "RECONCILE")

    def test_collector_source_has_no_local_write_or_production_import_surface(self) -> None:
        import ast

        source_path = Path(collector.__file__)
        source = source_path.read_text(encoding="utf-8")
        tree = ast.parse(source)

        forbidden_calls = {
            "write_text",
            "write_bytes",
            "unlink",
            "rename",
            "replace",
            "mkdir",
            "touch",
        }
        seen_calls = {
            node.func.attr
            for node in ast.walk(tree)
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
        }
        self.assertTrue(seen_calls.isdisjoint(forbidden_calls), seen_calls & forbidden_calls)
        self.assertNotIn("skyforge_orchestrator", source)
        self.assertNotIn("from v2", source)
        self.assertNotIn("import v2", source)
        self.assertNotIn("shell=True", source)

    def test_production_runtime_does_not_import_shadow_collector(self) -> None:
        root = Path(collector.__file__).resolve().parent
        for path in [root / "skyforge_orchestrator.py", *sorted(root.glob("skyforge*_runtime.py"))]:
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("platform_v2_shadow_collector", source, path.name)

    def test_rendered_snapshot_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            first = self.collect(root, live_pr())
            second = self.collect(root, live_pr())
        self.assertEqual(collector.render_snapshot(first), collector.render_snapshot(second))


if __name__ == "__main__":
    unittest.main()
