# SF-IMP-0035 CurseForge Packaged Smoke Runbook

**Status:** Automated packaging gate PASS / manual packaged-artifact acceptance pending

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

## Automated packaging result — 2026-08-31

**Result: PASS.**

The local SF-IMP-0035 verifier completed successfully and identified the distributable artifact as:

```text
C:\Users\nicho\Documents\skyforge\skyforge-neoforge-1211\build\libs\skyforge-neoforge-1211-0.1.0.jar
```

The archive inspection passed for the NeoForge mod descriptor, Jar-in-Jar metadata, and all four backend-neutral Skyforge runtime libraries. The remaining acceptance step is a normal launch from the clean CurseForge profile without Gradle development classpath assistance.

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

The normal packaged mod is expected to be inert because no production world binding exists yet. The SF-IMP-0034 development specimen is enabled only by its development JVM property and is not part of normal packaged behavior.

## If the profile fails

Capture or paste the earliest Skyforge/NeoForge loader error. In particular, distinguish:

- invalid mod descriptor;
- missing embedded module;
- duplicate module/artifact identity;
- Jar-in-Jar selection error;
- Java version mismatch;
- unrelated CurseForge/launcher failure.

Do not add compatibility or optimization mods until the clean packaged artifact passes.

## After acceptance

After this packaged proof is accepted, Minecraft/NeoForge installation mechanics are no longer the primary unknown. The next substantive integration task is the earlier world-generation insertion point required for native heightmaps, lighting, features, vegetation and structures to reason about Skyforge terrain during generation.
