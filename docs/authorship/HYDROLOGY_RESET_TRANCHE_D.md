# Hydrology reset tranche D — continuous centerline and hard geomorphic qualification

**Status:** dependent implementation candidate under issue #1084  
**Depends on:** Tranche C / PR #1089  
**Minecraft changes:** none  
**Terrain mutation:** none

## Purpose

Tranches A-C establish semantic macro reaches, shared physical junctions, a fine terrain-aware route,
and candidate longitudinal hydraulics. Tranche D adds the missing boundary between a *candidate* and
a landform that Skyforge is actually willing to author.

The rule is fail-closed:

```text
candidate corridor
    -> bounded continuous centerline
    -> hydraulic geometry
    -> objective geomorphic qualification
    -> ACCEPT or REJECT
```

A rejected reach is not excavated. It must be rerouted/refined, assigned an explicit alternate
hydrologic fate, or rejected upstream.

## Qualification substrate

Qualification is measured against `SkyIslandPreHydrologicTerrainField`. Legacy coarse hydrologic
terrain adjustments are excluded from both route plausibility and excavation accounting. This keeps
the reset from receiving credit for valleys or basin shaping created by the retired architecture.

## Continuous centerline

The fine-lattice least-cost route from Tranches A-B is a search result, not literal river geometry.

Tranche D regularizes that route with bounded Chaikin subdivision and uniform resampling. Smoothing
is subordinate to the route authority:

- source/confluence/terminal endpoints remain exact;
- the smoothed line stays in a bounded tube around the accepted search route;
- a smoothed point may not climb materially above the terrain at its nearest accepted corridor point;
- a smoothed point may not leave the authored island interior;
- local smoothing that violates those rules falls back to the accepted corridor projection.

This prevents A* grid turns from becoming authored meanders while also preventing a decorative spline
from cutting across a ridge.

## World-unit qualification

Vertical hydrology values are normalized potentials. Geomorphic qualification converts them to
authored world units through `descriptor.reliefBudget()` before comparing vertical and horizontal
geometry.

The first hard envelope evaluates:

- ridge occupancy;
- maximum centerline lowering as a fraction of total island relief;
- incision relative to full bankfull width;
- water depth relative to full bankfull width;
- implied lateral bed-to-bank grade;
- longitudinal free-surface grade;
- curvature radius relative to bankfull width;
- normalized excavation-volume proxy;
- maximum raw-terrain ascent relative to bankfull width.

These are geometry constraints, not Minecraft-block constraints.

## Initial safety envelope

The current constants are deliberately conservative **starting bounds**, not empirical claims about
all terrestrial rivers and not values tuned to make the current proving ground pass.

Global limits:

- ridge-sample fraction: `<= 0.08`;
- maximum centerline lowering: `<= 0.18 * island relief budget`;
- water-depth/full-bankfull-width: `<= 0.35`;
- normalized bankfull excavation proxy: `<= 0.10`;
- maximum raw terrain ascent/full-bankfull-width: `<= 0.30`.

Profile-sensitive limits:

| profile | incision/full width | implied lateral grade | longitudinal grade | minimum curvature radius/full width |
|---|---:|---:|---:|---:|
| ALLUVIAL | 0.55 | 0.85 | 0.08 | 1.50 |
| INCISED | 1.10 | 1.50 | 0.20 | 1.00 |
| CASCADE | 1.60 | 2.20 | 0.65 | 0.70 |

If a macro reach contains multiple profile classes, the strictest applicable bound governs. This
avoids using a short cascade classification as permission for an otherwise alluvial reach to become
a canyon.

These limits are intended to reject obvious quarry/trench behavior while we collect a broader
accepted/rejected corpus. Future calibration must be justified from representative Skyforge terrain
and intended visual scale; thresholds must not be relaxed merely to make a failing specimen pass.

## Excavation proxy

Before any terrain mutation, Tranche D estimates bankfull cut volume by integrating a triangular
cross-section proxy:

```text
dV ~= halfWidth * requiredCenterlineLowering * ds
```

and normalizes it by a reach-scale prism based on path length, full bankfull width, and island relief.

This is not a sediment-transport model. It is a guardrail against hiding a severe route mismatch
inside many individually bounded samples.

## Acceptance semantics

A qualification report contains explicit failure kinds. Machine tests prove that a deliberately
narrow/deep synthetic trench is rejected for centerline lowering, incision/width, and lateral grade.

Current real proving-ground reaches are **not required to pass yet**. A failure is useful evidence for
the next candidate-selection loop. No threshold may be silently tuned around key 287.

## Next required layer

After this tranche is mechanically sound:

1. use qualification feedback to request alternate route/node candidates rather than accepting the
   first least-cost route;
2. implement continuous retained-basin/waterline geometry;
3. combine accepted river, basin, junction, and drop geometry into one backend-neutral
   hydromorphology field;
4. only then design Minecraft discretization with quantization-scale correction.

The backend remains forbidden from rescuing a rejected candidate.
