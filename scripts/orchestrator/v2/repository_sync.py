"""Model-free synchronization of the production repository work snapshot.

Platform-v2 activation reviews the controller runtime at one accepted baseline. Ordinary
repository work may advance main without changing that loaded runtime. This module keeps
those identities separate: a clean checkout may fast-forward to current remote main only
when the activation-critical runtime tree is unchanged from the reviewed baseline.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
import subprocess
from typing import Callable

from .objective_promotion_effect import read_remote_main


_SHA40_RE = re.compile(r"^[0-9a-f]{40}$")

# Any net content change here requires the normal reviewed Platform-v2 upgrade path.
# Directory pathspecs intentionally cover every v2 runtime module and deployment surface.
RUNTIME_CRITICAL_PATHS = (
    "scripts/orchestrator/platform_v2_hosted_runtime.py",
    "scripts/orchestrator/v2/",
    "scripts/orchestrator/requirements.txt",
    "scripts/orchestrator/sync_runtime_dependencies.py",
    "scripts/orchestrator/install_hosted.sh",
    "scripts/orchestrator/stage_platform_v2_cutover.sh",
    "scripts/orchestrator/platform_v2_operator_cutover.py",
    "scripts/orchestrator/platform_v2_routine_upgrade.py",
    "deploy/orchestrator/",
)


class RepositorySyncDisposition(str, Enum):
    CURRENT = "CURRENT"
    FAST_FORWARDED = "FAST_FORWARDED"
    UPGRADE_REQUIRED = "UPGRADE_REQUIRED"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class RepositorySyncResult:
    disposition: RepositorySyncDisposition
    reason: str
    activation_baseline_sha: str
    previous_head_sha: str
    target_sha: str
    changed_runtime_paths: tuple[str, ...] = ()

    @property
    def ready(self) -> bool:
        return self.disposition in {
            RepositorySyncDisposition.CURRENT,
            RepositorySyncDisposition.FAST_FORWARDED,
        }


def _sha40(value: str, label: str) -> str:
    text = str(value or "").strip().lower()
    if not _SHA40_RE.fullmatch(text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _git(
    root: Path,
    *args: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return runner(
        ["git", "-C", str(Path(root).resolve()), *args],
        text=True,
        capture_output=True,
        check=check,
        timeout=180,
    )


def _git_text(
    root: Path,
    *args: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> str:
    return str(_git(root, *args, runner=runner).stdout or "").strip()


def _is_ancestor(
    root: Path,
    older: str,
    newer: str,
    *,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> bool:
    result = _git(
        root,
        "merge-base",
        "--is-ancestor",
        older,
        newer,
        runner=runner,
        check=False,
    )
    return result.returncode == 0


def tracked_clean(
    root: Path,
    *,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> bool:
    return not bool(
        _git_text(
            root,
            "status",
            "--porcelain",
            "--untracked-files=no",
            runner=runner,
        )
    )


def runtime_critical_changes(
    root: Path,
    baseline_sha: str,
    target_sha: str,
    *,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> tuple[str, ...]:
    baseline = _sha40(baseline_sha, "baseline_sha")
    target = _sha40(target_sha, "target_sha")
    if baseline == target:
        return ()
    output = _git_text(
        root,
        "diff",
        "--name-only",
        baseline,
        target,
        "--",
        *RUNTIME_CRITICAL_PATHS,
        runner=runner,
    )
    return tuple(line.strip() for line in output.splitlines() if line.strip())


def checkout_is_activation_compatible(
    *,
    root: Path,
    activation_baseline_sha: str,
    checkout_sha: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> tuple[bool, tuple[str, ...], str]:
    root = Path(root).resolve()
    baseline = _sha40(activation_baseline_sha, "activation_baseline_sha")
    checkout = _sha40(checkout_sha, "checkout_sha")
    if checkout == baseline:
        return True, (), "checkout equals reviewed activation baseline"
    if not _is_ancestor(root, baseline, checkout, runner=runner):
        return False, (), "checkout is not a forward descendant of reviewed activation baseline"
    changed = runtime_critical_changes(root, baseline, checkout, runner=runner)
    if changed:
        return (
            False,
            changed,
            "checkout changes Platform-v2 runtime/deployment content after reviewed activation",
        )
    return (
        True,
        (),
        "checkout is ahead of activation baseline with runtime-critical tree unchanged",
    )


def sync_repository_snapshot(
    *,
    root: Path,
    repo: str,
    activation_baseline_sha: str,
    remote_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    git_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> RepositorySyncResult:
    """Fast-forward a clean production checkout over non-runtime repository changes."""

    root = Path(root).resolve()
    baseline = _sha40(activation_baseline_sha, "activation_baseline_sha")
    previous = _sha40(
        _git_text(root, "rev-parse", "HEAD", runner=git_runner),
        "previous_head_sha",
    )
    if not tracked_clean(root, runner=git_runner):
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            "tracked production checkout is dirty; refusing repository snapshot sync",
            baseline,
            previous,
            previous,
        )

    remote = _sha40(
        read_remote_main(root=root, repo=repo, runner=remote_runner),
        "remote_main_sha",
    )

    compatible, changed, reason = checkout_is_activation_compatible(
        root=root,
        activation_baseline_sha=baseline,
        checkout_sha=previous,
        runner=git_runner,
    )
    if not compatible:
        return RepositorySyncResult(
            RepositorySyncDisposition.UPGRADE_REQUIRED,
            reason,
            baseline,
            previous,
            remote,
            changed,
        )

    if previous == remote:
        return RepositorySyncResult(
            RepositorySyncDisposition.CURRENT,
            "production checkout already matches current remote main",
            baseline,
            previous,
            remote,
        )

    # Fetch only after remote truth proves movement; this keeps the already-current path
    # local/model-free apart from the exact accepted GitHub main lookup.
    try:
        _git(root, "fetch", "--prune", "origin", "main", runner=git_runner)
        fetched = _sha40(
            _git_text(root, "rev-parse", "origin/main", runner=git_runner),
            "fetched_origin_main_sha",
        )
    except (ValueError, subprocess.SubprocessError) as exc:
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            f"repository snapshot fetch failed: {type(exc).__name__}: {exc}",
            baseline,
            previous,
            remote,
        )

    if fetched != remote:
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            "fetched origin/main differs from exact GitHub remote-main observation",
            baseline,
            previous,
            remote,
        )
    if not _is_ancestor(root, previous, remote, runner=git_runner):
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            "remote main is not a fast-forward descendant of production checkout",
            baseline,
            previous,
            remote,
        )

    compatible, changed, reason = checkout_is_activation_compatible(
        root=root,
        activation_baseline_sha=baseline,
        checkout_sha=remote,
        runner=git_runner,
    )
    if not compatible:
        return RepositorySyncResult(
            RepositorySyncDisposition.UPGRADE_REQUIRED,
            reason + "; routine Platform-v2 upgrade required before new objective scoping",
            baseline,
            previous,
            remote,
            changed,
        )

    try:
        _git(root, "checkout", "--detach", "--quiet", remote, runner=git_runner)
        current = _sha40(
            _git_text(root, "rev-parse", "HEAD", runner=git_runner),
            "current_head_sha",
        )
    except (ValueError, subprocess.SubprocessError) as exc:
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            f"repository snapshot checkout failed: {type(exc).__name__}: {exc}",
            baseline,
            previous,
            remote,
        )
    if current != remote or not tracked_clean(root, runner=git_runner):
        return RepositorySyncResult(
            RepositorySyncDisposition.BLOCKED,
            "repository snapshot sync did not finish at exact clean remote main",
            baseline,
            previous,
            remote,
        )

    return RepositorySyncResult(
        RepositorySyncDisposition.FAST_FORWARDED,
        "clean production repository snapshot fast-forwarded to current remote main without runtime-critical changes",
        baseline,
        previous,
        remote,
    )
