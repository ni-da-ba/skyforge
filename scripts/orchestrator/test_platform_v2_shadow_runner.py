from __future__ import annotations

import ast
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import platform_v2_shadow_runner as runner


def snapshot() -> dict:
    head = "a" * 40
    spec = "b" * 64
    return {
        "schema_version": 1,
        "managed": {
            "state": {
                "managed": {
                    "Implementation": {
                        "pr_number": 900,
                        "branch": "codex/task-900",
                        "authority_key": "task:900",
                        "expected_head": head,
                        "auto_merge_eligible": True,
                    }
                },
                "pending_worker": None,
                "human_gate_records": {},
                "paused": False,
            },
            "observation": {
                "lane": "Implementation",
                "pr_number": 900,
                "active_pr": True,
                "base_branch": "main",
                "head_branch": "codex/task-900",
                "current_head_sha": head,
                "evidence_sha": head,
                "reviewed_sha": head,
                "task_spec_hash": spec,
                "accepted_task_spec_hash": spec,
                "ci_state": "PASS",
                "pr_class": "DELIVERY",
            },
        },
        "quota": {
            "provider": {"authoritative": True, "allowed": True, "phase": "surplus"},
            "local": {"calls_used": 99, "daily_limit": 1},
        },
        "effect": {
            "identity": {
                "attempt_id": "attempt-900",
                "kind": "CREATE_PR",
                "subject": "task:900",
            },
            "status": "PENDING",
            "remote_identity": "",
            "observation": {"presence": "ABSENT", "remote_identity": ""},
        },
        "timer": {
            "now_epoch": 100.0,
            "requested_delay_seconds": 25.0,
            "existing_due_epoch": None,
            "existing_timer_alive": False,
        },
    }


class ShadowRunnerTest(unittest.TestCase):
    def test_render_is_deterministic_and_canonical(self) -> None:
        first = runner.render_report(snapshot())
        second = runner.render_report(snapshot())
        self.assertEqual(first, second)
        parsed = json.loads(first)
        self.assertEqual(parsed["schema_version"], 1)
        self.assertEqual(
            [entry["policy"] for entry in parsed["report"]["entries"]],
            ["managed_pr", "pending_timer", "quota", "remote_effect"],
        )

    def test_stdin_and_file_input_are_equivalent(self) -> None:
        serialized = json.dumps(snapshot())
        with mock.patch.object(runner.sys, "stdin", io.StringIO(serialized)):
            stdin_value = runner._read_snapshot(None)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "snapshot.json"
            path.write_text(serialized, encoding="utf-8")
            file_value = runner._read_snapshot(str(path))
        self.assertEqual(stdin_value, file_value)
        self.assertEqual(runner.render_report(stdin_value), runner.render_report(file_value))

    def test_unknown_schema_or_field_fails_closed(self) -> None:
        bad_version = snapshot()
        bad_version["schema_version"] = 2
        with self.assertRaises(ValueError):
            runner.shadow_input_from_mapping(bad_version)
        bad_field = snapshot()
        bad_field["execute"] = True
        with self.assertRaises(ValueError):
            runner.shadow_input_from_mapping(bad_field)

    def test_malformed_effect_or_quota_fails_closed(self) -> None:
        bad_effect = snapshot()
        bad_effect["effect"]["status"] = "EXECUTED"
        with self.assertRaises(ValueError):
            runner.shadow_input_from_mapping(bad_effect)
        bad_quota = snapshot()
        bad_quota["quota"]["provider"] = {"authoritative": "yes", "allowed": True}
        with self.assertRaises(ValueError):
            runner.shadow_input_from_mapping(bad_quota)

    def test_main_emits_report_only_to_stdout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "snapshot.json"
            path.write_text(json.dumps(snapshot()), encoding="utf-8")
            out = io.StringIO()
            err = io.StringIO()
            with mock.patch.object(runner.sys, "stdout", out), mock.patch.object(runner.sys, "stderr", err):
                code = runner.main(["--input", str(path)])
            self.assertEqual(code, 0)
            self.assertEqual(err.getvalue(), "")
            self.assertEqual(len(out.getvalue().strip().splitlines()), 1)
            json.loads(out.getvalue())

    def test_runner_capability_surface_is_read_only(self) -> None:
        source = Path(runner.__file__).read_text(encoding="utf-8")
        tree = ast.parse(source)
        forbidden_import_roots = {
            "subprocess",
            "socket",
            "urllib",
            "requests",
            "openai_codex",
            "skyforge_orchestrator",
        }
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        self.assertTrue(imported.isdisjoint(forbidden_import_roots), imported)

        forbidden_tokens = (
            "WriterFence",
            "JsonStateStoreAdapter",
            ".save(",
            "git push",
            "gh pr merge",
            "gh issue comment",
            "dispatch_worker",
            "reconcile_remote_effect(",  # runner evaluates shadow; it must not execute an effect
        )
        # Effect reconciliation is allowed only inside v2.shadow, not directly in the executable.
        for token in forbidden_tokens:
            self.assertNotIn(token, source)

    def test_no_v2_production_import_is_introduced(self) -> None:
        root = Path(runner.__file__).resolve().parent
        for path in [root / "skyforge_orchestrator.py", *sorted(root.glob("skyforge*_runtime.py"))]:
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("import v2", source, path.name)
            self.assertNotIn("from v2", source, path.name)


if __name__ == "__main__":
    unittest.main()
