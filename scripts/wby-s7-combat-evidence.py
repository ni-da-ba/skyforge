#!/usr/bin/env python3
"""Create exact-pin and runtime-load evidence for the WBY S7 combat candidate arms."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import socket
import struct
import sys
import tomllib
import zipfile
from pathlib import Path
from typing import Any

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
ARMS = {"engineering": ENGINEERING, "tacz": ENGINEERING + TACZ, "scorched": ENGINEERING + SCORCHED}
ALL_CANDIDATES = ENGINEERING + TACZ + SCORCHED
TOML_PATH = "META-INF/neoforge.mods.toml"


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        match = re.match(r"([^:=\s]+)\s*[:=]\s*(.*)$", stripped)
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


def staged_profile(mods_dir: Path) -> tuple[set[str], dict[str, tuple[str, dict[str, Any]]]]:
    """Return jar hashes and metadata for an exactly staged server profile."""
    if not mods_dir.is_dir():
        raise SystemExit(f"staged mods directory does not exist: {mods_dir}")
    hashes: set[str] = set()
    mods: dict[str, tuple[str, dict[str, Any]]] = {}
    for jar in sorted(mods_dir.glob("*.jar")):
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        hashes.add(digest)
        doc = metadata(jar)
        for mod in doc.get("mods", []):
            mod_id = str(mod.get("modId", ""))
            if not mod_id:
                raise SystemExit(f"{jar.name} declares a blank mod id")
            previous = mods.get(mod_id)
            if previous is not None and previous[0] != jar.name:
                raise SystemExit(f"duplicate staged mod id {mod_id}: {previous[0]}, {jar.name}")
            mods[mod_id] = (jar.name, doc)
    return hashes, mods


def required_server_dependency_ids(doc: dict[str, Any], mod_id: str) -> set[str]:
    required: set[str] = set()
    entries = doc.get("dependencies", {}).get(mod_id, [])
    if isinstance(entries, dict):
        entries = [entries]
    for entry in entries:
        if str(entry.get("type", "")) != "required":
            continue
        if str(entry.get("side", "BOTH")) not in {"", "BOTH", "SERVER"}:
            continue
        dependency_id = str(entry.get("modId", ""))
        if dependency_id:
            required.add(dependency_id)
    return required


def license_for(metadata_doc: dict[str, Any], mod_id: str) -> str:
    matches = [mod for mod in metadata_doc.get("mods", []) if str(mod.get("modId", "")) == mod_id]
    return str(matches[0].get("license", "")) if len(matches) == 1 else ""


def safe(value: object) -> str:
    return str(value).replace("\\", "\\\\").replace("\t", " ").replace("\n", " ")


def rcon_packet(packet_id: int, packet_type: int, body: str) -> bytes:
    payload = struct.pack("<ii", packet_id, packet_type) + body.encode("utf-8") + b"\\x00\\x00"
    return struct.pack("<i", len(payload)) + payload


def rcon_response(sock: socket.socket) -> tuple[int, str]:
    chunks = bytearray()
    while len(chunks) < 4:
        chunk = sock.recv(4 - len(chunks))
        if not chunk:
            raise ConnectionError("RCON closed before packet length")
        chunks.extend(chunk)
    length = struct.unpack("<i", chunks)[0]
    payload = bytearray()
    while len(payload) < length:
        chunk = sock.recv(length - len(payload))
        if not chunk:
            raise ConnectionError("RCON closed before packet body")
        payload.extend(chunk)
    packet_id = struct.unpack("<i", payload[:4])[0]
    return packet_id, bytes(payload[8:-2]).decode("utf-8", errors="replace").strip()


def rcon(command: str, host: str, port: int, password: str) -> str:
    with socket.create_connection((host, port), timeout=15) as sock:
        sock.settimeout(30)
        sock.sendall(rcon_packet(1, 3, password))
        packet_id, _ = rcon_response(sock)
        if packet_id != 1:
            raise PermissionError("RCON authentication failed")
        sock.sendall(rcon_packet(2, 2, command))
        packet_id, body = rcon_response(sock)
        if packet_id != 2:
            raise RuntimeError("RCON response did not match the command")
        return body


def probe_tacz_npc(host: str, port: int, password: str) -> tuple[str, str]:
    summon = rcon(
        'execute at @a[limit=1] run summon tacznpcs:npc ~ ~ ~ {"template":""}',
        host, port, password,
    )
    selector = "@e[type=tacznpcs:npc,distance=..16,sort=nearest,limit=1]"
    uuid = rcon(
        f"execute at @a[limit=1] if entity {selector} run data get entity {selector} UUID",
        host, port, password,
    )
    if not summon or not uuid or "no entity" in uuid.lower():
        raise SystemExit(f"TaCZ NPC spawn/registry probe failed: summon={summon!r}; uuid={uuid!r}")
    return summon, uuid


def config_signals(value: object, prefix: str = "") -> list[str]:
    signals: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            path = f"{prefix}.{key}" if prefix else str(key)
            if any(marker in str(key).lower() for marker in ("spawn", "structure", "worldgen", "mob", "entity")):
                if not isinstance(child, (dict, list)):
                    signals.append(f"{path}={child}")
            signals.extend(config_signals(child, path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            signals.extend(config_signals(child, f"{prefix}[{index}]"))
    return signals


def append_runtime_config_evidence(root: Path, variant: str, report: list[list[object]]) -> None:
    if not root.is_dir():
        raise SystemExit(f"candidate runtime directory does not exist: {root}")
    candidate_markers = ("tacz", "scorched", "scguns", "firecontrol", "fire_control", "modernwarfare")
    files = sorted(
        path for path in root.rglob("*")
        if path.is_file()
        and path.suffix.lower() in {".toml", ".json"}
        and any(marker in str(path.relative_to(root)).lower() for marker in candidate_markers)
    )
    for path in files:
        relative = path.relative_to(root).as_posix()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        report.append(["runtime-config", variant, "", relative, digest, "", "", "", "", "candidate configuration"])
        try:
            if path.suffix.lower() == ".json":
                decoded: object = json.loads(path.read_text(encoding="utf-8"))
            else:
                decoded = tomllib.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError, tomllib.TOMLDecodeError) as exc:
            raise SystemExit(f"candidate config cannot be parsed: {relative}: {exc}") from exc
        for signal in config_signals(decoded):
            report.append(["config-signal", variant, "", relative, digest, "", "", "", "", signal])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--control-mods", type=Path)
    parser.add_argument("--staged-mods", type=Path)
    parser.add_argument("--runtime-root", type=Path, required=True)
    parser.add_argument("--rcon-host")
    parser.add_argument("--rcon-port", type=int, default=25575)
    parser.add_argument("--rcon-password")
    parser.add_argument("--pins", type=Path, default=Path("skyforge-neoforge-1211/wby-s7-combat.properties"))
    parser.add_argument("--variant", choices=tuple(ARMS), required=True)
    parser.add_argument("--server-log", type=Path, required=True)
    parser.add_argument("--client-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if bool(args.control_mods) != bool(args.staged_mods):
        raise SystemExit("--control-mods and --staged-mods must be supplied together")

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

    profile_delta: set[str] = set()
    append_runtime_config_evidence(args.runtime_root, args.variant, report)
    if args.variant == "tacz":
        if not args.rcon_host or not args.rcon_password:
            raise SystemExit("TaCZ arm requires RCON credentials for its live NPC spawn probe")
        summon, uuid = probe_tacz_npc(args.rcon_host, args.rcon_port, args.rcon_password)
        report.append(["entity-probe", args.variant, "", "", "", "tacznpcs:npc", "", "", "", f"summon={summon}; uuid={uuid}"])
    if args.control_mods and args.staged_mods:
        control_hashes, control_mods = staged_profile(args.control_mods)
        staged_hashes, staged_mods = staged_profile(args.staged_mods)
        control_ids = set(control_mods)
        staged_ids = set(staged_mods)
        missing_base_hashes = control_hashes - staged_hashes
        if missing_base_hashes:
            raise SystemExit(
                "candidate staging changed or omitted cumulative baseline jars; "
                f"missing hashes={sorted(missing_base_hashes)}"
            )
        if not control_ids <= staged_ids:
            raise SystemExit(f"candidate server staging omitted baseline mod IDs: {sorted(control_ids - staged_ids)}")

        profile_delta = staged_ids - control_ids
        expected_additions = set(active_mod_ids)
        pending = list(active_mod_ids)
        while pending:
            mod_id = pending.pop()
            staged_entry = staged_mods.get(mod_id)
            if staged_entry is None:
                raise SystemExit(f"candidate dependency closure is missing required mod ID {mod_id}")
            for dependency_id in required_server_dependency_ids(staged_entry[1], mod_id):
                if dependency_id not in control_ids and dependency_id not in expected_additions:
                    expected_additions.add(dependency_id)
                    pending.append(dependency_id)
        if profile_delta != expected_additions:
            raise SystemExit(
                "candidate profile differs from its declared required dependency closure: "
                f"unexpected={sorted(profile_delta - expected_additions)} "
                f"missing={sorted(expected_additions - profile_delta)}"
            )
        report.append([
            "profile-delta", args.variant, "", "", "", "", "", "", "",
            "control_ids=" + ",".join(sorted(control_ids))
            + ";candidate_added_ids=" + ",".join(sorted(profile_delta))
            + ";baseline_jar_hashes_preserved=" + str(len(control_hashes)),
        ])

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
