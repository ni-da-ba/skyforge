#!/usr/bin/env python3
"""Emit license-safe S9 evidence about staged candidate artifacts without copying them."""
from __future__ import annotations

import argparse
import hashlib
import re
import zipfile
from pathlib import Path

MOD_ID = re.compile(r'(?m)^\s*modId\s*=\s*"([^"]+)"')


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def mod_ids(jar: Path) -> str:
    found = set()
    with zipfile.ZipFile(jar) as archive:
        for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if name in archive.namelist():
                found.update(MOD_ID.findall(archive.read(name).decode("utf-8", "replace")))
    return ",".join(sorted(found))


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--variant", required=True)
    p.add_argument("--client-mods", type=Path, required=True)
    p.add_argument("--server-mods", type=Path, required=True)
    p.add_argument("--datapacks", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    rows = [
        "kind\tfilename\tsha256\tmod_ids",
        f"profile\t{a.variant}\t\t",
        "notice\tNo S9 candidate JARs or datapacks are uploaded as review artifacts; Actions evidence contains hashes and metadata only.\t\t",
    ]
    for side, root in (("client", a.client_mods), ("server", a.server_mods)):
        for jar in sorted(root.glob("*.jar")):
            rows.append(f"{side}\t{jar.name}\t{sha256(jar)}\t{mod_ids(jar)}")
    for pack in sorted(a.datapacks.glob("*.zip")):
        rows.append(f"world-datapack\t{pack.name}\t{sha256(pack)}\t")
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"WBY S9 LICENSE-SAFE REVIEW MANIFEST PASS variant={a.variant}; no binaries copied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
