#!/usr/bin/env python3
"""Prepare and launch a local S10 overlay from an accepted S9 review profile.

Candidate binaries stay local. Modrinth assets are pinned by version ID and SHA-512;
CurseForge assets are pinned by project/file ID; FTB assets use exact Maven coordinates.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

MINECRAFT_VERSION = "1.21.1"
NEOFORGE_VERSION = "21.1.249"

PROFILES = {
    "none": [],
    "quests": ["ftbLibrary", "ftbTeams", "ftbQuests"],
    "quests-book": ["ftbLibrary", "ftbTeams", "ftbQuests", "patchouli"],
    "enchantment-industry": ["createEnchantmentIndustry"],
    "communications": ["simpleVoiceChat", "walkieTalkiePlus"],
    "operations-stable": ["simpleBackupsStable", "serverCore"],
    "operations-beta": ["simpleBackupsBeta", "serverCore"],
    "adaptive-music": ["trueAdaptiveMusic"],
    "surface-audio": ["presenceFootsteps", "presenceFootstepsSable"],
    "full": [
        "ftbLibrary", "ftbTeams", "ftbQuests", "patchouli",
        "createEnchantmentIndustry", "simpleVoiceChat", "walkieTalkiePlus",
        "simpleBackupsStable", "serverCore", "trueAdaptiveMusic",
        "presenceFootsteps", "presenceFootstepsSable",
    ],
}
CURSE_FILES = {
    8006069: ("walkietalkie-1.4.0.jar", "walkietalkie"),
}


def normalize_path(raw: str | Path) -> Path:
    value = str(raw)
    if os.name == "nt":
        match = re.match(r"^/([A-Za-z])/(.*)$", value)
        if match:
            value = f"{match.group(1).upper()}:/{match.group(2)}"
    return Path(value).expanduser().resolve()


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if not text or text.startswith(("#", "!")):
            continue
        key, sep, value = text.partition("=")
        if sep:
            values[key.strip()] = value.strip()
    return values


def fetch(url: str, *, binary: bool = True) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Skyforge-S10-Review/1.0", "Accept": "*/*"},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    return data if binary else data.strip()


def sha256(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def check_jar(body: bytes, filename: str) -> None:
    try:
        with zipfile.ZipFile(__import__("io").BytesIO(body)) as archive:
            bad = archive.testzip()
            if bad:
                raise SystemExit(f"JAR CRC check failed for {filename}: {bad}")
            if not any(name in archive.namelist() for name in (
                "META-INF/neoforge.mods.toml", "META-INF/mods.toml"
            )):
                raise SystemExit(f"Downloaded file is not a NeoForge mod JAR: {filename}")
    except zipfile.BadZipFile as exc:
        raise SystemExit(f"Downloaded file is not a valid JAR: {filename}") from exc


def download_modrinth(project_id: str, version_id: str, cache: Path) -> tuple[Path, str]:
    version = json.loads(fetch(f"https://api.modrinth.com/v2/version/{version_id}"))
    if version.get("id") != version_id or version.get("project_id") != project_id:
        raise SystemExit(f"Modrinth returned a different pin for {project_id}:{version_id}")
    if MINECRAFT_VERSION not in version.get("game_versions", []) or "neoforge" not in version.get("loaders", []):
        raise SystemExit(f"Modrinth pin {version_id} is not for NeoForge {MINECRAFT_VERSION}")
    files = version.get("files", [])
    selected = [file for file in files if file.get("primary")] or files
    if len(selected) != 1:
        raise SystemExit(f"Expected one primary Modrinth file for {version_id}; got {len(selected)}")
    entry = selected[0]
    expected = entry.get("hashes", {}).get("sha512")
    filename = entry.get("filename", "")
    if not filename.lower().endswith(".jar") or not expected:
        raise SystemExit(f"Unexpected Modrinth JAR metadata for {version_id}: {filename}")
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / filename
    body = target.read_bytes() if target.is_file() else fetch(entry["url"])
    if hashlib.sha512(body).hexdigest().lower() != expected.lower():
        body = fetch(entry["url"])
        if hashlib.sha512(body).hexdigest().lower() != expected.lower():
            raise SystemExit(f"Modrinth SHA-512 mismatch for {project_id}:{version_id}")
        target.write_bytes(body)
    check_jar(body, filename)
    return target, sha256(body)


def download_curseforge(project_id: int, file_id: int, cache: Path) -> tuple[Path, str]:
    expected_name, expected_mod_id = CURSE_FILES[file_id]
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / expected_name
    if target.is_file():
        body = target.read_bytes()
    else:
        url = f"https://www.curseforge.com/api/v1/mods/{project_id}/files/{file_id}/download"
        response = fetch(url)
        if response.lstrip().startswith(b"{"):
            payload = json.loads(response)
            direct_url = payload.get("data")
            if not direct_url:
                raise SystemExit(f"CurseForge did not return a download URL for file {file_id}")
            response = fetch(direct_url)
        body = response
        target.write_bytes(body)
    check_jar(body, expected_name)
    with zipfile.ZipFile(__import__("io").BytesIO(body)) as archive:
        metadata = "\n".join(
            archive.read(name).decode("utf-8", "replace").lower()
            for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
            if name in archive.namelist()
        )
    if expected_mod_id not in metadata:
        target.unlink(missing_ok=True)
        raise SystemExit(f"CurseForge JAR file {file_id} does not identify as {expected_mod_id}")
    return target, sha256(body)


def download_ftb(group: str, artifact: str, version: str, cache: Path) -> tuple[Path, str]:
    base = "https://maven.ftb.dev/releases"
    relative = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"
    url = f"{base}/{relative}"
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / f"{artifact}-{version}.jar"
    body = fetch(url)
    check_jar(body, target.name)
    target.write_bytes(body)
    return target, sha256(body)


def resolve_pin(mod: str, pins: dict[str, str], cache: Path) -> tuple[Path, str]:
    coordinate = pins.get(f"{mod}.coordinate")
    if not coordinate:
        raise SystemExit(f"Missing exact coordinate for {mod} in the S10 pin file")
    parts = coordinate.split(":")
    if len(parts) != 3:
        raise SystemExit(f"Invalid Maven coordinate for {mod}: {coordinate}")
    group, artifact, version = parts
    if group == "maven.modrinth":
        return download_modrinth(artifact, version, cache)
    if group == "curse.maven":
        project_match = re.search(r"-(\d+)$", artifact)
        if not project_match:
            raise SystemExit(f"Cannot parse CurseForge project ID from {coordinate}")
        return download_curseforge(int(project_match.group(1)), int(version), cache)
    if group == "dev.ftb.mods":
        return download_ftb(group, artifact, version, cache)
    raise SystemExit(f"Unsupported official provider in S10 pin: {coordinate}")


def copy_overlay(source: Path, destination: Path) -> None:
    for name in (
        "mods", "config", "serverconfig", "defaultconfigs", "kubejs",
        "resourcepacks", "shaderpacks", "options.txt",
    ):
        item = source / name
        if not item.exists():
            continue
        target = destination / name
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            shutil.copy2(item, target)
    (destination / "mods").mkdir(parents=True, exist_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=sorted(PROFILES))
    parser.add_argument("accepted_s9_profile", type=Path)
    parser.add_argument("accepted_s9_diagnostics", type=Path)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    base = normalize_path(args.accepted_s9_profile)
    s9_diagnostics = normalize_path(args.accepted_s9_diagnostics)
    datapacks = s9_diagnostics / "world-datapacks"
    if not (base / "mods").is_dir() or not list((base / "mods").glob("*.jar")):
        raise SystemExit(f"Accepted S9 profile is missing staged mods: {base / 'mods'}")
    pack_files = sorted(path for path in datapacks.glob("*.zip") if path.is_file())
    if not any("incendium" in path.name.lower() for path in pack_files):
        raise SystemExit(f"Accepted S9 diagnostics must contain the S8 Incendium pack under {datapacks}")
    if not any("nullscape" in path.name.lower() for path in pack_files):
        raise SystemExit(f"Accepted S9 diagnostics must contain nullscape-review.zip under {datapacks}")

    pins_path = repo / "skyforge-neoforge-1211" / "wby-s10-onboarding-operations.properties"
    pins = read_properties(pins_path)
    if pins.get("minecraft.version") != MINECRAFT_VERSION or pins.get("neoforge.version") != NEOFORGE_VERSION:
        raise SystemExit("The S10 pin file does not match the supported Minecraft/NeoForge runtime")

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run = repo / "skyforge-neoforge-1211" / f"run-wby-s10-{args.profile}-manual-{stamp}"
    diagnostics = repo / ".skyforge-diagnostics" / f"wby-s10-{args.profile}-{stamp}"
    cache = repo / ".skyforge-diagnostics" / "s10-local-candidates" / args.profile
    run.mkdir(parents=True, exist_ok=False)
    diagnostics.mkdir(parents=True, exist_ok=False)
    copy_overlay(base, run)

    candidate_files: list[tuple[str, Path, str]] = []
    for mod in PROFILES[args.profile]:
        path, digest = resolve_pin(mod, pins, cache)
        shutil.copy2(path, run / "mods" / path.name)
        candidate_files.append((mod, path, digest))

    if "serverCore" in PROFILES[args.profile]:
        servercore = run / "config" / "servercore"
        servercore.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repo / "skyforge-neoforge-1211" / "wby-s10-servercore-config.yml", servercore / "config.yml")
        shutil.copy2(repo / "skyforge-neoforge-1211" / "wby-s10-servercore-optimizations.yml", servercore / "optimizations.yml")
    if "simpleBackupsStable" in PROFILES[args.profile] or "simpleBackupsBeta" in PROFILES[args.profile]:
        shutil.copy2(repo / "skyforge-neoforge-1211" / "wby-s10-simplebackups-common.toml", run / "config" / "simplebackups-common.toml")

    staged_packs = diagnostics / "world-datapacks"
    staged_packs.mkdir(parents=True, exist_ok=True)
    for pack in pack_files:
        shutil.copy2(pack, staged_packs / pack.name)

    lines = [
        "WBY S10 local manual review",
        f"profile={args.profile}",
        f"accepted_s9_profile={base}",
        f"run_directory={run}",
        f"seed=817310",
        "world_type=minecraft:normal",
        "third_party_candidate_binaries_uploaded_by_skyforge=false",
        "",
        "[S10 overlay mods: pin filename sha256]",
    ]
    for mod, path, digest in candidate_files:
        lines.append(f"{mod}\t{pins.get(f'{mod}.coordinate')}\t{path.name}\t{digest}")
    lines.extend(["", "[accepted S9 world datapacks: filename sha256]"])
    lines.extend(f"{pack.name}\t{sha256(pack.read_bytes())}" for pack in pack_files)
    (diagnostics / "manifest.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (run / "mods").mkdir(parents=True, exist_ok=True)
    (diagnostics / "staged-mods.txt").write_text(
        "\n".join(sorted(path.name for path in (run / "mods").glob("*.jar"))) + "\n",
        encoding="utf-8",
    )

    print(f"S10 profile: {args.profile}")
    print(f"Accepted S9 base: {base}")
    print(f"S10 game directory: {run}")
    print(f"S10 diagnostics: {diagnostics}")
    print("Create a NEW normal Overworld with seed 817310.")
    print("Open Data Packs > Open Pack Folder and copy both listed ZIPs from:")
    print(f"  {staged_packs}")
    print("Enable Incendium and Nullscape before creating the world.")
    print("Keep shaders off for this content/integration review.")
    print("Review the full candidate set for launch, save/reopen, progression, audio, and server behavior.")
    if args.profile in ("full", "enchantment-industry"):
        print("Specific CEI check: install the Mechanical Grindstone on an Item Drain, operate it with Sable enabled, then save/reopen.")
    if args.profile in ("full", "communications"):
        print("Specific communications check: test proximity voice and radio range/frequency locally; leave internet-stream broadcasting off.")
    if args.profile in ("full", "operations-stable", "operations-beta"):
        print("Specific operations check: run /simplebackups backup start, locate the archive under simplebackups, and restore only into a disposable copy.")
    launcher = repo / "scripts" / "wby-s4-launch-production-client.py"
    client_root = repo / ".skyforge-diagnostics" / "wby-s10-production-client"
    result = subprocess.run(
        [
            sys.executable, str(launcher),
            "--minecraft-directory", str(client_root),
            "--game-directory", str(run),
            "--username", "WbyS10Review",
            "--java", args.java,
        ],
        cwd=repo, check=False,
    )
    latest = run / "logs" / "latest.log"
    if latest.is_file():
        shutil.copy2(latest, diagnostics / "latest.log")
    crashes = run / "crash-reports"
    if crashes.is_dir():
        shutil.copytree(crashes, diagnostics / "crash-reports", dirs_exist_ok=True)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
