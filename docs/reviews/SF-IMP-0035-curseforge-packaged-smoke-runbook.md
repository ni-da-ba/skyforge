# SF-IMP-0035 CurseForge Packaged Smoke Runbook

**Status:** Accepted

## Purpose

SF-IMP-0035 proves that Skyforge can leave the Gradle development workspace and behave as one ordinary NeoForge mod artifact in a clean CurseForge instance.

SF-IMP-0034 already proved visible Skyforge terrain in a real Minecraft client. This work item deliberately isolates packaging and loader correctness.

## Required profile

Create or use a clean CurseForge custom profile with:

- Minecraft: `1.21.1`
- Mod loader: `NeoForge`
- NeoForge: `21.1.249`
- Other mods: none for the first smoke test

Do not use a normal play world for this test.

## Build and preflight

From the repository root on `agent/sf-imp-0035`:

```bat
scripts\verify-sf-imp-0035-packaged-mod.bat
```

The verifier prints the exact distributable path. The ModDevGradle artifact is under:

```text
skyforge-neoforge-1211\build\libs\
```

Do not infer that the Jar-in-Jar artifact must use an `-all.jar` classifier. ModDevGradle normally emits the distributable mod under its ordinary `<name>-<version>.jar` name. The verifier identifies the correct artifact by archive contents instead of filename.

The verifier checks that the artifact contains:

- `META-INF/neoforge.mods.toml`;
- `META-INF/jarjar/metadata.json`;
- embedded `skyforge-kernel`;
- embedded `skyforge-model`;
- embedded `skyforge-recipes`;
- embedded `skyforge-world`.

Automated package verification passed on 2026-08-31 for:

```text
skyforge-neoforge-1211\build\libs\skyforge-neoforge-1211-0.1.0.jar
```

## Install in CurseForge

1. In CurseForge, open the clean Skyforge test profile.
2. Use **Open Folder**.
3. Open the profile's `mods` directory.
4. Remove any older Skyforge test jars.
5. Copy the exact artifact path printed by the verifier into `mods`.
6. Launch the profile normally through CurseForge.

## Manual acceptance

Pass when:

1. NeoForge/Minecraft starts without a mod-loading crash.
2. There is no `ClassNotFoundException` / `NoClassDefFoundError` for any `io.github.nidaba.skyforge` backend-neutral class.
3. There is no Jar-in-Jar metadata/dependency selection failure.
4. Skyforge appears in the loaded mod set or startup log as mod id `skyforge`.
5. Minecraft reaches the main menu.

**Manual packaged-mod result: PASS — 2026-08-31.**

The normal packaged mod is expected to be inert because no production world binding exists yet. The SF-IMP-0034 development specimen is enabled only by its development JVM property and is not part of normal packaged behavior.

## Repository-wide validation

A fresh repository-wide:

```bat
gradlew.bat check
```

also completed successfully on `agent/sf-imp-0035` after the packaging and documentation changes.

**Overall result: PASS.**

## After acceptance

Minecraft/NeoForge installation mechanics are no longer the primary unknown. The next substantive integration task is the earlier world-generation insertion point required for native heightmaps, lighting, features, vegetation and structures to reason about Skyforge terrain during generation.
