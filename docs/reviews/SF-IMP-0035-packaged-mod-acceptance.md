# SF-IMP-0035 Packaged NeoForge Mod Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Scope

SF-IMP-0035 proves that Skyforge can be distributed and loaded as one ordinary NeoForge mod artifact outside the Gradle development workspace.

This milestone does not change geometry, semantics, lifecycle timing, or user-facing world behavior. It isolates packaging and loader correctness.

## Accepted artifact

```text
skyforge-neoforge-1211\build\libs\skyforge-neoforge-1211-0.1.0.jar
```

The normal packaged mod remains inert unless a runtime binding is explicitly installed.

## Automated evidence

`scripts\verify-sf-imp-0035-packaged-mod.bat` passed and verified that the distributable contains:

- `META-INF/neoforge.mods.toml`;
- `META-INF/jarjar/metadata.json`;
- embedded `skyforge-kernel`;
- embedded `skyforge-model`;
- embedded `skyforge-recipes`;
- embedded `skyforge-world`.

Backend-neutral independence remained green, NeoForge tests passed, and the final Jar-in-Jar archive built successfully.

A fresh repository-wide `gradlew.bat check` also passed after the final branch changes.

## Manual packaged-client evidence

The artifact was installed as the only Skyforge jar in a clean CurseForge profile using:

- Minecraft 1.21.1;
- NeoForge 21.1.249;
- no additional mods for the smoke test.

The packaged client test passed. Minecraft launched successfully and reached normal operation without a missing-class, invalid-mod-file, or Jar-in-Jar dependency failure.

## Architectural conclusion

The following distribution path is now empirically proven:

```text
Skyforge source workspace
    -> backend-neutral Java runtime modules
    -> NeoForge adapter
    -> NeoForge Jar-in-Jar packaging
    -> one distributable Skyforge jar
    -> clean CurseForge / NeoForge profile
    -> successful Minecraft load
```

Backend-neutral modules remain ordinary Java libraries rather than NeoForge mods, and users do not need to install internal Skyforge modules separately.

## Deferred

- public release publication and release automation;
- production world-plan/configuration bootstrap;
- compatibility with unrelated mods;
- final Minecraft world-generation insertion timing;
- heightmap, lighting, feature, vegetation and structure participation.

The next substantive integration milestone is to move Skyforge terrain into an earlier generation seam so native Minecraft systems can reason about it while chunks are being generated.
