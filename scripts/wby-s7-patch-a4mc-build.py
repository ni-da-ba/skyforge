#!/usr/bin/env python3
"""Apply a bounded build-only migration for A4MC's retired KikuGie coordinates.

The pinned upstream source remains unchanged in Skyforge's candidate manifest. This
patch only updates the upstream build tool coordinates so the exact 0.2.2 source
can be rebuilt after its historical Fletching Table artifact was replaced.
"""
from __future__ import annotations

import argparse
from pathlib import Path


def replace_once(path: Path, before: str, after: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(before)
    if count != 1:
        raise SystemExit(
            f"Expected exactly one occurrence of {before!r} in {path}; found {count}"
        )
    path.write_text(text.replace(before, after), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    root = parser.parse_args().upstream.resolve()
    versions = root / "gradle" / "libs.versions.toml"
    plugin = root / "build-logic" / "src" / "main" / "kotlin" / "ModPlatformPlugin.kt"

    replace_once(versions, 'fletching-table = "0.1.0-alpha.22"',
                 'fletching-table = "0.2.0-alpha.9"')
    replace_once(versions, 'module = "dev.kikugie:fletching-table"',
                 'module = "dev.kikugie.fletching-table:fletching-table"')
    replace_once(versions, 'id = "dev.kikugie.fletching-table"',
                 'id = "dev.kikugie.fletching-table.neoforge"')
    replace_once(plugin, '"dev.kikugie.fletching-table"',
                 '"dev.kikugie.fletching-table.neoforge"')

    print("Applied Fletching Table 0.2.0-alpha.9 build-tool migration.")
    print("Upstream A4MC source files and runtime sources remain pinned and unchanged.")


if __name__ == "__main__":
    main()
