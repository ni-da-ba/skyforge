# Alpha SkyBiome contract

**Status:** Proposed implementation contract for #1520 / #1523  
**Owner direction:** 2026-10-18 — SkyBiomes represent regional properties of the sky, including environmental context; the owner delegated taxonomy and system relationships to the implementation.

## Meaning

A SkyBiome is a stable Skyforge-authored class of sky habitat. It describes the character of the airspace around and between floating islands. It is separate from:

- SkyIslandEcologyRegime, which describes broad ecology on an island surface;
- an island, cluster, or province identity;
- a Minecraft biome key, which remains a local realization selected inside Skyforge-admitted island volume;
- live atmospheric measurements, which remain under Aerodynamics4MC (A4MC).

The alpha vocabulary is deliberately small:

| Stable ID | Habitat meaning |
| --- | --- |
| skyforge:open_sky | Broad, unobstructed aerial space. |
| skyforge:cloud_shelf | Airspace associated with a cloud layer or formations. The ID does not generate clouds. |
| skyforge:island_lee | Airspace sheltered by nearby island mass and lee geometry. |

These are habitat descriptors, not weather states. The catalog does not encode wind speeds, lift, turbulence, shear, pressure, or their trust/provenance. Consumers that need live conditions query A4MC. Skyforge adds no regional climate simulation or atmosphere authority.

## Authorship and content boundary

Skyforge Authorship owns the stable ID, habitat traits, coherent zone identity, and zone-to-class assignment. Content may combine those authored traits with accepted ecology/fauna inputs to define spawn eligibility and population behavior. The ID alone does not promise a species, spawn, cloud block, or gameplay effect.

Local island ecology remains independent and may differ between islands sharing the same surrounding SkyBiome. Implementation may project the zone assignment to one or more Minecraft biome choices within admitted island domains. That projection is not a one-to-one identity mapping and cannot claim the void outside owned island volumes.

## Identity and persistence

SkyBiomeZoneIdentity is versioned and consists of the world seed plus an opaque stable zone key. One zone may span multiple provinces, clusters, and islands; its geographic membership is stored/derived by the world-composition layer rather than encoded in the ID. SkyBiomeZoneAssignment binds that identity to a backend-neutral SkyBiomeId.

The canonical model JSON writes schema versions, world and zone keys as fixed-width hexadecimal values, and the stable namespace:path SkyBiome ID. Zone membership generation and save/reopen behavior are implementation concerns and require dedicated alpha runtime evidence.

## Deferred alpha work

- Deterministic spatial assignment of zones over the world and islands.
- Mapping habitat traits to content-owned species/spawn policy.
- Modded/native biome projection and exact-volume runtime persistence proof.
- Visual/play review of whether these sky regions read clearly in-game.
