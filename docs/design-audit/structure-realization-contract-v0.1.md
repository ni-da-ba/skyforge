# Structure Realization Contract v0.1

**Snapshot:** 2026-09-05  
**Status:** Design proposal for cross-agent use. Not yet an accepted ADR.

## Objective

Define a backend-neutral structure intent and terrain-envelope model that can support:

- ordinary optional Minecraft/modded structures;
- progression-critical vanilla structures;
- structures embedded inside exact Skyforge island volumes;
- cliff/underside-attached structures;
- detached air/water structures;
- structure-seeded terrain where the structure constrains island authorship.

The contract should reuse accepted generic structure-support machinery without teaching `skyforge-world` what a Stronghold, Trial Chamber, Ancient City, mansion, or third-party dungeon is.

## Design boundary

Skyforge owns:

- semantic reason for a structure to exist;
- required/progression importance;
- island/cluster/province context;
- terrain envelope and access requirements;
- ownership and collision policy;
- whether terrain may be authored because of the structure intent.

The Minecraft adapter owns:

- concrete structure registry identity;
- native `StructureStart` / `StructurePiece` realization;
- translation from concrete structure behavior to neutral requirements;
- compatibility with vanilla/modded placement, processors, loot, entities, and persistence.

## Structure intent

Candidate neutral model:

```text
StructureIntent {
    intentKey
    seed
    importance
    realizationPreference
    locationConstraint
    terrainEnvelope
    semanticContext
}
```

### Importance

```text
OPTIONAL
PREFERRED
REQUIRED
PROGRESSION_CRITICAL
```

- OPTIONAL may fail without replacement.
- PREFERRED should attempt fallback sites/assets.
- REQUIRED must produce an equivalent realization somewhere allowed by its location contract.
- PROGRESSION_CRITICAL must preserve progression semantics in addition to physical existence.

### Location constraint

Candidate classes:

```text
FREE
REGION_BOUND
CLUSTER_BOUND
ISLAND_BOUND
COORDINATE_ANCHORED
TOPOLOGY_ANCHORED
```

Examples:

- ordinary shrine -> FREE/REGION_BOUND;
- settlement airfield -> ISLAND_BOUND;
- Stronghold -> TOPOLOGY_ANCHORED or COORDINATE_ANCHORED;
- boss site tied to authored province -> REGION_BOUND.

## Terrain envelope

Candidate neutral requirement object:

```text
StructureTerrainEnvelope {
    horizontalFootprint
    requiredInteriorVolume
    requiredCoverDepth
    supportMode
    exposureMode
    accessModes
    excavationPolicy
    accommodationPolicy
    fluidPolicy
    orientationRequirements
    minimumSkyExposure
    maximumRelief
}
```

This is a semantic/physical contract, not a concrete block mask.

### Support modes

```text
SURFACE
SUBSURFACE
CLIFF
UNDERSIDE
DETACHED_AIR
DETACHED_WATER
MIXED
```

### Exposure modes

```text
EXPOSED
PARTIALLY_EXPOSED
BURIED
CAVERN
ANY
```

### Excavation policy

```text
NONE
BOUNDED_CLEARANCE
RESERVED_VOLUME
AUTHORED_EXCAVATION
```

The current accepted surface-structure path corresponds most closely to `SURFACE + NONE` with optional bounded fill-only accommodation.

## Canonical realization modes

### A. Surface-supported

```text
existing island
-> candidate site
-> native/modded structure start
-> piece-aware support admission
-> optional bounded foundation accommodation
-> realize
```

Best for towers, huts, temples, compact forts, ruins, and ordinary surface structures.

This mode should reuse accepted SF-IMP-0045/0047 mechanics.

### B. Settlement/network

```text
settlement intent
-> island/cluster site plan
-> multiple structure footprints
-> roads / docks / yards / utility spaces
-> individual building realization
```

Requires a new planner because settlement coherence cannot emerge from independent random structure candidates.

### C. Subsurface-embedded

```text
structure intent
-> exact island volume
-> reserve occupancy envelope
-> reconcile with authored caves / hydrology / geology
-> bounded excavation or authored cavity
-> native structure realization
```

Best for Trial Chambers, dungeons, mineshafts, Strongholds, and underground faction works.

### D. Cliff/underside-attached

```text
structure intent
-> suitable cliff/underside anchor
-> orientation from local surface frame
-> attachment/support envelope
-> realize
```

Best for hanging docks, cranes, cliff monasteries, lift stations, mine mouths, and underside ruins.

### E. Detached

```text
structure intent
-> open-air/water reservation
-> collision / route / horizon check
-> detached realization
```

Best for balloons, airships, floating stations, galleons, submarines, and derelict craft.

Detached does not mean unsupported error; it is an explicitly authorized support mode.

### F. Structure-seeded terrain

```text
required structure intent
-> no suitable existing site
-> reserve structure envelope
-> author island/terrain around envelope
-> validate composition
-> realize structure
```

This mode inverts the ordinary relationship and is required for progression-critical or exceptional structures when rejection is not acceptable.

## Site-selection order

For a generic intent:

```text
1. Resolve importance/location constraints.
2. Search already-planned islands for a compatible site.
3. Prefer the least-invasive realization mode that satisfies the envelope.
4. If optional and no site exists -> reject.
5. If required/progression-critical and no site exists -> invoke structure-seeded terrain or an approved equivalent fallback.
6. Reserve structure occupancy before incompatible downstream authorship.
7. Realize concrete Minecraft/modded structure through the adapter.
8. Attach structure/faction/encounter population provenance.
```

## Interaction with authorship

Structure reservations must become visible to relevant authorship stages before those stages create incompatible geometry.

Examples:

- a reserved Trial Chamber envelope should not later be bisected by an authored cave unless explicitly allowed;
- a Stronghold-bearing island needs enough certified interior support;
- an Ancient City cavern may deliberately dominate local cave topology;
- settlement plateau demand may influence surface morphology rather than flattening generated terrain afterward;
- cliff docks require a suitable cliff geometry, not arbitrary block replacement.

This suggests a future neutral reservation layer:

```text
World / province / cluster intent
        |
        v
Structure reservations
        |
        +--> island morphology planning
        +--> cave/hydrology planning
        +--> material/geology planning
        |
        v
Final structure realization
```

## Existing accepted implementation seam

The accepted surface structure work already provides valuable lower-level behavior:

- candidate interception;
- support evaluation;
- piece-aware floor-contact footprints;
- bounded fill-only accommodation;
- underside contradiction rejection;
- preservation of native structure identity and fallback semantics.

The new contract should extend those seams rather than replace them.

Relevant accepted documents:

- [SF-IMP-0045](../architecture/Skyforge_SF-IMP-0045_Structure_Candidate_Admission.md)
- [SF-IMP-0047](../architecture/Skyforge_SF-IMP-0047_Piece_Aware_Structure_Footprints.md)
- [SF-IMP-0050](../reviews/SF-IMP-0050-detached-underside-contradiction-acceptance.md)
- [SF-IMP-0052](../reviews/SF-IMP-0052-terrain-domain-isolation-acceptance.md)

## Vanilla structure policy sketches

### Stronghold

```text
importance = PROGRESSION_CRITICAL
location = TOPOLOGY_ANCHORED
preferred mode = SUBSURFACE_EMBEDDED
fallback = STRUCTURE_SEEDED_TERRAIN
```

Preserve vanilla-compatible progression/navigation topology where practical. Do not silently relocate a Stronghold merely because another island is convenient.

### Trial Chamber

```text
importance = PREFERRED/REQUIRED according to final distribution policy
preferred mode = SUBSURFACE_EMBEDDED
fallback = STRUCTURE_SEEDED_TERRAIN
population = TRIAL / STRUCTURE
```

The chamber should occupy a sufficiently thick island interior and keep its own trial-spawner encounter semantics.

### Ancient City — buried

```text
importance = exceptional
preferred mode = SUBSURFACE_EMBEDDED
envelope = enormous cavern + Deep Dark semantic context
```

### Ancient City — exposed

```text
importance = exceptional
preferred mode = STRUCTURE_SEEDED_TERRAIN
exposure = EXPOSED
```

Rare alternate realization: a massive island authored to carry an exposed Ancient City as a horizon-scale destination.

### Village

```text
preferred mode = SETTLEMENT_NETWORK
```

Require population/infrastructure intent, usable area, water/agricultural feasibility, and access/landing context.

### Mineshaft

```text
preferred mode = SUBSURFACE_EMBEDDED
semantic prerequisites = mineral value + historical extraction
```

### Ocean Monument

```text
preferred mode = detached/submerged water-volume realization
future prerequisite = ocean-island waterbody support
```

## Structure population provenance

Structure-associated mobs must be classified separately from ambient hostile pressure:

```text
STRUCTURE
TRIAL
FACTION
BOSS
```

A deliberately populated dungeon or fortress may be dense without increasing ambient wilderness monster density.

## Determinism and ownership

All structure intent and reservation decisions must remain deterministic from stable seed/context inputs.

Reservations and realized structures must retain unambiguous physical ownership so vertically stacked islands or BASE_WORLD geometry never become accidental support/occupancy partners.

## Open implementation questions

1. Exact neutral representation for 3-D occupancy envelopes.
2. Whether structure reservations live in `skyforge-world` or a higher world-planning package.
3. How native/modded structures expose required excavation without per-ID hacks.
4. How roads/bridges/docks become settlement/network components.
5. How detached structures interact with Distant Horizons and Sable/Aeronautics render/physics objects.
6. How Stronghold topology is preserved if final world layout differs radically from the vanilla Overworld.
7. How structure-seeded island reservations interact with authorship overlap admission and support certificates.
8. How rare exposed Ancient Cities are selected without compromising ordinary Ancient City availability.

## Acceptance principle

A future implementation should be considered successful when:

> A previously unseen ordinary modded structure can benefit from generic surface/embedded support where its geometry exposes enough information, while progression-critical and exceptional structures can request stronger neutral terrain contracts without contaminating the backend-neutral world model with Minecraft structure identities.
