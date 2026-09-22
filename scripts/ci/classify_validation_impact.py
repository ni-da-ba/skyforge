#!/usr/bin/env python3
"""Classify changed paths into scoped Skyforge validation impact.

Known paths route narrowly. Unknown executable/build paths fail safe to ordinary product CI.
Focused qualification workflows remain independently path-gated; these scopes make the routing
decision explicit and testable instead of collapsing everything into a binary full/lightweight bit.
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
REFERENCE_PREFIXES = ("skyforge-model/", "skyforge-world/", "skyforge-reference/")
COMPILER_PREFIXES = ("scripts/compiler-integration/", "tools/asset_compiler/", "skyforge-recipes/")

HYDROLOGY_TOKENS = ("hydrolog", "fluvial", "channel", "water")
ECOLOGY_TOKENS = ("ecolog", "population", "biome", "vegetation")
DR30_TOKENS = ("structure", "cave", "terrain", "worldgen")
AIRCRAFT_TOKENS = ("aircraft", "aerodynamic", "rudder", "propeller", "flight")
COMPILER_TOKENS = ("compiler", "mechanism", "sable", "kinetic")


@dataclass(frozen=True)
class ValidationImpact:
    ordinary: bool
    reference: bool
    neoforge: bool
    dr30: bool
    dr40: bool
    dr50: bool
    compiler: bool
    aircraft: bool
    music: bool
    unknown: bool
    reason: str

    @property
    def full(self) -> bool:
        """Compatibility alias for callers/tests that still mean ordinary product CI."""
        return self.ordinary

    @property
    def scopes(self) -> tuple[str, ...]:
        names = (
            ("ordinary", self.ordinary),
            ("reference", self.reference),
            ("neoforge", self.neoforge),
            ("dr30", self.dr30),
            ("dr40", self.dr40),
            ("dr50", self.dr50),
            ("compiler", self.compiler),
            ("aircraft", self.aircraft),
            ("music", self.music),
            ("unknown", self.unknown),
        )
        return tuple(name for name, enabled in names if enabled)


def _normalize(path: str) -> str:
    value = str(PurePosixPath(str(path).strip().replace("\\", "/")))
    if value in {"", "."} or value.startswith("../") or value.startswith("/"):
        raise ValueError(f"invalid changed path: {path!r}")
    return value


def _contains(path: str, tokens: tuple[str, ...]) -> bool:
    lowered = path.lower()
    return any(token in lowered for token in tokens)


def classify_paths(paths: Iterable[str]) -> ValidationImpact:
    changed = tuple(_normalize(path) for path in paths if str(path).strip())
    if not changed:
        return ValidationImpact(
            True, True, True, True, True, True, True, True, True, True,
            "empty/unknown change set fails safe to all validation scopes",
        )

    flags = {
        "ordinary": False,
        "reference": False,
        "neoforge": False,
        "dr30": False,
        "dr40": False,
        "dr50": False,
        "compiler": False,
        "aircraft": False,
        "music": False,
        "unknown": False,
    }

    for path in changed:
        if path in MUSIC_FILES or any(path.startswith(prefix) for prefix in MUSIC_PREFIXES):
            flags["music"] = True
            # Music-source integrity is independently verified; it does not require Java/Gradle.
            continue

        if path in LIGHTWEIGHT_FILES or any(path.startswith(prefix) for prefix in LIGHTWEIGHT_PREFIXES):
            if _contains(path, AIRCRAFT_TOKENS):
                flags["aircraft"] = True
            if _contains(path, COMPILER_TOKENS):
                flags["compiler"] = True
            continue

        if path.startswith("skyforge-model/"):
            flags["ordinary"] = True
            flags["reference"] = True
            if "/aircraft/" in path or _contains(path, AIRCRAFT_TOKENS):
                flags["aircraft"] = True
            continue

        if path.startswith("skyforge-world/"):
            flags["ordinary"] = True
            flags["reference"] = True
            flags["dr50"] = True
            if _contains(path, ECOLOGY_TOKENS):
                flags["dr40"] = True
            if _contains(path, DR30_TOKENS):
                flags["dr30"] = True
            continue

        if path.startswith("skyforge-reference/"):
            flags["ordinary"] = True
            flags["reference"] = True
            continue

        if path.startswith("skyforge-neoforge-1211/"):
            flags["ordinary"] = True
            flags["neoforge"] = True
            # Shared build configuration gets one ordinary configuration/compile gate. It is not,
            # by itself, authority to fan out every retained runtime characterization.
            if path == "skyforge-neoforge-1211/build.gradle.kts":
                continue
            if _contains(path, HYDROLOGY_TOKENS):
                flags["dr50"] = True
            if _contains(path, ECOLOGY_TOKENS):
                flags["dr40"] = True
                flags["dr50"] = True
            if _contains(path, DR30_TOKENS):
                flags["dr30"] = True
                flags["dr50"] = True
            if _contains(path, AIRCRAFT_TOKENS):
                flags["aircraft"] = True
            if _contains(path, COMPILER_TOKENS):
                flags["compiler"] = True
            continue

        if any(path.startswith(prefix) for prefix in COMPILER_PREFIXES):
            flags["ordinary"] = True
            flags["compiler"] = True
            if _contains(path, AIRCRAFT_TOKENS):
                flags["aircraft"] = True
            continue

        # Remaining build/source/assets are executable or currently unclassified. Preserve safety
        # by running ordinary check, while recording the unknown scope for routing follow-up.
        flags["ordinary"] = True
        flags["unknown"] = True

    scopes = tuple(name for name, enabled in flags.items() if enabled)
    if scopes:
        reason = "scoped validation impact: " + ",".join(scopes)
    else:
        reason = "validation/control-plane-only change: lightweight CI"

    return ValidationImpact(
        flags["ordinary"],
        flags["reference"],
        flags["neoforge"],
        flags["dr30"],
        flags["dr40"],
        flags["dr50"],
        flags["compiler"],
        flags["aircraft"],
        flags["music"],
        flags["unknown"],
        reason,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    parser.add_argument(
        "--github-output",
        help="append scoped validation outputs to this GitHub Actions output file",
    )
    args = parser.parse_args()
    result = classify_paths(args.paths)
    bool_outputs = {
        "full": result.full,
        "ordinary": result.ordinary,
        "reference": result.reference,
        "neoforge": result.neoforge,
        "dr30": result.dr30,
        "dr40": result.dr40,
        "dr50": result.dr50,
        "compiler": result.compiler,
        "aircraft": result.aircraft,
        "music": result.music,
        "unknown": result.unknown,
    }
    lines = tuple(
        [f"{name}={'true' if value else 'false'}" for name, value in bool_outputs.items()]
        + [f"scopes={','.join(result.scopes) or 'lightweight'}", f"reason={result.reason}"]
    )
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as handle:
            for line in lines:
                handle.write(line + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
