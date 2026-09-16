#!/usr/bin/env python3
"""Synchronize hosted-orchestrator Python dependencies and retire legacy project toolchains.

The DigitalOcean host may run the lightweight Codex orchestration/editing process, but project
build/test toolchains belong to GitHub Actions. In particular, the pre-boundary hosted Java toolchain
must not be provisioned or retained on the controller host.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
from pathlib import Path

STATE_DIR = ".skyforge-orchestrator"
FINGERPRINT_FILE = "requirements.sha256"
LEGACY_TOOLCHAINS_DIR = "toolchains"


def requirements_fingerprint(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def retire_legacy_project_toolchains(root: Path) -> bool:
    """Remove pre-boundary project build toolchains from ignored controller state."""
    toolchains = root / STATE_DIR / LEGACY_TOOLCHAINS_DIR
    if not toolchains.exists():
        return False
    shutil.rmtree(toolchains)
    print(f"[orchestrator-deps] retired legacy project toolchains at {toolchains}")
    return True


def sync_runtime_dependencies(root: Path) -> bool:
    requirements = root / "scripts" / "orchestrator" / "requirements.txt"
    if not requirements.is_file():
        raise RuntimeError(f"Missing orchestrator requirements file: {requirements}")

    state_dir = root / STATE_DIR
    state_dir.mkdir(parents=True, exist_ok=True)
    fingerprint_path = state_dir / FINGERPRINT_FILE
    current = requirements_fingerprint(requirements)
    previous = fingerprint_path.read_text().strip() if fingerprint_path.exists() else ""
    changed = False

    if previous == current:
        print("[orchestrator-deps] requirements fingerprint unchanged")
    else:
        subprocess.run(
            [
                sys.executable,
                "-m",
                "pip",
                "install",
                "--disable-pip-version-check",
                "-r",
                str(requirements),
            ],
            cwd=root,
            check=True,
            timeout=600,
        )

        tmp = fingerprint_path.with_name(fingerprint_path.name + ".tmp")
        tmp.write_text(current + "\n")
        os.replace(tmp, fingerprint_path)
        print(f"[orchestrator-deps] synchronized requirements {current[:12]}")
        changed = True

    # This is intentionally destructive only inside ignored orchestrator state. Accepted project
    # truth remains in GitHub. Removing the old Java bundle makes a stale hosted-JDK instruction fail
    # closed instead of turning the 2 GB controller back into a Gradle/NeoForge build machine.
    changed = retire_legacy_project_toolchains(root) or changed
    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    sync_runtime_dependencies(args.root.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
