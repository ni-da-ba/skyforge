#!/usr/bin/env python3
"""Validate staged S7 combat identities, licenses, hashes, and duplicate mod IDs."""
from __future__ import annotations

import argparse
import hashlib
import tomllib
from pathlib import Path
from zipfile import BadZipFile, ZipFile

ENGINEERING = (
    "copycatsPlus",
    "createRadars",
    "createAeroRadars",
    "createFireControl",
    "mianbaosNewModernWarfare",
    "cbcNeoWarfare",
    "cbcTerminalBallistics",
)
TACZ = ("tacz", "createTacz", "taczAeronauticsCompat", "taczNpcs")
SCORCHED = ("scorchedGuns", "framework", "curios")
ARMS = {
    "engineering": ENGINEERING,
    "tacz": ENGINEERING + TACZ,
    "scorched": ENGINEERING + SCORCHED,
}
ALL_CANDIDATES = ENGINEERING + TACZ + SCORCHED
METADATA_PATHS = {
    "meta-inf/neoforge.mods.toml",
    "meta-inf/mods.toml",
}


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        if "=" not in stripped:
            raise SystemExit(f"{path}:{line_number}: malformed properties row")
        key, value = stripped.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def coordinate_token(coordinate: str) -> str:
    parts = coordinate.split(":")
    if len(parts) != 3:
        raise SystemExit(f"Expected immutable Maven G:A:V coordinate, got {coordinate!r}")
    return parts[1] + "-" + parts[2]


def inspect_jar(jar: Path) -> tuple[list[dict[str, object]], str]:
    try:
        with ZipFile(jar) as archive:
            names = archive.namelist()
            candidates = [name for name in names if name.lower() in METADATA_PATHS]
            if not candidates:
                return [], hashlib.sha256(jar.read_bytes()).hexdigest()
            # Prefer NeoForge metadata if a compatibility artifact carries both formats.
            metadata_path = next(
                (name for name in candidates if name.lower() == "meta-inf/neoforge.mods.toml"),
                candidates[0],
            )
            document = tomllib.loads(archive.read(metadata_path).decode("utf-8"))
    except (BadZipFile, UnicodeError, tomllib.TOMLDecodeError) as exc:
        raise SystemExit(f"Cannot inspect staged mod metadata in {jar}: {exc}") from exc
    declarations = document.get("mods", [])
    if not isinstance(declarations, list):
        raise SystemExit(f"Invalid mod declarations in {jar}")
    return [item for item in declarations if isinstance(item, dict)], hashlib.sha256(jar.read_bytes()).hexdigest()


def scan_profile(label: str, directory: Path) -> tuple[dict[str, str], dict[str, Path]]:
    if not directory.is_dir():
        raise SystemExit(f"{label}: staged mods directory does not exist: {directory}")
    jars = sorted(directory.glob("*.jar"))
    if not jars:
        raise SystemExit(f"{label}: staged no JAR files in {directory}")
    owners: dict[str, str] = {}
    hashes: dict[str, Path] = {}
    for jar in jars:
        declarations, digest = inspect_jar(jar)
        hashes[digest] = jar
        for declaration in declarations:
            mod_id = str(declaration.get("modId", "")).strip()
            if not mod_id:
                continue
            previous = owners.get(mod_id)
            if previous is not None:
                raise SystemExit(f"{label}: duplicate staged mod id {mod_id}: {previous}, {jar.name}")
            owners[mod_id] = jar.name
    print(f"S7 staging preflight PASS profile={label} jars={len(jars)} mod_ids={len(owners)}")
    return owners, hashes


def candidate_identity(
    variant: str,
    pins: dict[str, str],
    server_dir: Path,
    client_dir: Path,
    output: Path,
) -> None:
    server_jars = sorted(server_dir.glob("*.jar"))
    client_jars = sorted(client_dir.glob("*.jar"))
    rows = [
        [
            "variant", "candidate", "coordinate", "expected_version", "metadata_version",
            "expected_license", "metadata_license", "source", "jar", "sha256",
        ]
    ]
    for key in ALL_CANDIDATES:
        coordinate = pins.get(f"{key}.coordinate", "")
        if not coordinate:
            raise SystemExit(f"Missing {key}.coordinate in candidate pin manifest")
        token = coordinate_token(coordinate)
        server_matches = [jar for jar in server_jars if token.lower() in jar.name.lower()]
        client_matches = [jar for jar in client_jars if token.lower() in jar.name.lower()]
        selected = key in ARMS[variant]
        if not selected:
            if server_matches or client_matches:
                raise SystemExit(f"Unselected candidate {key} leaked into {variant}")
            continue
        if len(server_matches) != 1 or len(client_matches) != 1:
            raise SystemExit(
                f"Expected one selected {key} jar on each side; "
                f"server={[p.name for p in server_matches]} client={[p.name for p in client_matches]}"
            )
        server_jar, client_jar = server_matches[0], client_matches[0]
        server_declarations, digest = inspect_jar(server_jar)
        _, client_digest = inspect_jar(client_jar)
        if digest != client_digest:
            raise SystemExit(f"{key}: client/server artifact hashes differ")
        if not server_declarations:
            raise SystemExit(f"{key}: no mod declaration in {server_jar.name}")
        expected_version = pins.get(f"{key}.version", "").strip()
        expected_license = pins.get(f"{key}.license", "").strip()
        source = pins.get(f"{key}.source", "").strip()
        if not expected_version or not expected_license or not source.startswith("https://"):
            raise SystemExit(f"{key}: manifest must pin version, license, and HTTPS source")
        expected_hash = pins.get(f"{key}.sha256", "").strip().lower()
        if expected_hash and digest.lower() != expected_hash:
            raise SystemExit(f"{key}: SHA-256 mismatch; expected={expected_hash} actual={digest}")
        for declaration in server_declarations:
            actual_version = str(declaration.get("version", "")).strip()
            if actual_version and actual_version not in {expected_version, "${file.jarVersion}"}:
                raise SystemExit(
                    f"{key}: metadata version mismatch; expected={expected_version!r} actual={actual_version!r}"
                )
        primary = server_declarations[0]
        metadata_version = str(primary.get("version", "")).strip()
        metadata_license = str(primary.get("license", "")).strip()
        rows.append([
            variant, key, coordinate, expected_version, metadata_version,
            expected_license, metadata_license, source, server_jar.name, digest,
        ])
    output.write_text(
        "\n".join("\t".join(cell.replace("\t", " ").replace("\n", " ") for cell in row) for row in rows) + "\n",
        encoding="utf-8",
    )
    print(f"S7 candidate identity evidence PASS variant={variant} candidates={len(rows) - 1} report={output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--control-server", type=Path, required=True)
    parser.add_argument("--control-client", type=Path, required=True)
    parser.add_argument("--candidate-server", type=Path, required=True)
    parser.add_argument("--candidate-client", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--variant", choices=tuple(ARMS), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    control_server_ids, control_server_hashes = scan_profile("control-server", args.control_server)
    control_client_ids, control_client_hashes = scan_profile("control-client", args.control_client)
    candidate_server_ids, candidate_server_hashes = scan_profile("candidate-server", args.candidate_server)
    candidate_client_ids, candidate_client_hashes = scan_profile("candidate-client", args.candidate_client)

    for label, control_ids, control_hashes, candidate_ids, candidate_hashes in (
        ("server", control_server_ids, control_server_hashes, candidate_server_ids, candidate_server_hashes),
        ("client", control_client_ids, control_client_hashes, candidate_client_ids, candidate_client_hashes),
    ):
        missing_hashes = set(control_hashes) - set(candidate_hashes)
        missing_ids = set(control_ids) - set(candidate_ids)
        if missing_hashes or missing_ids:
            raise SystemExit(
                f"{label}: candidate profile altered the cumulative baseline; "
                f"missing jar hashes={sorted(missing_hashes)} missing mod IDs={sorted(missing_ids)}"
            )
        print(f"S7 baseline preservation PASS side={label} jars={len(control_hashes)} mod_ids={len(control_ids)}")

    pins = read_properties(args.pins)
    candidate_identity(args.variant, pins, args.candidate_server, args.candidate_client, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
