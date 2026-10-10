#!/usr/bin/env python3
"""Apply a bounded build-only migration for retired APIs in the pinned A4MC build tools.

The upstream runtime commit is immutable. This patch removes an unused, now-incompatible
Fletching Table build plugin and restores the Tomlkt JVM artifact matching upstream imports.
It does not alter main, client, content, or compatibility runtime sources.
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


def require_once(path: Path, expected: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(expected)
    if count != 1:
        raise SystemExit(
            f"Expected exactly one occurrence of {expected!r} in {path}; found {count}"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    root = parser.parse_args().upstream.resolve()

    versions = root / "gradle" / "libs.versions.toml"
    build_logic = root / "build-logic" / "build.gradle.kts"
    plugin = root / "build-logic" / "src" / "main" / "kotlin" / "ModPlatformPlugin.kt"
    stonecutter = root / "stonecutter.gradle.kts"

    # The pinned source imports net.peanuuutz.tomlkt.Toml. Its original catalog
    # points at a newer artifact whose package was moved; use the matching JVM API.
    replace_once(versions, 'serialization-toml = "0.6.0"', 'serialization-toml = "0.3.7"')
    replace_once(
        versions,
        'serialization-toml = { module = "dev.eav.tomlkt:tomlkt", version.ref = "serialization-toml" }',
        'serialization-toml = { module = "net.peanuuutz:tomlkt-jvm", version.ref = "serialization-toml" }',
    )

    # Fletching Table 0.2 split/changed the old build API. A4MC's exact NeoForge
    # profile already generates its mixin config through its own MixinsExtension;
    # this old plugin contributes no required runtime behavior to the profile.
    replace_once(versions, 'fletching-table = "0.1.0-alpha.22"\n', "")
    replace_once(
        versions,
        'fletching-table = { module = "dev.kikugie:fletching-table", version.ref = "fletching-table" }\n',
        "",
    )
    replace_once(
        versions,
        'fletching-table = { id = "dev.kikugie.fletching-table", version.ref = "fletching-table" }\n',
        "",
    )
    replace_once(build_logic, '\timplementation(libs.fletching.table)\n', "")
    replace_once(
        plugin,
        'import dev.kikugie.fletching_table.extension.FletchingTableExtension\n',
        "",
    )
    replace_once(
        plugin,
        '\t\tlistOf("org.jetbrains.kotlin.jvm", "com.google.devtools.ksp", "dev.kikugie.fletching-table").forEach {',
        '\t\tlistOf("org.jetbrains.kotlin.jvm", "com.google.devtools.ksp").forEach {',
    )
    replace_once(plugin, '\t\tconfigureFletchingTable(ctx)\n', "")
    replace_once(
        plugin,
        '\tprivate fun Project.configureFletchingTable(ctx: Context) {\n'
        '\t\textensions.configure<FletchingTableExtension> {\n'
        '\t\t\tmixins.create("main") { mixin("default", "${ctx.modId}.mixins.json") }\n'
        '\t\t\tj52j.register("main") { extension("json", "**/*.json5") }\n'
        '\t\t}\n'
        '\t}\n\n',
        "",
    )
    replace_once(stonecutter, '\talias(libs.plugins.fletching.table).apply(false)\n', "")

    require_once(
        versions,
        'serialization-toml = { module = "net.peanuuutz:tomlkt-jvm", version.ref = "serialization-toml" }',
    )
    require_once(build_logic, "implementation(libs.serialization.toml)")
    require_once(plugin, "configureProcessResources(ctx)")
    require_once(stonecutter, "alias(libs.plugins.stonecutter)")
    if "dev.kikugie.fletching-table" in plugin.read_text(encoding="utf-8"):
        raise SystemExit("Fletching Table plugin references remain in the patched build logic")
    if "fletching-table" in versions.read_text(encoding="utf-8"):
        raise SystemExit("Fletching Table catalog references remain after migration")
    if "net.peanuuutz:tomlkt-jvm" not in versions.read_text(encoding="utf-8"):
        raise SystemExit("Pinned source's matching Tomlkt JVM artifact was not installed")

    print("Applied build-only migration: removed the unused Fletching Table extension and matched Tomlkt 0.3.7 JVM coordinates.")
    print("A4MC runtime sources remain pinned and unchanged.")


if __name__ == "__main__":
    main()
