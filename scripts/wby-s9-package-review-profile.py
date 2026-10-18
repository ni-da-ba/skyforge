#!/usr/bin/env python3
"""Package a cumulative S9 client/server profile for the owner-controlled local review."""
from __future__ import annotations

import argparse
import hashlib
import shutil
from pathlib import Path


def copy_jars(source: Path, target: Path, label: str) -> list[Path]:
    jars = sorted(source.glob("*.jar"))
    if not jars:
        raise SystemExit(f"{label} has no staged JARs: {source}")
    target.mkdir(parents=True, exist_ok=True)
    for jar in jars:
        shutil.copy2(jar, target / jar.name)
    return [target / jar.name for jar in jars]


def copy_files(source: Path, target: Path, pattern: str, *, required: bool = False) -> list[Path]:
    files = sorted(source.rglob(pattern)) if source.exists() else []
    if required and not files:
        raise SystemExit(f"Required staged files not found under {source}: {pattern}")
    copied = []
    for file in files:
        if file.is_file():
            destination = target / file.relative_to(source)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(file, destination)
            copied.append(destination)
    return copied


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", required=True)
    parser.add_argument("--client-mods", type=Path, required=True)
    parser.add_argument("--server-mods", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--kubejs-data", type=Path, required=True)
    parser.add_argument("--datapacks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    root = args.output
    if root.exists():
        shutil.rmtree(root)
    client_jars = copy_jars(args.client_mods, root / "client" / "mods", "client profile")
    server_jars = copy_jars(args.server_mods, root / "server" / "mods", "server profile")
    copy_files(args.config, root / "client" / "config", "*.toml")
    copy_files(args.config, root / "server" / "config", "*.toml")
    copy_files(args.kubejs_data, root / "server" / "kubejs" / "data", "*")
    packs = copy_files(args.datapacks, root / "world" / "datapacks", "*.zip", required=True)

    manifest = [
        "WBY S9 owner review profile",
        f"variant={args.variant}",
        "minecraft=1.21.1",
        "neoforge=21.1.249",
        "client_game_dir=client",
        "dedicated_server_dir=server",
        "copy_world_datapacks_to=server/<world-name>/datapacks before first boot",
        "shaders=disabled; simple_clouds_and_distant_horizons=enabled",
        "",
        "[client-mods]",
    ]
    manifest += [f"{path.name}\t{digest(path)}" for path in client_jars]
    manifest += ["", "[server-mods]"]
    manifest += [f"{path.name}\t{digest(path)}" for path in server_jars]
    manifest += ["", "[world-datapacks]"]
    manifest += [f"{path.name}\t{digest(path)}" for path in packs]
    (root / "manifest.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print(f"WBY S9 REVIEW PROFILE PACKAGE PASS variant={args.variant} client={len(client_jars)} server={len(server_jars)} datapacks={len(packs)} path={root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
