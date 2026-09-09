#!/usr/bin/env python3
"""Synchronize pinned hosted-orchestrator Python dependencies only when requirements change."""

from __future__ import annotations

import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

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
    if previous == current:
        print("[orchestrator-deps] requirements fingerprint unchanged")
        return False

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
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    sync_runtime_dependencies(args.root.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
