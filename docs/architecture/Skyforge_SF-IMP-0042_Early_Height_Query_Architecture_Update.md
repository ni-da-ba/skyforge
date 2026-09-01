# Skyforge SF-IMP-0042 — Early Height Query Architecture Update

SF-IMP-0042 adds the first accepted pre-write Minecraft query seam for Skyforge terrain.

Accepted relationship:

```text
early Minecraft worldgen consumer
    -> SkyforgeNoiseBasedChunkGenerator.getBaseHeight(...)
        -> vanilla NoiseBasedChunkGenerator height
        -> active Skyforge compiled-runtime height
        -> max(vanilla, Skyforge)
    -> later vanilla surface construction
    -> accepted post-surface Skyforge block realization
```

This separates **early geometric visibility** from **physical block insertion**. Minecraft systems that ask the generator about terrain can now receive an elevated Skyforge answer without forcing Skyforge terrain to be written during an earlier and less stable chunk-status phase.

The bridge remains adapter-local. The backend-neutral engine still owns semantic world composition and geometry, while Minecraft owns heightmap types, predicates and generator query conventions.

The accepted implementation is correctness-first. It may materialize more backend data than a future optimized height-only query requires; caching and specialized column evaluation remain implementation optimizations rather than architectural requirements.

Most importantly, SF-IMP-0042 is not itself structure support. It establishes the prerequisite seam for the next experiment: identify a real Minecraft structure-start consumer and prove that it can reason about the elevated island before the post-surface write occurs.
