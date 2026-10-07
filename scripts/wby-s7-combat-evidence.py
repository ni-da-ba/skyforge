#!/usr/bin/env python3
"""Create exact-pin and runtime-load evidence for the WBY S7 combat candidate arms."""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
import tomllib
import zipfile
from pathlib import Path
from typing import Any

ENGINEERING = (
    "createRadars",
    "createAeroRadars",
    "createFireControl",
    "mianbaosNewModernWarfare",
    "cbcNeoWarfare",
    "cbcTerminalBallistics",
)
TACZ = ("tacz", "createTacz", "taczAeronauticsCompat", "taczNpcs")
SCORCHED = ("scorchedGuns",)
ARMS = {"engineering": ENGINEERING, "tacz": ENGINEERING + TACZ, "scorched": ENGINEERING + SCORCHED}
ALL_CANDIDATES = ENGINEERING + TACZ + SCORCHED
TOML_PATH = "META-INF/neoforge.mods.toml"


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        match = re.match(r"([^:=\\s]+)\\s*[:=]\\s*(.*)$", stripped)
        if not match:
            raise SystemExit(f"{path}:{line_number}: malformed properties row")
        values[match.group(1)] = match.group(2).strip()
    return values


def coordinate_token(coordinate: str) -> str:
    parts = coordinate.split(":")
    if len(parts) != 3:
        raise SystemExit(f"Expected immutable Maven G:A:V coordinate, got {coordinate!r}")
    return parts[1] + "-" + parts[2]


def metadata(jar: Path) -> dict[str, Any]:
    with zipfile.ZipFile(jar) as archive:
        matches = [name for name in archive.namelist() if name.lower() == TOML_PATH.lower()]
        if len(matches) != 1:
            raise SystemExit(f"{jar.name}: expected one {TOML_PATH}, found {matches}")
        return tomllib.loads(archive.read(matches[0]).decode("utf-8"))


def license_for(metadata_doc: dict[str, Any], mod_id: str) -> str:
    matches = [mod for mod in metadata_doc.get("mods", []) if str(mod.get("modId", "")) == mod_id]
    return str(matches[0].get("license", "")) if len(matches) == 1 else ""


def safe(value: object) -> str:
    return str(value).replace("\\", "\\\\").replace("\t", " ").replace("\n", " ")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--pins", type=Path, default=Path("skyforge-neoforge-1211/wby-s7-combat.properties"))
    parser.add_argument("--variant", choices=tuple(ARMS), required=True)
    parser.add_argument("--server-log", type=Path, required=True)
    parser.add_argument("--client-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    pins = read_properties(args.pins)
    selected = set(ARMS[args.variant])
    jars = sorted(args.mods.glob("*.jar"))
    report: list[list[object]] = [
        ["record", "variant", "coordinate", "jar", "sha256", "mod_id", "declared_version", "metadata_license", "required_dependencies", "worldgen_resources"],
    ]
    active_mod_ids: set[str] = set()
    used: set[Path] = set()
    for key in ALL_CANDIDATES:
        coordinate = pins[f"{key}.coordinate"]
        token = coordinate_token(coordinate)
        matches = [jar for jar in jars if token.lower() in jar.name.lower()]
        if key in selected:
            if len(matches) != 1:
                raise SystemExit(f"Expected exactly one selected {key} artifact ({token}); found {[p.name for p in matches]}")
            jar = matches[0]
            used.add(jar)
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            doc = metadata(jar)
            mods = doc.get("mods", [])
            if not mods:
                raise SystemExit(f"{jar.name} contains no mod declarations")
            dep_map = doc.get("dependencies", {})
            requirements: list[str] = []
            for entry in dep_map.get(str(mods[0].get("modId", "")), []):
                if str(entry.get("type", "")) == "required":
                    requirements.append(
                        f"{entry.get('modId', '')}{entry.get('versionRange', '')}@{entry.get('side', '')}"
                    )
            worldgen = []
            with zipfile.ZipFile(jar) as archive:
                for name in archive.namelist():
                    lower = name.lower()
                    if lower.startswith("data/") and any(
                        marker in lower for marker in ("/worldgen/", "/tags/worldgen/", "/biome_modifier/")
                    ):
                        worldgen.append(name)
            for mod in mods:
                mod_id = str(mod.get("modId", ""))
                active_mod_ids.add(mod_id)
                report.append([
                    "artifact",
                    args.variant,
                    coordinate,
                    jar.name,
                    digest,
                    mod_id,
                    mod.get("version", ""),
                    mod.get("license", ""),
                    ",".join(requirements),
                    ",".join(sorted(worldgen)),
                ])
        elif matches:
            raise SystemExit(f"Unselected firearm candidate {key} leaked into {args.variant}: {[p.name for p in matches]}")

    server_log = args.server_log.read_text(encoding="utf-8", errors="replace")
    client_log = args.client_log.read_text(encoding="utf-8", errors="replace")
    for mod_id in sorted(active_mod_ids):
        marker = f"({mod_id})"
        if marker not in server_log:
            raise SystemExit(f"Server runtime log did not discover {mod_id}")
        if marker not in client_log:
            raise SystemExit(f"Client runtime log did not discover {mod_id}")

    for label, path in (("server", args.server_log), ("client", args.client_log)):
        report.append(["runtime-load", args.variant, "", path.name, "", "", "", "", "", f"{label}:all selected mod ids loaded"])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "\n".join("\t".join(safe(cell) for cell in row) for row in report) + "\n",
        encoding="utf-8",
    )
    print(
        f"WBY S7 COMBAT EVIDENCE PASS variant={args.variant} "
        f"selected_artifacts={len(used)} mod_ids={len(active_mod_ids)} report={args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
