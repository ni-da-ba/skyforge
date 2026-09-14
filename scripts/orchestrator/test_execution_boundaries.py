import pathlib
import sys
import tempfile
import unittest

ORCHESTRATOR_DIR = pathlib.Path(__file__).resolve().parent
ROOT = ORCHESTRATOR_DIR.parents[1]
if str(ORCHESTRATOR_DIR) not in sys.path:
    sys.path.insert(0, str(ORCHESTRATOR_DIR))

import hosted_jdk
import skyforge_execution_boundary_runtime as boundary_runtime


class ExecutionBoundaryTests(unittest.TestCase):
    def test_boundary_runtime_overrides_hosted_worker_contract(self):
        instructions = boundary_runtime.core.WORKER_INSTRUCTIONS
        self.assertIn("DO NOT run project builds", instructions)
        self.assertIn("GitHub Actions is the authority", instructions)
        self.assertIn("project owner's local machine", instructions)
        self.assertIn(boundary_runtime.RUNTIME_PATH, boundary_runtime.core.CONTROLLER_RUNTIME_PATHS)
        self.assertIn(boundary_runtime.BOUNDARY_PATH, boundary_runtime.core.PROTECTED_WORKER_PATHS)

    def test_service_launches_boundary_runtime(self):
        service = (ROOT / "deploy" / "orchestrator" / "skyforge-orchestrator.service.in").read_text()
        self.assertIn("skyforge_execution_boundary_runtime.py", service)
        self.assertNotIn("ExecStart=@@VENV_PYTHON@@ @@ROOT@@/scripts/orchestrator/skyforge_roadmap_runtime.py", service)

    def test_startup_dependency_sync_has_no_project_jdk_bootstrap(self):
        source = (ORCHESTRATOR_DIR / "sync_runtime_dependencies.py").read_text()
        self.assertNotIn("ensure_hosted_jdk", source)
        self.assertNotIn("hosted_jdk", source)
        self.assertNotIn("JAVA_HOME", source)

    def test_legacy_hosted_jdk_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            with self.assertRaisesRegex(RuntimeError, "GitHub Actions"):
                hosted_jdk.ensure_hosted_jdk(root)
            with self.assertRaisesRegex(RuntimeError, "GitHub Actions"):
                hosted_jdk.java_home(root)
            with self.assertRaisesRegex(RuntimeError, "GitHub Actions"):
                hosted_jdk.toolchain_env(root)

    def test_policy_names_all_three_execution_surfaces(self):
        policy = (ROOT / "docs" / "agent-state" / "EXECUTION_BOUNDARIES.md").read_text()
        self.assertIn("DigitalOcean `skyforge-orchestrator` droplet", policy)
        self.assertIn("GitHub / GitHub Actions", policy)
        self.assertIn("Project owner's local machine", policy)


if __name__ == "__main__":
    unittest.main()
