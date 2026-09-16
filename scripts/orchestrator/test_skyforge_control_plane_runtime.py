import importlib
import pathlib
import sys
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

    def test_worker_instructions_are_edit_only(self):
        instructions = runtime.core.WORKER_INSTRUCTIONS
        self.assertIn("HOSTED EXECUTION BOUNDARY — EDITING ONLY", instructions)
        self.assertIn("Automated project verification belongs to GitHub Actions", instructions)
        self.assertIn("Do NOT run project builds", instructions)
        self.assertIn("with_hosted_jdk.py", instructions)
        self.assertNotIn(
            "Make local source/test/doc changes and run appropriate local verification.",
            instructions,
        )

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
