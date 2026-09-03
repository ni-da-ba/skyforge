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

Thresholds are normalized against the island-local maximum accumulation rather than block counts or fixed discharge units. This preserves the same authored interpretation across islands of different nominal radii.

## Deferred

- channel width and hierarchy;
- lake/wetland extent and water level;
- waterfall drop geometry;
- erosion feedback;
- irregular island-domain naturalization;
- backend realization.

## Evidence

The deterministic `authorship-hydrologic-features-v1` corpus renders channel candidates in cyan, retained-water anchors in blue, and waterfall/outlet anchors in orange. Its purpose is to verify coherent network extraction and expose upstream radial artifacts rather than conceal them.
