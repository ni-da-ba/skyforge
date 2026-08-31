# Skyforge Minecraft Adapter Boundary Proposal

## Purpose

This note defines the first concrete game-backend boundary that should follow accepted terrain semantics. It deliberately avoids selecting a Minecraft version or loader. The purpose is to preserve dependency direction and identify what a Minecraft-facing module must consume from Skyforge.

The recovered Aetherial Islands / Aetherial Companion lineage is now an explicit design input. The predecessor proved that floating-island integration creates real compatibility problems around structures, ores, biomes, registries, spawn search and optional mods. The lesson is **not** to port Companion's masks/gates/reducers. It is to preserve the questions and compatibility knowledge while letting modern Skyforge upstream semantics eliminate the corrective machinery.

See `docs/history/Aetherial_Islands_Companion_Lessons_Learned.md`.

## Dependency boundary

A future Minecraft-facing module may depend on Skyforge core modules:

```text
skyforge-kernel
      ^
skyforge-model
      ^
skyforge-recipes
      ^
skyforge-world
      ^
skyforge-minecraft / skyforge-neoforge / other adapter
```

No core module may import Minecraft or loader APIs.

## Runtime responsibility split

### Skyforge owns

- deterministic world-volume identity;
- island/group/archipelago planning;
- compiled procedural density geometry;
- conservative spatial world queries;
- structural terrain semantics;
- deterministic realization independent of chunk order;
- semantic composition relationships that define where islands may exist;
- future suitability fields only where they describe backend-independent validity of Skyforge geometry.

### Minecraft adapter owns

- translation from Minecraft chunk/section coordinates into Skyforge `WorldBounds` or equivalent sampled region;
- access to native biome/environment information;
- concrete block-state selection;
- chunk/section writes;
- Minecraft lifecycle hooks;
- registry integration and runtime registry probing;
- backend-specific caching and scheduling;
- compatibility with Minecraft terrain/feature stages;
- optional-mod adapters and graceful fallback behavior;
- projection of backend-native feature/structure requests onto Skyforge suitability information.

## Intended chunk path

Conceptually:

```text
Minecraft requests or prepares a chunk
        -> adapter computes world-space query bounds
        -> SkyIslandWorldCatalog.query(bounds)
        -> adapter samples only relevant Skyforge volumes
        -> SkyIslandTerrainSemantic at block/sample positions
        -> adapter combines semantic with native biome/environment context
        -> adapter selects BlockState
        -> adapter writes the backend chunk/section
```

The adapter must not rerun group or archipelago planning for every chunk request.

The density/geometry bridge must remain a **translation layer**, not a second terrain generator. Backend-only morphology is out of scope unless a future backend-specific visual effect is explicitly separated from authoritative world geometry.

## World-plan lifetime

A Minecraft world should obtain or derive a deterministic Skyforge regional/world plan from the world seed or an explicitly persisted Skyforge plan.

The intended lifecycle is:

```text
world seed / Skyforge configuration
        -> deterministic regional planning
        -> compiled SkyIslandWorldCatalog
        -> long-lived world-generation context
        -> repeated chunk queries
```

The first adapter proof may keep the catalog in memory. Persistence and cross-session cache formats are deferred.

## Chunk independence

SF-IMP-0028 already establishes that disjoint tile ownership plus conservative closed catalog queries can reproduce monolithic geometry exactly. The Minecraft adapter should preserve the same property:

- chunk generation order must not change geometry;
- neighboring chunks may conservatively consider the same island;
- each backend voxel/block position is written by the chunk/section that owns that position;
- no geometry seam may appear because an island crosses a chunk boundary.

## Native biome participation

The adapter should prefer Minecraft's native biome/environment system where that system already owns the concept.

Example mapping:

```text
Skyforge SURFACE_MANTLE
+ native biome/environment
-> backend surface palette

Skyforge UNDERSIDE_SHELL
+ native biome/environment if relevant
-> backend underside palette
```

Skyforge should not duplicate Minecraft climate variables solely to drive block selection.

The predecessor contained a wrapper `BiomeSource` to adapt Minecraft biome output. That precedent is useful as evidence that backend biome adaptation can preserve native Minecraft contracts. However, Skyforge must **not** use biome suppression/remapping to manufacture geography. Geography is already authoritative upstream.

The older idea `Skyforge climate/ecology -> Minecraft biome` remains only a possible future projection if genuinely backend-independent environment semantics become necessary. It is not a current required descriptor model; ADR-0034 remains authoritative.

## Suitability before broad feature integration

Aetherial Companion demonstrated that biome validity alone is insufficient for floating-world feature or structure validity. A tree, village, mineshaft, stronghold or other feature may need geometric conditions that a normal biome check cannot express.

Before broad vanilla/modded feature integration, the adapter should establish a minimal suitability seam over authoritative Skyforge geometry.

Candidate backend-neutral inputs include:

- terrain thickness;
- distance to upper surface;
- distance to underside;
- surface availability;
- surface-mantle/soil depth where defined;
- slope or exposure once continuous derivative/normal support exists;
- stable island/group identity where relevant.

The eventual query shape may resemble:

```text
structureSuitability(type, position)
featureSuitability(type, position)
surfaceSuitability(type, position)
terrainThickness(position)
distanceToSurface(position)
distanceToUnderside(position)
```

Do not implement all of these for the first chunk-writing proof. The first adapter should prove geometry/material realization; suitability becomes mandatory before enabling broad structure/feature behavior that assumes ordinary terrestrial volume.

The old predecessor term `Authority` is not required. The useful abstraction is eligibility/suitability.

## Registry compatibility layer

The recovered Companion environment notes document a concrete failure mode: optional Terralith/Biomes O' Plenty cleanup resources were withheld because registry IDs had changed, and stale static JSON could create unbound registry references even when the corresponding mod was installed.

Therefore optional integration must follow:

```text
mod presence
    -> inspect active registry / tags / capabilities
    -> validate the exact target key
    -> enable compatibility adapter
```

Not:

```text
modLoaded(X)
    -> assume historical registry key Y exists
```

The adapter should eventually make it possible to ask at initialization:

- is registry key X present?
- is tag Y present?
- is mod Z loaded?
- does configured/placed feature Q exist?
- is a requested compatibility capability available?

Optional integration must fail gracefully rather than preventing world load.

## Optional-mod adapters

Compatibility code should remain separated from core materialization, conceptually:

```text
CreateCompatibilityAdapter
TerralithCompatibilityAdapter
BiomesOPlentyCompatibilityAdapter
LithostitchedCompatibilityAdapter
...
```

Names and exact module shape are deferred, but the dependency principle is fixed: optional-mod knowledge belongs on the backend side.

The later recovered Companion build adds guarded Create zinc resources and overrides for Create's native ore biome modifiers. This proves the compatibility problem existed in practice, but Skyforge must **not** reproduce that workaround automatically. Preserve the reason; re-audit the actual Skyforge world before applying a patch.

## Public-control conditioning

The predecessor exposed useful high-level controls but several were difficult to tune because candidate rejection, retries, overlap limits, vertical clearance and corrective density systems interacted nonlinearly.

Skyforge public composition controls should therefore be treated as measurable contracts.

Where appropriate they should be:

- monotonic: increasing requested island density should reliably increase observed density;
- locally influential: changing stacking policy should not silently redefine unrelated island size or ocean prevalence;
- statistically testable over deterministic corpora.

Future acceptance can compare requested and observed values, for example:

```text
requested mean group membership -> observed mean within tolerance
requested stacking rate         -> observed rate within tolerance
requested regional occurrence   -> observed occurrence within tolerance
```

Retry counts may remain internal implementation details. They must not become meaningful world-design parameters.

## Debug and provenance are part of the adapter

Companion's extensive audit/report tooling is a precedent worth preserving.

During development, the Minecraft bridge should make it possible to explain a position approximately as:

```text
Minecraft block / biome / feature decision
    -> backend rule or compatibility adapter
    -> Skyforge terrain semantic / suitability result
    -> SkyIslandWorldVolumeId
    -> compiled field / recipe provenance
    -> descriptor / composition ancestor
```

Long-term diagnostic commands may include ideas such as:

```text
skyforge inspect position
skyforge inspect island
skyforge inspect group
skyforge explain density
skyforge explain material
skyforge explain biome
skyforge explain structure-suitability
skyforge validate composition
skyforge profile backend
```

Exact command design is deferred. Explainability is not.

## First adapter proof scope

The first concrete proof should be intentionally narrow:

1. one deterministic Skyforge regional plan;
2. one small set of chunk-like backend regions;
3. AIR versus structural terrain semantics mapped to a minimal block palette;
4. native biome/environment lookup permitted but optional for the first geometry-write proof;
5. exact deterministic chunk results across generation-order permutations;
6. seam-crossing islands remain continuous;
7. no planner invocation in the per-block hot path;
8. no Minecraft imports outside the adapter module;
9. provenance can identify the Skyforge world volume responsible for a written solid sample;
10. compatibility hooks are structured so optional integrations can later probe active registry state rather than rely on static assumptions.

## Non-goals for the first adapter

Do not require the first proof to solve:

- final biome compatibility;
- vegetation/features;
- caves or structures;
- full suitability taxonomy;
- fluids;
- lighting integration;
- multiplayer scheduling policy;
- final caching strategy;
- every Minecraft worldgen stage;
- every loader/version combination;
- historical Companion compatibility hacks unless reproduced by current Skyforge evidence.

The adapter exists first to prove the boundary and obtain real performance/integration evidence.

## Module naming

Do not choose `skyforge-neoforge`, `skyforge-fabric`, or another loader-specific module name until the concrete integration target is confirmed. A temporary `skyforge-minecraft-reference` or similar module may be useful if a loader-neutral Minecraft API seam is genuinely practical, but this should not be assumed in advance.

## Evidence required before production direction

The first adapter should report:

- chunk dimensions and sampled vertical range;
- candidate island count per chunk;
- density/semantic evaluation counts;
- block-write count;
- cold and warm generation latency;
- cache footprint;
- deterministic output identity;
- seam checks across neighboring chunks;
- provenance identity for sampled/written terrain;
- any compatibility fallback or missing-registry decision encountered during initialization.

Those measurements feed the live/preload/hybrid decision rather than being treated as incidental profiling.

## Inheritance rule

The predecessor lessons are summarized by:

> World rules control occurrence and relationships.  
> Descriptors control identity.  
> Recipes control form.  
> Fields control realization.  
> The Minecraft backend controls representation.

Skyforge should never need to negotiate with its own terrain generator the way Aetherial Companion had to negotiate with Aetherial Islands.
