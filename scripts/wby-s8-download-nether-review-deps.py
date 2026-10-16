#!/usr/bin/env python3
"""Download immutable official S8 review dependencies into the local diagnostics folder.

This utility writes only into .skyforge-diagnostics, never into a distributable pack.
Modrinth releases are checked against their published SHA-512 values. CurseForge downloads
use immutable project/file IDs and are hashed locally. ARR files are for local review only.
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS = ROOT / ".skyforge-diagnostics" / "s8-nether-downloads"
USER_AGENT = "Skyforge-WBY-S8-Review/1.0 (local diagnostic profile)"

MODRINTH = {
    "create-nether-industry": ("SNPbuKeo", "create-nether-industry"),
    "thinair-relived": ("WW4CTUTl", "thinair-relived"),
    "yungs-better-nether-fortresses": ("iopJiJQp", "yungs-better-nether-fortresses"),
    "incendium": ("pwe1kTJE", "incendium"),
}
CURSEFORGE = {
    "nether-depths-upgrade": (670011, 8940364, "netherdepthsupgrade-3.3-1.21.1-NeoForge.jar"),
    "curios": (309927, 6529130, "curios-9.5.1+1.21.1.jar"),
    "my-nethers-delight": (1003673, 8854319, "MyNethersDelight-1.21.1-1.10.4.1.jar"),
}


def request(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.read()


def save_checked(name: str, data: bytes, expected_sha512: str | None = None) -> Path:
    digest = hashlib.sha512(data).hexdigest()
    if expected_sha512 and digest.lower() != expected_sha512.lower():
        raise SystemExit(f"SHA-512 mismatch for {name}: expected {expected_sha512}, got {digest}")
    path = DOWNLOADS / name
    if path.exists():
        existing = path.read_bytes()
        if existing != data:
            raise SystemExit(f"Refusing to overwrite a different existing file: {path}")
    else:
        path.write_bytes(data)
    if not zipfile.is_zipfile(path):
        raise SystemExit(f"Downloaded file is not a valid JAR/ZIP: {path}")
    print(f"verified {path.relative_to(ROOT)} sha512={digest}")
    return path


def modrinth(version_id: str, alias: str) -> None:
    metadata = json.loads(request(f"https://api.modrinth.com/v2/version/{version_id}"))
    if version_id not in {metadata.get("id"), metadata.get("version_number")}:
        raise SystemExit(f"Modrinth returned unexpected release metadata for {version_id}")
    if alias != "incendium":
        if "neoforge" not in metadata.get("loaders", []):
            raise SystemExit(f"{alias} release is not marked NeoForge: {metadata.get('loaders')}")
        if "1.21.1" not in metadata.get("game_versions", []):
            raise SystemExit(f"{alias} release is not marked for Minecraft 1.21.1")
    else:
        if not {"1.21", "1.21.1"}.issubset(set(metadata.get("game_versions", []))):
            raise SystemExit("Incendium release does not list Minecraft 1.21.1 support")
    files = [item for item in metadata.get("files", []) if item.get("primary") and item.get("filename")]
    if len(files) != 1:
        raise SystemExit(f"Expected one primary file for {version_id}, got {[f.get('filename') for f in files]}")
    item = files[0]
    filename = "Incendium-5.4.4.zip" if alias == "incendium" else item["filename"]
    data = request(item["url"])
    save_checked(filename, data, item.get("hashes", {}).get("sha512"))


def curseforge(project_id: int, file_id: int, filename: str) -> None:
    url = f"https://www.curseforge.com/api/v1/mods/{project_id}/files/{file_id}/download"
    data = request(url)
    save_checked(filename, data)


def main() -> int:
    if len(sys.argv) != 2 or sys.argv[1] != "incendium-full":
        raise SystemExit("Usage: python scripts/wby-s8-download-nether-review-deps.py incendium-full")
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    for alias, (version_id, project) in MODRINTH.items():
        modrinth(version_id, alias)
    for alias, (project_id, file_id, filename) in CURSEFORGE.items():
        curseforge(project_id, file_id, filename)
    print("S8 Incendium full-profile review dependencies are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
