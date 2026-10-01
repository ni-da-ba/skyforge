# AUTH-0031 — Continuous Subsurface Material Character

AUTH-0031 begins the post-cave material-authorship tranche.

It derives backend-neutral material character from the geological and cave systems already accepted in AUTH-0022 through AUTH-0030.

It does not select named rock types, ores, Minecraft blocks, backend palettes, or concrete resource drops.

## Dependency

~~~text
SkyIslandDescriptor
    -> AUTH-0022 continuous geology
    -> AUTH-0023 mesoscale geological systems
    -> AUTH-0024..AUTH-0030 cave authorship
    -> AUTH-0031 continuous subsurface material character
        -> matrix integrity
        -> alteration / weathering
        -> saturation
        -> mineralization tendency
        -> cave-wall alteration
    -> future material domains / resource signals
    -> backend realization
~~~

## Why this layer exists

AUTH-0022 describes geological conditions.

AUTH-0031 describes the material-realization consequences of those conditions.

The distinction is deliberate.

For example:

- bulk competence is a geological tendency;
- matrix integrity is the corresponding realization tendency after fracture and alteration are considered;
- groundwater potential is a hydrogeological tendency;
- saturation is the corresponding material-state tendency;
- fracture plus groundwater plus compatible host competence can support mineralization without naming a specific mineral species.

The backend should eventually decide how these semantics become concrete materials.

## Material ownership

A semantic subsurface position can be in one of three states.

### Unowned

The horizontal point lies outside current naturalized island ownership.

The sample has no material meaning and all tendencies are zero.

### Authored cave void

The point lies inside the accepted AUTH-0030 continuous cave volume.

The position remains part of the island's authored subsurface coordinate space, but no host material is present there.

The sample is therefore:

~~~text
owned = true
materialPresent = false
all material tendencies = 0
~~~

### Host material

The position is owned and outside authored cave void.

All AUTH-0031 material tendencies are defined there.

## Matrix integrity

Matrix integrity represents how coherent the realized host material should tend to be.

It derives from:

- AUTH-0022 bulk competence;
- inverse fracture intensity;
- inverse alteration.

It is not a hardness scale tied to a backend.

A backend may later use it to choose among stronger/weaker material representations, fracture density, erosion response, or visual texture.

## Alteration / weathering

Alteration combines:

- descriptor erosion maturity;
- shallow exposure and edge weathering;
- fracture intensity;
- groundwater potential;
- a subordinate broad chemical-affinity field.

The shallow component weakens with semantic depth.

Fractures and groundwater allow some alteration to remain meaningful deeper in the island.

## Saturation

Saturation is the material-state consequence of hydrogeological conditions.

It derives from:

- groundwater potential;
- connected permeability;
- semantic depth;
- protection from direct exterior exposure.

It does not imply literal fluid occupancy at every high-saturation sample.

Future realizers may use it for damp wall character, seepage eligibility, wet material families, or underground ecology.

## Mineralization tendency

AUTH-0031 does not introduce random ore noise.

Mineralization tendency is supported by:

- fracture / groundwater interaction;
- a moderate host-competence band;
- a broad mid-depth preference;
- alteration;
- subordinate coherent chemical affinity.

The chemical-affinity term is intentionally weak enough that it cannot create mineralization independently of geological support.

AUTH-0031 therefore means:

> this host material is relatively suitable for geological deposition / mineral concentration

not:

> place a specific ore block here.

Named mineral families and resource realization remain downstream.

## Cave-wall alteration

Cave-wall alteration is defined only in solid material immediately outside accepted AUTH-0030 cave void.

It uses the normalized signed-clearance field from the authored cave boundary.

Support increases near the wall and combines:

- existing alteration;
- saturation;
- fracture intensity.

Inside cave void, material is absent and cave-wall alteration is zero.

Far from an authored cave boundary, cave-wall alteration decays to zero.

This creates a semantic distinction between ordinary deep host material and material exposed along cave walls without adding a backend decoration rule.

## Coherent chemical affinity

AUTH-0031 adds one broad deterministic three-dimensional chemical-affinity field.

It is subordinate to established geology.

The field uses island identity and rotated/sheared smooth value noise so it does not expose rectangular lattice axes.

Its only first-generation role is to modulate alteration and mineralization where existing geological common causes already support those processes.

## Evidence

The `authorship-subsurface-material-character-v1` corpus uses the canonical six cave/geology representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Every specimen renders x/depth sections through island-local z=0:

- MATRIX INTEGRITY;
- ALTERATION;
- SATURATION;
- MINERALIZATION;
- CAVE-WALL ALTERATION.

White is outside naturalized ownership.

Black is authored AUTH-0030 cave void and therefore contains no material.

`manifest.csv` records:

- material-present sample count;
- authored-void sample count;
- mean matrix integrity;
- mean alteration;
- mean saturation;
- mean mineralization tendency;
- mean and maximum cave-wall alteration.

## Acceptance gate

Reject AUTH-0031 if:

- material meaning exists outside naturalized ownership;
- authored cave void retains host-material tendencies;
- rock competence stops influencing matrix integrity;
- hydrological potential stops influencing saturation;
- mineralization becomes independent high-frequency noise;
- cave-wall alteration appears on cave-free controls;
- cave-wall alteration fills cave void rather than host material;
- every island has nearly identical material character;
- material fields expose obvious rectangular lattice artifacts;
- named Minecraft blocks, ores, registry keys, biome IDs, or backend APIs enter the world layer.

## Parallel implementation boundary

SF-IMP-0065 may continue realizing merged AUTH-0026/AUTH-0027 sealed cave volume in Minecraft.

AUTH-0031 does not change cave geometry or the backend carve contract.

A future backend material adapter may eventually query AUTH-0031 after the material semantics themselves are accepted and promoted into a stable realization seam.

## Next milestone

If AUTH-0031 is accepted, the next Skyforge-native milestone should convert these continuous material tendencies into connected mesoscale **material domains**.

That should likely include:

- coherent altered/weathered zones;
- wet/saturated host-material bodies;
- fracture-controlled mineralization bodies;
- structural/fabric domains suitable for later strata or rock-family realization.

Those regions should remain semantic and overlapping where appropriate.

Named rock/mineral taxonomies and Minecraft block palettes should remain downstream until the material-domain behavior is visually and structurally convincing.
