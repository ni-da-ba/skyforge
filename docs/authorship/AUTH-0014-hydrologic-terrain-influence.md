# AUTH-0014 — Hydrologic Terrain-Influence Planning

AUTH-0014 converts accepted hydrologic semantics into backend-neutral terrain-response potentials without modifying the authored terrain field itself.

## Dependency

```text
semantic geography
    -> hydrology / watershed topology
    -> channel hierarchy / riparian corridors
    -> channel geomorphic profiles
    -> discrete drop events
    -> hydrologic terrain influence
    -> later terrain-field modification / backend realization
```

## Purpose

AUTH-0012 and AUTH-0013 describe the accepted routed channel network and discrete drop events, but downstream terrain realization still needs to know **how** the surrounding terrain should tend to respond.

AUTH-0014 provides that missing semantic bridge. It does not carve terrain. Instead, each affected coarse watershed cell can carry four normalized response potentials:

- `incisionPotential` — support for lowering/confined channel-bed shaping;
- `depositionPotential` — support for aggradation, bar, or alluvial-bed shaping;
- `floodplainPotential` — support for broader low-gradient lateral terrain adjustment beside a channel;
- `dropShapingPotential` — localized support for waterfall/cascade lip and receiving-zone shaping.

A cell also exposes a deterministic dominant response for diagnostics. The full scalar vector remains authoritative.

## Channel-centerline influence

Accepted AUTH-0012 channel profiles seed centerline influence at their source and downstream watershed cells.

Incision support combines:

- AUTH-0012 incision potential;
- stream-power potential;
- gradient potential.

Deposition support combines:

- low gradient;
- bankfull-width potential;
- relative discharge;
- weak incision support.

This intentionally permits a channel to carry both incision and deposition potential. The planner records competing semantic tendencies rather than forcing one geomorphic label to erase the other.

## Riparian / floodplain influence

AUTH-0011 riparian cells preserve the accepted channel segment that owns each dry transition cell. AUTH-0014 reuses that ownership directly.

Floodplain support combines:

- channel width;
- relative discharge;
- low gradient;
- local retention potential;
- distance from the accepted channel.

Deposition support is propagated into the same dry corridor with distance attenuation. Second-ring coarse cells therefore receive weaker support than first-ring cells.

## Drop shaping

AUTH-0013 discrete drop events seed localized `dropShapingPotential`.

- interior waterfall/cascade events annotate the accepted downstream lip cell;
- immediately adjacent accepted riparian cells may receive attenuated drop-shaping support derived from drop and plunge-pool potentials;
- edge falls annotate the accepted edge-outlet cell but do not invent terrain outside the island domain.

AUTH-0013's spatial event suppression remains authoritative; AUTH-0014 does not create new drop events.

## Ownership precedence

Standing-water semantics retain precedence:

1. AUTH-0009 retained-water footprint;
2. AUTH-0010 standing-water margin;
3. AUTH-0014 channel terrain influence.

Waterbody footprint and margin cells are reserved before hydrologic terrain influence is emitted. This prevents the channel layer from rewriting already accepted standing-water geometry semantics.

When multiple routed reaches influence the same non-reserved cell, AUTH-0014 combines each scalar by **maximum support**, not addition. This prevents confluences and fragmented parallel components from creating artificial over-carving simply because several upstream records overlap.

## Evidence

The deterministic `authorship-hydrologic-terrain-influence-v1` corpus reuses keys 77, 118, 241, 512, 811, and 83.

The atlas renders the dominant response at each influenced cell:

- red — incision;
- gold — deposition;
- green — floodplain;
- magenta — drop shaping.

Color intensity follows the dominant normalized potential. Accepted channel profiles remain visible as thin gray lines and accepted standing water as pale blue cells.

`manifest.csv` summarizes dominant-response counts and maximum scalar potentials. `cells.csv` preserves all four potentials for every influenced cell.

The visual gate should reject the milestone if influence becomes island-wide, if drop shaping detaches from accepted drop events, if floodplain influence blankets steep cascade systems, if standing-water ownership is overwritten, or if converging channels create obviously saturated artifacts.

## Deferred

AUTH-0014 does **not** author:

- modified elevation values;
- literal channel-bed lowering;
- sediment transport simulation;
- physical erosion iteration;
- bank or floodplain polygons;
- river meanders;
- waterfall height or cliff geometry;
- plunge-pool geometry;
- block/metre dimensions;
- world-Y values;
- Minecraft blocks, fluids, biomes, or placed features.

Those remain downstream terrain-realization concerns.
