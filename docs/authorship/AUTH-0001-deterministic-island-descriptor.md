# AUTH-0001 — Deterministic island descriptor

**Status:** implementation candidate

## Objective

Establish the first placement-free, backend-neutral semantic description of a Skyforge-authored island.

This milestone answers only the question:

> Given a stable hierarchical island identity, which persistent island-scale environmental and morphological tendencies does Skyforge author?

It does **not** yet generate local terrain fields, ecological regions, hydrology, Minecraft biomes, blocks, structures, or backend coordinates.

## Relationship to existing descriptors

The repository already contains `IslandDescriptor` and `SkyIslandVolumeDescriptor`. Those types are retained because accepted historical recipes and evidence depend on them. They include realized-world placement state such as X/Z centers and, for suspended volumes, suspension elevation.

AUTH-0001 therefore introduces a new authorship boundary instead of changing those compatibility surfaces:

```text
SkyIslandIdentity
        |
        v
SkyIslandDescriptorGenerator
        |
        v
SkyIslandDescriptor
        |
        +--> future island-local semantic fields
        +--> future Province/Cluster intersections
        +--> future backend placement / volume planning
```

`SkyIslandIdentity` contains only stable hierarchy keys:

```text
world seed -> province key -> cluster key -> island key
```

It deliberately contains no world coordinates.

## Descriptor schema 1

The first authored descriptor contains:

- `authorshipSeed` — stable domain-separated seed for downstream authored systems;
- `morphologyFamily` — the existing semantic primary-morphology vocabulary;
- `nominalRadius` — horizontal spatial budget;
- `reliefBudget` — vertical relief budget;
- `rockCompetence` — resistance of the island's material to deformation/erosion;
- `permeability` — tendency for water to infiltrate rather than remain at the surface;
- `temperatureTendency` — broad island-local thermal prior;
- `moistureTendency` — broad island-local moisture prior;
- `exposureTendency` — broad exposure/wind/weathering prior;
- `erosionMaturity` — relative maturity of erosional modification;
- `hydrologicalPotential` — derived summary of moisture, permeability, relief, and exposure;
- `ecologicalPotential` — derived summary of moisture, thermal suitability, exposure, and erosion maturity.

The geological vocabulary is intentionally continuous in this milestone. AUTH-0001 does not yet introduce a large categorical rock or climate taxonomy.

## Initial authorship ranges

The schema validator requires positive finite spatial budgets and normalized `[0, 1]` semantic tendencies.

The initial generator policy emits:

| Property | Range |
| --- | ---: |
| nominal radius | 96–640 Skyforge world units |
| relief budget | 24–192 Skyforge world units |
| all continuous semantic tendencies | `[0, 1]` |

The generator biases the radius distribution toward smaller islands while retaining substantial large-island outcomes. Morphology contributes to the relief prior, but local terrain realization remains deferred.

## Causal policy

Hydrological and ecological potential are not independent random samples.

Hydrological potential is derived from:

- moisture tendency;
- inverse permeability;
- relief;
- inverse exposure.

Ecological potential is derived from:

- moisture tendency;
- thermal suitability;
- inverse exposure;
- inverse erosion maturity.

This is the first explicit common-cause relationship in the native-authorship lane. Later semantic fields should consume these descriptors rather than invent unrelated local noise distributions.

## Determinism and hierarchy

Authorship uses pure 64-bit mixing with explicit domain separation. There is no mutable random-number generator and no global state.

The seed derivation order is:

```text
world -> province -> cluster -> island
```

All four stable identity components influence the resulting descriptor. The hierarchy shape is intentionally preserved so later Province- and Cluster-level semantic descriptors can become additional causes without changing island identity or backend placement contracts.

## Diagnostics

`SkyIslandDescriptorJson` emits canonical deterministic JSON using hexadecimal integer and floating-point representations. The output is intended as the first machine-readable native-authorship diagnostic.

The canonical descriptor diagnostic contains no `centerX`, `centerZ`, suspension elevation, Minecraft registry IDs, block state, NeoForge type, graph node, or signal algorithm.

AUTH-0002 should extend diagnostics from this scalar summary into island-local semantic field maps.

## Acceptance gates

AUTH-0001 is acceptable when repository CI proves:

1. identical `SkyIslandIdentity` values produce equal descriptors and byte-stable canonical JSON;
2. changes to world, Province, Cluster, and island identity each affect authorship;
3. a representative identity corpus differentiates authorship seeds;
4. generated descriptors remain within declared ranges;
5. descriptor validation rejects invalid scale and normalized values;
6. canonical diagnostics contain semantic state but no backend placement coordinates;
7. the repository-wide backend-independence gate still rejects Minecraft/NeoForge imports from `skyforge-model` and `skyforge-world`;
8. existing accepted Minecraft-integration behavior remains untouched because this branch adds only backend-neutral authorship code and documentation.

## Next milestone

AUTH-0002 should introduce a reusable deterministic island-local semantic field abstraction and begin with morphology/elevation tendency, temperature, moisture, exposure, and interiority/edge influence. Those fields should be derived from this descriptor and evaluated in island-relative coordinates rather than backend coordinates.
