#!/usr/bin/env python3
"""Record bounded S8 Nether substrate artifact and pack-format evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import socket
import struct
import zipfile
from pathlib import Path

MOD_ID = re.compile(r'(?m)^\s*modId\s*=\s*"([^"]+)"')
WORLDGEN = re.compile(r"^data/[^/]+/worldgen/(biome|structure|structure_set|configured_feature|placed_feature)/")
EXPECTED = {
    "jadensNetherExpansion": "netherexp",
    "elysiumApi": "elysium_api",
    "lodestone": "lodestone",
    "myNethersDelight": "mynethersdelight",
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
    parser.add_argument("--variant", choices=("control", "control-mnd", "jne", "jne-mnd", "incendium"), required=True)
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--datapacks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default="skyforge-s7-local-probe")
    parser.add_argument("--seed", default="817304")
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

    expected = set(EXPECTED[mod] for mod in ("jadensNetherExpansion", "elysiumApi", "lodestone")) if args.variant in {"jne", "jne-mnd"} else set()
    if args.variant in {"control-mnd", "jne-mnd"}:
        expected.add(EXPECTED["myNethersDelight"])
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
    if args.variant != "incendium" and args.datapacks.exists():
        candidate_names = [p.name for p in args.datapacks.glob("*") if p.name.lower().startswith("incendium")]
        if candidate_names:
            raise SystemExit(f"S8 {args.variant} profile leaked Incendium: {candidate_names}")
    if args.live:
        commands = (
            ("nether_fortress", "execute in minecraft:the_nether run locate structure minecraft:fortress"),
            ("nether_wastes", "execute in minecraft:the_nether run locate biome minecraft:nether_wastes"),
            ("enabled_datapacks", "datapack list enabled"),
        )
        for label, command in commands:
            response = rcon(command, args.host, args.port, args.password)
            if re.search(r"unknown command|unknown structure|unknown biome|invalid structure", response, re.IGNORECASE):
                raise SystemExit(f"S8 live Nether probe rejected {command!r}: {response}")
            if label == "enabled_datapacks":
                has_incendium = "incendium" in response.lower()
                if args.variant == "incendium" and not has_incendium:
                    raise SystemExit(f"Incendium ZIP is staged but is not enabled in the test world: {response}")
                if args.variant != "incendium" and has_incendium:
                    raise SystemExit(f"Incendium leaked into the {args.variant} world: {response}")
            rows.append(f"live_probe\t{label}\t\t\t\tseed={args.seed}; command={command}; response={response}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"WBY S8 SUBSTRATE ARTIFACT EVIDENCE PASS variant={args.variant} mods={len(list(args.mods.glob('*.jar')))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
