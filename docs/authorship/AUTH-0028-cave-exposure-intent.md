# AUTH-0028 — Cave Exposure Intent

AUTH-0028 authors the first sparse semantic relationship between an existing cave system and the island exterior.

It does not cut an entrance corridor, alter AUTH-0025 geometry, modify the AUTH-0026 continuous cave field, or realize Minecraft blocks.

## Dependency

~~~text
AUTH-0022 geology
    -> AUTH-0023 geological regions
    -> AUTH-0024 cave topology
    -> AUTH-0025 cave geometry
    -> AUTH-0026 continuous cave field
    -> AUTH-0027 physical realization transform
    -> AUTH-0028 cave exposure intent
        -> sealed system
        -> upper-surface opening intent
        -> underside opening intent
    -> future boundary-connection geometry
    -> backend realization
~~~

## Why exposure is separate from cave existence

A geologically coherent cave system does not imply an exterior entrance.

AUTH-0028 therefore begins from the already-accepted cave geometry and asks whether erosion, weakness, fracture structure, hydrology, morphology, and actual cave-to-boundary proximity justify one exterior opening.

A system may remain sealed.

That is a valid authored result.

## Sparse first generation

AUTH-0028 allows **at most one exterior exposure intent per cave system**.

This prevents the first exterior-connectivity layer from turning islands into perforated shells.

Multiple entrances, shafts, windows, collapse mouths, and secondary vents remain downstream detail.

## Candidate geometry

For each chamber and sampled passage point, AUTH-0028 evaluates the nearest existing cave boundary toward:

- semantic depth 0: upper surface;
- semantic depth 1: underside.

The cave-side anchor lies on the existing AUTH-0025 primitive thickness.

The corresponding exterior boundary anchor initially preserves x/z and projects only in semantic depth.

This vertical projection is an intent anchor, not the future entrance centerline.

A later geometry milestone may steer the actual connection through geology and terrain.

## Support model

Exposure scoring is deterministic and explainable.

### Geometric proximity

The semantic gap between existing cave geometry and the candidate exterior boundary is the dominant support.

Caves already approaching an exterior boundary are easier to expose than deeply sealed systems.

### Fracture support

AUTH-0022 fracture intensity is sampled at the cave-side anchor.

Strong fracture structure makes natural opening or collapse more plausible.

### Weathering support

Weathering support combines:

- erosion maturity;
- inverse rock competence.

Older, weaker island material is more likely to expose pre-existing internal voids.

### Hydrologic support

Upper-surface exposure also considers:

- local AUTH-0022 groundwater potential;
- whether AUTH-0024 characterizes the cave system as water-influenced.

This allows drainage/sink-style exposure to emerge without declaring every wet cave open.

### Exterior exposure

Underside opening additionally responds to the island descriptor's exposure tendency.

Highly exposed suspended material is more plausible as an underside failure/opening.

## Morphology bias

Morphology contributes only a small prior after geology and proximity.

First-generation biases are intentionally modest:

- BASIN favors upper-surface exposure;
- TABLELAND mildly favors upper-surface exposure;
- SPINE favors underside exposure;
- LOBED mildly permits either, with a slight underside preference;
- MASSIF is nearly neutral.

Morphology cannot rescue a deeply buried, geologically unsupported opening by itself.

## Acceptance

The higher-scoring upper/underside candidate is accepted only when its total score crosses the first-generation exposure threshold.

If neither side crosses the threshold, the cave system remains sealed.

AUTH-0028 does not force every positive cave corpus specimen to expose itself.

## Evidence

The `authorship-cave-exposure-intent-v1` corpus reuses the accepted cave representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- CAVE SECTION — existing AUTH-0025 chamber/passage geometry;
- UPPER EXPOSURE — candidate/accepted upper-surface intent;
- UNDERSIDE EXPOSURE — candidate/accepted underside intent;
- DECISION — final sparse exposure result.

Evidence lines between cave anchor and exterior boundary are schematic intent paths, not authored entrance geometry.

`manifest.csv` records:

- cave-system count;
- exposed/sealed-system counts;
- upper/underside intent counts;
- accepted side;
- accepted score;
- semantic gap;
- proximity/fracture/weathering/hydrologic support.

## Acceptance gate

Reject AUTH-0028 if:

- cave-free controls receive exposure intents;
- more than one first-generation opening is accepted per cave system;
- opening intents originate outside existing AUTH-0025 cave geometry;
- morphology bias dominates cave-to-boundary proximity and geology;
- every cave system is mechanically forced open;
- no positive cave system can ever become exposed;
- underside and upper-surface decisions are indistinguishable;
- the evidence lines are treated as literal tunnel geometry;
- Minecraft, BlockPos, carving, or backend write concepts enter the model.

## Parallel implementation boundary

The Minecraft implementation lane may proceed immediately with a **sealed authored-cave realization proof** using AUTH-0026 and AUTH-0027.

AUTH-0028 is not required for that proof.

The implementation proof should not fabricate entrances. Until a future accepted exposure-geometry milestone reaches the backend, AUTH-0026 cave volume remains the only authored carve volume.

## Next milestone

If AUTH-0028 is accepted, the next cave-authorship milestone should convert an accepted exposure intent into a geology-steered boundary connection while preserving the sparse one-opening policy.

That geometry should then compose into AUTH-0026 as an additional authored cave primitive, after which Minecraft can realize exterior-connected caves through the same AUTH-0027 transform and exact-volume fence.
