#!/usr/bin/env python3
"""Collect fixed-seed S7 structure-set metadata and bounded live /locate samples."""
from __future__ import annotations

import argparse
import json
import re
import socket
import struct
import zipfile
from pathlib import Path
from typing import Iterable

STRUCTURE = re.compile(r"^data/([^/]+)/worldgen/structure/(.+)\.json$")
SET = re.compile(r"^data/([^/]+)/worldgen/structure_set/(.+)\.json$")
S7_NAME_HINTS = (
    "friends", "foes", "pillage", "illager", "incontrol", "resourceful",
)
PILLAGE_ENTITY_IDS = (
    "takesapillage:archer",
    "takesapillage:legioner",
    "takesapillage:skirmisher",
)
INVALID_LOCATE = re.compile(
    r"unknown (?:or incomplete )?command|unknown structure|invalid structure|"
    r"could not find that structure",
    re.IGNORECASE,
)


def packet(packet_id: int, packet_type: int, body: str) -> bytes:
    payload = struct.pack("<ii", packet_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def receive_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        chunk = sock.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("RCON closed before completing a packet")
        chunks.extend(chunk)
    return bytes(chunks)


def receive_packet(sock: socket.socket) -> tuple[int, int, str]:
    length = struct.unpack("<i", receive_exact(sock, 4))[0]
    payload = receive_exact(sock, length)
    packet_id, packet_type = struct.unpack("<ii", payload[:8])
    body = payload[8:-2].decode("utf-8", errors="replace")
    return packet_id, packet_type, body


def rcon(command: str, host: str, port: int, password: str) -> str:
    with socket.create_connection((host, port), timeout=15) as sock:
        sock.settimeout(180)
        sock.sendall(packet(1, 3, password))
        response_id, _, body = receive_packet(sock)
        if response_id != 1:
            raise PermissionError("RCON authentication failed")
        sock.sendall(packet(2, 2, command))
        response_id, _, body = receive_packet(sock)
        if response_id != 2:
            raise RuntimeError("RCON response did not match its command")
        return body.strip()


def safe_cell(value: object) -> str:
    return str(value).replace("\t", " ").replace("\r", " ").replace("\n", " ")


def read_archive(path: Path) -> tuple[list[dict[str, object]], list[str]]:
    sets: list[dict[str, object]] = []
    structures: list[str] = []
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            match = SET.match(name)
            if match:
                document = json.loads(archive.read(name))
                placement = document.get("placement", {})
                spread = placement if placement.get("type") == "minecraft:random_spread" else {}
                refs = [
                    item.get("structure", "")
                    for item in document.get("structures", [])
                    if isinstance(item, dict)
                ]
                sets.append({
                    "id": match.group(1) + ":" + match.group(2),
                    "spacing": spread.get("spacing", ""),
                    "separation": spread.get("separation", ""),
                    "salt": spread.get("salt", ""),
                    "structures": ",".join(refs),
                })
            match = STRUCTURE.match(name)
            if match and match.group(1) not in {"minecraft", "neoforge"}:
                structures.append(match.group(1) + ":" + match.group(2))
    return sets, sorted(set(structures))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", required=True, type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", required=True)
    parser.add_argument("--seed", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--skip-entity-probes", action="store_true")
    parser.add_argument("--entities-only", action="store_true")
    args = parser.parse_args()
    if args.skip_entity_probes and args.entities_only:
        parser.error("--skip-entity-probes and --entities-only cannot be combined")

    rows: list[list[object]] = [
        ["record", "jar", "registry_id", "spacing_chunks", "separation_chunks", "salt", "details"],
        ["run", "", "", "", "", "", "dimension=minecraft:overworld; locate_origin=0,64,0; seed=" + args.seed],
    ]
    if args.entities_only:
        locate_targets: list[tuple[str, str]] = []
        accepted_locates = 0
    else:
        locate_targets: list[tuple[str, str]] = []
        for jar in sorted(args.mods.glob("*.jar")):
            if not any(hint in jar.name.lower() for hint in S7_NAME_HINTS):
                continue
            try:
                sets, structures = read_archive(jar)
            except (OSError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
                rows.append(["scan-error", jar.name, "", "", "", "", type(exc).__name__ + ": " + str(exc)])
                continue
            for item in sets:
                rows.append([
                    "structure-set", jar.name, item["id"], item["spacing"], item["separation"],
                    item["salt"], item["structures"],
                ])
            if structures:
                locate_targets.append((jar.name, structures[0]))

        accepted_locates = 0
        for jar_name, structure_id in locate_targets[:16]:
            command = (
                "execute in minecraft:overworld positioned 0 64 0 "
                "run locate structure " + structure_id
            )
            try:
                result = rcon(command, args.host, args.port, args.password)
            except Exception as exc:  # Preserve the failure in the evidence before stopping.
                result = "ERROR " + type(exc).__name__ + ": " + str(exc)
            rows.append(["locate", jar_name, structure_id, "", "", "", result or "(empty RCON response)"])
            if not result or result.startswith("ERROR ") or INVALID_LOCATE.search(result):
                raise SystemExit(f"Live /locate did not recognize staged structure {structure_id}: {result}")
            accepted_locates += 1

    registered_entities = 0
    if not args.skip_entity_probes:
        for entity_id in PILLAGE_ENTITY_IDS:
            kill_selector = (
                f"@e[type={entity_id},distance=..8,sort=nearest,limit=1]"
                if args.entities_only else f"@e[type={entity_id},limit=1]"
            )
            try:
                if args.entities_only:
                    summon_command = (
                        f"execute at @a run summon {entity_id} ~ ~ ~ "
                        "{PersistenceRequired:1b,NoAI:1b}"
                    )
                    entity_selector = (
                        f"@e[type={entity_id},distance=..8,sort=nearest,limit=1]"
                    )
                    uuid_command = (
                        f"execute at @a if entity {entity_selector} "
                        f"run data get entity {entity_selector} UUID"
                    )
                else:
                    summon_command = f"summon {entity_id} 0 80 0"
                    entity_selector = f"@e[type={entity_id},limit=1]"
                    uuid_command = (
                        f"execute if entity {entity_selector} "
                        f"run data get entity {entity_selector} UUID"
                    )
                summon_result = rcon(
                    summon_command, args.host, args.port, args.password,
                )
                uuid_result = rcon(
                    uuid_command, args.host, args.port, args.password,
                )
                evidence = f"summon={summon_result}; live_uuid={uuid_result}"
                rows.append(["entity", entity_id.split(":")[0], entity_id, "", "", "", evidence])
                if not summon_result or not uuid_result or "uuid" not in uuid_result.lower():
                    raise SystemExit(f"Live entity registration/spawn probe failed for {entity_id}: {evidence}")
                registered_entities += 1
            finally:
                try:
                    rcon(f"kill {kill_selector}", args.host, args.port, args.password)
                except Exception:
                    pass

    if not args.entities_only and not accepted_locates:
        raise SystemExit("No staged S7 structures were accepted by live /locate")
    if not args.skip_entity_probes and registered_entities != len(PILLAGE_ENTITY_IDS):
        raise SystemExit(
            f"Only {registered_entities}/{len(PILLAGE_ENTITY_IDS)} It Takes a Pillage entities registered"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.entities_only and args.output.is_file():
        rows_to_write = rows[2:]
        with args.output.open("a", encoding="utf-8") as evidence_file:
            evidence_file.write(
                "\n".join("\t".join(safe_cell(cell) for cell in row) for row in rows_to_write) + "\n"
            )
    else:
        args.output.write_text(
            "\n".join("\t".join(safe_cell(cell) for cell in row) for row in rows) + "\n",
            encoding="utf-8",
        )
    total_sets = sum(row[0] == "structure-set" for row in rows[1:])
    print("WBY S7 STRUCTURE/ENTITY EVIDENCE: seed=" + args.seed
          + " structure_sets=" + str(total_sets)
          + " registered_locates=" + str(accepted_locates)
          + " spawned_pillage_entities=" + str(registered_entities)
          + " report=" + str(args.output))
    if not args.entities_only and (total_sets == 0 or not locate_targets):
        raise SystemExit("No S7 structure-set metadata or custom structure IDs were found in staged candidate jars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
