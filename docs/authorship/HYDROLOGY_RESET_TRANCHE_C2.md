# Hydrology reset tranche C2 — width-aware continuous centerlines

**Status:** corrective candidate geometry under issue #1084  
**Depends on:** D2 hard-safety qualification  
**Terrain mutation:** none  
**Minecraft changes:** none

## Trigger

Post-C1 D2 qualification rejected every fixed specimen for excessive local curvature on at least one
reach. The D1 metric is approximately:

```text
curvatureWidth = kappa * W ~= W / R
```

where `W` is bankfull width and `R` is local bend radius.

The original C1 regularizer had no knowledge of channel width, so it could create a visually smooth
polyline whose bend radius was still smaller than the physical channel it was supposed to carry.

## Correction

C2 supplies the maximum downstream bankfull width of each semantic reach to continuous-centerline
regularization.

The solver tries bounded Chaikin candidates from two through six iterations. Every candidate still:

- preserves exact shared source/confluence/terminal endpoints;
- remains within the C1 search-path deviation tube;
- is rejected back to the search path when smoothing climbs materially higher terrain;
- remains inside the authored island interiority domain.

For a requested minimum bend radius `R_min = W`, the first candidate satisfying

```text
max(kappa) * R_min <= 1
```

is selected.

If no candidate inside the existing terrain/search authority can satisfy the bound, C2 returns the
least-curved admissible candidate. D2 then rejects it. C2 never expands the route corridor or ignores
terrain merely to force a pass.

## Physical basis

USGS observations commonly place natural meander `R/W` near 2-3. D2's `R/W >= 1` rule is
therefore a deliberately permissive hard-safety floor, not a naturalness target.

## Expected result

C2 should reduce or remove `CURVATURE_TO_WIDTH` failures where the existing semantic corridor has
enough geometric room. Persistent failures identify reaches that require upstream rerouting or
network-node adjustment rather than stronger smoothing.

No terrain or water is authored by this tranche.
