#!/usr/bin/env python3
"""Pinned user-space JDK bootstrap for the hosted Skyforge worker runtime."""

from __future__ import annotations

import hashlib
import os
import platform
import shutil
import tarfile
import tempfile
import urllib.request
from pathlib import Path

STATE_DIR = ".skyforge-orchestrator"
TOOLCHAIN_ID = "temurin-25.0.2+10"
ARCHIVE_NAME = "OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz"
DOWNLOAD_URL = (
    "https://github.com/adoptium/temurin25-binaries/releases/download/"
    "jdk-25.0.2%2B10/" + ARCHIVE_NAME
)
ARCHIVE_SHA256 = "987387933b64b9833846dee373b640440d3e1fd48a04804ec01a6dbf718e8ab8"


def java_home(root: Path) -> Path:
    return root / STATE_DIR / "toolchains" / TOOLCHAIN_ID


def _valid_install(path: Path) -> bool:
    java = path / "bin" / "java"
    javac = path / "bin" / "javac"
    return java.is_file() and os.access(java, os.X_OK) and javac.is_file() and os.access(javac, os.X_OK)


def ensure_hosted_jdk(root: Path) -> bool:
    """Ensure the pinned hosted JDK exists; return True only when a new install was made."""
    root = root.resolve()
    destination = java_home(root)
    marker = destination / ".skyforge-sha256"
    if _valid_install(destination) and marker.is_file() and marker.read_text().strip() == ARCHIVE_SHA256:
        print(f"[orchestrator-jdk] pinned JDK ready at {destination}")
        return False

    if platform.system() != "Linux" or platform.machine().lower() not in {"x86_64", "amd64"}:
        raise RuntimeError(
            f"Pinned hosted JDK supports Linux x86_64 only; got {platform.system()} {platform.machine()}"
        )

    toolchains = destination.parent
    toolchains.mkdir(parents=True, exist_ok=True)
    archive_tmp = toolchains / (ARCHIVE_NAME + ".part")
    extract_tmp = Path(tempfile.mkdtemp(prefix="skyforge-jdk-", dir=toolchains))

    try:
        digest = hashlib.sha256()
        request = urllib.request.Request(
            DOWNLOAD_URL,
            headers={"User-Agent": "skyforge-orchestrator-jdk-bootstrap/1"},
        )
        print(f"[orchestrator-jdk] downloading pinned {TOOLCHAIN_ID}")
        with urllib.request.urlopen(request, timeout=120) as response, archive_tmp.open("wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
                digest.update(chunk)

        actual = digest.hexdigest()
        if actual != ARCHIVE_SHA256:
            raise RuntimeError(
                f"Pinned JDK checksum mismatch: expected {ARCHIVE_SHA256}, got {actual}"
            )

        with tarfile.open(archive_tmp, "r:gz") as archive:
            archive.extractall(extract_tmp, filter="data")

        candidates = [path for path in extract_tmp.iterdir() if path.is_dir() and _valid_install(path)]
        if len(candidates) != 1:
            raise RuntimeError(
                f"Expected exactly one JDK root after extraction, found {len(candidates)}"
            )

        if destination.exists():
            shutil.rmtree(destination)
        shutil.move(str(candidates[0]), str(destination))
        marker.write_text(ARCHIVE_SHA256 + "\n")
        if not _valid_install(destination):
            raise RuntimeError("Pinned JDK install is missing executable java/javac")
        print(f"[orchestrator-jdk] installed pinned JDK at {destination}")
        return True
    finally:
        archive_tmp.unlink(missing_ok=True)
        shutil.rmtree(extract_tmp, ignore_errors=True)


def toolchain_env(root: Path, base: dict[str, str] | None = None) -> dict[str, str]:
    home = java_home(root.resolve())
    if not _valid_install(home):
        raise RuntimeError(
            f"Hosted JDK is not provisioned at {home}; refresh/restart the hosted controller first"
        )
    env = dict(os.environ if base is None else base)
    env["JAVA_HOME"] = str(home)
    env["PATH"] = str(home / "bin") + os.pathsep + env.get("PATH", "")
    return env
