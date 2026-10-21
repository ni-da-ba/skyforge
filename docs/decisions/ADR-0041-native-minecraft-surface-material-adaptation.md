# ADR-0041 — Native Minecraft Surface Material Adaptation

**Status:** Accepted

## Context

SF-IMP-0036 moved Skyforge terrain into a registered `NoiseBasedChunkGenerator` immediately after vanilla surface construction. That timing is early enough for later carvers, final heightmaps, features and lighting to observe Skyforge terrain, but vanilla `SurfaceSystem` has already finished before Skyforge blocks exist.

The current engineering palette therefore still projects Skyforge structural semantics directly to dirt, stone and deepslate. Hard-coding a parallel Skyforge biome or climate table would violate the accepted backend boundary, while rerunning vanilla `SurfaceSystem` against independently generated floating geometry would couple Skyforge to internal preliminary-surface assumptions that SF-IMP-0036 deliberately avoided.

Minecraft has already produced a concrete native surface result in the chunk by the time Skyforge runs. That result is a backend-authored representation decision and can be reused directly.

## Decision

The first Minecraft-native surface adaptation samples the already-built native Minecraft surface in each chunk column before Skyforge writes any blocks.

For each `(x, z)` column:

1. scan the post-surface `ChunkAccess` from top to bottom;
2. ignore air and fluid blocks;
3. record the first remaining block as that column's native surface-top material;
4. inspect the accepted Skyforge materialization for exposed solid tops, defined as a Skyforge solid with Skyforge AIR immediately above it;
5. for representation-only adaptation, copy a sampled material only when it is not a gravity-affected `FallingBlock`;
6. for terrain-profile projection, place the provider material on the projected cap only when the block directly below is non-air; unsupported fringe retains Skyforge structural material, and provider-selected stone retains a non-stone authored cap surface to avoid an isolated rock patch;
7. do not apply the provider material in a second pass to unrelated exposed Skyforge segments;
8. leave all other Skyforge materialization entries unchanged;
9. perform the existing additive solid overlay into the real Minecraft chunk.

Conceptually:

```text
vanilla NOISE
    -> vanilla SURFACE
    -> snapshot native surface-top material per column
    -> Skyforge semantic materialization
    -> adapt exposed Skyforge top keys from native surface snapshot
    -> additive live BlockState write
    -> vanilla CARVERS / HEIGHTMAPS / FEATURES / LIGHTING
```

The implementation remains entirely inside `skyforge-neoforge-1211`. No biome, climate, block ID or Minecraft surface concept is added to kernel/model/recipes/world.

## Why copy Minecraft's surface result rather than classify biomes in Skyforge

The native surface block is the output of Minecraft's actual backend rules. Sampling it has several advantages over a new Skyforge biome table:

- Minecraft remains authoritative for concrete material representation;
- vanilla biome/surface-rule changes can flow through without a duplicated climate model;
- a modded surface block can be inherited if it is already present in the active Minecraft block registry and native surface result;
- Skyforge structural semantics remain unchanged;
- the adaptation does not require upstream modules to know Minecraft biome IDs, tags or registry keys.

This is intentionally a representation adapter, not a new environmental simulation layer.

## Why only the exposed top block

SF-IMP-0037 is the smallest useful proof. Replacing eligible exposed tops gives Minecraft-native visual/environmental continuity while preserving accepted structural palette choices for the rest of the island. Gravity-affected surface materials require a support check: owner review of the Tectonic treatment showed gravel falling into a hollow seam. The profile adapter now applies gravity-affected material only on a projected cap with structural support directly below it, and avoids repainting separate exposed ledges. Unsupported fringe keeps Skyforge's stable structural material.

Copying an arbitrary depth of native filler material would require a separate policy for mantle thickness, falling blocks, shore/ocean columns and vertically varying biome context. Those concerns should be justified by observed results rather than folded into the first adapter.

## Multiple vertically separated islands

The adapter treats every Skyforge solid segment whose immediately higher Skyforge sample is AIR as an exposed top. It therefore does not assume only one island can occupy a vertical `(x, z)` column.

The same native column surface material may currently be reused for more than one elevated segment. That is an explicit first-proof limitation, not a new world-composition constraint.

## Terrain intersections

A sampled native surface at or above a Skyforge exposed top is not copied onto that top. Such a sample is treated as a terrain intersection rather than a floating exposed surface.

Skyforge AIR remains non-destructive and native Minecraft terrain remains authoritative outside Skyforge solids.

## Binding policy

The existing unadapted post-surface install path remains available for exact engineering-palette tests.

A distinct native-surface-adapted binding opts into this representation behavior. The SF-IMP-0037 ModDev specimen uses the adapted binding so the real-client proof cannot silently pass through the old engineering-only path.

## Invariants

1. Backend-neutral modules remain free of Minecraft/NeoForge APIs.
2. Skyforge density and structural terrain semantics remain authoritative for occupancy and geometry.
3. Surface adaptation changes only concrete Minecraft representation.
4. The adapter never changes Skyforge AIR into a solid block.
5. The adapter never removes a Skyforge solid.
6. Native fluid blocks are not copied as Skyforge surface solids.
7. Gravity-affected native surface blocks are copied only onto projected tops with a direct solid Skyforge support; unsupported fringe retains stable structural material.
8. Native terrain at or above a Skyforge top prevents that top from being treated as a floating exposed surface.
9. No planner/group/archipelago work enters the per-chunk hot path.
10. The development world preset remains development-only and excluded from the production JAR.

## Explicit limitations

SF-IMP-0037 does **not** claim that:

- vanilla `SurfaceSystem` has actually run over Skyforge geometry;
- the sampled ground-level surface always represents a vertically different high-altitude biome perfectly;
- native subsurface/filler depth has been reproduced;
- beaches, ocean floors, snow layers, powder snow, fluids or shore transitions are production-tuned;
- falling-block surface materials are inherited on unsupported floating island edges;
- structure placement is Skyforge-aware;
- every modded surface system is automatically compatible;
- the current Massif morphology is final.

A further accepted integration observation is that Minecraft's normal top-surface heightmaps are single-valued per `(x,z)`. When an elevated Skyforge island occupies a column, the upper island may become the heightmap target while preserved native ground below remains physically present. This can redirect heightmap-driven vegetation/features away from the lower ground and requires a separate multi-surface suitability/placement strategy if Skyforge needs independently decorated stacked surfaces.

## Acceptance criteria

ADR-0041 becomes Accepted only after:

1. an automated test proves air and fluids are skipped while sampling the native surface;
2. an automated test proves every elevated exposed Skyforge segment in a column can inherit the sampled native top material;
3. non-top Skyforge material remains unchanged;
4. Skyforge solids at/below the native surface are not incorrectly adapted as floating tops;
5. solid occupancy and candidate-volume metadata remain unchanged by adaptation;
6. the real post-surface binding can opt into native surface adaptation while the unadapted engineering path remains available;
7. the SF-IMP-0037 development runtime explicitly uses the adapted binding and not `ChunkEvent.Load`;
8. backend-neutral independence remains green;
9. focused NeoForge tests and repository-wide `check` pass;
10. a real ModDev world visibly retains the Massif, native terrain, later worldgen interaction and persistence while its exposed surface uses Minecraft's already-built native surface material rather than only the fixed engineering top representation.

## Acceptance evidence — 2026-08-31

All criteria above passed.

Automated validation passed through `scripts\verify-sf-imp-0037-native-surface-adaptation.bat` and repository-wide `gradlew.bat check`, including the final regression diagnostic proving that preserved lower ground remains present while Minecraft's final single-valued world-surface heightmap selects the elevated Skyforge surface.

In the real ModDev client, the Massif remained geometrically intact and persistent, native terrain remained additive, the exposed island top appeared remarkably like the surrounding Minecraft terrain, copied native material remained shallow rather than replacing the whole island interior, and later world-generation/lighting behavior remained functional. The sparse vegetation observed directly beneath the Massif is recorded as the heightmap/multi-surface integration boundary described above, not as a failure of native material adaptation.


## SF-IMP-0054 provider height-profile experiment — in progress

The separate provider-profile path may shape the exposed Skyforge cap while preserving its horizontal footprint and the authored lower body/underside. To prevent positive provider relief from being flattened at the original cap ceiling, this development experiment permits up to 32 blocks of additional height above the authored top-volume bound, constrained by Minecraft's build range. Added cells receive claims for the same exact Skyforge volume. This is experimental Implementation evidence and is not a production morphology or world-scale policy; acceptance requires exact-head A/B evidence and owner visual review.

## Tectonic visible-relief datum follow-up — in progress

The owner found consecutive Tectonic treatments visually too similar. Evidence from the pinned profile showed the origin-chunk median datum leaving the provider surface at or below the authored island cap, so the adapter mostly carved relief downward. The isolated experiment now uses the lower-quartile surface elevation from the provider origin chunk as its single world-fixed datum. This keeps cross-chunk continuity while allowing the upper part of the provider profile to rise above the authored cap; the existing 32-block rise ceiling, exact-volume claims, stable support rule, footprint, body, and underside remain unchanged. This is a tuning hypothesis pending exact-head A/B machine evidence and owner visual review.


## Broad-window Tectonic datum and ridge acceptance — in progress

Exact-head run [39511112544](https://github.com/ni-da-ba/skyforge/actions/runs/39511112544) passed its general terrain checks after switching to the origin-chunk lower quartile, but the realized Tectonic top still ended at Y=265, the same cap as the vanilla specimen. Relief alone had not made provider ridges visible above the authored cap. The next experiment samples a fixed 256-by-256-block window for its world-shared lower-quartile datum and requires the treatment's realized maximum top to reach Y=273. The existing exact-volume, footprint, underside, support, and 32-block extension rules remain in force. Visual acceptance remains pending.


## Low-percentile origin datum — in progress

The adaptive broad-window retry kept the vanilla control green but still left the Tectonic treatment flat at the authored Y=265 cap. Its 29-block provider source range produced zero realized island relief. The next mapping uses a world-fixed fifth-percentile datum sampled from the origin chunk: the same reference applies across island chunks, while most provider columns carry positive ridge relief above the authored cap. The acceptance now reports that datum on relief failures. The machine ridge threshold remains Y=273; visual acceptance remains pending.

## Minimum-datum Tectonic follow-up — 2026-10-20

The exact-head 5th-percentile experiment measured the datum at Y=292 against Tectonic source Y=271–300; realized island relief remained 21 blocks (Y=244–265), with no ridge above the authored cap. This confirms the source-height distribution is weighted toward its upper end and the 5th percentile remains too high. The next profile uses the minimum of the same fixed origin chunk as its datum, mapping the full sampled source relief upward. The existing 32-block cap-rise allowance limits outlier influence; the A/B still requires at least 24 blocks of realized relief and a ridge at Y≥273. Visual acceptance remains pending.


## Provider-rise admission fix — in progress

The minimum-datum A/B run measured datum Y=271 against source Y=271–300, but the realized cap remained flat at Y=265. The terrain adapter had emitted exact provider claims for added top cells, but physical admission still required every claimed cell to be inside the authored volume's vertical bounds; that contradicted the adapter's existing bounded 32-block top-rise allowance. Provider claims now authorize writes above the authored top only within the same island's horizontal bounds, above its existing solid support, and no more than 32 blocks above the authored Y maximum. Interior, underside, competing-volume, and unclaimed writes remain blocked. The next exact-head A/B must demonstrate the uplift; human visual acceptance remains pending.


## Provider-write diagnosis — in progress

At the failed exact head, the captured provider snapshot and base-column sampler agree at Y=271–300, with 255 of 256 columns above the Y=271 datum, yet the realized island is unchanged. This rules out provider snapshot and datum mismatch. The development writer now records exact claimed-cell counts, admission rejections, occupied positions, and successful provider writes in the Tectonic acceptance log. Use that accounting to isolate the remaining write-path failure before further terrain tuning.


## Tectonic relief amplification follow-up — in progress

The owner reports that successive Tectonic specimens remain visually too similar. The current profile uses the origin chunk's minimum provider surface as its datum and maps the resulting positive relief one-to-one onto Skyforge's cap. This provider-profile experiment doubles the sampled relief and raises the provider-owned top-rise ceiling to 64 blocks. This yields a substantially taller, more legible terrain profile while keeping the same exact X/Z island footprint and exact-volume claims. The multiplier is shared by the vanilla control and Tectonic treatment so the A/B isolates the provider's native profile; actual output remains bounded by exact ownership checks and the 64-block ceiling.

The exact-head fixture now requires at least 48 blocks of realized vertical range, a ridge at or above Y=300, and records the relief multiplier. Machine success still does not establish visual coherence. The Tectonic treatment must again be reviewed for visible terrain, stable surface seams, island spawn, intact underside, and surrounding void.


## Tectonic profile concentration experiment — in progress

The doubled linear transfer still mapped most of Tectonic's measured source range (Y=271–300) above the island's ridge band: 249 of 256 sampled columns reached Y=300, even after increasing Tectonic's horizontal frequency settings. Exact provider writes all succeeded, so this was a shape-mapping issue rather than an ownership/admission failure.

The development profile adapter now applies a deterministic signed cubic transfer to provider relief relative to its fixed world datum. Relief is normalized to 32 source blocks and mapped into the existing 64-block rise envelope. This keeps modest relief close to the authored cap while concentrating the source profile's upper tail into narrower peaks; negative relief remains deterministic and symmetric. Exact X/Z ownership, added-cell claims, stable material support, body, and underside boundaries are unchanged.

This is an Implementation experiment, not an accepted production terrain-shape contract. The focused adapter test checks the lowland, peak, negative-relief, and saturation values. The exact-head Tectonic A/B must demonstrate both the minimum relief and reduced ridge-band coverage before another visual review is requested.
