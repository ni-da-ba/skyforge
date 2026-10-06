#!/usr/bin/env python3
"""Collect fixed-seed S7 structure-set metadata and bounded live /locate samples."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import socket
import struct
import tomllib
import zipfile
from pathlib import Path
from typing import Iterable

STRUCTURE = re.compile(r"^data/([^/]+)/worldgen/structure/(.+)\.json$")
SET = re.compile(r"^data/([^/]+)/worldgen/structure_set/(.+)\.json$")
S7_NAME_HINTS = (
    "friends", "foes", "pillage", "illager", "incontrol", "resourceful", "mowzie",
    "iceandfire", "jupiter", "uranus",
)
PILLAGE_ENTITY_IDS = (
    "takesapillage:archer",
    "takesapillage:legioner",
    "takesapillage:skirmisher",
)
MOWZIE_ENTITY_IDS = ("mowziesmobs:foliaath",)
ICE_AND_FIRE_ENTITY_IDS = ("iceandfire:fire_dragon", "iceandfire:stymphalian_bird")
ENTITY_PROBE_IDS = PILLAGE_ENTITY_IDS + MOWZIE_ENTITY_IDS
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
    parser.add_argument("--mowzie-output", required=True, type=Path)
    parser.add_argument("--ice-and-fire", action="store_true")
    parser.add_argument("--ice-and-fire-output", type=Path)
    parser.add_argument("--skip-entity-probes", action="store_true")
    parser.add_argument("--entities-only", action="store_true")
    args = parser.parse_args()
    if args.skip_entity_probes and args.entities_only:
        parser.error("--skip-entity-probes and --entities-only cannot be combined")
    if args.ice_and_fire and args.ice_and_fire_output is None:
        parser.error("--ice-and-fire-output is required with --ice-and-fire")

    mowzie_jars = sorted(
        jar for jar in args.mods.glob("*.jar")
        if "mowzies-mobs-250498-7760267" in jar.name.lower()
    )
    if len(mowzie_jars) != 1:
        raise SystemExit(f"Expected the pinned Mowzie's Mobs jar; found {[jar.name for jar in mowzie_jars]}")
    mowzie_jar = mowzie_jars[0]
    mowzie_sha256 = hashlib.sha256(mowzie_jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(mowzie_jar) as archive:
        mowzie_structures = sorted(
            name.removeprefix("data/mowziesmobs/worldgen/structure/").removesuffix(".json")
            for name in archive.namelist()
            if name.startswith("data/mowziesmobs/worldgen/structure/") and name.endswith(".json")
        )
        mowzie_structure_sets = sorted(
            name.removeprefix("data/mowziesmobs/worldgen/structure_set/").removesuffix(".json")
            for name in archive.namelist()
            if name.startswith("data/mowziesmobs/worldgen/structure_set/") and name.endswith(".json")
        )
    ice_fire_jar = None
    ice_fire_sha256 = ""
    ice_fire_structures: list[str] = []
    ice_fire_structure_sets: list[dict[str, object]] = []
    ice_fire_metadata_rows: list[list[object]] = []
    ice_fire_artifacts: dict[str, tuple[Path, str]] = {}
    if args.ice_and_fire:
        artifact_pins = {
            "iceandfire-ce-1040076-8929517": "iceandfire",
            "jupiter-1072905-7704602": "jupiter",
            "uranus-1010827-8587525": "uranus",
        }
        for token, expected_mod_id in artifact_pins.items():
            matches = sorted(
                jar for jar in args.mods.glob("*.jar")
                if token in jar.name.lower()
            )
            if len(matches) != 1:
                raise SystemExit(f"Expected exactly one pinned {expected_mod_id} jar ({token}); found {[jar.name for jar in matches]}")
            jar = matches[0]
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            ice_fire_artifacts[expected_mod_id] = (jar, digest)
            with zipfile.ZipFile(jar) as archive:
                metadata_names = [
                    name for name in archive.namelist()
                    if name.lower() == "meta-inf/neoforge.mods.toml"
                ]
                if len(metadata_names) != 1:
                    raise SystemExit(f"Expected one NeoForge mods metadata file in {jar.name}; found {metadata_names}")
                metadata = tomllib.loads(archive.read(metadata_names[0]).decode("utf-8"))
            matching_mods = [
                mod for mod in metadata.get("mods", [])
                if mod.get("modId") == expected_mod_id
            ]
            if not matching_mods:
                raise SystemExit(f"{jar.name} metadata did not declare expected mod id {expected_mod_id}")
            for mod in matching_mods:
                ice_fire_metadata_rows.append([
                    "artifact", jar.name, digest, expected_mod_id,
                    mod.get("version", ""), "", mod.get("displayName", ""),
                ])
            dependency_map = metadata.get("dependencies", {})
            entries = dependency_map.get(expected_mod_id, [])
            if isinstance(entries, dict):
                entries = [entries]
            for entry in entries:
                ice_fire_metadata_rows.append([
                    "dependency-metadata", jar.name, digest,
                    entry.get("modId", ""), entry.get("versionRange", ""), entry.get("side", ""),
                    "type=" + str(entry.get("type", "")) + ";ordering=" + str(entry.get("ordering", "")),
                ])
        ice_fire_jar, ice_fire_sha256 = ice_fire_artifacts["iceandfire"]
        ice_fire_structure_sets, ice_fire_structures = read_archive(ice_fire_jar)
        required_libraries = {
            row[3] for row in ice_fire_metadata_rows
            if row[0] == "dependency-metadata"
            and row[1] == ice_fire_jar.name
            and row[3] in {"jupiter", "uranus"}
            and "type=required" in str(row[6])
            and row[5] == "BOTH"
            and bool(row[4])
        }
        if required_libraries != {"jupiter", "uranus"}:
            raise SystemExit(
                "Ice & Fire runtime metadata did not declare both pinned required libraries; "
                f"found {sorted(required_libraries)}"
            )

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

    entity_probe_ids = list(ENTITY_PROBE_IDS) + (list(ICE_AND_FIRE_ENTITY_IDS) if args.ice_and_fire else [])
    registered_entities = 0
    ice_fire_entity_rows: list[list[object]] = []
    if not args.skip_entity_probes:
        for entity_id in entity_probe_ids:
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
                if entity_id.startswith("iceandfire:"):
                    ice_fire_entity_rows.append([
                        "entity", ice_fire_jar.name if ice_fire_jar else "", ice_fire_sha256,
                        entity_id, evidence,
                    ])
                uuid_array = re.search(r"\[\s*I;\s*-?\d+(?:\s*,\s*-?\d+){3}\s*\]", uuid_result)
                if not summon_result or not (uuid_array or "uuid" in uuid_result.lower()):
                    raise SystemExit(f"Live entity registration/spawn probe failed for {entity_id}: {evidence}")
                registered_entities += 1
            finally:
                try:
                    rcon(f"kill {kill_selector}", args.host, args.port, args.password)
                except Exception:
                    pass

    if not args.entities_only and not accepted_locates:
        raise SystemExit("No staged S7 structures were accepted by live /locate")
    expected_entities = len(entity_probe_ids)
    if not args.skip_entity_probes and registered_entities != expected_entities:
        raise SystemExit(
            f"Only {registered_entities}/{expected_entities} S7 entity probes registered"
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
    mowzie_entity_rows = [
        row for row in rows
        if row[0] == "entity" and str(row[2]).startswith("mowziesmobs:")
    ]
    mowzie_rows = [
        ["record", "jar", "sha256", "registry_id", "details"],
        ["jar", mowzie_jar.name, mowzie_sha256, "mowziesmobs", "pinned Mowzie's Mobs artifact"],
        ["structures", mowzie_jar.name, mowzie_sha256, "", ",".join(mowzie_structures) or "(none found in worldgen/structure resources)"],
        ["structure-sets", mowzie_jar.name, mowzie_sha256, "", ",".join(mowzie_structure_sets) or "(none found in worldgen/structure_set resources)"],
    ]
    normalized_mowzie_entity_rows = [
        ["entity", row[1], mowzie_sha256, row[2], row[6]]
        for row in mowzie_entity_rows
    ]
    mowzie_rows.extend(normalized_mowzie_entity_rows)
    args.mowzie_output.parent.mkdir(parents=True, exist_ok=True)
    if args.entities_only and args.mowzie_output.is_file():
        with args.mowzie_output.open("a", encoding="utf-8") as evidence_file:
            evidence_file.write(
                "\n".join("\t".join(safe_cell(cell) for cell in row) for row in normalized_mowzie_entity_rows) + "\n"
            )
    else:
        args.mowzie_output.write_text(
            "\n".join("\t".join(safe_cell(cell) for cell in row) for row in mowzie_rows) + "\n",
            encoding="utf-8",
        )

    if args.ice_and_fire:
        assert args.ice_and_fire_output is not None and ice_fire_jar is not None
        ice_output = args.ice_and_fire_output
        if args.entities_only and ice_output.is_file():
            extra_rows = ice_fire_entity_rows
            with ice_output.open("a", encoding="utf-8") as evidence_file:
                evidence_file.write("\n".join("\t".join(safe_cell(cell) for cell in row) for row in extra_rows) + ("\n" if extra_rows else ""))
        else:
            ice_rows: list[list[object]] = [
                ["record", "jar", "sha256", "registry_id", "value", "side", "details"],
                ["run", ice_fire_jar.name, ice_fire_sha256, "", "dimension=minecraft:overworld; seed=" + args.seed, "", ""],
                ["worldgen-structures", ice_fire_jar.name, ice_fire_sha256, "", ",".join(ice_fire_structures) or "(none found)", "", ""],
                ["worldgen-structure-sets", ice_fire_jar.name, ice_fire_sha256, "", ",".join(str(item["id"]) for item in ice_fire_structure_sets) or "(none found)", "", ""],
            ]
            ice_rows.extend(ice_fire_metadata_rows)
            ice_rows.extend(ice_fire_entity_rows)
            ice_output.parent.mkdir(parents=True, exist_ok=True)
            ice_output.write_text("\n".join("\t".join(safe_cell(cell) for cell in row) for row in ice_rows) + "\n", encoding="utf-8")

    total_sets = sum(row[0] == "structure-set" for row in rows[1:])
    pillage_probes = sum(row[0] == "entity" and str(row[2]).startswith("takesapillage:") for row in rows)
    mowzie_probes = sum(row[0] == "entity" and str(row[2]).startswith("mowziesmobs:") for row in rows)
    print("WBY S7 STRUCTURE/ENTITY EVIDENCE: seed=" + args.seed
          + " structure_sets=" + str(total_sets)
          + " registered_locates=" + str(accepted_locates)
          + " spawned_pillage_entities=" + str(pillage_probes)
          + " spawned_mowzie_entities=" + str(mowzie_probes)
          + " spawned_ice_and_fire_entities=" + str(sum(row[0] == "entity" and str(row[2]).startswith("iceandfire:") for row in rows))
          + " mowzie_worldgen_structures=" + str(len(mowzie_structures))
          + " report=" + str(args.output)
          + " mowzie_report=" + str(args.mowzie_output)
          + (" ice_and_fire_sha256=" + ice_fire_sha256 + " ice_and_fire_structures=" + str(len(ice_fire_structures)) + " ice_and_fire_report=" + str(args.ice_and_fire_output) if args.ice_and_fire else ""))
    if not args.entities_only and (total_sets == 0 or not locate_targets):
        raise SystemExit("No S7 structure-set metadata or custom structure IDs were found in staged candidate jars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
