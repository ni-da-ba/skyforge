# AUTH-0027 — Semantic-Depth Physical Realization Transform

AUTH-0027 establishes the backend-neutral transform between authored semantic subsurface depth and an island's authoritative realized upper/underside surfaces.

It does not define a new island underside, does not emit Minecraft block coordinates, and does not alter AUTH-0026 cave authorship.

## Dependency

~~~text
AUTH-0026 continuous cave-volume field
    -> AUTH-0027 semantic-depth realization transform
        -> authoritative physical upper/underside column
        -> semantic depth <-> physical Y
        -> physical-Y cave query view
    -> future backend coordinate placement
    -> exact-volume carve realization
~~~

## Why this layer exists

AUTH-0022 deliberately defined depthFraction independently of any backend vertical coordinate.

That separation allowed geology and cave authorship to mature without inheriting Minecraft's altitude or chunk-column assumptions.

AUTH-0027 now defines the explicit transform needed to realize those semantics physically.

The transform is column-relative.

For one realized solid column:

~~~text
upperY     = authoritative upper surface
undersideY = authoritative underside surface
thickness  = upperY - undersideY

physicalY(depth) = upperY - depth * thickness

depth(physicalY) = (upperY - physicalY) / thickness
~~~

Therefore:

- semantic depth 0 maps exactly to the upper surface;
- semantic depth 1 maps exactly to the underside;
- intermediate depths deform continuously with local island thickness.

## No new underside model

AUTH-0027 does not invent physical island thickness.

SkyIslandVerticalColumnField is an abstraction over whatever physical realization is authoritative.

The first production adapter, SkyIslandCompiledVolumeColumnField, reads the already-compiled upper and underside procedural graphs from CompiledSkyIslandVolume.

This preserves the established physical-volume ownership model.

## Horizontal coordinates

AUTH-0027 intentionally transforms only the vertical coordinate.

Authored x/z remain island-local.

A backend remains responsible for placing those local horizontal coordinates into world space through translation and, if later supported, rotation.

SkyIslandCompiledVolumeColumnField already performs the existing descriptor center translation when querying legacy/current compiled physical surfaces.

## Positive physical columns only

A SkyIslandVerticalColumn exists only when upperY is strictly greater than undersideY.

Collapsed or inverted surface pairs do not define semantic interior depth and therefore return no column.

Physical points above upperY or below undersideY do not map into semantic subsurface space.

## Cave realization view

SkyIslandRealizedCaveVolumeField composes:

- one authoritative AUTH-0026 semantic cave field;
- one physical vertical-column field;
- the AUTH-0027 transform.

Its behavior is deliberately minimal:

~~~text
island-local x/z + physical Y
    -> physical column
    -> semantic depthFraction
    -> AUTH-0026 sample
~~~

It does not bend, resize, connect, delete, or add caves.

The physical and semantic realizations must share the same nominal horizontal radius. A mismatch fails immediately rather than silently stretching authored caves.

## Existing Minecraft compatibility

The current NeoForge implementation already supplies compatible realization seams.

SkyforgeVerticalPlacementFrame can map native underground-placement heights into exact owner-column support.

SkyforgeCarverVerticalFrame maps native carver vertical samples into caller-selected island interior intervals.

SkyforgeCarverExecutionStage authorizes direct carver writes only against owner solid and rejects foreign stacked-volume solid.

AUTH-0027 therefore does not require a second Minecraft ownership model.

A later Minecraft authored-cave adapter can perform:

~~~text
BlockPos
    -> active SkyIslandWorldVolume
    -> world x/z -> island-local x/z
    -> authoritative compiled upper/underside column
    -> physical Y -> semantic depthFraction
    -> AUTH-0026 cave field
    -> positive?
    -> existing exact-volume write fence
~~~

## Tests

AUTH-0027 requires:

- exact semantic -> physical -> semantic round-trip over variable-thickness columns;
- exact depth-0/depth-1 endpoint behavior;
- no semantic mapping for physical air above/below the island;
- no mapping where physical upper/underside surfaces do not form a positive column;
- existing CompiledSkyIslandVolume upper/underside surfaces to match the new column adapter exactly;
- AUTH-0026 chamber and passage samples to remain equivalent after realization round-trip;
- nominal-radius mismatch between cave authorship and physical realization to fail explicitly.

## Evidence

The `authorship-semantic-depth-realization-v1` corpus uses compiled schema-2 physical-volume fixtures spanning the accepted morphology families.

Each specimen renders:

- CENTER DEPTH BANDS — upper/underside plus semantic 25/50/75% layers at z=0;
- OFFSET DEPTH BANDS — the same transform at an offset z section;
- MID-DEPTH PLAN — physical Y corresponding to semantic depth 0.5 across x/z;
- COLUMN THICKNESS — physical upper-to-underside thickness across x/z.

The fixtures validate the transform, not a new authorship-to-volume descriptor mapping.

`manifest.csv` records positive-column counts, minimum/maximum thickness, maximum semantic-depth round-trip error, and maximum disagreement with SkyIslandTerrainInterpreter surfaces.

## Acceptance gate

Reject AUTH-0027 if:

- semantic depth bands cross or leave their physical upper/underside surfaces;
- round-trip depth error is materially nonzero;
- the transform uses a bounding-box Y interval instead of local column surfaces;
- collapsed/non-owned columns acquire semantic interior;
- AUTH-0026 samples change after semantic/physical round-trip;
- the compiled-volume adapter disagrees with existing authoritative physical surfaces;
- horizontal scale mismatch is silently accepted;
- Minecraft/NeoForge types enter the world-layer transform.

## Next boundary

If AUTH-0027 is accepted, the cave authorship lane has a complete backend-neutral handoff:

~~~text
meaning
-> geology
-> cave regions
-> cave topology
-> cave geometry
-> continuous cave field
-> physical column realization
~~~

The next appropriate milestone is an implementation proof that samples AUTH-0026 through AUTH-0027 inside one exact Minecraft Skyforge volume and realizes authored cave air through the existing mutation fence.

That proof should remain small and evidence-heavy before replacing or suppressing any broader native cave behavior.
