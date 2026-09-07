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

1. **Prepare** — creates a fresh `run-skyforge-showcase/saves/showcase` world using the accepted stacked production lifecycle and waits for its self-checking proof to PASS. SF-IMP-0080 changes only the human-facing configuration: the base representation comes from the development-only `skyforge:showcase_land` preset (the same `skyforge:noise_overlay` generator over a fixed plains biome), while the lower and upper exact volumes resolve as forest and taiga respectively.
2. **View** — launches Minecraft directly into the saved world with every mutation lifecycle inert. The viewer restores only the deterministic compiled terrain-ownership catalog required to fence persisted generated-fluid ticks, plus the presentation-only navigator.

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
| 2 | `/skyforge_showcase lower_surface` | **Human gate:** lower forest volume. Confirm grass/soil, persistent native logs/leaves, surface plants, and physical separation from the upper volume. |
| 3 | `/skyforge_showcase upper_surface` | **Human gate:** upper taiga volume. Confirm grass/soil, visibly different tree/ecology character, surface plants, and independent stacked realization. |
| 4 | `/skyforge_showcase west_biome` | Legacy west approach retained as a convenient alternate view of the upper taiga surface. |
| 5 | `/skyforge_showcase east_biome` | Legacy east approach retained as a convenient alternate view of the upper taiga surface. |
| 6 | `/skyforge_showcase lower_caves` | Spectator inspection through the lower island: native-carved AIR plus authored exterior-connected cave topology, followed by native underground population. |
| 7 | `/skyforge_showcase upper_caves` | The same composed-cave/interior pipeline in the upper volume, independently fenced from the lower volume. |

The surface stops use creative mode. Cave and overview stops use spectator mode so the physical/cave relationships are directly inspectable.

## What this world actually contains

The prepare task reuses the accepted **stacked production fixture and production lifecycle** rather than a showcase-specific terrain generator. SF-IMP-0080 does not alter exact ownership, admission, caves, interior population, persistence, or the `skyforge:noise_overlay` generator. It deliberately supplies a grass-bearing plains base representation to the already-accepted native-surface adapter, then assigns the lower exact volume to `minecraft:forest` and the upper exact volume to `minecraft:taiga` so native ecology is visually inspectable. For each exact volume, the persisted world still comes through the real accepted ordering:

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
- monotonic obligation completion and no replay;
- SF-IMP-0080 lower/upper biome identities of forest/taiga;
- nonzero final grass, soil, logs, leaves, and surface plants on **both** exact volumes.

The viewer run does **not** reinstall admission, biome/surface population, cave, interior-population, material, or other mutation bindings. It restores only the same immutable compiled stacked-volume ownership catalog used by the accepted reload proofs, because persisted generated-fluid ticks must still know their exact-volume boundary after a full stop/reload. It then opens the saved chunks and registers navigation commands.

## SF-IMP-0080 ecology human gate

The machine proof can establish that the final persisted world contains land surface material and persistent native ecology. It cannot decide whether the result is visually legible.

Before SF-IMP-0080 is accepted, inspect `lower_surface` and `upper_surface` in the actual quick-play showcase and confirm:

- both surfaces visibly read as land rather than gravel/ocean floor;
- grass/soil is plainly visible;
- trees and foliage are plainly visible on both volumes;
- surface plants are present rather than only machine-counted off-camera;
- lower forest and upper taiga have a meaningful visual distinction;
- vegetation is attached to plausible surfaces and is not obviously floating;
- the improved ecology does not conceal a new terrain/persistence defect.

This is an **ecology legibility gate**, not production morphology acceptance. The compact showcase islands may still look geometrically primitive; issue #214 governs the later production-morphology human review.

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

The current authorship publication/checkpoint chain does not yet map semantic material or hydrology policy to concrete Minecraft block/fluid palettes. The water/lava visible here remain native Minecraft lake/spring behavior under the accepted floating-island plausibility and provenance rules; authored waterfalls/outlets are not fabricated for the showcase. The plains base preset is used only as a native **surface-representation source**, not as a replacement material ontology.

## Correctness authority

This world is for visual comprehension. Acceptance remains machine-discriminating:

- SF-IMP-0068 composed-cave acceptance;
- SF-IMP-0069 production interior acceptance;
- showcase preparation verification;
- actual quick-play showcase viewer reopen verification, including a persisted generated-fluid tick under ownership-only fencing;
- normal repository CI.

If the showcase ever disagrees with those gates, the automated acceptance results win.
