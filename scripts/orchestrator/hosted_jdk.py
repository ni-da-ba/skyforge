#!/usr/bin/env python3
"""Retired compatibility module for the former hosted project JDK.

The DigitalOcean host is the Skyforge orchestration control plane only. Automated project
machine work belongs on GitHub Actions. This module intentionally fails closed so stale
worker prompts cannot silently re-provision a project toolchain on the orchestrator host.
"""

from __future__ import annotations

from pathlib import Path


_POLICY_ERROR = (
    "Hosted project JDK use is prohibited by docs/agent-state/EXECUTION_BOUNDARIES.md; "
    "use GitHub Actions for automated project build/test/runtime verification."
)


def shared_repository_root(root: Path) -> Path:
    """Preserve this harmless helper for callers that only need repository-root resolution."""
    root = root.resolve()
    dot_git = root / ".git"
    if not dot_git.is_file():
        return root

    line = dot_git.read_text().strip()
    prefix = "gitdir:"
    if not line.lower().startswith(prefix):
        return root
    git_dir = Path(line[len(prefix):].strip())
    if not git_dir.is_absolute():
        git_dir = (root / git_dir).resolve()

    common_file = git_dir / "commondir"
    if not common_file.is_file():
        return root
    common_dir = Path(common_file.read_text().strip())
    if not common_dir.is_absolute():
        common_dir = (git_dir / common_dir).resolve()
    return common_dir.parent


def java_home(root: Path) -> Path:
    raise RuntimeError(_POLICY_ERROR)


def ensure_hosted_jdk(root: Path) -> bool:
    raise RuntimeError(_POLICY_ERROR)


def toolchain_env(root: Path, base: dict[str, str] | None = None) -> dict[str, str]:
    raise RuntimeError(_POLICY_ERROR)
