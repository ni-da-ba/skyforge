#!/usr/bin/env python3
"""Cross-platform launcher for the local Skyforge orchestration pilot."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = "ni-da-ba/skyforge"
STATE_DIR = ".skyforge-orchestrator"
DEFAULT_PORT = "3000"


def _run(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
    timeout: int | None = None,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
        timeout=timeout,
    )


def _repo_root() -> Path:
    result = _run(["git", "rev-parse", "--show-toplevel"], cwd=Path.cwd(), timeout=30)
    return Path(result.stdout.strip()).resolve()


def _venv_python(venv: Path) -> Path:
    if os.name == "nt":
        return venv / "Scripts" / "python.exe"
    return venv / "bin" / "python"


def _ensure_prerequisites(root: Path) -> str:
    gh = shutil.which("gh")
    if not gh:
        raise RuntimeError("GitHub CLI (gh) is required.")

    auth = _run([gh, "auth", "status"], cwd=root, check=False, timeout=30)
    if auth.returncode != 0:
        raise RuntimeError("GitHub CLI is not authenticated. Run: gh auth login")

    webhook = _run([gh, "webhook", "--help"], cwd=root, check=False, timeout=30)
    if webhook.returncode != 0:
        raise RuntimeError(
            "The gh-webhook extension is required. Review it, then run: "
            "gh extension install cli/gh-webhook"
        )
    return gh


def _ensure_venv(root: Path) -> Path:
    state_dir = root / STATE_DIR
    venv = state_dir / "venv"
    python = _venv_python(venv)
    if python.exists():
        return python

    state_dir.mkdir(parents=True, exist_ok=True)
    print(f"[pilot] creating isolated virtualenv at {venv}", flush=True)
    _run([sys.executable, "-m", "venv", str(venv)], cwd=root, timeout=180)
    _run([str(python), "-m", "pip", "install", "--upgrade", "pip"], cwd=root, timeout=300)
    _run(
        [
            str(python),
            "-m",
            "pip",
            "install",
            "-r",
            "scripts/orchestrator/requirements.txt",
        ],
        cwd=root,
        timeout=600,
    )
    return python


def main() -> int:
    root = _repo_root()
    gh = _ensure_prerequisites(root)
    python = _ensure_venv(root)
    port = os.environ.get("SKYFORGE_ORCHESTRATOR_PORT", DEFAULT_PORT)

    env = os.environ.copy()
    env["SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE"] = "1"

    server = subprocess.Popen(
        [
            str(python),
            "scripts/orchestrator/skyforge_orchestrator.py",
            "--root",
            str(root),
            "--repo",
            REPO,
            "--port",
            port,
        ],
        cwd=root,
        env=env,
    )

    try:
        time.sleep(1)
        code = server.poll()
        if code is not None:
            raise RuntimeError(f"Orchestrator server exited during startup with code {code}")

        print(f"[pilot] forwarding selected Skyforge webhooks to localhost:{port}", flush=True)
        print(
            "[pilot] local development transport only; do not expose gh webhook forward as production infrastructure",
            flush=True,
        )
        forwarded = subprocess.run(
            [
                gh,
                "webhook",
                "forward",
                "--repo",
                REPO,
                "--events",
                "push,pull_request,workflow_run,issue_comment",
                "--url",
                f"http://127.0.0.1:{port}/webhook",
            ],
            cwd=root,
            check=False,
        )
        return int(forwarded.returncode)
    except KeyboardInterrupt:
        return 130
    finally:
        if server.poll() is None:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)


if __name__ == "__main__":
    raise SystemExit(main())
