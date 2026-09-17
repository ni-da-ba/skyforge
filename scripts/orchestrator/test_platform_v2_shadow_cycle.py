from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_shadow_cycle as cycle


MAIN = "c" * 40
HEAD = "a" * 40


def write_state(root: Path, *, managed=None, pending_decision=None) -> None:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir()
    state = {
        "managed": managed if managed is not None else {},
        "human_gate_records": {},
        "pending_worker": None,
        "paused": False,
        "pending_decision": pending_decision,
    }
    (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")


def managed_record():
    return {
        "Implementation": {
            "branch": "codex/task-900",
            "pr_number": 900,
            "authority_key": "task:900",
            "expected_head": HEAD,
            "auto_merge_eligible": True,
            "changed_paths": [],
        }
    }


def legacy_dispatch():
    return {
        "decision": {
            "decision": "DISPATCH",
            "lane": "Implementation",
            "pr_number": 900,
            "objective": "repair bounded PR",
            "stop_boundary": "checks green",
            "reusable_evidence": None,
            "worker_tier": "TERRA",
            "allowed_paths": None,
            "reason": "repair failed checks",
            "human_message": None,
        },
        "event_keys": [],
        "authority_event_keys": [],
        "ordinary_event_keys": [],
        "task_issue_numbers": [],
        "captured_at": "2026-09-17T23:00:00+00:00",
        "snapshot_main": MAIN,
        "source_pr_head": HEAD,
    }


def fake_git(args, **kwargs):
    return subprocess.CompletedProcess(args, 0, stdout=MAIN + "\n", stderr="")


def fake_gh(args, **kwargs):
    pr = {
        "state": "OPEN",
        "headRefName": "codex/task-900",
        "headRefOid": HEAD,
        "baseRefName": "main",
        "reviewDecision": "",
        "statusCheckRollup": [
            {"status": "COMPLETED", "conclusion": "FAILURE", "name": "build"}
        ],
        "isDraft": False,
        "mergeStateStatus": "CLEAN",
    }
    return subprocess.CompletedProcess(args, 0, stdout=json.dumps(pr), stderr="")


class HostedShadowCycleTest(unittest.TestCase):
    def test_cycle_composes_collector_runner_and_parity_deterministically(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, managed=managed_record(), pending_decision=legacy_dispatch())
            first = cycle.run_shadow_cycle(
                root=root,
                repo="ni-da-ba/skyforge",
                git_runner=fake_git,
                gh_runner=fake_gh,
            )
            second = cycle.run_shadow_cycle(
                root=root,
                repo="ni-da-ba/skyforge",
                git_runner=fake_git,
                gh_runner=fake_gh,
            )
        self.assertEqual(first, second)
        self.assertEqual(first["managed_lane_count"], 1)
        self.assertEqual(first["summary"], {"AGREE": 1})
        self.assertEqual(
            first["samples"][0]["parity"]["sample"]["classification"],
            "AGREE",
        )
        self.assertEqual(
            first["samples"][0]["shadow_report"]["report"]["entries"][0]["disposition"],
            "REPAIR_ELIGIBLE",
        )

    def test_empty_managed_set_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            value = cycle.run_shadow_cycle(
                root=root,
                repo="ni-da-ba/skyforge",
                git_runner=fake_git,
                gh_runner=fake_gh,
            )
        self.assertEqual(value["managed_lane_count"], 0)
        self.assertEqual(value["samples"], [])
        self.assertEqual(value["summary"], {})

    def test_malformed_managed_lane_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, managed={"Implementation": []})
            with self.assertRaises(ValueError):
                cycle.run_shadow_cycle(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    git_runner=fake_git,
                    gh_runner=fake_gh,
                )

    def test_git_allowlist_is_exact(self) -> None:
        self.assertEqual(
            cycle.validate_readonly_git_command(["git", "rev-parse", "HEAD"]),
            ("git", "rev-parse", "HEAD"),
        )
        for command in (
            ["git", "fetch", "origin"],
            ["git", "status"],
            ["git", "rev-parse", "origin/main"],
            ["git", "push"],
        ):
            with self.subTest(command=command), self.assertRaises(ValueError):
                cycle.validate_readonly_git_command(command)

    def test_bad_head_identity_fails_closed(self) -> None:
        def bad_git(args, **kwargs):
            return subprocess.CompletedProcess(args, 0, stdout="not-a-sha\n", stderr="")
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root)
            with self.assertRaises(ValueError):
                cycle.run_shadow_cycle(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    git_runner=bad_git,
                    gh_runner=fake_gh,
                )

    def test_cycle_source_has_no_file_write_or_mutation_command_surface(self) -> None:
        source = Path(cycle.__file__).read_text(encoding="utf-8")
        for token in (
            "write_text",
            "write_bytes",
            "unlink(",
            "rename(",
            "replace(",
            "git push",
            "gh pr merge",
            "gh issue comment",
            "openai_codex",
            "WriterFence",
            "skyforge_control_plane_runtime",
        ):
            self.assertNotIn(token, source)

    def test_systemd_shadow_unit_is_read_only_and_separate(self) -> None:
        root = Path(__file__).resolve().parents[2]
        service = (root / "deploy/orchestrator/skyforge-v2-shadow.service.in").read_text()
        timer = (root / "deploy/orchestrator/skyforge-v2-shadow.timer").read_text()
        self.assertIn("platform_v2_shadow_cycle.py", service)
        self.assertIn("ProtectSystem=strict", service)
        self.assertIn("ProtectHome=read-only", service)
        self.assertIn("ReadOnlyPaths=@@ROOT@@", service)
        self.assertIn("PYTHONDONTWRITEBYTECODE=1", service)
        self.assertNotIn("ReadWritePaths", service)
        self.assertNotIn("skyforge_control_plane_runtime.py", service)
        self.assertNotIn("EnvironmentFile=/etc/skyforge-orchestrator/env", service)
        self.assertIn("OnUnitActiveSec=15m", timer)
        self.assertIn("skyforge-v2-shadow.service", timer)

    def test_install_and_remove_are_explicit_and_do_not_touch_production_service(self) -> None:
        root = Path(__file__).resolve().parents[2]
        install = (root / "scripts/orchestrator/install_v2_shadow.sh").read_text()
        remove = (root / "scripts/orchestrator/remove_v2_shadow.sh").read_text()
        self.assertIn("--confirm-read-only", install)
        self.assertIn("skyforge-v2-shadow.service", install)
        self.assertIn("skyforge-v2-shadow.timer", install)
        self.assertNotIn("restart skyforge-orchestrator.service", install)
        self.assertNotIn("Caddyfile", install)
        self.assertNotIn("/hooks", install)
        self.assertIn("--confirm", remove)
        self.assertNotIn("skyforge-orchestrator.service", remove)


if __name__ == "__main__":
    unittest.main()
