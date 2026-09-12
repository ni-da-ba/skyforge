import importlib.util
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).with_name("sync_runtime_dependencies.py")
SPEC = importlib.util.spec_from_file_location("sync_runtime_dependencies", MODULE_PATH)
assert SPEC and SPEC.loader
deps = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = deps
SPEC.loader.exec_module(deps)

import hosted_jdk


class RuntimeDependencySyncTests(unittest.TestCase):
    def make_root(self, base: pathlib.Path, requirements: str = "example-package==1.0\n") -> pathlib.Path:
        root = base / "repo"
        path = root / "scripts" / "orchestrator"
        path.mkdir(parents=True)
        (path / "requirements.txt").write_text(requirements)
        return root

    def test_fingerprint_is_content_stable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(pathlib.Path(tmp))
            path = root / "scripts" / "orchestrator" / "requirements.txt"
            first = deps.requirements_fingerprint(path)
            second = deps.requirements_fingerprint(path)
            self.assertEqual(first, second)

    def test_unchanged_fingerprint_skips_pip(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(pathlib.Path(tmp))
            state = root / ".skyforge-orchestrator"
            state.mkdir()
            req = root / "scripts" / "orchestrator" / "requirements.txt"
            (state / "requirements.sha256").write_text(
                deps.requirements_fingerprint(req) + "\n"
            )

            with mock.patch.object(deps.subprocess, "run") as run:
                changed = deps.sync_runtime_dependencies(root)

            self.assertFalse(changed)
            run.assert_not_called()

    def test_changed_requirements_run_pip_then_record_fingerprint(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(pathlib.Path(tmp))
            req = root / "scripts" / "orchestrator" / "requirements.txt"
            expected = deps.requirements_fingerprint(req)

            with mock.patch.object(deps.subprocess, "run") as run:
                changed = deps.sync_runtime_dependencies(root)

            self.assertTrue(changed)
            run.assert_called_once()
            args = run.call_args.args[0]
            self.assertEqual(args[:4], [sys.executable, "-m", "pip", "install"])
            self.assertIn(str(req), args)
            self.assertEqual(
                (root / ".skyforge-orchestrator" / "requirements.sha256").read_text().strip(),
                expected,
            )

    def test_failed_pip_does_not_advance_fingerprint(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(pathlib.Path(tmp))
            fingerprint = root / ".skyforge-orchestrator" / "requirements.sha256"

            with mock.patch.object(
                deps.subprocess,
                "run",
                side_effect=subprocess.CalledProcessError(1, ["pip"]),
            ):
                with self.assertRaises(subprocess.CalledProcessError):
                    deps.sync_runtime_dependencies(root)

            self.assertFalse(fingerprint.exists())

    def test_hosted_clone_provisions_jdk_without_rerunning_pip(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(pathlib.Path(tmp))
            state = root / ".skyforge-orchestrator"
            state.mkdir()
            req = root / "scripts" / "orchestrator" / "requirements.txt"
            (state / "requirements.sha256").write_text(
                deps.requirements_fingerprint(req) + "\n"
            )

            with (
                mock.patch.dict(os.environ, {"SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE": "1"}),
                mock.patch.object(deps.subprocess, "run") as run,
                mock.patch.object(deps, "ensure_hosted_jdk", return_value=True) as ensure,
            ):
                changed = deps.sync_runtime_dependencies(root)

            self.assertTrue(changed)
            run.assert_not_called()
            ensure.assert_called_once_with(root)

    def test_worker_worktree_uses_shared_controller_toolchain(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = pathlib.Path(tmp)
            root = self.make_root(base)
            common_git = root / ".git"
            worker_git = common_git / "worktrees" / "worker"
            worker_git.mkdir(parents=True)
            (worker_git / "commondir").write_text("../..\n")

            worktree = base / "worker"
            worktree.mkdir()
            (worktree / ".git").write_text(f"gitdir: {worker_git}\n")

            self.assertEqual(
                hosted_jdk.java_home(worktree),
                root / ".skyforge-orchestrator" / "toolchains" / hosted_jdk.TOOLCHAIN_ID,
            )


if __name__ == "__main__":
    unittest.main()
