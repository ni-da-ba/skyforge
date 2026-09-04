# AUTH-0019 — Coherent Hydrologic Realization Composition

AUTH-0019 propagates the accepted AUTH-0018 coherent visible-channel skeleton through the downstream river-dependent authorship layers without invalidating the historical AUTH-0011 through AUTH-0017 evidence.

## Dependency

~~~text
AUTH-0005 physical watershed routing
    -> raw visible channel candidates / profiles
    -> AUTH-0018 coherent visible-channel skeleton
    -> AUTH-0019 coherent riparian semantics
    -> coherent interior drop selection
    -> coherent terrain influence
    -> coherent coarse terrain shaping
    -> coherent continuous hydrologic terrain
    -> coherent naturalized centerlines
~~~

Standing-water footprints, standing-water margins, and physical edge-outlet hydrology remain downstream of the unchanged watershed and are not erased by visible-channel pruning.

## Historical evidence preservation

AUTH-0019 does not silently change the meaning of the accepted AUTH-0011 through AUTH-0017 planners.

Those default planners continue to expose the complete pre-coherence visible-channel diagnostics used by their accepted evidence corpora.

Instead, each river-dependent planner now also accepts an explicit channel-profile subset. AUTH-0019 composes those parameterized entry points with SkyIslandCoherentChannelPlanner.

This preserves reproducibility while giving later backends and authored systems one explicit current coherent composition.

## Coherent realization bundle

SkyIslandCoherentHydrologicRealizationPlanner produces one internally consistent bundle containing:

- the AUTH-0018 coherent channel components;
- riparian corridor cells derived only from retained reaches;
- interior waterfall/cascade candidates derived only from retained reaches;
- terrain-response influence derived only from retained reaches plus watershed-derived edge falls;
- the resulting coarse adjusted terrain surface;
- continuous interpolation of that coherent surface;
- AUTH-0017-style naturalized paths for retained reaches only.

## Edge outlets

EDGE_FALL events remain watershed-derived.

A small drainage path may fail promotion into a visible authored river yet still discharge over an island edge. AUTH-0019 therefore does not delete physically meaningful edge-outlet semantics merely because the visible-channel component was suppressed.

This keeps the distinction between physical drainage and visible river authorship explicit.

## Strong regression boundary

For representative islands where AUTH-0018 retained every channel component (77, 118, 241, and 83), the coherent downstream composition must be exactly equal to the historical raw-stage outputs.

Only islands with component pruning should change downstream.

## Evidence

The authorship-coherent-hydrologic-realization-v1 corpus renders:

- RAW TERRAIN — AUTH-0016 terrain driven by the complete pre-coherence visible network;
- COHERENT TERRAIN — terrain after AUTH-0018 pruning is propagated through riparian/drop/influence/surface/continuous layers;
- MIGRATION DELTA — coherent minus raw terrain, orange where redundant channel incision is removed/restored upward and blue where the coherent composition is lower.

Raw channels are shown in gray and coherent naturalized channels in blue.

The ordinary unpruned representatives should have a blank migration-delta panel. Keys 512 and 811 should lose terrain support associated with the rejected rake components.

## Acceptance gate

Reject AUTH-0019 if:

- any unpruned representative changes downstream;
- coherent riparian cells reference a suppressed channel reach;
- coherent interior drop events reference a suppressed reach;
- retained standing-water ownership changes;
- coherent terrain still contains the dominant 512/811 rake scars;
- migration produces broad island-wide elevation changes unrelated to suppressed drainage;
- continuous interpolation no longer reproduces the coherent coarse anchors exactly.

## Roadmap boundary

Acceptance of AUTH-0019 closes the first island-local hydrology tranche at the neutral-authoring level.

Further hydrology work should be driven by a new requirement rather than incremental tuning. The next major authorship fronts are expected to be irregular terrain/domain naturalization, geological/subsurface authorship, cave/interior semantics, richer ecological spatial structure, and later cluster/province composition.

Minecraft fluid/block realization of authored rivers and retained water remains downstream of this neutral model.
