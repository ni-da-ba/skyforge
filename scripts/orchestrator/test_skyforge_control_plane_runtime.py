import importlib
import pathlib
import sys
import tempfile
import threading
import unittest
from unittest import mock


MODULE_DIR = pathlib.Path(__file__).resolve().parent
REPO_ROOT = MODULE_DIR.parent.parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

runtime = importlib.import_module("skyforge_control_plane_runtime")


class LightweightHostedEditingRuntimeTests(unittest.TestCase):
    def test_hosted_entrypoint_installs_closed_active_issue_recovery(self):
        recovery = runtime.roadmap_closed_issue_recovery_runtime
        self.assertTrue(
            getattr(runtime.roadmap_runtime, "_skyforge_closed_active_issue_recovery_installed", False)
        )
        self.assertIs(
            runtime.roadmap_runtime._roadmap_resolve_active,
            recovery._resolve_active_closed_issue,
        )

    def test_worker_instructions_are_edit_only_and_checkpoint_first(self):
        instructions = runtime.core.WORKER_INSTRUCTIONS
        self.assertIn("HOSTED EXECUTION BOUNDARY — EDITING ONLY", instructions)
        self.assertIn("Automated project verification belongs to GitHub Actions", instructions)
        self.assertIn("Do NOT run project builds", instructions)
        self.assertIn("with_hosted_jdk.py", instructions)
        self.assertIn("docs/agent-state/CURRENT_PROJECT_STATE.md", instructions)
        self.assertNotIn(
            "Make local source/test/doc changes and run appropriate local verification.",
            instructions,
        )
        self.assertIn(runtime.CURRENT_PROJECT_STATE_PATH, runtime.core.PROTECTED_WORKER_PATHS)

    def test_worker_prompt_is_guarded_even_when_replayed_text_is_stale(self):
        stale_prompt = (
            "Persist the bounded result as local file changes and tests.\n"
            "On the hosted worker, use scripts/orchestrator/with_hosted_jdk.py for Gradle verification."
        )
        with mock.patch.object(runtime, "_ORIGINAL_WORKER", return_value="done") as original:
            result = runtime._edit_only_worker(object(), stale_prompt, "TERRA", pathlib.Path("/tmp/work"))

        self.assertEqual(result, "done")
        forwarded = original.call_args.args[1]
        self.assertIn("GitHub Actions owns automated validation", forwarded)
        self.assertIn("Do NOT run project builds", forwarded)
        self.assertIn("Do not download or provision a project JDK/toolchain", forwarded)

    def test_blocked_gate_keeps_identity_but_refreshes_manifest_message(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            orchestrator = runtime.core.Orchestrator.__new__(runtime.core.Orchestrator)
            orchestrator.root = root
            orchestrator.state = runtime.core.LocalState(root / "state.json")
            orchestrator._state_lock = threading.RLock()

            old_manifest = runtime.roadmap_runtime.roadmap_policy.parse_manifest(
                {
                    "schema_version": 1,
                    "roadmap_id": "gate-refresh-test",
                    "enabled": True,
                    "max_auto_claims_per_utc_day": 1,
                    "nodes": [
                        {
                            "id": "human-gate",
                            "kind": "gate",
                            "lane": "Implementation",
                            "priority": 1,
                            "max_runs": 1,
                            "prerequisites": [],
                            "human_message": "Old review wording.",
                        }
                    ],
                }
            )
            state = runtime._ORIGINAL_ROADMAP_STATE_LOCKED(orchestrator, old_manifest)
            state["blocked_nodes"] = {
                "human-gate": {"at": "2026-09-17T00:00:00+00:00", "reason": "Old review wording."}
            }
            orchestrator.state.save()

            new_manifest = runtime.roadmap_runtime.roadmap_policy.parse_manifest(
                {
                    "schema_version": 1,
                    "roadmap_id": "gate-refresh-test",
                    "enabled": True,
                    "max_auto_claims_per_utc_day": 1,
                    "nodes": [
                        {
                            "id": "human-gate",
                            "kind": "gate",
                            "lane": "Implementation",
                            "priority": 1,
                            "max_runs": 1,
                            "prerequisites": [],
                            "human_message": "Current review wording.",
                        }
                    ],
                }
            )
            refreshed = runtime._roadmap_state_locked_with_current_gate_messages(
                orchestrator, new_manifest
            )

            gate = refreshed["blocked_nodes"]["human-gate"]
            self.assertEqual(gate["at"], "2026-09-17T00:00:00+00:00")
            self.assertEqual(gate["reason"], "Current review wording.")
            self.assertIn("last_gate_message_refresh_at", refreshed)
            self.assertEqual(refreshed["manifest_fingerprint"], new_manifest.fingerprint)

    def test_terminal_gate_quiescence_retires_only_ordinary_noise(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            orchestrator = runtime.core.Orchestrator.__new__(runtime.core.Orchestrator)
            orchestrator.root = root
            orchestrator.state = runtime.core.LocalState(root / "state.json")
            orchestrator._state_lock = threading.RLock()

            manifest = runtime.roadmap_runtime.roadmap_policy.parse_manifest(
                {
                    "schema_version": 1,
                    "roadmap_id": "terminal-gate-test",
                    "enabled": True,
                    "max_auto_claims_per_utc_day": 1,
                    "nodes": [
                        {
                            "id": "task-a",
                            "kind": "task",
                            "lane": "Implementation",
                            "issue_number": 1,
                            "priority": 2,
                            "max_runs": 1,
                            "prerequisites": [],
                            "objective_hint": "finish task",
                            "stop_boundary": "stop",
                        },
                        {
                            "id": "human-gate",
                            "kind": "gate",
                            "lane": "Implementation",
                            "priority": 1,
                            "max_runs": 1,
                            "prerequisites": ["task-a"],
                            "human_message": "Review it.",
                        },
                    ],
                }
            )
            orchestrator.state.data["roadmap"] = {
                "roadmap_id": manifest.roadmap_id,
                "manifest_fingerprint": manifest.fingerprint,
                "completed_runs": {"task-a": 1},
                "blocked_nodes": {
                    "human-gate": {
                        "at": "2026-09-17T00:00:00+00:00",
                        "reason": "Review it.",
                    }
                },
                "active": None,
            }
            ordinary = runtime.core.EventDecision(
                actionable=True,
                reason="workflow completed",
                event="workflow_run",
                action="completed",
                source_id="ordinary-1",
            ).to_state()
            orchestrator.state.data["pending_events"] = [ordinary]
            orchestrator.state.save()

            with mock.patch.object(runtime.roadmap_runtime, "_roadmap_manifest", return_value=manifest):
                snapshot = runtime._terminal_human_gate_snapshot(orchestrator)
                self.assertTrue(snapshot["latched"])
                with orchestrator._state_lock:
                    retired = runtime._retire_terminal_gate_noise_locked(orchestrator)

            self.assertEqual(retired, 1)
            self.assertEqual(orchestrator.state.data["pending_events"], [])
            self.assertEqual(
                orchestrator.state.data["metrics"]["terminal_gate_events_retired_model_free"],
                1,
            )

            protected = runtime.core.EventDecision(
                actionable=True,
                reason="explicit task authority",
                event="issue_comment",
                action="created",
                pr_number=2,
                source_id="protected-1",
                signal_kind="task",
                signal_text="Do the bounded task.",
            ).to_state()
            orchestrator.state.data["pending_events"] = [protected]
            orchestrator.state.save()
            with orchestrator._state_lock:
                self.assertTrue(runtime._protected_pending_authority_exists_locked(orchestrator))
                self.assertEqual(runtime._retire_terminal_gate_noise_locked(orchestrator), 0)
            self.assertEqual(orchestrator.state.data["pending_events"], [protected])

    def test_blocked_task_prevents_terminal_gate_latch_and_delegates_to_roadmap_drain(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            orchestrator = runtime.core.Orchestrator.__new__(runtime.core.Orchestrator)
            orchestrator.root = root
            orchestrator.state = runtime.core.LocalState(root / "state.json")
            orchestrator._state_lock = threading.RLock()

            manifest = runtime.roadmap_runtime.roadmap_policy.parse_manifest(
                {
                    "schema_version": 1,
                    "roadmap_id": "terminal-gate-reconcile-test",
                    "enabled": True,
                    "max_auto_claims_per_utc_day": 2,
                    "nodes": [
                        {
                            "id": "old-gate",
                            "kind": "gate",
                            "lane": "Implementation",
                            "priority": 100,
                            "max_runs": 1,
                            "prerequisites": [],
                            "human_message": "Older review gate.",
                        },
                        {
                            "id": "repair",
                            "kind": "task",
                            "lane": "Implementation",
                            "issue_number": 10,
                            "priority": 90,
                            "max_runs": 1,
                            "prerequisites": [],
                            "objective_hint": "repair",
                            "stop_boundary": "stop",
                        },
                        {
                            "id": "new-gate",
                            "kind": "gate",
                            "lane": "Implementation",
                            "priority": 80,
                            "max_runs": 1,
                            "prerequisites": ["repair"],
                            "human_message": "New review gate.",
                        },
                    ],
                }
            )
            orchestrator.state.data["roadmap"] = {
                "roadmap_id": manifest.roadmap_id,
                "manifest_fingerprint": manifest.fingerprint,
                "completed_runs": {},
                "blocked_nodes": {
                    "old-gate": {"reason": "Older review gate."},
                    "repair": {"reason": "awaiting out-of-band completion"},
                },
                "active": None,
            }
            orchestrator.state.data["pending_events"] = []
            orchestrator.state.save()

            with (
                mock.patch.object(runtime.roadmap_runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_ORIGINAL_DRAIN_AND_DISPATCH") as original,
            ):
                snapshot = runtime._terminal_human_gate_snapshot(orchestrator)
                self.assertFalse(snapshot["latched"])
                self.assertIn("blocked roadmap task requires", snapshot["reason"])
                runtime._terminal_gate_quiescent_drain(orchestrator)

            original.assert_called_once_with(orchestrator)

    def test_manifest_change_breaks_terminal_gate_quiescence(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            orchestrator = runtime.core.Orchestrator.__new__(runtime.core.Orchestrator)
            orchestrator.root = root
            orchestrator.state = runtime.core.LocalState(root / "state.json")
            orchestrator._state_lock = threading.RLock()
            manifest = runtime.roadmap_runtime.roadmap_policy.parse_manifest(
                {
                    "schema_version": 1,
                    "roadmap_id": "manifest-change-test",
                    "enabled": True,
                    "max_auto_claims_per_utc_day": 1,
                    "nodes": [
                        {
                            "id": "human-gate",
                            "kind": "gate",
                            "lane": "Audit",
                            "priority": 1,
                            "max_runs": 1,
                            "prerequisites": [],
                            "human_message": "Review it.",
                        }
                    ],
                }
            )
            orchestrator.state.data["roadmap"] = {
                "roadmap_id": manifest.roadmap_id,
                "manifest_fingerprint": "old-fingerprint",
                "completed_runs": {},
                "blocked_nodes": {"human-gate": {"reason": "Review it."}},
                "active": None,
            }
            with mock.patch.object(runtime.roadmap_runtime, "_roadmap_manifest", return_value=manifest):
                snapshot = runtime._terminal_human_gate_snapshot(orchestrator)
            self.assertFalse(snapshot["latched"])
            self.assertEqual(snapshot["reason"], "roadmap manifest changed")

    def test_service_has_resource_and_java_backstops(self):
        service = (REPO_ROOT / "deploy" / "orchestrator" / "skyforge-orchestrator.service.in").read_text()
        self.assertIn("CPUQuota=85%", service)
        self.assertIn("MemoryHigh=1200M", service)
        self.assertIn("MemoryMax=1600M", service)
        self.assertIn("JAVA_HOME=/nonexistent/skyforge-project-java-is-github-actions-only", service)
        self.assertIn("InaccessiblePaths=-@@ROOT@@/.skyforge-orchestrator/toolchains", service)
        self.assertIn("-/usr/lib/jvm", service)

    def test_legacy_hosted_jdk_helpers_are_absent(self):
        self.assertFalse((MODULE_DIR / "hosted_jdk.py").exists())
        self.assertFalse((MODULE_DIR / "with_hosted_jdk.py").exists())

    def test_runtime_reports_no_github_cloud_agent_requirement(self):
        source = pathlib.Path(runtime.__file__).read_text()
        self.assertIn('"github_cloud_agent_required": False', source)
        self.assertIn('"hosted_project_validation_enabled": False', source)
        self.assertIn('"hosted_project_workers_enabled": True', source)
        self.assertNotIn("copilot-swe-agent", source)


if __name__ == "__main__":
    unittest.main()
