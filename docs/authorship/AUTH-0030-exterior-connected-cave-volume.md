# AUTH-0030 — Exterior-Connected Continuous Cave Volume

AUTH-0030 composes accepted AUTH-0029 exposure-connection geometry into the continuous authored cave-volume field.

It completes the Skyforge-native representation of an exterior-connected cave while leaving backend realization to the implementation lane.

## Dependency

~~~text
AUTH-0026 sealed continuous cave volume
    + AUTH-0029 accepted exposure connection geometry
    -> AUTH-0030 constructive union
        -> unchanged sealed cave volume
        -> connected authored entrance/underside corridor
        -> positive void reaches accepted semantic boundary
        -> explicit base-vs-exposure provenance
    -> AUTH-0027 physical realization transform
    -> backend exact-volume realization
~~~

## Base field remains authoritative

AUTH-0030 does not rewrite SkyIslandCaveVolumeField.

Instead, SkyIslandExteriorConnectedCaveVolumeField owns:

- one unchanged AUTH-0026 base field;
- one AUTH-0029 exposure-geometry plan;
- the constructive maximum of those signed-clearance contributions.

This gives sealed systems a strong compatibility invariant:

> If an island has no accepted AUTH-0029 connection, every AUTH-0030 signed-clearance sample is bit-for-bit identical to AUTH-0026.

## Sign convention

AUTH-0030 inherits AUTH-0026 exactly:

- positive: authored cave void;
- zero: authored cave boundary;
- negative: exterior.

Connection geometry uses the same anisotropic elliptic-capsule interpretation as AUTH-0026 passages.

The union is:

~~~text
connectedCave(point)
    = max(
        AUTH-0026 base cave clearance,
        every AUTH-0029 connection clearance)
~~~

No smoothing or backend voxelization is introduced.

## Connection continuity

Each AUTH-0029 corridor begins at an accepted cave-side primitive boundary with nonzero thickness.

The connection capsule therefore overlaps the interior of the source AUTH-0025 chamber/passage rather than merely touching it at an infinitesimal point.

AUTH-0030 requires:

- every sampled connection centerline point to remain positive;
- dense interpolation between connection samples to remain positive;
- a point displaced from the cave anchor into the source primitive to be positive in both AUTH-0026 and AUTH-0030.

This proves volumetric continuity between the established cave and its exterior connection.

## Boundary reach

The final AUTH-0029 mouth center lies exactly at:

- semantic depth 0 for UPPER_SURFACE exposure;
- semantic depth 1 for UNDERSIDE exposure.

AUTH-0030 must be positive at that exact boundary point.

The field does not extrapolate semantic depth outside [0, 1].

The connection's finite mouth radius therefore creates an authored opening *at* the accepted island boundary without inventing semantic space above the upper surface or below the underside.

## Opposite-boundary containment

An upper-surface connection must not create EXPOSURE_CONNECTION provenance on the underside.

An underside connection must not create EXPOSURE_CONNECTION provenance on the upper surface.

Dense boundary sampling verifies this independently of the mouth anchor.

Existing AUTH-0026 cave volume may still contribute its own base field where appropriate; AUTH-0030 specifically forbids the new connection primitive from leaking to the wrong exterior side.

## Naturalized horizontal ownership

AUTH-0030 preserves the AUTH-0020/AUTH-0021 horizontal ownership fence.

A position outside current naturalized island ownership cannot become positive authored cave void even if a mathematical connection capsule would overlap it.

This remains independent from the later Minecraft exact-volume write fence.

## Provenance

SkyIslandExteriorConnectedCaveVolumeSample records whether the winning field contribution comes from:

- NONE;
- BASE_CAVE;
- EXPOSURE_CONNECTION.

BASE_CAVE provenance preserves the AUTH-0026:

- system id;
- CHAMBER/PASSAGE kind;
- primitive id.

EXPOSURE_CONNECTION provenance records:

- system id;
- AUTH-0028 source primitive kind/id;
- accepted exposure side.

On exact signed-clearance ties, established BASE_CAVE provenance wins.

This avoids gratuitously relabeling existing cave interior merely because the new connection overlaps it.

## Physical-Y realization

SkyIslandRealizedExteriorConnectedCaveVolumeField composes AUTH-0030 through the accepted AUTH-0027 vertical-column transform.

The adapter performs:

~~~text
island-local x/z + physical Y
    -> authoritative physical column
    -> semantic depth
    -> AUTH-0030 sample
~~~

It performs no Minecraft carving and introduces no backend coordinate types.

## Evidence

The `authorship-exterior-connected-cave-volume-v1` corpus reuses the canonical cave representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- BASE SECTION — AUTH-0026 maximum cave occupancy through z;
- CONNECTED SECTION — AUTH-0030 maximum occupancy through z;
- ADDED TOP-DOWN — positive void contributed specifically by AUTH-0029 connection geometry;
- PROVENANCE — connected top-down positive samples separated into base cave and exposure connection.

`manifest.csv` records:

- cave systems;
- exposure connections;
- base positive sample count;
- connected positive sample count;
- added positive sample count;
- sampled added-volume fraction;
- connected occupied-component count;
- upper/underside exposure-boundary positive sample count.

## Acceptance gate

Reject AUTH-0030 if:

- any sealed/no-connection representative differs from AUTH-0026;
- a connection centerline contains a non-positive gap;
- connection volume fails to overlap established cave volume;
- the accepted mouth boundary point is not positive;
- exposure provenance reaches the opposite exterior boundary;
- positive volume escapes naturalized horizontal ownership;
- connection union fragments an existing one-system cave;
- added volume is disproportionately large relative to the existing cave/interior;
- provenance relabels base cave on exact ties;
- AUTH-0027 physical realization changes semantic results;
- Minecraft, BlockPos, chunk, or NeoForge concepts enter the world layer.

## Parallel implementation boundary

SF-IMP-0065 is the correct parallel proof for sealed AUTH-0026/AUTH-0027 cave realization in Minecraft.

AUTH-0030 must not be consumed by that proof until it is separately accepted and merged.

After AUTH-0030 acceptance, the implementation lane may add exterior connections as a small follow-on capability using the same:

- semantic-depth transform;
- exact-volume ownership fence;
- deterministic carve realization.

No new backend cave-authorship logic should be required.

## Next Skyforge milestone

AUTH-0030 closes the first coherent cave-authorship tranche:

~~~text
geology
-> mesoscale regions
-> cave topology
-> cave geometry
-> sealed continuous volume
-> semantic/physical transform
-> exposure intent
-> exposure geometry
-> exterior-connected continuous volume
~~~

The next Skyforge-native authorship milestone should move away from cave connectivity itself and author **subsurface material/geological realization semantics**: how geological structure controls rock/material tendencies, cave-wall character, wet/dry subsurface regions, and later resource/mineral signals without collapsing those concepts into Minecraft blocks.
