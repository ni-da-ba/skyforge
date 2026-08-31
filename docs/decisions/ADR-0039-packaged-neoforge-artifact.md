# ADR-0039 — Packaged NeoForge Artifact

**Status:** Accepted

## Context

SF-IMP-0034 proved that Skyforge can run inside a real interactive Minecraft 1.21.1 client when launched through ModDevGradle. That development run receives backend-neutral Skyforge modules through Gradle's development runtime classpath. A normal CurseForge/NeoForge installation does not have that Gradle workspace available.

The distributable Skyforge mod therefore must carry the backend-neutral engine modules it requires without:

- copying or shading their classes into the Minecraft adapter;
- turning backend-neutral modules into NeoForge mods;
- reversing the accepted dependency direction;
- requiring users to install multiple internal Skyforge jars manually.

NeoForge ModDevGradle provides Jar-in-Jar specifically for embedding Java-library dependencies in a mod artifact.

## Decision

The `skyforge-neoforge-1211` distributable uses NeoForge Jar-in-Jar to embed these backend-neutral runtime modules:

```text
skyforge-kernel
skyforge-model
skyforge-recipes
skyforge-world
```

The adapter continues to depend on `skyforge-world` normally at compile time and uses `additionalRuntimeClasspath` for ModDevGradle 1.21.1 development runs. Jar-in-Jar is the packaging mechanism only.

The normal packaged mod remains inert unless a runtime binding is explicitly installed. The development specimen from SF-IMP-0034 is not made part of ordinary user-facing world generation merely to test packaging.

## Invariants

1. Backend-neutral source modules contain no Minecraft or NeoForge APIs.
2. The distributable is one user-installed Skyforge mod jar.
3. Internal engine modules are embedded as Java libraries through NeoForge Jar-in-Jar rather than exposed as required user-installed mods.
4. `META-INF/neoforge.mods.toml` remains owned by the NeoForge adapter artifact.
5. The packaged artifact contains NeoForge Jar-in-Jar metadata.
6. All four required backend-neutral runtime jars are present in the distributable.
7. The same backend-neutral artifacts remain independently buildable and usable outside Minecraft.
8. Normal packaged Skyforge does not automatically install the SF-IMP-0034 engineering specimen.

## Acceptance

Accepted on 2026-08-31 after all planned gates passed:

1. `scripts\\verify-sf-imp-0035-packaged-mod.bat` passed;
2. the built artifact contained the production NeoForge mod descriptor;
3. the artifact contained Jar-in-Jar metadata and all four backend-neutral runtime modules;
4. repository-wide `gradlew.bat check` passed;
5. `skyforge-neoforge-1211-0.1.0.jar` was installed as the only Skyforge jar in a clean CurseForge Minecraft 1.21.1 / NeoForge 21.1.249 profile;
6. Minecraft reached the main menu without missing-class, invalid-mod-file, or Jar-in-Jar dependency errors;
7. the packaged Skyforge mod loaded successfully outside the Gradle development runtime.

A second visual-island proof was not required because SF-IMP-0034 already accepted the interactive realization path. This milestone isolates distributable artifact correctness.

## Deferred

- public release metadata and CurseForge project publication;
- semantic versioning/release automation;
- final world configuration/bootstrap behavior;
- the earlier world-generation insertion point;
- compatibility with unrelated mods.
