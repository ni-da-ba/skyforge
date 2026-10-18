#!/usr/bin/env python3
"""Check a Simple Backups archive by safely extracting a disposable restore probe."""
from __future__ import annotations

import argparse
import hashlib
import tempfile
import zipfile
from pathlib import Path, PurePosixPath


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backups", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    archives = sorted(
        (path for path in args.backups.rglob("*.zip") if path.is_file()),
        key=lambda path: path.stat().st_mtime_ns,
    )
    if not archives:
        raise SystemExit(f"No Simple Backups ZIP found under {args.backups}")
    archive = archives[-1]
    with zipfile.ZipFile(archive) as bundle:
        bad_member = bundle.testzip()
        if bad_member:
            raise SystemExit(f"Backup archive CRC failed at {bad_member}")
        members = [name for name in bundle.namelist() if not name.endswith("/")]
        for name in bundle.namelist():
            candidate = PurePosixPath(name)
            if candidate.is_absolute() or ".." in candidate.parts:
                raise SystemExit(f"Unsafe backup member path: {name}")
        level_files = [name for name in members if PurePosixPath(name).name == "level.dat"]
        if len(level_files) != 1:
            raise SystemExit(f"Expected exactly one world level.dat in {archive.name}; found {level_files}")
        with tempfile.TemporaryDirectory(prefix="wby-s10-restore-probe-") as tmp:
            restore = Path(tmp) / "restore-copy"
            restore.mkdir()
            for info in bundle.infolist():
                relative = PurePosixPath(info.filename)
                if info.is_dir():
                    (restore / Path(*relative.parts)).mkdir(parents=True, exist_ok=True)
                    continue
                target = restore / Path(*relative.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(bundle.read(info))
            restored_level = restore.joinpath(*PurePosixPath(level_files[0]).parts)
            if not restored_level.is_file():
                raise SystemExit("Restored probe is missing level.dat")
            region_count = sum(
                1 for path in restore.rglob("*")
                if path.is_file() and path.suffix == ".mca"
            )
            if not region_count:
                raise SystemExit("Restored probe contains no region files")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(
                "kind\tarchive\tsha256\tworld_level_dat\tregion_files\tnotes\n"
                f"restore-probe\t{archive.name}\t{sha256(archive)}\t"
                f"{level_files[0]}\t{region_count}\tCRC-valid isolated extraction\n",
                encoding="utf-8",
            )
            print(
                f"WBY S10 BACKUP RESTORE PROBE PASS archive={archive.name} "
                f"regions={region_count} sha256={sha256(archive)}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
