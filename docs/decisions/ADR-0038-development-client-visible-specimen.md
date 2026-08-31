# ADR-0038 — Development Client Visible Specimen

**Status:** Accepted

## Context

SF-IMP-0033 proved that an FML-loaded Skyforge mod can receive a real NeoForge new-chunk lifecycle event and add accepted Skyforge solids to real Minecraft chunk storage. That proof remained headless: it did not establish that the same path can be exercised successfully inside an interactive Minecraft client/world.

The production adapter is intentionally inert until a runtime binding is installed. Installing an engineering specimen unconditionally would prematurely make test data part of Skyforge's user-facing world model. Conversely, delaying all visual validation until a complete configuration/world-selection system exists would leave a large integration gap untested.

## Decision

Provide one explicit development-only ModDevGradle client run for the first visual smoke test.

The run sets:

```text
skyforge.dev.specimen=true
```

When and only when that JVM property is true, the production `@Mod("skyforge")` entrypoint installs a finite deterministic Overworld runtime binding containing one built-in Massif near the world origin.

The specimen uses the accepted backend-neutral world/catalog/morphology/terrain-semantic machinery and the accepted SF-IMP-0033 additive writer. It is not a parallel or simplified Minecraft-only terrain generator.

## Invariants

1. Packaged Skyforge remains inert when the development property is absent.
2. The specimen is deterministic and finite.
3. The specimen is restricted to the Overworld by adapter-local level selection.
4. Skyforge AIR remains non-destructive to backend-native terrain.
5. The development specimen is constructed from ordinary Skyforge descriptors/recipes/catalogs.
6. No development-run concept enters kernel/model/recipes/world.
7. The client uses an isolated game directory.
8. Manual visual acceptance does not promote `ChunkEvent.Load` to the final worldgen insertion point.

## Inspection contract

The development specimen is centered near:

```text
x=0
z=0
suspension elevation ~= 224
```

The standard developer inspection position is:

```text
/tp @s 0 300 0
```

The specimen is sized to cross several Minecraft chunks while fitting inside the ordinary Overworld vertical range.

## Consequences

Positive:

- closes the first real interactive Minecraft validation loop;
- keeps the visual proof deterministic and easy to reproduce;
- makes chunk seams, persistence, native-terrain preservation, and gross lighting/timing problems directly inspectable;
- does not require a premature user configuration surface.

Negative / deferred:

- this is still a development run, not packaged CurseForge validation;
- the temporary material palette is not production terrain art;
- the late new-chunk load seam may produce lighting/heightmap/feature anomalies;
- no claim is made yet about structures, vegetation, ores, or compatibility mods.

## Acceptance

Accepted on 2026-08-31 after:

1. automated SF-IMP-0034 preflight completed successfully;
2. a real ModDevGradle Minecraft 1.21.1 client launched with Skyforge loaded;
3. a new disposable Overworld visibly contained the deterministic Massif at the documented location;
4. the specimen appeared to slot coherently into the world without an obvious chunk-ownership seam;
5. native Minecraft terrain remained present around/below the specimen rather than being globally erased by Skyforge AIR;
6. save/reload preserved the generated specimen.

Manual review also identified expected morphology/design issues: the development Massif is visibly preliminary, its underside is oversized relative to the desired playable form, and the current shape is not yet judged production-playable. These observations do not invalidate this ADR because SF-IMP-0034 accepts the interactive realization path, not final morphology quality.
