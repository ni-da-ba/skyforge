#!/usr/bin/env python3
"""Install and launch a production NeoForge client for S4 review/acceptance.

This deliberately uses a production launcher profile: ModDev's development client enables
NeoForge GameTest discovery, which can reflect optional upstream GameTest classes.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

import minecraft_launcher_lib


MINECRAFT_VERSION = "1.21.1"
NEOFORGE_VERSION = "21.1.249"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-directory", required=True, type=Path)
    parser.add_argument("--game-directory", required=True, type=Path)
    parser.add_argument("--username", default="WbyS4Review")
    parser.add_argument("--server", help="Optional host:port for quick play.")
    parser.add_argument("--java", help="Optional Java executable path.")
    parser.add_argument(
        "--program-args-file",
        type=Path,
        help="Write the generated client argv as JSON for acceptance evidence.",
    )
    args = parser.parse_args()

    minecraft_directory = args.minecraft_directory.expanduser().resolve()
    game_directory = args.game_directory.expanduser().resolve()
    minecraft_directory.mkdir(parents=True, exist_ok=True)
    game_directory.mkdir(parents=True, exist_ok=True)

    loader = minecraft_launcher_lib.mod_loader.get_mod_loader("neoforge")
    installed_version = loader.get_installed_version(MINECRAFT_VERSION, NEOFORGE_VERSION)
    version_json = minecraft_directory / "versions" / installed_version / f"{installed_version}.json"
    if not version_json.is_file():
        print(
            f"Installing NeoForge {NEOFORGE_VERSION} for Minecraft {MINECRAFT_VERSION} "
            f"into {minecraft_directory}",
            flush=True,
        )
        loader.install(
            MINECRAFT_VERSION,
            minecraft_directory,
            loader_version=NEOFORGE_VERSION,
            java=args.java,
        )

    # This also installs/checks the vanilla client jar, libraries, and assets referenced by the
    # installed NeoForge profile. It is safe to rerun and repairs missing files.
    minecraft_launcher_lib.install.install_minecraft_version(installed_version, minecraft_directory)

    options = minecraft_launcher_lib.utils.generate_test_options()
    options["username"] = args.username
    options["gameDirectory"] = str(game_directory)
    options["launcherName"] = "Skyforge S4 Review"
    options["launcherVersion"] = "1"
    options["defaultExecutablePath"] = args.java or "java"
    if args.java:
        options["executablePath"] = args.java
    if args.server:
        options["quickPlayMultiplayer"] = args.server

    command = minecraft_launcher_lib.command.get_minecraft_command(
        installed_version,
        minecraft_directory,
        options,
    )
    if args.program_args_file:
        output = args.program_args_file.expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(command, indent=2), encoding="utf-8")

    print(f"Launching production NeoForge profile {installed_version}", flush=True)
    print(f"Minecraft directory: {minecraft_directory}", flush=True)
    print(f"Game directory: {game_directory}", flush=True)
    if args.server:
        print(f"Quick-play server: {args.server}", flush=True)
    completed = subprocess.run(command, cwd=game_directory, check=False)
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
