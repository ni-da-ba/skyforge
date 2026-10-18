#!/usr/bin/env python3
"""Capture S9 End artifact and first-generation evidence for an isolated review profile."""
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


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def packet(pid: int, typ: int, body: str) -> bytes:
    payload = struct.pack("<ii", pid, typ) + body.encode() + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def recv_exact(sock: socket.socket, n: int) -> bytes:
    out = bytearray()
    while len(out) < n:
        chunk = sock.recv(n - len(out))
        if not chunk:
            raise ConnectionError("RCON closed early")
        out.extend(chunk)
    return bytes(out)


def recv_packet(sock: socket.socket) -> tuple[int, str]:
    size = struct.unpack("<i", recv_exact(sock, 4))[0]
    payload = recv_exact(sock, size)
    return struct.unpack("<i", payload[:4])[0], payload[8:-2].decode("utf-8", "replace").strip()


def rcon(command: str, host: str, port: int, password: str) -> str:
    with socket.create_connection((host, port), timeout=15) as sock:
        sock.settimeout(180)
        sock.sendall(packet(1, 3, password))
        pid, _ = recv_packet(sock)
        if pid != 1:
            raise PermissionError("RCON auth failed")
        sock.sendall(packet(2, 2, command))
        pid, response = recv_packet(sock)
        if pid != 2:
            raise RuntimeError(f"RCON mismatch for {command}")
        return response


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--variant", required=True)
    p.add_argument("--mods", type=Path, required=True)
    p.add_argument("--datapacks", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--live", action="store_true")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=25575)
    p.add_argument("--password", default=os.environ.get("WBY_S7_RCON_PASSWORD"))
    a = p.parse_args()
    rows = ["kind\tname\tsha256\tmod_ids\tworldgen_resources\tnotes"]
    ids: set[str] = set()
    for jar in sorted(a.mods.glob("*.jar")):
        with zipfile.ZipFile(jar) as z:
            jar_ids = set()
            resources = {}
            for meta in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                try:
                    jar_ids.update(MOD_ID.findall(z.read(meta).decode("utf-8", "replace")))
                except KeyError:
                    pass
            for name in z.namelist():
                match = WORLDGEN.match(name)
                if match:
                    resources[match.group(1)] = resources.get(match.group(1), 0) + 1
        ids.update(jar_ids)
        counts = ",".join(f"{k}:{resources[k]}" for k in sorted(resources))
        rows.append(f"mod\t{jar.name}\t{sha256(jar)}\t{','.join(sorted(jar_ids))}\t{counts}\t")

    packs = sorted(a.datapacks.glob("*.zip"))
    for pack in packs:
        with zipfile.ZipFile(pack) as z:
            names = z.namelist()
            if any(Path(n).is_absolute() or ".." in PurePosixPath(n).parts for n in names):
                raise SystemExit(f"Unsafe path in data pack {pack}")
            try:
                meta = json.loads(z.read("pack.mcmeta"))
            except (KeyError, json.JSONDecodeError) as exc:
                raise SystemExit(f"Invalid data pack {pack}: {exc}")
            fmt = meta.get("pack", {}).get("pack_format", "unknown")
            rows.append(f"datapack\t{pack.name}\t{sha256(pack)}\t\t\tpack_format={fmt}; files={len(names)}")
    if a.variant.startswith("nullscape") and not any("nullscape" in p.name.lower() for p in packs):
        raise SystemExit("Selected Nullscape profile has no staged Nullscape ZIP")
    if a.live:
        if not a.password:
            p.error("set WBY_S7_RCON_PASSWORD for --live")
        enabled = rcon("datapack list enabled", a.host, a.port, a.password)
        if a.variant.startswith("nullscape") and "nullscape" not in enabled.lower():
            raise SystemExit(f"Nullscape is not enabled in this new world: {enabled}")
        # Materialize representative End chunks and record the bounded generation window.
        # Chunk data is saved by the acceptance runner on shutdown.
        generation_started = time.perf_counter()
        for x, z in ((0, 0), (128, 0), (-128, 0), (0, 128), (0, -128)):
            response = rcon(f"execute in minecraft:the_end run forceload add {x} {z}", a.host, a.port, a.password)
            normalized_response = response.lower()
            successful_force_load = (
                "added" in normalized_response
                or "already" in normalized_response
                or ("marked chunk" in normalized_response and "force loaded" in normalized_response)
            )
            if not successful_force_load:
                raise SystemExit(f"Could not force-load End chunk {x},{z}: {response}")
        time.sleep(8)
        rcon("execute in minecraft:the_end run forceload remove all", a.host, a.port, a.password)
        generation_seconds = time.perf_counter() - generation_started
        rows.append(f"live\tthe_end\t\t\t\tfirst_chunks=5; generation_window_seconds={generation_seconds:.3f}; datapacks_enabled={enabled}")
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"WBY S9 END EVIDENCE PASS variant={a.variant} jars={len(list(a.mods.glob('*.jar')))} packs={len(packs)} live={a.live}")
    print(f"modIds={','.join(sorted(ids))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
