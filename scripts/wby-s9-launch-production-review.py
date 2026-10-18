#!/usr/bin/env python3
"""Prepare an S9 local review overlay from official sources and a staged S8 profile.

This script only prepares a local manual client profile; it never uploads third-party binaries.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import zipfile

MODRINTH = {
    "incendium": ("pwe1kTJE", ".zip"),
    "nullscape": ("3fv8O3xX", ".zip"),
    "betterend": ("BgNy6PHx", ".jar"),
    "bclib": ("QkXw8Fsl", ".jar"),
    "wunderlib": ("5db3GZzg", ".jar"),
    "worldweaver": ("TrReu8cF", ".jar"),
    "unusual-end": ("s1PcPdqg", ".jar"),
    "moogs-end-structures": ("s0Quk5Z8", ".jar"),
    "yungs-better-strongholds": ("Vq5mh4Ot", ".jar"),
    "yungs-better-end-island": ("I52NZ1qK", ".jar"),
    "ender-dragon-fight-remastered": ("5OYoqItD", ".jar"),
    "ends-delight": ("La8SvoPm", ".jar"),
    "flyhigher": ("OzpXNl6w", ".jar"),
    "farmers-cutting-betterend": ("3rJRBvNa", ".zip"),
}
BETTER_END = ["betterend", "bclib", "wunderlib", "worldweaver"]
STRUCTURES = ["moogs-end-structures", "yungs-better-strongholds", "yungs-better-end-island"]
VARIANTS = {
    "control": [],
    "nullscape": [],
    "nullscape-betterend": BETTER_END,
    "nullscape-phantasm": ["phantasm"],
    "nullscape-unusual": ["unusual-end"],
    "nullscape-structures": STRUCTURES,
    "nullscape-edr": ["ender-dragon-fight-remastered"],
    "nullscape-flyhigher": ["flyhigher"],
    "betterend": BETTER_END,
    "betterend-food": BETTER_END + ["ends-delight"],
    "betterend-structures": BETTER_END + STRUCTURES,
}
CURSEFORGE = {
    "phantasm": ("Phantasm-1.0.3.jar",
                 "https://www.curseforge.com/minecraft/mc-mods/phantasm/files/6367800"),
}


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "Skyforge-S9-Review/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def download_modrinth(slug: str, target: Path) -> Path:
    version_id, expected_ext = MODRINTH[slug]
    version = json.loads(fetch(f"https://api.modrinth.com/v2/version/{version_id}"))
    files = version.get("files", [])
    primary = [item for item in files if item.get("primary")]
    selected = primary if len(primary) == 1 else files
    if len(selected) != 1:
        raise SystemExit(f"Expected one file for {slug} {version_id}; found {[f.get('filename') for f in files]}")
    entry = selected[0]
    if not entry.get("filename", "").lower().endswith(expected_ext):
        raise SystemExit(f"Unexpected official file for {slug}: {entry.get('filename')}")
    body = fetch(entry["url"])
    expected = entry.get("hashes", {}).get("sha512")
    actual = hashlib.sha512(body).hexdigest()
    if not expected or actual.lower() != expected.lower():
        raise SystemExit(f"Official SHA-512 verification failed for {slug} {version_id}")
    target.mkdir(parents=True, exist_ok=True)
    output = target / entry["filename"]
    output.write_bytes(body)
    return output


def add_overlay(source: Path, destination: Path) -> None:
    for name in ("mods", "config", "serverconfig", "defaultconfigs", "kubejs",
                 "resourcepacks", "shaderpacks", "options.txt"):
        item = source / name
        if not item.exists():
            continue
        target = destination / name
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            shutil.copy2(item, target)
    (destination / "mods").mkdir(parents=True, exist_ok=True)


def validate_phantasm(path: Path, source_url: str) -> Path:
    if not path.is_file():
        raise SystemExit(
            f"Phantasm is a local-only CurseForge review dependency. Download the official file at\n"
            f"  {source_url}\nand save it as\n  {path}"
        )
    with zipfile.ZipFile(path) as archive:
        candidates = [name for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
                      if name in archive.namelist()]
        if not candidates or "phantasm" not in "\n".join(
            archive.read(name).decode("utf-8", "replace").lower() for name in candidates
        ):
            raise SystemExit(f"The local CurseForge JAR is not identifiable as Phantasm: {path}")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("variant", choices=sorted(VARIANTS))
    parser.add_argument("s8_profile", type=Path,
                        help="Previously staged accepted S8 local profile containing mods/")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--java", default="java")
    args = parser.parse_args()

    repo = args.repo_root.expanduser().resolve()
    base = args.s8_profile.expanduser().resolve()
    if not (base / "mods").is_dir() or not list((base / "mods").glob("*.jar")):
        raise SystemExit(f"Accepted S8 profile is missing staged mods: {base / 'mods'}")

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run = repo / "skyforge-neoforge-1211" / f"run-wby-s9-{args.variant}-manual-{stamp}"
    diagnostics = repo / ".skyforge-diagnostics" / f"wby-s9-{args.variant}-{stamp}"
    downloads = repo / ".skyforge-diagnostics" / "s9-local-candidates" / args.variant
    world_packs = diagnostics / "world-datapacks"
    run.mkdir(parents=True, exist_ok=False)
    diagnostics.mkdir(parents=True, exist_ok=False)
    add_overlay(base, run)

    # Keep the already reviewed Nether datapack from the accepted S8 profile.
    s8_packs = sorted((base / "datapacks").glob("*.zip")) if (base / "datapacks").is_dir() else []
    if not s8_packs:
        s8_download = download_modrinth("incendium", downloads)
        s8_packs = [s8_download]
    world_packs.mkdir(parents=True, exist_ok=True)
    packs = []
    for pack in s8_packs:
        if "incendium" in pack.name.lower():
            packs.append(pack)

    variant_mods = list(VARIANTS[args.variant])
    if args.variant.startswith("nullscape"):
        packs.append(download_modrinth("nullscape", downloads))
    if args.variant == "betterend-food":
        packs.append(download_modrinth("farmers-cutting-betterend", downloads))

    overlay_jars = []
    for mod in variant_mods:
        if mod == "phantasm":
            filename, source = CURSEFORGE[mod]
            jar = validate_phantasm(downloads / filename, source)
        else:
            jar = download_modrinth(mod, downloads)
        overlay_jars.append(jar)
        shutil.copy2(jar, run / "mods" / jar.name)

    for pack in packs:
        shutil.copy2(pack, world_packs / pack.name)

    manifest = [
        "WBY S9 local manual review",
        f"variant={args.variant}",
        f"source_s8_profile={base}",
        f"run_directory={run}",
        f"seed=817309",
        "world_type=minecraft:normal",
        "s9_candidate_binaries_uploaded_by_skyforge=false",
        "",
        "[S9 overlay mods: filename sha256]",
    ]
    manifest += [f"{jar.name}\t{hashlib.sha256(jar.read_bytes()).hexdigest()}" for jar in overlay_jars]
    manifest += ["", "[world datapacks: filename sha256]"]
    manifest += [f"{pack.name}\t{hashlib.sha256(pack.read_bytes()).hexdigest()}" for pack in packs]
    (diagnostics / "manifest.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")

    print(f"S9 profile: {args.variant}")
    print(f"Copied accepted S8 baseline: {base}")
    print("Create a NEW normal Overworld with seed 817309. Do not reuse an existing world.")
    if packs:
        print("Before creating it, use Create World > Data Packs > Open Pack Folder and copy:")
        for pack in packs:
            print(f"  {world_packs / pack.name}")
        print("Enable the listed datapacks before creating the world.")
    print("Review the End generator and BOP biome ownership, End access, structure density, survival pressure, and save/reopen.")
    print("Keep shaders off for this worldgen/content gate; retain accepted Simple Clouds and Distant Horizons settings.")
    print("Phantasm is locally sourced from CurseForge for comparison and is not included in public CI artifacts.")
    launcher = repo / "scripts" / "wby-s4-launch-production-client.py"
    client_root = repo / ".skyforge-diagnostics" / "wby-s9-production-client"
    completed = subprocess.run([
        sys.executable, str(launcher), "--minecraft-directory", str(client_root),
        "--game-directory", str(run), "--username", "WbyS9Review", "--java", args.java,
    ], cwd=repo, check=False)
    log = run / "logs" / "latest.log"
    if log.is_file():
        shutil.copy2(log, diagnostics / "latest.log")
    crashes = run / "crash-reports"
    if crashes.is_dir():
        shutil.copytree(crashes, diagnostics / "crash-reports", dirs_exist_ok=True)
    (diagnostics / "staged-mods.txt").write_text(
        "\n".join(sorted(path.name for path in (run / "mods").glob("*.jar"))) + "\n",
        encoding="utf-8",
    )
    print(f"S9 diagnostics: {diagnostics}")
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
