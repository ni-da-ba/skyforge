# DR-40 AUTH-0096 runtime-column projection

Issue #494 exposed a concrete adapter question: how may accepted AUTH-0011/AUTH-0091 water and
riparian evidence be consumed by the Minecraft production-ecology carrier without inventing a second
ecological or hydrologic authority?

No new Authorship contract is required. AUTH-0046 already establishes the direct unit-scale
island-local frame, `SkyIslandCompiledVolumeColumnField` already applies `world = realized center +
local`, and AUTH-0096 already preserves every accepted 49x49 watershed cell identity while attaching
retained-waterbody, shoreline, margin, riparian, channel, and exact realized surface evidence.

`SkyforgeAuthoredSurfaceCellRasterizer` is therefore an Implementation-only backend adapter. It:

- accepts exactly one AUTH-0096 profile;
- keeps its exact AUTH-0046 realized volume identity;
- assigns an in-domain Minecraft X/Z column to the nearest site on the existing watershed lattice;
- returns no semantic cell for an inactive/out-of-domain lattice site;
- projects an accepted anchor with the same `round(realizedCenter + local)` integer convention already
  accepted by DR-20 authored hydrology;
- verifies the resulting integer column against the exact compiled-volume occupancy bridge;
- exposes freshwater/riparian presence only from existing AUTH-0096 values, with no new threshold.

The nearest-anchor rasterization is backend presentation, not a new planning grid or authored semantic
field. It does not choose a biome, density, carrying capacity, predator pressure, or aesthetic policy.
DR-40 may now use this adapter to bind accepted water/riparian context to the existing
`SkyforgeNativeSurfacePopulationStage` and persistent biome-presentation lifecycle.
