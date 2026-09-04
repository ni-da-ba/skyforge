# AUTH-0026 — Continuous Cave-Volume Field

AUTH-0026 compiles the chamber and passage geometry accepted in AUTH-0025 into one continuous backend-neutral cave-volume query.

This is the first cave-authorship layer designed to be directly consumable by a backend adapter without requiring that adapter to understand cave-system graphs, chamber planning, or geological steering.

## Dependency

~~~text
SkyIslandDescriptor
    -> AUTH-0022 continuous geology
    -> AUTH-0023 mesoscale geological regions
    -> AUTH-0024 cave-system topology
    -> AUTH-0025 chamber/passage geometry
    -> AUTH-0026 continuous cave-volume field
        -> positive authored cave void
        -> zero cave boundary
        -> negative outside cave
        -> primitive/system provenance
    -> future semantic-depth -> physical-volume transform
    -> exact backend carve authorization
~~~

## Sign convention

AUTH-0026 represents **cave void occupancy**, not island solid density.

Its sign convention is therefore explicit:

- positive: inside authored cave void;
- zero: authored cave boundary;
- negative: outside authored cave void.

The returned value is a normalized local clearance and is **not** a physical distance in blocks.

This is deliberately separate from the kernel SignedDensity solid convention, where positive means solid.

## Chamber compilation

Every AUTH-0025 chamber is evaluated as an anisotropic semantic ellipsoid.

Horizontal distance is normalized by the chamber horizontal radius.

Semantic-depth distance is independently normalized by the chamber depth radius.

The chamber contribution is:

~~~text
1 - sqrt(horizontalNormalized^2 + depthNormalized^2)
~~~

so its accepted boundary is exactly the zero set.

## Passage compilation

Every AUTH-0025 passage is compiled as the union of elliptic capsules between consecutive sampled passage points.

Each capsule uses:

- local interpolated horizontal radius;
- local interpolated semantic-depth radius;
- a closest-point solve in the locally scaled x/z/depth coordinate.

This prevents visible or queryable gaps between the 13 authored passage samples while preserving the AUTH-0025 distinction between horizontal scale and semantic-depth scale.

## Union field

The island cave field is the maximum signed clearance from all authored chamber and passage primitives.

This is a constructive union:

~~~text
caveField(point) = max(all chamber clearances, all passage clearances)
~~~

AUTH-0026 does not smooth-union adjacent primitives in its first generation.

The exact chamber/passage boundary remains explainable, and later wall-naturalization can be introduced as a separate authored detail layer if needed.

## Naturalized-domain fence

A horizontally unowned AUTH-0020/AUTH-0021 position cannot become cave void even if an extrapolated primitive would mathematically overlap it.

The field returns outside-cave state for positions beyond current naturalized ownership.

This gives the backend two independent safety layers later:

1. the authored cave field says whether a semantic point is cave void;
2. the implementation exact-volume mutation fence still says whether the corresponding physical block belongs to the active island.

## Provenance

SkyIslandCaveVolumeSample records the winning primitive:

- cave system identifier;
- CHAMBER or PASSAGE;
- source node/link identifier.

Provenance is deterministic under ties: lower system id, then chamber before passage, then lower primitive id.

This is primarily an explainability and diagnostic feature.

A future backend can carve from the signed field alone, while tests and tooling can still explain why a position was considered inside a cave.

## Minecraft translation

AUTH-0026 intentionally stops one layer before Minecraft.

The expected adapter contract is:

~~~text
Minecraft BlockPos
    -> active SkyIslandWorldVolume
    -> world/local horizontal transform
    -> explicit physical-Y -> semantic-depth transform
    -> SkyIslandSubsurfacePosition
    -> AUTH-0026 cave field
    -> if positive:
           exact-volume owner-solid / foreign-solid fence
           backend carve or authored-void realization
~~~

The existing Minecraft implementation already supplies the important containment seams:

- SkyforgeCarverVerticalFrame maps native carver vertical sampling into caller-selected island interior frames;
- SkyforgeCarverExecutionStage authorizes writes only against owner solid and rejects foreign stacked-volume solid.

The principal remaining integration requirement is therefore an explicit, tested mapping between semantic depth and physical island interior Y.

AUTH-0026 does not guess that mapping.

## Evidence

The `authorship-continuous-cave-volume-v1` corpus uses the accepted AUTH-0025 representatives.

Each specimen renders:

- SECTION OCCUPANCY — maximum cave clearance through z for each x/depth point;
- TOP-DOWN OCCUPANCY — maximum cave clearance through depth for each x/z point;
- SECTION CLEARANCE — continuous signed-clearance evidence, not merely binary occupancy;
- SYSTEM PROVENANCE — top-down occupied points colored by winning cave-system identity.

The maximum projections are evidence views only. The field itself remains fully three-dimensional.

`manifest.csv` records:

- cave systems;
- chamber/passages counts;
- sampled 3D cave-volume fraction;
- positive sample count;
- maximum positive clearance;
- minimum exterior clearance near the accepted sampled domain;
- occupied connected-component count on the evidence sampling lattice.

## Acceptance gate

Reject AUTH-0026 if:

- cave-free AUTH-0025 controls contain positive field samples;
- chamber centers or passage centerlines are outside the compiled field;
- visible gaps appear along accepted passages;
- an isolated chamber's zero boundary does not agree with its AUTH-0025 radius;
- positive field samples escape current naturalized ownership;
- sampled cave occupancy becomes implausibly large relative to island interior volume;
- the field fragments one visually continuous cave system into many components at reasonable evidence resolution;
- the field sign convention is confused with solid density;
- backend coordinates, BlockPos, Minecraft Y, or NeoForge types enter the world layer.

## Next milestone

If AUTH-0026 is accepted, the next authorship/integration boundary should be an explicit **semantic-depth physical realization transform**.

That transform should relate:

- authored upper surface;
- authored underside/interior thickness;
- semantic depthFraction;
- exact physical island Y;
- AUTH-0026 cave field sampling.

Once that transform is accepted, a Minecraft integration milestone can consume AUTH-0026 without reinterpreting cave authorship.
