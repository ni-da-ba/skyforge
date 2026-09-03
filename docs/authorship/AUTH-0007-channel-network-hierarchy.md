# AUTH-0007 — Channel Network Hierarchy

AUTH-0007 promotes selected AUTH-0006 channel corridors into a backend-neutral drainage hierarchy.

## Dependency

```text
semantic geography
    -> local hydrology
    -> watershed topology and accumulation
    -> hydrologic feature selection
    -> channel hierarchy
    -> later channel geometry and terrain realization
```

## Semantics

Each selected channel corridor receives:

- a Strahler-style `streamOrder` derived only from selected upstream topology;
- a semantic role: `HEADWATER`, `TRIBUTARY`, or `TRUNK`;
- `relativeDischarge`, the source cell's accumulation normalized against the island watershed maximum;
- `corridorScale`, a normalized combination of stream order and relative discharge for downstream geometric interpretation.

A headwater has no selected upstream channel. Trunks are resolved as drainage-component main stems: starting from each terminal selected channel, the planner traces upstream through the strongest accumulated-flow predecessor at every confluence. Non-headwater corridors on those main-stem traces are `TRUNK`; other non-headwater branches are `TRIBUTARY`.

This topology-first definition prevents a weaker but independent drainage system from being mislabeled as a tributary merely because another outlet elsewhere on the island carries more water.

These values describe importance and network role. They do not specify block width, water depth, bank material, carving depth, flow velocity, or Minecraft placement.

## Ordering

Stream order follows the standard Strahler recurrence over the selected acyclic channel graph: headwaters begin at order 1; a downstream corridor inherits the maximum upstream order unless at least two upstream corridors share that maximum, in which case it increments by one.

Stream order and main-stem role are related but distinct. Order records branching hierarchy; the main-stem trace identifies the dominant routed continuation of each drainage component.

## Evidence

The deterministic `authorship-channel-hierarchy-v1` corpus reuses the six AUTH-0006 islands so hierarchy can be reviewed on the exact accepted feature networks. The atlas renders:

- light cyan headwaters;
- medium blue tributaries;
- dark blue trunks;
- line width proportional to normalized `corridorScale`;
- retained-water anchors in blue;
- edge discharge/waterfall anchors in orange.

The evidence intentionally preserves upstream radial/domain artifacts. AUTH-0007 classifies the network it receives; it does not conceal or repair upstream geography.

## Deferred

- physical river width, depth, and bank geometry;
- lake/wetland extent and water level;
- waterfall drop geometry;
- erosion feedback into terrain fields;
- irregular island-domain naturalization;
- backend realization.
