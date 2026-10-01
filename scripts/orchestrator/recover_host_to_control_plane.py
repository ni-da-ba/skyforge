#!/usr/bin/env python3
"""One-shot migration of a legacy Skyforge hosted worker to control-plane-only mode.

Run this script as root on the DigitalOcean orchestrator host. It performs infrastructure recovery only:

1. stop the orchestrator service;
2. preserve any pre-contract pending worker by committing/pushing its *existing* bytes to a recovery
   branch without running project builds/tests or changing the deliverable;
3. clear only the obsolete pending-worker journal after the GitHub push succeeds;
4. fast-forward the controller checkout to current ``origin/main``;
5. install the current systemd unit template and restart the control-plane runtime;
6. verify localhost ``/healthz`` reports the DigitalOcean control-plane-only boundary.

It deliberately does not run Gradle, Java, NeoForge, Minecraft, tests, benchmarks, or generators.
"""

from __future__ import annotations

import json
import os
import pwd
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SERVICE = "skyforge-orchestrator.service"
STATE_DIR = ".skyforge-orchestrator"
STATE_FILE = "state.json"
BACKUP_FILE = "state.json.bak"
RECOVERY_ISSUE = 492


class RecoveryError(RuntimeError):
    pass


def run(
    command: list[str],
    *,
    cwd: Path | None = None,
    user: str | None = None,
    check: bool = True,
    timeout: int = 180,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    if user:
        account = pwd.getpwnam(user)
        env["HOME"] = account.pw_dir
        command = ["runuser", "-u", user, "--", "env", f"HOME={account.pw_dir}", *command]
    completed = subprocess.run(
        command,
        cwd=str(cwd) if cwd else None,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if check and completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "command failed").strip()
        raise RecoveryError(f"{' '.join(command[:5])}: {detail[:2000]}")
    return completed


def systemd_value(name: str) -> str:
    return run(["systemctl", "show", SERVICE, f"--property={name}", "--value"]).stdout.strip()


def load_state(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text())
    if not isinstance(value, dict):
        raise RecoveryError(f"{path} does not contain a JSON object")
    return value


def atomic_state_write(primary: Path, backup: Path, state: dict[str, Any]) -> None:
    """Replace both durable state mirrors without changing controller ownership/mode."""
    payload = json.dumps(state, indent=2, sort_keys=True) + "\n"
    template = primary.stat() if primary.exists() else backup.stat()
    for path in (primary, backup):
        prior = path.stat() if path.exists() else template
        tmp = path.with_name(path.name + ".migration.tmp")
        tmp.write_text(payload)
        os.chown(tmp, prior.st_uid, prior.st_gid)
        os.chmod(tmp, prior.st_mode & 0o7777)
        os.replace(tmp, path)


def preserve_pending_worker(
    *,
    root: Path,
    state_path: Path,
    backup_path: Path,
    service_user: str,
    state: dict[str, Any],
) -> str | None:
    pending = state.get("pending_worker")
    if not isinstance(pending, dict):
        print("[recovery] no pending hosted worker is attached")
        return None

    raw_worktree = pending.get("worktree")
    if not raw_worktree:
        raise RecoveryError(
            "pending worker has no isolated worktree path; the preserved provider snapshot must be used for manual recovery"
        )
    worktree = Path(str(raw_worktree))
    if not worktree.is_absolute():
        worktree = root / worktree
    worktree = worktree.resolve()
    if not worktree.exists():
        raise RecoveryError(f"pending worker worktree is missing: {worktree}")
    if worktree == root.resolve():
        raise RecoveryError("refusing migration of a legacy worker stored in the controller checkout")

    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    recovery_dir = root / STATE_DIR / "recovery" / stamp
    recovery_dir.mkdir(parents=True, exist_ok=False)
    shutil.copy2(state_path, recovery_dir / STATE_FILE)
    if backup_path.exists():
        shutil.copy2(backup_path, recovery_dir / BACKUP_FILE)

    source_head = run(["git", "rev-parse", "HEAD"], cwd=worktree, user=service_user).stdout.strip()
    source_branch = run(["git", "branch", "--show-current"], cwd=worktree, user=service_user).stdout.strip()
    recovery_branch = f"recovery/dr20-pre-boundary-{stamp}"

    print(
        f"[recovery] preserving hosted worker branch={source_branch or '(detached)'} "
        f"head={source_head[:12]} -> {recovery_branch}"
    )

    # Switching branch and creating a commit are transport/integrity operations only. No project
    # command is executed and the existing worktree bytes are not edited by this utility.
    run(["git", "switch", "-c", recovery_branch], cwd=worktree, user=service_user)
    run(["git", "add", "--all"], cwd=worktree, user=service_user)
    staged = run(["git", "diff", "--cached", "--quiet"], cwd=worktree, user=service_user, check=False)
    if staged.returncode not in {0, 1}:
        raise RecoveryError("could not determine whether the recovery worktree has staged changes")
    if staged.returncode == 1:
        run(
            [
                "git", "commit", "-m",
                "RECOVERY: preserve pre-boundary DR-20 hosted worktree",
            ],
            cwd=worktree,
            user=service_user,
        )

    run(["git", "push", "-u", "origin", recovery_branch], cwd=worktree, user=service_user, timeout=240)
    pushed_head = run(["git", "rev-parse", "HEAD"], cwd=worktree, user=service_user).stdout.strip()

    body = (
        "[skyforge-orchestrator] LEGACY_RECOVERY_EXPORTED\n\n"
        f"The pre-boundary DigitalOcean DR-20 worktree was preserved to `{recovery_branch}` at "
        f"`{pushed_head}` before the hosted worker journal was retired.\n\n"
        "No Gradle/Java/NeoForge/Minecraft build, test, benchmark, generator, or project validation "
        "was run on the Droplet during this evacuation. The branch is recovery/reference material; "
        "active development resumes from current `main` through GitHub's execution plane."
    )
    run(
        [
            "gh", "issue", "comment", str(RECOVERY_ISSUE),
            "--repo", "ni-da-ba/skyforge",
            "--body", body,
        ],
        cwd=root,
        user=service_user,
        timeout=120,
    )

    # Only after the remote branch and issue pointer exist is the obsolete hosted-worker ownership
    # retired. Preserve the classifier decision/event so the new runtime can hand the same bounded
    # issue authority to GitHub's cloud agent.
    current_state = load_state(state_path)
    current_pending = current_state.get("pending_worker")
    if not isinstance(current_pending, dict):
        raise RecoveryError("pending-worker ownership disappeared during evacuation")
    if str(current_pending.get("branch") or "") != str(pending.get("branch") or ""):
        raise RecoveryError("pending-worker ownership changed during evacuation")
    current_state["pending_worker"] = None
    current_state["legacy_hosted_worker_recovery"] = {
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "source_branch": source_branch,
        "source_head": source_head,
        "recovery_branch": recovery_branch,
        "recovery_head": pushed_head,
        "issue_number": RECOVERY_ISSUE,
        "project_validation_run_on_host": False,
    }
    current_state["paused"] = False
    current_state["paused_at"] = None
    current_state["paused_by"] = None
    atomic_state_write(state_path, backup_path, current_state)

    run(["git", "worktree", "remove", "--force", str(worktree)], cwd=root, user=service_user, timeout=120)
    run(["git", "worktree", "prune"], cwd=root, user=service_user, check=False)
    print(f"[recovery] recovery branch pushed and legacy worker journal retired: {recovery_branch}")
    return recovery_branch


def update_controller_checkout(root: Path, service_user: str) -> str:
    print("[recovery] fast-forwarding clean controller checkout to origin/main")
    dirty = run(["git", "status", "--porcelain"], cwd=root, user=service_user).stdout.strip()
    if dirty:
        raise RecoveryError("controller checkout is dirty; refusing automatic control-plane update")
    run(["git", "fetch", "--prune", "origin"], cwd=root, user=service_user, timeout=240)
    run(["git", "checkout", "main"], cwd=root, user=service_user)
    run(["git", "pull", "--ff-only", "origin", "main"], cwd=root, user=service_user, timeout=240)
    return run(["git", "rev-parse", "HEAD"], cwd=root, user=service_user).stdout.strip()


def install_current_unit(root: Path, service_user: str) -> None:
    account = pwd.getpwnam(service_user)
    venv_python = root / STATE_DIR / "venv" / "bin" / "python"
    if not venv_python.exists():
        raise RecoveryError(f"orchestrator virtualenv Python is missing: {venv_python}")
    template_path = root / "deploy" / "orchestrator" / "skyforge-orchestrator.service.in"
    template = template_path.read_text()
    unit = (
        template.replace("@@ROOT@@", str(root))
        .replace("@@USER@@", service_user)
        .replace("@@HOME@@", account.pw_dir)
        .replace("@@VENV_PYTHON@@", str(venv_python))
    )
    unit_path = Path("/etc/systemd/system/skyforge-orchestrator.service")
    unit_path.write_text(unit)
    os.chmod(unit_path, 0o644)
    run(["systemctl", "daemon-reload"])
    run(["systemctl", "enable", SERVICE])
    run(["systemctl", "restart", SERVICE], timeout=120)


def verify_health() -> dict[str, Any]:
    last_error = "health endpoint did not respond"
    for _ in range(45):
        response = run(
            ["curl", "-fsS", "http://127.0.0.1:3000/healthz"],
            check=False,
            timeout=10,
        )
        if response.returncode == 0:
            try:
                payload = json.loads(response.stdout)
            except json.JSONDecodeError:
                last_error = "health endpoint returned non-JSON data"
            else:
                boundary = payload.get("execution_boundary") or {}
                if (
                    boundary.get("mode") == "digitalocean-control-plane-only"
                    and boundary.get("hosted_project_workers_enabled") is False
                ):
                    return payload
                last_error = f"unexpected execution boundary: {boundary}"
        elif response.stderr:
            last_error = response.stderr.strip()
        time.sleep(2)
    raise RecoveryError(last_error)


def main() -> int:
    if os.geteuid() != 0:
        print("Run this recovery utility as root (for example with sudo).", file=sys.stderr)
        return 2

    service_user = systemd_value("User")
    root_text = systemd_value("WorkingDirectory")
    if not service_user or not root_text:
        raise RecoveryError("could not derive service user/working directory from systemd")
    root = Path(root_text).resolve()
    state_path = root / STATE_DIR / STATE_FILE
    backup_path = root / STATE_DIR / BACKUP_FILE
    if not state_path.exists():
        raise RecoveryError(f"controller state file is missing: {state_path}")

    print(f"[recovery] stopping {SERVICE} on {root} as service user {service_user}")
    run(["systemctl", "stop", SERVICE], timeout=60)

    state = load_state(state_path)
    preserve_pending_worker(
        root=root,
        state_path=state_path,
        backup_path=backup_path,
        service_user=service_user,
        state=state,
    )
    head = update_controller_checkout(root, service_user)
    install_current_unit(root, service_user)
    health = verify_health()

    print(f"[recovery] controller main head: {head}")
    print("[recovery] control plane is healthy")
    print(json.dumps({
        "status": health.get("status"),
        "runtime_head": health.get("runtime_head"),
        "paused": health.get("paused"),
        "pending_worker": health.get("pending_worker"),
        "execution_boundary": health.get("execution_boundary"),
        "bounded_roadmap": health.get("bounded_roadmap"),
    }, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RecoveryError as exc:
        print(f"RECOVERY FAILED CLOSED: {exc}", file=sys.stderr)
        raise SystemExit(1)
