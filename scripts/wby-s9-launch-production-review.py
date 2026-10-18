#!/usr/bin/env python3
"""Launch the selected Actions-built S9 profile for interactive owner review.

This is a manual review launcher. It installs/runs NeoForge distributions from staged artifacts;
it does not invoke Gradle or perform automated acceptance on the workstation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

import minecraft_launcher_lib


MC = "1.21.1"
NEOFORGE = "21.1.249"
USERNAME = "WbyS9Review"
INSTALLER_URL = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE}/neoforge-{NEOFORGE}-installer.jar"


def packet(pid: int, kind: int, body: str) -> bytes:
    payload = (pid.to_bytes(4, "little", signed=True) + kind.to_bytes(4, "little", signed=True)
               + body.encode("utf-8") + b"\0\0")
    return len(payload).to_bytes(4, "little", signed=True) + payload


def recv_exact(sock, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        part = sock.recv(size - len(data))
        if not part:
            raise ConnectionError("RCON closed before replying")
        data.extend(part)
    return bytes(data)


def rcon(password: str, command: str, port: int = 25575) -> str:
    import socket
    with socket.create_connection(("127.0.0.1", port), timeout=5) as sock:
        sock.settimeout(10)
        sock.sendall(packet(11, 3, password))
        size = int.from_bytes(recv_exact(sock, 4), "little", signed=True)
        auth = recv_exact(sock, size)
        if int.from_bytes(auth[:4], "little", signed=True) != 11:
            raise PermissionError("Local RCON authentication failed")
        sock.sendall(packet(12, 2, command))
        size = int.from_bytes(recv_exact(sock, 4), "little", signed=True)
        response = recv_exact(sock, size)
        return response[8:-2].decode("utf-8", "replace").strip()


def offline_uuid(name: str) -> str:
    raw = hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest()
    return str(uuid.UUID(bytes=raw, version=3))


def copy_tree(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    destination.mkdir(parents=True, exist_ok=True)
    for item in source.iterdir():
        target = destination / item.name
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            shutil.copy2(item, target)


def prepare_runtime(profile: Path, runtime_root: Path, java: str) -> tuple[Path, Path, str, str]:
    runtime_root.mkdir(parents=True, exist_ok=True)
    server_dir = runtime_root / "server"
    client_dir = runtime_root / "client"
    server_dir.mkdir(parents=True, exist_ok=True)
    client_dir.mkdir(parents=True, exist_ok=True)

    source_packs = profile / "world" / "datapacks"
    if not list(source_packs.glob("*.zip")):
        raise SystemExit(f"Profile has no world data packs: {source_packs}")
    for source, dest in ((profile / "server" / "mods", server_dir / "mods"),
                         (profile / "server" / "config", server_dir / "config"),
                         (profile / "server" / "kubejs", server_dir / "kubejs"),
                         (profile / "client" / "mods", client_dir / "mods"),
                         (profile / "client" / "config", client_dir / "config")):
        copy_tree(source, dest)

    world_name = "s9-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    world_dir = server_dir / world_name
    packs_target = world_dir / "datapacks"
    packs_target.mkdir(parents=True, exist_ok=True)
    for source in source_packs.glob("*.zip"):
        shutil.copy2(source, packs_target / source.name)

    password = uuid.uuid4().hex
    properties = {
        "motd": "Skyforge S9 owner review",
        "server-ip": "127.0.0.1",
        "server-port": "25565",
        "online-mode": "false",
        "enforce-secure-profile": "false",
        "enable-rcon": "true",
        "rcon.port": "25575",
        "rcon.password": password,
        "level-name": world_name,
        "level-seed": "817309",
        "level-type": "minecraft:normal",
        "gamemode": "creative",
        "difficulty": "normal",
        "allow-flight": "true",
        "spawn-protection": "0",
        "view-distance": "8",
        "simulation-distance": "4",
        "max-players": "2",
        "white-list": "false",
    }
    (server_dir / "server.properties").write_text(
        "\n".join(f"{key}={value}" for key, value in properties.items()) + "\n",
        encoding="utf-8",
    )
    (server_dir / "ops.json").write_text(json.dumps([{
        "uuid": offline_uuid(USERNAME), "name": USERNAME, "level": 4,
        "bypassesPlayerLimit": False,
    }], indent=2) + "\n", encoding="utf-8")

    java_path = shutil.which(java) if os.path.basename(java) == java else java
    if not java_path or (os.path.basename(java) == java and not java_path):
        raise SystemExit("Java 21 was not found. Install a Java 21 runtime and reopen Git Bash.")
    installer = runtime_root / "neoforge-installer.jar"
    if not installer.exists():
        print("Downloading the pinned NeoForge installer for the manual client/server review.")
        urllib.request.urlretrieve(INSTALLER_URL, installer)
    if not (server_dir / "run.bat").is_file() and not (server_dir / "run.sh").is_file():
        print("Installing the pinned NeoForge dedicated-server runtime (no Gradle build).")
        result = subprocess.run([java_path, "-jar", str(installer), "--installServer"], cwd=server_dir)
        if result.returncode:
            raise SystemExit(f"NeoForge server installation failed with exit code {result.returncode}")

    mc_dir = runtime_root / "minecraft"
    loader = minecraft_launcher_lib.mod_loader.get_mod_loader("neoforge")
    installed = loader.get_installed_version(MC, NEOFORGE)
    version_json = mc_dir / "versions" / installed / f"{installed}.json"
    if not version_json.is_file():
        print("Installing the pinned production client runtime.")
        loader.install(MC, str(mc_dir), loader_version=NEOFORGE, java=java_path)
    minecraft_launcher_lib.install.install_minecraft_version(installed, str(mc_dir))
    return server_dir, client_dir, password, java_path, installed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--runtime-root", type=Path)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()

    profile = args.profile.expanduser().resolve()
    if not (profile / "manifest.txt").is_file():
        raise SystemExit(f"Missing manifest.txt in extracted profile: {profile}")
    variant = next((line.split("=", 1)[1] for line in
                    (profile / "manifest.txt").read_text(encoding="utf-8").splitlines()
                    if line.startswith("variant=")), "unknown")
    base = args.runtime_root.expanduser().resolve() if args.runtime_root else profile.parent / f"wby-s9-runtime-{variant}"
    server_dir, client_dir, password, java_path, installed = prepare_runtime(profile, base, args.java)

    eula_file = server_dir / "eula.txt"
    if not eula_file.is_file() or eula_file.read_text(encoding="utf-8").strip().lower() != "eula=true":
        answer = input("Review and accept the Minecraft EULA to run the local server? Type YES to continue: ")
        if answer.strip() != "YES":
            raise SystemExit("Stopped; the Minecraft EULA was not accepted.")
        eula_file.write_text("eula=true\n", encoding="utf-8")

    if os.name == "nt":
        command = ["cmd.exe", "/c", "run.bat", "nogui"]
    else:
        command = ["bash", "run.sh", "nogui"]
    log_path = base / "server-console.log"
    log_stream = log_path.open("w", encoding="utf-8", errors="replace")
    server = subprocess.Popen(command, cwd=server_dir, stdout=log_stream, stderr=subprocess.STDOUT)

    try:
        deadline = time.monotonic() + 600
        while time.monotonic() < deadline:
            if server.poll() is not None:
                raise SystemExit(f"Review server exited early; inspect {log_path}")
            try:
                enabled = rcon(password, "datapack list enabled")
                break
            except (OSError, TimeoutError, ConnectionError, PermissionError):
                time.sleep(2)
        else:
            raise SystemExit(f"Review server did not become ready; inspect {log_path}")
        print(f"Fresh normal world: {server_dir / (server_dir / 'server.properties').read_text().split('level-name=',1)[1].splitlines()[0]}")
        print(f"Enabled data packs: {enabled}")
        if variant.startswith("nullscape") and "nullscape" not in enabled.lower():
            raise SystemExit("Nullscape is not enabled in the fresh review world; inspect its datapack.")
        rcon(password, f"op {USERNAME}")
        options = minecraft_launcher_lib.utils.generate_test_options()
        options["username"] = USERNAME
        options["gameDirectory"] = str(client_dir)
        options["launcherName"] = "Skyforge S9 Review"
        options["launcherVersion"] = "1"
        options["defaultExecutablePath"] = java_path
        options["executablePath"] = java_path
        options["quickPlayMultiplayer"] = "127.0.0.1:25565"
        command = minecraft_launcher_lib.command.get_minecraft_command(installed, str(base / "minecraft"), options)
        print("Opening the client. Allow it to join the local fresh-world server.")
        print("In chat, use /locate structure minecraft:stronghold to inspect access and /execute in minecraft:the_end run tp @s 0 100 0 to inspect the End.")
        completed = subprocess.run(command, cwd=client_dir, check=False)
        try:
            rcon(password, "save-all flush")
            rcon(password, "stop")
        except (OSError, TimeoutError, ConnectionError):
            pass
        server.wait(timeout=90)
        return completed.returncode
    finally:
        if server.poll() is None:
            try:
                rcon(password, "save-all flush")
                rcon(password, "stop")
                server.wait(timeout=90)
            except Exception:
                server.terminate()
                server.wait(timeout=20)
        log_stream.close()
        print(f"S9 server log: {log_path}")


if __name__ == "__main__":
    raise SystemExit(main())
