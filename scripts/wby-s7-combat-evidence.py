#!/usr/bin/env python3
"""Create exact-pin and runtime-load evidence for the WBY S7 combat candidate arms."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import socket
import struct
import sys
import tomllib
import zipfile
from pathlib import Path, PurePosixPath
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
FIREPOWER = ("cbcFirepowerComponents",)
ARMS = {
    "engineering": ENGINEERING,
    "tacz": ENGINEERING + TACZ,
    "scorched": ENGINEERING + SCORCHED,
    "firepower": ENGINEERING + FIREPOWER,
}
ALL_CANDIDATES = ENGINEERING + TACZ + SCORCHED + FIREPOWER
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


def staged_jar_metadata(
    jar_bytes: bytes,
    owner: str,
    *,
    depth: int = 0,
) -> list[tuple[str, dict[str, Any], str]]:
    """Read a jar and its bounded JarJar children for staged dependency closure."""
    if depth > 4:
        raise SystemExit(f"{owner}: JarJar nesting exceeds the supported depth")
    try:
        archive = zipfile.ZipFile(io.BytesIO(jar_bytes))
    except (OSError, zipfile.BadZipFile) as exc:
        raise SystemExit(f"{owner}: invalid nested jar: {exc}") from exc

    results: list[tuple[str, dict[str, Any], str]] = []
    with archive:
        names = archive.namelist()
        metadata_entries = [name for name in names if name.lower() == TOML_PATH.lower()]
        if len(metadata_entries) > 1:
            raise SystemExit(f"{owner}: expected at most one {TOML_PATH}, found {metadata_entries}")
        if metadata_entries:
            try:
                doc = tomllib.loads(archive.read(metadata_entries[0]).decode("utf-8"))
            except (UnicodeError, tomllib.TOMLDecodeError) as exc:
                raise SystemExit(f"{owner}: invalid mod metadata: {exc}") from exc
            results.append((owner, doc, hashlib.sha256(jar_bytes).hexdigest()))

        jarjar_entries = [name for name in names if name.lower() == "meta-inf/jarjar/metadata.json"]
        if len(jarjar_entries) > 1:
            raise SystemExit(f"{owner}: duplicate JarJar metadata entries")
        if not jarjar_entries:
            return results
        try:
            jarjar = json.loads(archive.read(jarjar_entries[0]).decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise SystemExit(f"{owner}: invalid JarJar metadata: {exc}") from exc

        children = jarjar.get("jars", []) if isinstance(jarjar, dict) else None
        if not isinstance(children, list):
            raise SystemExit(f"{owner}: JarJar metadata has no jars list")
        for child in children:
            if not isinstance(child, dict) or not isinstance(child.get("path"), str):
                raise SystemExit(f"{owner}: JarJar entry has no path")
            raw_path = child["path"]
            path = PurePosixPath(raw_path)
            if path.is_absolute() or not path.parts or ".." in path.parts or "\\" in raw_path:
                raise SystemExit(f"{owner}: unsafe JarJar path {raw_path!r}")
            matches = [info for info in archive.infolist() if info.filename == raw_path]
            if len(matches) != 1:
                raise SystemExit(f"{owner}: expected one JarJar child {raw_path!r}, found {len(matches)}")
            info = matches[0]
            if info.file_size > 128 * 1024 * 1024:
                raise SystemExit(f"{owner}: JarJar child exceeds 128 MiB: {raw_path}")
            child_owner = f"{owner}!{raw_path}"
            results.extend(
                staged_jar_metadata(archive.read(info), child_owner, depth=depth + 1)
            )
    return results


def compare_mod_versions(left: str, right: str) -> int:
    """Compare common Maven-style mod versions conservatively for loader deduplication."""
    qualifier_aliases = {
        "a": "alpha", "b": "beta", "m": "milestone", "cr": "rc",
        "ga": "", "final": "", "release": "",
    }
    qualifier_rank = {
        "alpha": 0, "beta": 1, "milestone": 2, "rc": 3, "snapshot": 4,
        "": 6, "sp": 7,
    }

    def tokens(value: str) -> list[tuple[str, object]]:
        if not value or "${" in value:
            raise ValueError(f"unresolved mod version {value!r}")
        parts = re.findall(r"\d+|[a-z]+", value.lower())
        if not parts:
            raise ValueError(f"unrecognized mod version {value!r}")
        return [("number", int(part)) if part.isdigit() else ("text", qualifier_aliases.get(part, part))
                for part in parts]

    def compare_token(a: tuple[str, object] | None, b: tuple[str, object] | None) -> int:
        if a is None and b is None:
            return 0
        if a is None:
            # An omitted numeric component is zero; a release outranks a prerelease qualifier.
            return compare_token(("number", 0), b) if b and b[0] == "number" else 1
        if b is None:
            return -compare_token(b, a)
        if a[0] == b[0] == "number":
            return (a[1] > b[1]) - (a[1] < b[1])
        if a[0] != b[0]:
            return 1 if a[0] == "number" else -1
        qa, qb = str(a[1]), str(b[1])
        ra, rb = qualifier_rank.get(qa, 5), qualifier_rank.get(qb, 5)
        if ra != rb:
            return (ra > rb) - (ra < rb)
        if ra == 5 and qa != qb:
            return (qa > qb) - (qa < qb)
        return 0

    a_tokens, b_tokens = tokens(left), tokens(right)
    for index in range(max(len(a_tokens), len(b_tokens))):
        a = a_tokens[index] if index < len(a_tokens) else None
        b = b_tokens[index] if index < len(b_tokens) else None
        result = compare_token(a, b)
        if result:
            return result
    return 0


def staged_profile(mods_dir: Path) -> tuple[set[str], dict[str, tuple[str, dict[str, Any], str]]]:
    """Return staged hashes and the mod files selected by NeoForge's primary-ID version rule."""
    if not mods_dir.is_dir():
        raise SystemExit(f"staged mods directory does not exist: {mods_dir}")
    hashes: set[str] = set()
    candidates: dict[str, tuple[str, dict[str, Any], str, str]] = {}
    for jar in sorted(mods_dir.glob("*.jar")):
        jar_bytes = jar.read_bytes()
        hashes.add(hashlib.sha256(jar_bytes).hexdigest())
        declarations = staged_jar_metadata(jar_bytes, jar.name)
        if not declarations:
            raise SystemExit(f"{jar.name}: no mod metadata in the outer jar or JarJar children")
        for owner, doc, content_hash in declarations:
            declared_mods = doc.get("mods", [])
            if not isinstance(declared_mods, list) or not declared_mods:
                raise SystemExit(f"{owner} has no mod declarations")
            primary = declared_mods[0]
            primary_id = str(primary.get("modId", "")).strip()
            primary_version = str(primary.get("version", "")).strip()
            if not primary_id:
                raise SystemExit(f"{owner} declares a blank primary mod id")
            previous = candidates.get(primary_id)
            if previous is None:
                candidates[primary_id] = (owner, doc, content_hash, primary_version)
                continue
            if previous[2] == content_hash:
                continue
            try:
                comparison = compare_mod_versions(primary_version, previous[3])
            except ValueError as exc:
                raise SystemExit(
                    f"cannot resolve duplicate staged primary mod id {primary_id}: "
                    f"{previous[0]} version={previous[3]!r}; {owner} version={primary_version!r}; {exc}"
                ) from exc
            if comparison == 0:
                raise SystemExit(
                    f"same-version staged primary mod id {primary_id} has different bytes: "
                    f"{previous[0]} version={previous[3]!r} sha256={previous[2]}, "
                    f"{owner} version={primary_version!r} sha256={content_hash}"
                )
            if comparison > 0:
                candidates[primary_id] = (owner, doc, content_hash, primary_version)

    # NeoForge chooses the newest file per primary mod ID first; duplicate secondary IDs
    # that remain across those selected files are still invalid and must not be hidden.
    mods: dict[str, tuple[str, dict[str, Any], str]] = {}
    for owner, doc, content_hash, _version in candidates.values():
        for mod in doc.get("mods", []):
            mod_id = str(mod.get("modId", "")).strip()
            if not mod_id:
                raise SystemExit(f"{owner} declares a blank mod id")
            previous = mods.get(mod_id)
            if previous is not None and previous[2] != content_hash:
                raise SystemExit(
                    f"duplicate selected staged mod id {mod_id}: "
                    f"{previous[0]} sha256={previous[2]}, {owner} sha256={content_hash}"
                )
            mods[mod_id] = (owner, doc, content_hash)
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
    payload = struct.pack("<ii", packet_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
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
    selector = "@e[type=tacznpcs:npc,tag=skyforge_s7_tacz_probe,distance=..16,sort=nearest,limit=1]"
    summon = ""
    uuid = ""
    try:
        # Omitting template NBT intentionally selects TaCZ NPCs' documented default template.
        summon = rcon(
            'execute at @a[limit=1] run summon tacznpcs:npc ~ ~ ~ {"Tags":["skyforge_s7_tacz_probe"]}',
            host, port, password,
        )
        uuid = rcon(
            f"execute at @a[limit=1] if entity {selector} run data get entity {selector} UUID",
            host, port, password,
        )
        if not uuid or "no entity" in uuid.lower():
            raise SystemExit(f"TaCZ NPC spawn/registry probe failed: summon={summon!r}; uuid={uuid!r}")
        return summon, uuid
    finally:
        # This is a bounded, uniquely tagged probe entity; always clean it up.
        primary_error = sys.exc_info()[0]
        try:
            rcon(
                "kill @e[type=tacznpcs:npc,tag=skyforge_s7_tacz_probe]",
                host, port, password,
            )
        except Exception:
            if primary_error is None:
                raise


def probe_radar_debug_report(root: Path, host: str, port: int, password: str) -> tuple[str, str]:
    report_dir = root / "create_radar_debug"

    def signature(path: Path) -> tuple[int, int, int, str]:
        stat = path.stat()
        return (
            stat.st_size,
            stat.st_mtime_ns,
            stat.st_ctime_ns,
            hashlib.sha256(path.read_bytes()).hexdigest(),
        )

    before = {
        path.name: signature(path)
        for path in report_dir.glob("debug_*.txt")
    } if report_dir.is_dir() else {}
    response = rcon("radar debug gen_debug_file", host, port, password)
    if "Create Radar debug generated:" not in response:
        raise SystemExit(f"Create: Radars debug command did not report success: {response!r}")
    reports = sorted(report_dir.glob("debug_*.txt")) if report_dir.is_dir() else []
    fresh = [path for path in reports if before.get(path.name) != signature(path)]
    if not fresh:
        raise SystemExit(f"Create: Radars debug command reported success but wrote no fresh report under {report_dir}")
    report_path = max(fresh, key=lambda path: (path.stat().st_mtime_ns, path.name))
    contents = report_path.read_text(encoding="utf-8", errors="replace")
    required_sections = (
        "=== Create Radar Debug Dump ===",
        "=== radar dump_links ===",
        "=== radar list_active_filters ===",
        "=== radar debug weapon_endpoints ===",
    )
    missing = [section for section in required_sections if section not in contents]
    if missing:
        raise SystemExit(f"Create: Radars debug report is incomplete: missing {missing}")
    return report_path.name, hashlib.sha256(report_path.read_bytes()).hexdigest()


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
    candidate_markers = ("tacz", "scorched", "scguns", "firecontrol", "fire_control", "modernwarfare", "firepower")
    config_roots = {"config", "serverconfig", "defaultconfigs"}
    files = sorted(
        path for path in root.rglob("*")
        if path.is_file()
        and path.suffix.lower() in {".toml", ".json"}
        and path.relative_to(root).parts
        and path.relative_to(root).parts[0].lower() in config_roots
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
            raise SystemExit(f"Unselected combat candidate {key} leaked into {args.variant}: {[p.name for p in matches]}")

    server_log = args.server_log.read_text(encoding="utf-8", errors="replace")
    client_log = args.client_log.read_text(encoding="utf-8", errors="replace")
    for mod_id in sorted(active_mod_ids):
        marker = f"({mod_id})"
        if marker not in server_log:
            raise SystemExit(f"Server runtime log did not discover {mod_id}")
        if marker not in client_log:
            raise SystemExit(f"Client runtime log did not discover {mod_id}")

    profile_delta: set[str] = set()
    if not args.rcon_host or not args.rcon_password:
        raise SystemExit("Combat arm requires RCON credentials for its radar diagnostic report probe")
    radar_report, radar_digest = probe_radar_debug_report(
        args.runtime_root, args.rcon_host, args.rcon_port, args.rcon_password
    )
    report.append([
        "radar-debug-report", args.variant, "", radar_report, radar_digest, "", "", "", "",
        "registered command generated links/filter/weapon-endpoint diagnostic sections",
    ])
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
        visited: set[str] = set()
        while pending:
            mod_id = pending.pop()
            if mod_id in visited:
                continue
            visited.add(mod_id)
            staged_entry = staged_mods.get(mod_id)
            if staged_entry is None:
                raise SystemExit(f"candidate dependency closure is missing required mod ID {mod_id}")

            # JarJar children are runtime mods bundled inside the selected parent JAR, not
            # standalone declared dependencies. staged_profile preserves that provenance in
            # the owner path (parent.jar!META-INF/jarjar/child.jar), so include only children
            # whose bytes are actually nested under a mod already in this candidate closure.
            embedded_owner_prefix = staged_entry[0] + "!"
            embedded_ids = {
                embedded_id
                for embedded_id, embedded_entry in staged_mods.items()
                if embedded_entry[0].startswith(embedded_owner_prefix)
            }
            for embedded_id in sorted(embedded_ids):
                if embedded_id not in control_ids:
                    expected_additions.add(embedded_id)
                pending.append(embedded_id)

            for dependency_id in required_server_dependency_ids(staged_entry[1], mod_id):
                # Minecraft and NeoForge are provided by the runtime, not staged as mod JARs.
                if dependency_id in {"minecraft", "neoforge"}:
                    continue
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
