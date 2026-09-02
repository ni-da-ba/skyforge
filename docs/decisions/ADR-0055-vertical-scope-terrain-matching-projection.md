# ADR-0055: Terrain-owner-scoped structure projection

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0051

## Context

The SF-IMP-0050 interactive proof exposed a stacked-surface compatibility defect unrelated to its underside-contradiction rule. A vanilla village rooted on ordinary Overworld terrain below a floating Skyforge specimen left path/plank continuation blocks on the upper island.

Minecraft's jigsaw `terrain_matching` projection owns this behavior generically. `StructureTemplatePool.Projection.TERRAIN_MATCHING` contributes a shared `GravityProcessor` whose block processing normally asks one global heightmap for the highest surface at each projected X/Z. In a vertically stacked Skyforge world, that global surface can belong to a different terrain body from the structure being placed.

Rejecting villages, scanning downward through unrelated islands, or cataloguing affected structures would violate Skyforge's naturalization, deterministic-world-object, and unknown-mod compatibility goals.

## Decision

Terrain-sensitive placement SHALL resolve one independent terrain domain **before** choosing a projection surface.

The supported domains are:

1. `BASE_WORLD`: native Minecraft terrain as it existed immediately after vanilla surface construction and before Skyforge wrote any suspended-volume overlay;
2. `SKYFORGE_VOLUME(id)`: one exact independently compiled `SkyIslandWorldVolumeId`.

A projection query SHALL read only the surface authority of its resolved domain:

- `BASE_WORLD` reads an immutable per-chunk pre-overlay Minecraft surface snapshot;
- `SKYFORGE_VOLUME(id)` reads the deterministic first-free surface computed from that exact compiled island's upper/underside/density fields.

Unrelated terrain domains SHALL be invisible to the query. They do not compete by elevation, and the resolver SHALL NOT scan through one domain to discover another.

If the native placement anchor cannot be resolved to exactly one terrain domain, Skyforge SHALL preserve the vanilla result unchanged.

## Domain resolution

The native template placement anchor supplies conservative terrain ownership evidence.

At one X/Z:

- if the anchor lies inside exactly one independently compiled Skyforge volume envelope, the terrain domain is that exact `SKYFORGE_VOLUME(id)`;
- if it lies in no Skyforge volume envelope, the domain is `BASE_WORLD`;
- if multiple Skyforge envelopes plausibly own the anchor, ownership is ambiguous and Skyforge fails open to vanilla.

This preserves vertically stacked islands as independent world objects. There is no rule such as "highest surface wins" or "nearest surface wins."

## Base-world isolation

`SkyforgeNoiseBasedChunkGenerator.buildSurface` executes vanilla surface construction first. Before Skyforge materializes any island blocks, the adapter captures the chunk's native `WORLD_SURFACE_WG` first-free heights as an immutable `MinecraftBaseTerrainSurfaceSnapshot`.

The snapshot exists only across that chunk's SURFACE-to-FEATURES generation interval and is consumed when the same chunk enters biome decoration. It is not serialized and is not a second world model.

Consequently, a base-world village road beneath a Skyforge island asks only the captured native surface and cannot observe the island at all.

## Skyforge-volume isolation

A Skyforge-owned terrain query addresses one exact `SkyIslandWorldVolumeId`. Its first-free height is derived from the already-compiled deterministic island fields. Vanilla terrain and other stacked Skyforge volumes are not consulted.

This is the same provenance principle used by structure admission, foundation ownership, and 3-D terrain observation: independent compiled volumes remain independent all the way to backend adaptation.

## Shared processor seam

Minecraft's `terrain_matching` projection is shared by vanilla and ordinary modded jigsaw pool elements. Skyforge widens only the projection's processor-list field through an Access Transformer so bootstrap can replace the exact vanilla `GravityProcessor` while preserving every other processor.

The replacement executes vanilla processing first so ordinary template state/NBT/transformation semantics remain authoritative. It changes only the projected Y when an active Skyforge decoration scope resolves a terrain domain and that domain provides a surface.

If bootstrap does not observe exactly one exact vanilla gravity processor, it logs a warning and leaves the shared projection unchanged rather than overwriting another mod's incompatible ownership of the seam.

The replacement remains globally installed but is inert outside `SkyforgeNoiseBasedChunkGenerator`'s scoped biome-decoration call.

## Naturalization boundary

The rule does not inspect:

- village identity;
- structure registry keys;
- template names;
- block palette semantics;
- mod ownership;
- jigsaw pool names.

Any vanilla or modded jigsaw element using ordinary `terrain_matching` projection receives the same terrain-domain isolation automatically.

## Development proof

A dedicated SF-IMP-0051 ModDev run uses a clean, pocket-free floating island overlapping the outskirts of a forced lower plains village.

The lower village anchor resolves to `BASE_WORLD`; the upper island is a distinct `SKYFORGE_VOLUME(id)`. Where vanilla's composite heightmap would report the island near Y=224, base-world projection must continue to use the lower native terrain near the village.

A successful redirected projection emits:

```text
SF-IMP-0051 TERRAIN PROJECTION SCOPED
```

Interactive acceptance requires the lower village to remain present while the upper island remains free of village path/plank or other terrain-projection contamination.

## Rejected alternatives

### Scan downward past unrelated islands

Rejected as the permanent architecture. It can repair the symptom, but it begins from a composite heightmap and reasons across terrain bodies that should be independent.

### Reject villages or jigsaw structures near upper islands

Rejected. It destroys valid native structures and does not generalize to unknown mods.

### Copy or reimplement jigsaw placement

Rejected. Skyforge should preserve Minecraft's processor pipeline and answer only the physical terrain query with the correct owner.

### Choose the highest or nearest surface heuristically

Rejected. Terrain ownership precedes surface selection; unrelated domains do not participate in the query.

## Validation

SF-IMP-0051 requires automated evidence that:

- base-world projection reads only the pre-overlay base-world authority;
- Skyforge projection reads only one exact compiled volume authority;
- missing or ambiguous domain evidence fails open;
- the base-world snapshot is chunk-scoped and consumed rather than persisted;
- bootstrap remains idempotent and preserves unrelated processors;
- the development fixture resolves lower village and upper island anchors to distinct domains;
- full repository tests and both evidence-publication stages pass on the exact PR head.

It additionally requires an interactive Minecraft proof before this ADR can become **Accepted**.
