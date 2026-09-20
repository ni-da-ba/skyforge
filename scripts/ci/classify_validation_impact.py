#!/usr/bin/env python3
"""Classify a changed-path set for canonical Skyforge CI.

Fail-safe rule: anything not explicitly known to be validation/control-plane-only
requires full product CI.
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
    "AGENTS.md",
    "README.md",
    "CONTRIBUTING.md",
    "SECURITY.md",
    "CODE_OF_CONDUCT.md",
}
MUSIC_PREFIXES = ("assets/music/",)
MUSIC_FILES = {"scripts/music/verify_music_sources.py"}


@dataclass(frozen=True)
class ValidationImpact:
    full: bool
    music: bool
    reason: str


def _normalize(path: str) -> str:
    value = str(PurePosixPath(str(path).strip().replace("\\", "/")))
    if value in {"", "."} or value.startswith("../") or value.startswith("/"):
        raise ValueError(f"invalid changed path: {path!r}")
    return value


def classify_paths(paths: Iterable[str]) -> ValidationImpact:
    changed = tuple(_normalize(path) for path in paths if str(path).strip())
    if not changed:
        return ValidationImpact(True, False, "empty/unknown change set fails safe to full CI")

    music = any(
        path in MUSIC_FILES or any(path.startswith(prefix) for prefix in MUSIC_PREFIXES)
        for path in changed
    )
    lightweight = all(
        path in LIGHTWEIGHT_FILES
        or any(path.startswith(prefix) for prefix in LIGHTWEIGHT_PREFIXES)
        for path in changed
    )
    if lightweight:
        return ValidationImpact(
            False,
            music,
            "validation/control-plane-only change: skip product Gradle/evidence generation",
        )
    return ValidationImpact(
        True,
        music,
        "product, build, asset, reusable-action, or unclassified change: run full CI",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    parser.add_argument(
        "--github-output",
        help="append full/music/reason outputs to this GitHub Actions output file",
    )
    args = parser.parse_args()
    result = classify_paths(args.paths)
    lines = (
        f"full={'true' if result.full else 'false'}",
        f"music={'true' if result.music else 'false'}",
        f"reason={result.reason}",
    )
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as handle:
            for line in lines:
                handle.write(line + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
