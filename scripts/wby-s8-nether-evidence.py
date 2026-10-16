#!/usr/bin/env python3
"""Record bounded S8 Nether substrate artifact and pack-format evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import socket
import struct
import time
import zipfile
from pathlib import Path, PurePosixPath

MOD_ID = re.compile(r'(?m)^\s*modId\s*=\s*"([^"]+)"')
WORLDGEN = re.compile(r"^data/[^/]+/worldgen/(biome|structure|structure_set|configured_feature|placed_feature)/")
EXPECTED = {
    "jadensNetherExpansion": "netherexp",
    "elysiumApi": "elysium_api",
    "lodestone": "lodestone",
    "myNethersDelight": "mynethersdelight",
    "betterNetherNewDawn": "betternether",
    "bclibNewDawn": "bclib",
    "wunderLibNewDawn": "wunderlib",
    "worldWeaverNewDawn": "wover",
}
FULL_STACK_EXPECTED = {
    "netherDepthsUpgrade": "netherdepthsupgrade",
    "createNetherIndustry": "createnetherindustry",
    "thinAirRelived": "thinair",
    "curios": "curios",
    "yungsBetterNetherFortresses": "betterfortresses",
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
    return packet_id, packet_type, payload[8:-2].decode("utf-8", errors="replace")

def rcon(command: str, host: str, port: int, password: str) -> str:
    with socket.create_connection((host, port), timeout=15) as sock:
        sock.settimeout(180)
        sock.sendall(packet(1, 3, password))
        response_id, _, _ = receive_packet(sock)
        if response_id != 1:
            raise PermissionError("RCON authentication failed")
        sock.sendall(packet(2, 2, command))
        response_id, _, body = receive_packet(sock)
        if response_id != 2:
            raise RuntimeError("RCON response did not match its command")
        return body.strip()
def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=("control", "control-mnd", "jne", "jne-mnd", "incendium", "incendium-full", "incendium-betternether"), required=True)
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--datapacks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default=os.environ.get("WBY_S7_RCON_PASSWORD"))
    parser.add_argument("--seed", default="817304")
    args = parser.parse_args()
    if args.live and not args.password:
        parser.error("set WBY_S7_RCON_PASSWORD or pass --password when --live is used")

    rows = ["kind\tname\tsha256\tmod_ids\tworldgen_resource_counts\tnotes"]
    found: set[str] = set()
    candidate_namespaces = {
        "control": set(),
        "control-mnd": {"mynethersdelight"},
        "jne": {"netherexp"},
        "jne-mnd": {"netherexp", "mynethersdelight"},
        "incendium": {"incendium"},
        "incendium-full": {"incendium", "netherdepthsupgrade", "createnetherindustry", "betterfortresses"},
        "incendium-betternether": {"incendium", "betternether"},
    }[args.variant]
    candidate_resources: dict[str, set[str]] = {
        kind: set() for kind in ("biome", "structure", "structure_set", "configured_feature", "placed_feature")
    }
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
                    kind = match.group(1)
                    counts[kind] += 1
                    parts = PurePosixPath(name).parts
                    if len(parts) >= 5 and parts[0] == "data" and parts[1] in candidate_namespaces and parts[2] == "worldgen":
                        resource = PurePosixPath(*parts[4:]).with_suffix("")
                        candidate_resources[kind].add(f"{parts[1]}:{resource.as_posix()}")
        found.update(ids)
        count_text = ",".join(f"{kind}:{counts[kind]}" for kind in counts)
        rows.append(f"mod\t{jar.name}\t{sha256(jar)}\t{','.join(sorted(ids))}\t{count_text}\t")

    expected = set(EXPECTED[mod] for mod in ("jadensNetherExpansion", "elysiumApi", "lodestone")) if args.variant in {"jne", "jne-mnd"} else set()
    if args.variant in {"control-mnd", "jne-mnd", "incendium-full"}:
        expected.add(EXPECTED["myNethersDelight"])
    if args.variant == "incendium-full":
        expected.update(FULL_STACK_EXPECTED.values())
    if args.variant == "incendium-betternether":
        expected.update(EXPECTED[mod] for mod in ("betterNetherNewDawn", "bclibNewDawn", "wunderLibNewDawn", "worldWeaverNewDawn"))
    for mod_id in expected:
        if mod_id not in found:
            raise SystemExit(f"S8 {args.variant} profile is missing expected mod id {mod_id}; found {sorted(found)}")
    leaked = set(EXPECTED.values()) - expected
    if leaked & found:
        raise SystemExit(f"S8 {args.variant} profile leaked unselected JNE content: {sorted(leaked & found)}")

    packs = sorted(args.datapacks.glob("*.zip"))
    if args.variant in {"incendium", "incendium-full", "incendium-betternether"}:
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
            for name in names:
                match = WORLDGEN.match(name)
                if match:
                    kind = match.group(1)
                    parts = PurePosixPath(name).parts
                    if len(parts) >= 5 and parts[0] == "data" and parts[1] in candidate_namespaces and parts[2] == "worldgen":
                        resource = PurePosixPath(*parts[4:]).with_suffix("")
                        candidate_resources[kind].add(f"{parts[1]}:{resource.as_posix()}")
    if args.variant not in {"incendium", "incendium-full", "incendium-betternether"} and args.datapacks.exists():
        candidate_names = [p.name for p in args.datapacks.glob("*") if p.name.lower().startswith("incendium")]
        if candidate_names:
            raise SystemExit(f"S8 {args.variant} profile leaked Incendium: {candidate_names}")
    for kind, resources in candidate_resources.items():
        for resource in sorted(resources):
            rows.append(f"candidate_resource\t{resource}\t\t\t{kind}\t")
    if args.live:
        def performance_sample(stage: str) -> None:
            command = "tick query"
            started = time.perf_counter()
            response = rcon(command, args.host, args.port, args.password)
            probe_ms = round((time.perf_counter() - started) * 1000, 1)
            if re.search(r"unknown command|unknown or incomplete command", response, re.IGNORECASE):
                raise SystemExit(f"S8 server tick query is unavailable: {response}")
            rows.append(f"performance_sample\tserver_tick_query\t\t\t\tseed={args.seed}; stage={stage}; command={command}; probe_ms={probe_ms}; response={response}")

        performance_sample("before-nether-locate-probes")
        commands = (
            ("nether_fortress", "execute in minecraft:the_nether run locate structure minecraft:fortress"),
            ("nether_wastes", "execute in minecraft:the_nether run locate biome minecraft:nether_wastes"),
            ("enabled_datapacks", "datapack list enabled"),
        )
        for label, command in commands:
            started = time.perf_counter()
            response = rcon(command, args.host, args.port, args.password)
            probe_ms = round((time.perf_counter() - started) * 1000, 1)
            if re.search(r"unknown command|unknown structure|unknown biome|invalid structure", response, re.IGNORECASE):
                raise SystemExit(f"S8 live Nether probe rejected {command!r}: {response}")
            if label == "enabled_datapacks":
                has_incendium = "incendium" in response.lower()
                if args.variant in {"incendium", "incendium-full", "incendium-betternether"} and not has_incendium:
                    raise SystemExit(f"Incendium ZIP is staged but is not enabled in the test world: {response}")
                if args.variant not in {"incendium", "incendium-full", "incendium-betternether"} and has_incendium:
                    raise SystemExit(f"Incendium leaked into the {args.variant} world: {response}")
            rows.append(f"live_probe\t{label}\t\t\t\tseed={args.seed}; probe_ms={probe_ms}; command={command}; response={response}")
        for kind in ("biome", "structure"):
            for resource in sorted(candidate_resources[kind])[:3]:
                command = f"execute in minecraft:the_nether run locate {kind} {resource}"
                started = time.perf_counter()
                response = rcon(command, args.host, args.port, args.password)
                probe_ms = round((time.perf_counter() - started) * 1000, 1)
                if re.search(r"unknown command|unknown structure|unknown biome|invalid structure", response, re.IGNORECASE):
                    raise SystemExit(f"Candidate resource is not registered in the live world: {resource}: {response}")
                rows.append(f"candidate_locate\t{resource}\t\t\t{kind}\tseed={args.seed}; probe_ms={probe_ms}; response={response}")
        performance_sample("after-nether-locate-probes")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"WBY S8 SUBSTRATE ARTIFACT EVIDENCE PASS variant={args.variant} mods={len(list(args.mods.glob('*.jar')))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
