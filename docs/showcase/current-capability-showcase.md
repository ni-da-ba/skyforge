# Skyforge current-capability Minecraft showcase

This showcase is a **human-viewable presentation of already accepted runtime behavior**. It does not add a new generation algorithm, force feature IDs, or invent authored material/hydrology semantics.

## One-command launch

From a clean checkout at the repository root:

### Windows

```powershell
.\gradlew.bat :skyforge-neoforge-1211:launchShowcase
```

### macOS / Linux

```bash
./gradlew :skyforge-neoforge-1211:launchShowcase
```

The task performs two ordered phases:

1. **Prepare** — creates a fresh `run-skyforge-showcase/saves/showcase` world using the accepted SF-IMP-0069 stacked production runtime and waits for its self-checking proof to PASS.
2. **View** — launches Minecraft directly into the saved world with all generation bindings inert and only the presentation-only showcase navigator enabled.

The preparation phase is intentionally destructive to the previous showcase directory so every `launchShowcase` starts from the same deterministic state.

To reopen an already-prepared showcase without rebuilding it:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runShowcaseClient
```

## Guided navigation

On entry the player is moved to the panorama stop in spectator mode. Use:

```text
/skyforge_showcase
```

for the in-game stop list.

Recommended order:

| Stop | Command | What to inspect |
| --- | --- | --- |
| 1 | `/skyforge_showcase panorama` | Both vertically aligned production volumes in one view. This is the clearest visual proof that the same X/Z footprint can host independently tracked stacked volumes. |
| 2 | `/skyforge_showcase lower_surface` | Lower island terrain, native surface adaptation, exact-volume surface population, and the physical separation from the upper volume. |
| 3 | `/skyforge_showcase upper_surface` | Upper island independently admitted and populated through the same production lifecycle. |
| 4 | `/skyforge_showcase west_biome` | West half of the exact-volume resolver, using `minecraft:river`. |
| 5 | `/skyforge_showcase east_biome` | East half of the exact-volume resolver, using `minecraft:dripstone_caves`. |
| 6 | `/skyforge_showcase lower_caves` | Spectator inspection through the lower island: native-carved AIR plus authored exterior-connected cave topology, followed by native underground population. |
| 7 | `/skyforge_showcase upper_caves` | The same composed-cave/interior pipeline in the upper volume, independently fenced from the lower volume. |

The surface stops use creative mode. Cave and overview stops use spectator mode so the physical/cave relationships are directly inspectable.

## What this world actually contains

The prepare task reuses the accepted **SF-IMP-0069 stacked production fixture** rather than a showcase-specific generator. For each exact volume, the persisted world therefore comes through the real accepted ordering:

```text
native/base world generation
    -> Skyforge physical admission
    -> deferred terrain catch-up
    -> exact-volume biome identity
    -> native surface population
    -> SF-IMP-0068 composed caves
         native caves first
         authored exterior-connected caves last
    -> SF-IMP-0069 native interior population
         LAKES
         LOCAL_MODIFICATIONS
         UNDERGROUND_ORES
         UNDERGROUND_DECORATION
         FLUID_SPRINGS
```

The preparation proof also requires:

- both stacked volume ledgers to complete independently;
- nonzero native interior output in both volumes;
- generated fluid provenance in both volumes;
- foreign-volume fluid propagation rejection;
- whole-volume cave completion before interior population;
- monotonic obligation completion and no replay.

The viewer run does **not** reinstall any of those mutation bindings. It only opens the saved chunks and registers navigation commands.

## Intentionally not fabricated into the integrated world

Some accepted Skyforge capabilities are real but do not yet share this exact production composition path. The showcase leaves them out rather than faking them.

### Multiple morphology families

Skyforge has accepted Minecraft-visible morphology-family specimens (including the earlier MASSIF and TABLELAND development proofs), but the current production cave/interior lifecycle is validated on the compact AUTH-0030-connected morphology used by SF-IMP-0068/0069. This integrated showcase therefore uses that accepted production specimen twice in a vertical stack rather than transplanting unrelated historical morphology fixtures into the production ledger.

### Structure accommodation

Native structure-start visibility and bounded fill-only accommodation are accepted implementation capabilities, but the deterministic SF-IMP-0046 mansion fixture owns a different isolated terrain binding. It is intentionally not overlaid onto the SF-IMP-0069 production world.

The existing companion human-eye proof remains available with:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runAccommodationClient
```

Create a new world using **Skyforge Development** and inspect near `x=8, y=242, z=8`. That run is self-checking and logs `SF-IMP-0046 FOUNDATION ATTACHED` only when the real native mansion start requires and accepts bounded fill accommodation.

### Authored materials and authored hydrology

The current authorship publication/checkpoint chain does not yet map semantic material or hydrology policy to concrete Minecraft block/fluid palettes. The water/lava visible here are **native Minecraft LAKES / FLUID_SPRINGS behavior** accepted in SF-IMP-0063/0064/0069. No showcase-only semantic material or fluid mapping has been invented.

## Correctness authority

This world is for visual comprehension. Acceptance remains machine-discriminating:

- SF-IMP-0068 composed-cave acceptance;
- SF-IMP-0069 production interior acceptance;
- showcase preparation verification;
- normal repository CI.

If the showcase ever disagrees with those gates, the automated acceptance results win.
