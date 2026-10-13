#!/usr/bin/env python3
"""Record bounded S8 Nether substrate artifact and pack-format evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

MOD_ID = re.compile(r'(?m)^\\s*modId\\s*=\\s*"([^"]+)"')
WORLDGEN = re.compile(r"^data/[^/]+/worldgen/(biome|structure|structure_set|configured_feature|placed_feature)/")
EXPECTED = {
    "jadensNetherExpansion": "jadensnetherexpansion",
    "elysiumApi": "elysium_api",
    "lodestone": "lodestone",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe(name: str) -> bool:
    path = Path(name)
    return not path.is_absolute() and ".." not in path.parts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=("control", "jne", "incendium"), required=True)
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--datapacks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    rows = ["kind\tname\tsha256\tmod_ids\tworldgen_resource_counts\tnotes"]
    found: set[str] = set()
    for jar in sorted(args.mods.glob("*.jar")):
        ids: set[str] = set()
        counts = {kind: 0 for kind in ("biome", "structure", "structure_set", "configured_feature", "placed_feature")}
        with zipfile.ZipFile(jar) as archive:
            for metadata_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                try:
                    ids.update(MOD_ID.findall(archive.read(metadata_name).decode("utf-8", "replace")))
                except KeyError:
                    pass
            for name in archive.namelist():
                match = WORLDGEN.match(name)
                if match:
                    counts[match.group(1)] += 1
        found.update(ids)
        count_text = ",".join(f"{kind}:{counts[kind]}" for kind in counts)
        rows.append(f"mod\t{jar.name}\t{sha256(jar)}\t{','.join(sorted(ids))}\t{count_text}\t")

    expected = set(EXPECTED.values()) if args.variant == "jne" else set()
    for mod_id in expected:
        if mod_id not in found:
            raise SystemExit(f"S8 {args.variant} profile is missing expected mod id {mod_id}; found {sorted(found)}")
    leaked = set(EXPECTED.values()) - expected
    if leaked & found:
        raise SystemExit(f"S8 {args.variant} profile leaked unselected JNE content: {sorted(leaked & found)}")

    packs = sorted(args.datapacks.glob("*.zip"))
    if args.variant == "incendium":
        packs = [p for p in packs if p.name.lower().startswith("incendium")]
        if len(packs) != 1:
            raise SystemExit(f"Expected exactly one official Incendium ZIP; found {[p.name for p in packs]}")
        pack = packs[0]
        with zipfile.ZipFile(pack) as archive:
            names = archive.namelist()
            if any(not safe(name) for name in names):
                raise SystemExit("Unsafe path in staged Incendium ZIP")
            try:
                meta = json.loads(archive.read("pack.mcmeta"))
            except (KeyError, json.JSONDecodeError) as exc:
                raise SystemExit(f"Incendium ZIP has no valid root pack.mcmeta: {exc}")
            pack_section = meta.get("pack", {})
            pack_format = pack_section.get("pack_format")
            if not isinstance(pack_format, int):
                raise SystemExit(f"Incendium pack.mcmeta has no integer pack_format: {pack_section}")
            rows.append(f"datapack\t{pack.name}\t{sha256(pack)}\t\t\tpack_format={pack_format}; files={len(names)}")
    elif packs:
        raise SystemExit(f"S8 {args.variant} profile must not contain a Nether candidate data pack: {[p.name for p in packs]}")

    if args.variant != "incendium" and args.datapacks.exists():
        candidate_names = [p.name for p in args.datapacks.glob("*") if p.name.lower().startswith("incendium")]
        if candidate_names:
            raise SystemExit(f"S8 {args.variant} profile leaked Incendium: {candidate_names}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"WBY S8 SUBSTRATE ARTIFACT EVIDENCE PASS variant={args.variant} mods={len(list(args.mods.glob('*.jar')))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
