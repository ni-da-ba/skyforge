# AUTH-0006 — Hydrologic Feature Planning

AUTH-0006 converts watershed topology into backend-neutral semantic feature candidates.

## Dependency

```text
semantic geography
    -> local hydrology
    -> watershed routing and accumulation
    -> hydrologic feature candidates
    -> later terrain realization
```

## Feature classes

- `CHANNEL`: a routed watershed corridor whose accumulated flow is significant relative to the island's strongest drainage path.
- `RETAINED_WATER`: a retained watershed sink suitable for later lake, pond, marsh, or wetland interpretation.
- `EDGE_WATERFALL`: a significant edge outlet suitable for later waterfall or discharge realization.

These are semantic candidates, not Minecraft blocks, river splines, water levels, or final carved geometry.

## Scaling

Channel extraction uses two island-local controls rather than backend units. Accumulation must first be significant relative to the island's strongest drainage path. The resulting candidates are then bounded by a corridor budget derived from the routable watershed domain and the descriptor's hydrological potential. This prevents weakly differentiated watersheds from turning into dense channel carpets while preserving stronger trunks and tributaries across islands of different nominal radii.

The budget is a semantic planning control, not a guarantee of final river count, width, discharge, or Minecraft block coverage.

## Deferred

- channel width and hierarchy;
- lake/wetland extent and water level;
- waterfall drop geometry;
- erosion feedback;
- irregular island-domain naturalization;
- backend realization.

## Evidence

The deterministic `authorship-hydrologic-features-v1` corpus renders routed channel corridors in cyan, retained-water anchors in blue, and waterfall/outlet anchors in orange. Its purpose is to verify coherent network extraction and expose upstream radial artifacts rather than conceal them. The corpus deliberately includes a retained-water example and a previously pathological massif case so density normalization remains visually reviewable.
