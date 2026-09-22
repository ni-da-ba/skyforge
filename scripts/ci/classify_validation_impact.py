#!/usr/bin/env python3
"""Classify changed paths into Skyforge validation scopes.

Known paths receive the narrowest safe scope. Unknown executable/build paths fail
safe to ordinary product CI plus the broad reference/runtime scopes.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Iterable

LIGHTWEIGHT_PREFIXES = (
    "docs/",
    "scripts/orchestrator/",
    "deploy/orchestrator/",
    ".github/workflows/",
    "scripts/ci/",
    "config/ci/",
)
LIGHTWEIGHT_FILES = {
    "AGENTS.md", "README.md", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md",
}
MUSIC_PREFIXES = ("assets/music/",)
MUSIC_FILES = {"scripts/music/verify_music_sources.py"}
REFERENCE_PREFIXES = ("skyforge-model/", "skyforge-world/", "skyforge-reference/")
NEOFORGE_PREFIXES = ("skyforge-neoforge-1211/",)
KNOWN_PRODUCT_PREFIXES = (
    "skyforge-kernel/", "skyforge-recipes/", "assets/", "tools/",
)
BUILD_FILES = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat",
}


@dataclass(frozen=True)
class ValidationImpact:
    full: bool
    music: bool
    reference: bool
    neoforge: bool
    dr30: bool
    dr40: bool
    dr50: bool
    compiler_mechanism: bool
    aircraft: bool
    fail_safe: bool
    reason: str

    @property
    def scopes(self) -> tuple[str, ...]:
        ordered = (
            ("ordinary", self.full),
            ("reference", self.reference),
            ("neoforge", self.neoforge),
            ("dr30", self.dr30),
            ("dr40", self.dr40),
            ("dr50", self.dr50),
            ("compiler-mechanism", self.compiler_mechanism),
            ("aircraft", self.aircraft),
            ("music", self.music),
        )
        return tuple(name for name, enabled in ordered if enabled)


def _normalize(path: str) -> str:
    value = str(PurePosixPath(str(path).strip().replace("\\", "/")))
    if value in {"", "."} or value.startswith("../") or value.startswith("/"):
        raise ValueError(f"invalid changed path: {path!r}")
    return value


def _contains_scope(path: str, token: str) -> bool:
    lowered = path.lower()
    return token in lowered.replace("_", "-").replace("/", "-")


def classify_paths(paths: Iterable[str]) -> ValidationImpact:
    changed = tuple(_normalize(path) for path in paths if str(path).strip())
    if not changed:
        return ValidationImpact(
            True, True, True, True, True, True, True, True, True, True,
            "empty/unknown change set fails safe to all validation scopes",
        )

    music = any(path in MUSIC_FILES or path.startswith(MUSIC_PREFIXES) for path in changed)
    dr30 = any(_contains_scope(path, "dr30") for path in changed)
    dr40 = any(_contains_scope(path, "dr40") for path in changed)
    dr50 = any(_contains_scope(path, "dr50") for path in changed)
    aircraft = any("aircraft" in path.lower() or "rudder" in path.lower() for path in changed)
    compiler_mechanism = any(
        token in path.lower()
        for path in changed
        for token in ("compiler-platform", "mechanism", "mech-", "portable-engine", "wave-c")
    )

    executable = [
        path for path in changed
        if not (
            path in LIGHTWEIGHT_FILES
            or any(path.startswith(prefix) for prefix in LIGHTWEIGHT_PREFIXES)
        )
    ]
    if not executable:
        return ValidationImpact(
            False, music, False, False, dr30, dr40, dr50,
            compiler_mechanism, aircraft, False,
            "validation/control-plane-only change: no ordinary product Gradle lane",
        )

    reference = any(path.startswith(REFERENCE_PREFIXES) for path in executable)
    neoforge = any(path.startswith(NEOFORGE_PREFIXES) for path in executable)
    known = all(
        path in BUILD_FILES
        or path.startswith(("gradle/", "buildSrc/"))
        or path.startswith(REFERENCE_PREFIXES)
        or path.startswith(NEOFORGE_PREFIXES)
        or path.startswith(KNOWN_PRODUCT_PREFIXES)
        or path.startswith(MUSIC_PREFIXES)
        or path in MUSIC_FILES
        for path in executable
    )
    fail_safe = not known
    if fail_safe or any(path in BUILD_FILES or path.startswith(("gradle/", "buildSrc/")) for path in executable):
        reference = True
        neoforge = True

    reason = (
        "unclassified executable path fails safe to ordinary/reference/NeoForge validation"
        if fail_safe
        else "known executable change routed to scoped ordinary validation"
    )
    return ValidationImpact(
        True, music, reference, neoforge, dr30, dr40, dr50,
        compiler_mechanism, aircraft, fail_safe, reason,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    parser.add_argument("--github-output")
    args = parser.parse_args()
    result = classify_paths(args.paths)
    outputs = {
        "full": result.full,
        "ordinary": result.full,
        "reference": result.reference,
        "neoforge": result.neoforge,
        "dr30": result.dr30,
        "dr40": result.dr40,
        "dr50": result.dr50,
        "compiler_mechanism": result.compiler_mechanism,
        "aircraft": result.aircraft,
        "music": result.music,
        "fail_safe": result.fail_safe,
    }
    lines = [f"{name}={'true' if value else 'false'}" for name, value in outputs.items()]
    lines += [f"scopes={','.join(result.scopes)}", f"reason={result.reason}"]
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())