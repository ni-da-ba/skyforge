# Hydrology reset tranche A — semantic reaches and terrain-aware candidate routing

**Status:** implementation candidate under issue #1084  
**Layer:** backend-neutral Authorship/world mathematics  
**Minecraft changes:** none

## Purpose

The first post-H6 implementation tranche changes the authority boundary before changing rendered
terrain.

The old chain represented nearly every accepted 49x49 watershed edge as a physical reach. Even when
later naturalization moved samples between those nodes, every intermediate coarse node remained a
mandatory waypoint. That made a routing-resolution artifact capable of forcing physical excavation.

Tranche A separates:

```
semantic drainage graph
    -> semantic macro reach
    -> terrain-aware fine candidate route
    -> later hydraulic/geomorphic qualification
```

No candidate route is yet allowed to carve terrain or become Minecraft water.

## Semantic macro reaches

A macro reach begins or ends only at a hydrologically meaningful graph event:

- source/headwater entry;
- confluence;
- terminal/outlet.

A chain of ordinary nodes with exactly one incoming and one outgoing channel is collapsed into one
semantic reach.

The old coarse positions are retained as a guidance polyline. They constrain where a solution may
search, but they are not hard physical waypoints.

This preserves accepted catchment connectivity and discharge topology while removing the accidental
claim that the watershed planning lattice already knows the final river centerline.

## Fine-route search

The candidate route solver performs deterministic bounded least-cost search on a finer lattice within
a corridor around the semantic guidance polyline.

For candidate path `P = {p_i}`, transition cost is presently of the form:

```text
J(P) = sum_i [
    w_L * ||p_i - p_(i-1)|| / delta
  + w_U * max(0, z_i - z_(i-1))
  + w_R * R_i
  + w_Z * z_i
  + w_D * (d_i / W)^2
  + w_E * E_i
]
```

Where:

- `delta` is fine search spacing;
- `z_i` is uncarved authored terrain elevation;
- `R_i` is local ridge/TPI penalty;
- `d_i` is distance from coarse semantic guidance;
- `W` is the allowed corridor half-width;
- `E_i` penalizes very low island interiority.

All terms are non-negative so the A* heuristic remains admissible.

The solver accepts bounded endpoint **regions**, not exact coarse-cell coordinates. This is required
for later joint source/confluence/terminal placement.

## Why this differs from H2/H6

The archived H2 terrain-aware corridor solver still operated one coarse watershed edge at a time and
preserved both endpoints of every such edge. It could choose a better curve inside that individual
edge but could not route around a ridge whose bad geometry spanned several coarse cells.

Tranche A searches across a macro reach. Ordinary per-cell nodes are guidance only.

This is still not the final network solver. Independent candidate routes are not yet assembled into
shared confluence geometry. The primitive is deliberately isolated until the shared-junction and
longitudinal-hydraulic solve is specified.

## Mathematical / industry basis

This design follows established terrain-analysis principles without pretending that Skyforge is a
civil-engineering simulator:

1. **Priority-Flood remains useful for depression spill levels, catchment connectivity, and retained
   basin semantics.** Barnes, Lehman & Mulla's Priority-Flood work treats depression filling /
   watershed labeling as a DEM preprocessing/topology problem; the flood discovery tree is not
   promoted here into final physical river geometry.
2. **Terrain flow direction should be tied to downslope terrain, not queue ancestry.** Tarboton's
   D-infinity method derives flow direction from steepest descent on local terrain facets. Skyforge
   does not copy D-infinity literally in this tranche, but uses the same separation between drainage
   topology and physical terrain direction.
3. **Prefer lower-impact correction over brute-force excavation.** Least-cost depression-breaching
   methods explicitly minimize required lowering and commonly impose maximum breach cost/length.
   Skyforge carries that principle forward: a route that needs excessive terrain modification will
   later fail geomorphic qualification rather than being rescued by the backend.
4. **Hydraulic geometry comes after routing.** Width/depth scaling will use discharge-dependent power
   relationships in the Leopold-Maddock form, calibrated dimensionlessly for Skyforge rather than
   importing real-world units.

References:

- Barnes, R., Lehman, C., & Mulla, D. (2014), *Priority-Flood: An Optimal Depression-Filling and
  Watershed-Labeling Algorithm for Digital Elevation Models*, Computers & Geosciences 62.
- Tarboton, D. G. (1997), *A New Method for the Determination of Flow Directions and Contributing
  Areas in Grid Digital Elevation Models*, Water Resources Research 33(2).
- Lindsay, J. B. & Dhun, K. (2015), least-cost depression breaching; represented in the
  WhiteboxTools hydrological-analysis workflow.
- Leopold, L. B. & Maddock, T. (1953), USGS Professional Paper 252, *The Hydraulic Geometry of
  Stream Channels and Some Physiographic Implications*.

## Acceptance for tranche A

Machine tests must prove:

- deterministic decomposition and routing;
- every retained coarse semantic channel segment belongs to exactly one macro reach;
- macro reaches preserve the accepted directed graph;
- ordinary 1-in/1-out planning nodes do not become macro anchors;
- fine route remains inside its semantic corridor and endpoint regions;
- a synthetic ridge-with-gap fixture routes through the lower-impact gap rather than cutting the
  direct ridge crossing;
- route diagnostics are finite and bounded.

## Explicit non-goals

This tranche does **not** yet:

- publish candidate routes as the production channel network;
- choose a shared physical confluence point;
- solve longitudinal bed/free-surface grade;
- determine bankfull width/depth/cross-section;
- solve retained-water basin contours;
- modify AUTH-0105 terrain;
- change Minecraft realization.

Those are the next layers. This tranche exists to make the first authority transition correct before
any new terrain is carved.
