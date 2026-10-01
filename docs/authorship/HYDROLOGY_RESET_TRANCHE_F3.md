# Hydrology reset tranche F3 — explicit transition ownership topology

**Status:** first transition tranche under issue #1084  
**Depends on:** accepted C3 route-functional correction, regenerated D1/D2/D3/E1 evidence, F2/F2A/F2B/F2C, and F1 terminal-fate hardening  
**Terrain mutation:** none  
**Hydraulic-head authority:** none  
**Minecraft changes:** none

## Purpose

F3 converts the transition deferrals already exposed by F1/F2C into explicit deterministic ownership
objects without yet inventing a transition shape or water-surface solution.

The accepted ordering remains:

```text
C2 geometry
    -> F2B head-independent hydraulic skeleton
    -> F3 explicit transition ownership topology
    -> finite transition geometry + coupled bounded head solve
    -> D2/E2 + transition requalification
    -> terrain authority
```

F3 is deliberately narrower than transition realization. It answers **which semantic feature owns each
non-ordinary boundary and where its exact current C2/F2B boundary state is**.

## Confluences

Every C2 node classified as `CONFLUENCE` becomes exactly one confluence transition site.

The site owns:

- the exact shared C2 node position;
- every incoming reach endpoint state;
- the exactly one outgoing reach endpoint state;
- discharge, bankfull half-width, depth potential, terrain elevation, and reach identity at each
  incident endpoint.

The site is permutation-independent. Incident reaches do not independently own the shared node.

F3 does not yet choose a finite junction radius, blend surface, node head, energy loss, or excavation
shape. Those belong to the next geometry/solve tranche and must reproduce the F3 boundary states.

## Cascade/drop ownership

F3 preserves the existing accepted mapping used by D1/F1/F2C:

```text
profile i of n owns station fraction [i/n, (i+1)/n)
```

Every maximal contiguous run of authored `CASCADE` profiles becomes one cascade transition site.

The site records:

- owning semantic macro reach;
- exact profile-index interval;
- coarse semantic start/end cells of the cascade run;
- exact upstream/downstream station fractions;
- deterministic F2B boundary states interpolated on the unchanged C2 polyline.

Thus D3's authored drop evidence now has an explicit topology owner. This does **not** yet authorize a
signed hydraulic discontinuity or terrain mutation.

## Retained-basin terminals

Terminal fate continues to follow the authoritative watershed graph.

- `EDGE_OUTLET` remains an ordinary free terminal and creates no transition site.
- `RETAINED_OPEN_WATER` creates one basin transition site.
- `RETAINED_WETLAND` creates one wetland/basin transition site.
- `UNRESOLVED` remains an explicit unresolved terminal and receives no geometry authority.

A retained transition site binds the exact incoming channel terminal state to the downstream watershed
fate. It does not imply that E2 production limits are calibrated or that a POND/LAKE may be realized.

## Boundary-state interpolation

When a cascade profile boundary falls between native F2B samples, F3 evaluates the boundary on the
accepted C2 polyline at exact arc length `s = fraction * pathLength`.

Position and accumulated normalized discharge are linearly interpolated between neighboring F2B
samples. Width and depth are then recomputed through the accepted F2B hydraulic calibration from that
interpolated discharge; pre-hydrologic terrain is sampled at the resulting continuous position.

This creates no new route and no new hydraulic calibration.

## Hard invariants

F3 must preserve all of the following:

1. one confluence transition object per semantic confluence node;
2. every confluence incident reach is represented exactly once;
3. every authored CASCADE profile belongs to exactly one maximal cascade transition run;
4. retained/wetland terminal ownership follows watershed fate, never coordinate coincidence;
5. unresolved terminals remain unresolved and receive no transition geometry;
6. transition boundary states lie on the accepted C2 geometry and consume only accepted F2B
   head-independent quantities;
7. no transition object mutates terrain, selects a water-surface head, changes D2/E2 policy, or
   changes Minecraft behavior.

## Current fixed evidence

The focused fixed corpus proves:

- confluence key 632: node 710 owns two incoming and one outgoing boundary;
- primary key 287: all ten authored CASCADE profiles are owned exactly once by contiguous transition
  sites;
- lake key 609: channel terminal 1397 resolves to retained open water / LAKE and becomes a basin
  transition site;
- stress key 512: unresolved terminals 306 and 497 remain explicit unresolved fates rather than being
  treated as basins or free outlets.

## Next tranche

F3A should size finite confluence/cascade transition envelopes from incident hydraulic width/depth,
solve explicit transition boundary variables, and expose transition diagnostics before any new terrain
authority.

Retained-basin geometry remains blocked from production authority until POND/LAKE E2 calibration is
adequate. Basin topology may be carried through the coupled solve, but no uncalibrated basin may be
realized merely because F3 can identify its junction.
