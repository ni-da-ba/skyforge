from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.repository_sync import (
    RepositorySyncDisposition,
    checkout_is_activation_compatible,
    runtime_critical_changes,
    sync_repository_snapshot,
)


REPO = "ni-da-ba/skyforge"


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(root), *args],
        text=True,
        capture_output=True,
        check=check,
    )


def git_text(root: Path, *args: str) -> str:
    return git(root, *args).stdout.strip()


class RemoteMainRunner:
    def __init__(self, sha: str):
        self.sha = sha
        self.calls = 0

    def __call__(self, args, **kwargs):
        expected = [
            "gh",
            "api",
            f"repos/{REPO}/commits/main",
            "--jq",
            ".sha",
        ]
        if list(args) != expected:
            raise AssertionError(f"unexpected remote-main command: {args}")
        self.calls += 1
        return subprocess.CompletedProcess(args, 0, stdout=self.sha + "\n", stderr="")


def fixture(tmp: Path):
    bare = tmp / "remote.git"
    author = tmp / "author"
    prod = tmp / "prod"

    subprocess.run(
        ["git", "init", "--bare", "--initial-branch=main", str(bare)],
        text=True,
        capture_output=True,
        check=True,
    )
    author.mkdir()
    git(author, "init", "-b", "main")
    git(author, "config", "user.email", "test@example.invalid")
    git(author, "config", "user.name", "Skyforge Test")

    files = {
        "docs/state.md": "baseline\n",
        "scripts/orchestrator/platform_v2_hosted_runtime.py": "# runtime baseline\n",
        "scripts/orchestrator/v2/base.py": "# v2 baseline\n",
        "scripts/orchestrator/requirements.txt": "example==1\n",
        "scripts/orchestrator/sync_runtime_dependencies.py": "# sync baseline\n",
        "scripts/orchestrator/install_hosted.sh": "#!/bin/sh\n",
        "scripts/orchestrator/stage_platform_v2_cutover.sh": "#!/bin/sh\n",
        "scripts/orchestrator/platform_v2_operator_cutover.py": "# operator baseline\n",
        "scripts/orchestrator/platform_v2_routine_upgrade.py": "# upgrade baseline\n",
        "deploy/orchestrator/skyforge-orchestrator-v2.service.in": "[Service]\n",
    }
    for rel, text in files.items():
        path = author / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    git(author, "add", ".")
    git(author, "commit", "-m", "baseline")
    git(author, "remote", "add", "origin", str(bare))
    git(author, "push", "-u", "origin", "main")
    baseline = git_text(author, "rev-parse", "HEAD")

    subprocess.run(
        ["git", "clone", "--quiet", str(bare), str(prod)],
        text=True,
        capture_output=True,
        check=True,
    )
    return bare, author, prod, baseline


class PlatformV2RepositorySyncTest(unittest.TestCase):
    def test_docs_only_main_advance_fast_forwards_without_runtime_change(self):
        with tempfile.TemporaryDirectory() as td:
            _bare, author, prod, baseline = fixture(Path(td))
            (author / "docs/state.md").write_text("updated docs\n", encoding="utf-8")
            git(author, "add", "docs/state.md")
            git(author, "commit", "-m", "docs update")
            git(author, "push", "origin", "main")
            target = git_text(author, "rev-parse", "HEAD")

            self.assertEqual(runtime_critical_changes(prod, baseline, target), ())
            result = sync_repository_snapshot(
                root=prod,
                repo=REPO,
                activation_baseline_sha=baseline,
                remote_runner=RemoteMainRunner(target),
            )

            self.assertEqual(result.disposition, RepositorySyncDisposition.FAST_FORWARDED)
            self.assertEqual(git_text(prod, "rev-parse", "HEAD"), target)
            compatible, changed, _reason = checkout_is_activation_compatible(
                root=prod,
                activation_baseline_sha=baseline,
                checkout_sha=target,
            )
            self.assertTrue(compatible)
            self.assertEqual(changed, ())

            again = sync_repository_snapshot(
                root=prod,
                repo=REPO,
                activation_baseline_sha=baseline,
                remote_runner=RemoteMainRunner(target),
            )
            self.assertEqual(again.disposition, RepositorySyncDisposition.CURRENT)

    def test_runtime_change_requires_reviewed_upgrade_and_does_not_move_checkout(self):
        with tempfile.TemporaryDirectory() as td:
            _bare, author, prod, baseline = fixture(Path(td))
            runtime = author / "scripts/orchestrator/v2/base.py"
            runtime.write_text("# changed runtime\n", encoding="utf-8")
            git(author, "add", "scripts/orchestrator/v2/base.py")
            git(author, "commit", "-m", "runtime update")
            git(author, "push", "origin", "main")
            target = git_text(author, "rev-parse", "HEAD")

            result = sync_repository_snapshot(
                root=prod,
                repo=REPO,
                activation_baseline_sha=baseline,
                remote_runner=RemoteMainRunner(target),
            )

            self.assertEqual(result.disposition, RepositorySyncDisposition.UPGRADE_REQUIRED)
            self.assertIn("scripts/orchestrator/v2/base.py", result.changed_runtime_paths)
            self.assertEqual(git_text(prod, "rev-parse", "HEAD"), baseline)
            compatible, changed, _reason = checkout_is_activation_compatible(
                root=prod,
                activation_baseline_sha=baseline,
                checkout_sha=target,
            )
            self.assertFalse(compatible)
            self.assertIn("scripts/orchestrator/v2/base.py", changed)

    def test_dirty_checkout_blocks_before_fetch_or_checkout(self):
        with tempfile.TemporaryDirectory() as td:
            _bare, author, prod, baseline = fixture(Path(td))
            (author / "docs/state.md").write_text("remote docs\n", encoding="utf-8")
            git(author, "add", "docs/state.md")
            git(author, "commit", "-m", "remote docs")
            git(author, "push", "origin", "main")
            target = git_text(author, "rev-parse", "HEAD")

            (prod / "docs/state.md").write_text("local dirty\n", encoding="utf-8")
            remote = RemoteMainRunner(target)
            result = sync_repository_snapshot(
                root=prod,
                repo=REPO,
                activation_baseline_sha=baseline,
                remote_runner=remote,
            )

            self.assertEqual(result.disposition, RepositorySyncDisposition.BLOCKED)
            self.assertIn("dirty", result.reason)
            self.assertEqual(remote.calls, 0)
            self.assertEqual(git_text(prod, "rev-parse", "HEAD"), baseline)

    def test_divergent_checkout_blocks_without_discarding_local_commit(self):
        with tempfile.TemporaryDirectory() as td:
            _bare, author, prod, baseline = fixture(Path(td))
            git(prod, "config", "user.email", "test@example.invalid")
            git(prod, "config", "user.name", "Skyforge Test")
            (prod / "docs/local.md").write_text("local\n", encoding="utf-8")
            git(prod, "add", "docs/local.md")
            git(prod, "commit", "-m", "local divergent")
            local_head = git_text(prod, "rev-parse", "HEAD")

            (author / "docs/remote.md").write_text("remote\n", encoding="utf-8")
            git(author, "add", "docs/remote.md")
            git(author, "commit", "-m", "remote divergent")
            git(author, "push", "origin", "main")
            remote_head = git_text(author, "rev-parse", "HEAD")

            result = sync_repository_snapshot(
                root=prod,
                repo=REPO,
                activation_baseline_sha=baseline,
                remote_runner=RemoteMainRunner(remote_head),
            )

            self.assertEqual(result.disposition, RepositorySyncDisposition.BLOCKED)
            self.assertIn("fast-forward descendant", result.reason)
            self.assertEqual(git_text(prod, "rev-parse", "HEAD"), local_head)


if __name__ == "__main__":
    unittest.main()
