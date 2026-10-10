#!/usr/bin/env python3
"""Fail fast on duplicate NeoForge mod IDs in staged S7 control/candidate profiles."""
from __future__ import annotations

import argparse
import tomllib
from pathlib import Path
from zipfile import BadZipFile, ZipFile


def mod_ids(jar: Path) -> list[str]:
    try:
        with ZipFile(jar) as archive:
            candidates = [
                name for name in archive.namelist()
                if name.lower() in {
                    "meta-inf/neoforge.mods.toml",
                    "meta-inf/mods.toml",
                }
            ]
            if not candidates:
                return []
            # Prefer the active NeoForge metadata if a compatibility jar carries both forms.
            path = next(
                (name for name in candidates if name.lower() == "meta-inf/neoforge.mods.toml"),
                candidates[0],
            )
            document = tomllib.loads(archive.read(path).decode("utf-8"))
    except (BadZipFile, UnicodeError, tomllib.TOMLDecodeError) as exc:
        raise SystemExit(f"Cannot inspect staged mod metadata in {jar}: {exc}") from exc

    declarations = document.get("mods", [])
    if not isinstance(declarations, list):
        raise SystemExit(f"Invalid mod declarations in {jar}")
    return [
        str(entry["modId"])
        for entry in declarations
        if isinstance(entry, dict) and entry.get("modId")
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--profile",
        action="append",
        nargs=2,
        metavar=("NAME", "MODS_DIR"),
        required=True,
        help="profile label and staged mods directory; repeat for each profile",
    )
    args = parser.parse_args()

    for label, raw_directory in args.profile:
        directory = Path(raw_directory)
        if not directory.is_dir():
            raise SystemExit(f"{label}: staged mods directory does not exist: {directory}")
        jars = sorted(directory.glob("*.jar"))
        if not jars:
            raise SystemExit(f"{label}: staged no JAR files in {directory}")
        owners: dict[str, str] = {}
        for jar in jars:
            for mod_id in mod_ids(jar):
                previous = owners.get(mod_id)
                if previous is not None:
                    raise SystemExit(
                        f"{label}: duplicate staged mod id {mod_id}: {previous}, {jar.name}"
                    )
                owners[mod_id] = jar.name
        print(f"S7 staging preflight PASS profile={label} jars={len(jars)} mod_ids={len(owners)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
