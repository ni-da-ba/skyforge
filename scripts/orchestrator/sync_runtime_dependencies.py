#!/usr/bin/env python3
"""Synchronize pinned hosted-orchestrator dependencies and hosted worker toolchains."""

from __future__ import annotations

import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

from hosted_jdk import ensure_hosted_jdk

STATE_DIR = ".skyforge-orchestrator"
FINGERPRINT_FILE = "requirements.sha256"


def requirements_fingerprint(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


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

    # The hosted controller's bounded workers need the same Java 25 capability as repository CI.
    # Provision it without root into ignored durable state. Local/non-hosted development keeps its
    # existing developer-selected Java environment and never downloads this toolchain implicitly.
    if os.environ.get("SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE") == "1":
        changed = ensure_hosted_jdk(root) or changed

    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    sync_runtime_dependencies(args.root.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
