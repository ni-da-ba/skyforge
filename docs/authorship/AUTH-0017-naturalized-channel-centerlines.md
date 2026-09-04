# AUTH-0017 — Naturalized Channel Centerlines

AUTH-0017 adds a sub-grid geometric centerline layer over the accepted semantic channel graph.

## Dependency

~~~text
accepted watershed routing
    -> accepted channel graph / hierarchy
    -> accepted channel geomorphic profiles
    -> AUTH-0017 naturalized centerline geometry
    -> later channel-aware terrain support / literal backend realization
~~~

## Purpose

AUTH-0016 made hydrologically shaped elevation continuous, which exposed the next upstream limitation clearly: accepted channel centerlines still follow one-cell 49x49 routing segments and therefore retain straight, diagonal, and stair-stepped geometry.

AUTH-0017 addresses that geometric presentation without rewriting the hydrologic topology.

## Topology preservation

The accepted graph remains authoritative.

AUTH-0017 does not:

- move a graph node;
- change source or downstream cell identity;
- add or remove a reach;
- change stream order, role, discharge, or profile kind;
- reroute a watershed;
- alter a retained waterbody.

Every naturalized path begins and ends at the exact accepted channel-segment positions. Therefore confluences remain connected by construction.

## Shared node tangents

A canonical tangent is computed at every accepted graph node.

- headwaters point toward their accepted downstream reach;
- terminal nodes inherit the strongest accepted incoming direction;
- internal nodes use the direction from the strongest incoming reach through the accepted downstream reach.

The strongest incoming reach is selected by relative discharge, with stable cell-index tie-breaking.

Adjacent splines therefore meet at the same graph node with a coherent dominant-flow direction rather than reproducing the raw lattice turn literally.

## Profile-dependent sub-grid bend

Each segment is sampled as a cubic curve between its fixed endpoints.

Control freedom depends on accepted geomorphic profile:

- ALLUVIAL receives the most smoothing freedom;
- INCISED receives less;
- CASCADE remains comparatively direct.

A small deterministic interior lateral bend is added between endpoints. Its amplitude also depends on bankfull-width and gradient potentials.

The bend is seeded only from stable island authorship identity and accepted source/downstream cell identity.

## Bounded geometry

Every sample is constrained relative to its original accepted segment chord.

Maximum deviation is 0.42 of one AUTH-0005 planning-cell spacing, multiplied by a zero-at-endpoints envelope. The planner therefore cannot move a naturalized reach arbitrarily far from the hydrologic corridor that justified it.

This is a centerline geometry layer, not a new routing solver.

## Evidence

The deterministic authorship-naturalized-channels-v1 corpus reuses keys 77, 118, 241, 512, 811, and 83.

Each island panel uses the accepted AUTH-0016 continuous terrain as a shared background:

- COARSE — original accepted straight channel segments in red;
- NATURALIZED — AUTH-0017 sub-grid paths in blue;
- OVERLAY — original gray plus naturalized blue, with black accepted graph nodes.

manifest.csv summarizes path counts, profile mix, maximum normalized deviation, and path-length ratios.

paths.csv records per-reach geometry metrics.

points.csv records every generated sub-grid point.

## Acceptance gate

Reject AUTH-0017 if:

- any accepted graph endpoint moves;
- confluences disconnect;
- source/downstream identity or profile semantics change;
- path deviation exceeds the bounded corridor;
- splines loop, kink severely, or become longer than their geomorphic role justifies;
- cascade networks become implausibly meandering;
- naturalization is visually indistinguishable from the coarse lattice;
- the overlay suggests crossings or artifacts introduced solely by the spline layer.

## Deferred

AUTH-0017 does not yet:

- reroute fragmented channel topology;
- relax or move the accepted graph nodes;
- author true meander migration;
- compute bank polygons;
- modify AUTH-0016 terrain shaping around the new centerlines;
- author literal river widths/depths;
- create Minecraft fluids or blocks.

The larger-scale location of each channel remains inherited from the accepted watershed graph.
