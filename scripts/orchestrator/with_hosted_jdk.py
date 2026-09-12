#!/usr/bin/env python3
"""Execute a repository-local command with the pinned hosted Java toolchain."""

from __future__ import annotations

import argparse
import os
from pathlib import Path

from hosted_jdk import java_home, toolchain_env


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run a command with the hosted pinned JAVA_HOME/PATH."
    )
    parser.add_argument("--print-java-home", action="store_true")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    root = repository_root()
    if args.print_java_home:
        print(java_home(root))
        return 0

    command = list(args.command)
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        parser.error("provide a command after --")

    env = toolchain_env(root)
    os.execvpe(command[0], command, env)
    return 127


if __name__ == "__main__":
    raise SystemExit(main())
