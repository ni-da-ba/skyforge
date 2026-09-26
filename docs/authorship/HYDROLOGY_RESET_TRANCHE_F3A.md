# Hydrology reset tranche F3A — finite transition geometry evidence

**Status:** geometry/evidence tranche under issue #1084  
**Depends on:** accepted C3/F2B/F2C, F1A terminal-fate hardening, and F3 explicit transition ownership  
**Terrain mutation:** none  
**Hydraulic-head authority:** none  
**Minecraft changes:** none

## Purpose

F3 identifies who owns every non-ordinary hydrologic boundary. F3A gives those owners finite,
measurable continuous geometry without yet choosing authoritative water-surface head or terrain
mutation.

The tranche is deliberately diagnostic:

```text
F3 transition owner
    -> F3A finite geometry evidence
    -> coupled transition/head solve
    -> transition diagnostics + D2/E2 requalification
    -> terrain authority
```

A geometry candidate that looks numerically awkward is evidence for changing the transition model or
failing closed. It is not permission to enlarge excavation budgets.

## Confluence finite legs

Each confluence incident reach already meets the exact shared C2 node at an F3 boundary state.

F3A retreats each incident boundary along its unchanged accepted C2 centerline by exactly one local
bankfull half-width measured at the node boundary:

```text
L_leg = w_bankfull,node
```

This **unit-hydraulic-scale hypothesis** is intentionally the smallest dimensionally natural first
candidate. It is not asserted to be a universal river-junction constant and is not frozen production
policy by this tranche.

For every leg F3A records:

- exact F3 node boundary;
- exact finite ordinary/transition boundary sampled on the F2B skeleton;
- arc-length retreat;
- role (incoming/outgoing);
- position, accumulated normalized discharge, width, depth, and pre-hydrologic terrain at the finite
  boundary.

If one local half-width would consume the entire incident reach, planning fails closed. F3A does not
silently clip the transition until it fits.

The next coupled solver must reproduce these finite boundary states or replace this geometry
hypothesis through an explicitly reviewed/calibrated policy.

## Cascade/drop finite intervals

CASCADE geometry does not require a new arbitrary sizing scale.

F3 already partitions each maximal contiguous authored CASCADE profile run. F3A materializes the exact
corresponding interval on the accepted C2 centerline:

- exact upstream boundary;
- exact downstream boundary;
- every native F2B point strictly inside the interval;
- exact arc length equal to the F3 boundary arc-length difference.

This interval is the finite region that may later own an explicit signed head/bed discontinuity.
F3A itself does not choose that discontinuity.

For primary key 287 this preserves all ten authored CASCADE profiles as explicit finite transition
geometry, consistent with D3 evidence that roughly 83.88% of accumulated downhill drop is already
associated with CASCADE profiles.

## Retained-open-water interface evidence

For a terminal whose authoritative watershed fate is `RETAINED_OPEN_WATER`, F3A consumes the current
continuous E0 basin candidate and records:

- exact river terminal boundary;
- exact watershed-terminal basin identity;
- continuous basin water datum;
- deterministic nearest continuous shoreline crossing;
- straight-line terminal-to-nearest-shoreline gap as a diagnostic distance;
- preferred pre-solve river-to-basin datum mismatch in world units.

The nearest shoreline point is **not** a connector-route authority. A later transition route must
still be terrain-aware and coupled to the solved basin datum.

The preferred datum mismatch is also diagnostic rather than a threshold. The coupled F2 transition
solve must use the accepted basin datum as an exact boundary if and only if that basin eventually
receives calibrated E2 production authority.

## Wetlands

Retained wetlands remain explicit F3 transition owners but are carried forward as deferred interfaces.
They are not forced through the open-water basin construction.

## Hard invariants

F3A:

1. never changes C2 geometry;
2. never changes F2B hydraulic calibration;
3. never changes F3 transition ownership;
4. derives confluence extent only from existing local hydraulic width;
5. uses the exact authored CASCADE interval rather than inventing a drop footprint;
6. binds open-water interface evidence to the exact watershed-terminal continuous basin;
7. treats nearest shoreline geometry as evidence, not a physical connector;
8. preserves unresolved terminal fate as an explicit blocker;
9. selects no hydraulic head;
10. contributes zero terrain delta;
11. changes no Minecraft behavior.

## Current evidence implications

The fixed proving grounds now exercise:

- confluence key 632 / node 710: two incoming and one outgoing finite legs;
- primary key 287: all ten authored CASCADE profiles materialized as exact finite intervals;
- lake key 609 / terminal 1397: exact LAKE interface plus continuous shoreline/datum evidence;
- stress key 512: unresolved terminal fates remain blockers.

The lake-609 datum pressure remains evidence that basin coupling is a real boundary-value problem; it
must not be repaired by widening E2 tolerances or backend excavation.

## Stop boundary

F3A does **not** yet authorize:

- a confluence water/bed surface;
- an energy-loss or momentum surrogate;
- signed cascade head discontinuity;
- downstream plunge/recovery terrain;
- a river-to-basin connector route;
- retained basin terrain;
- POND/LAKE E2 production limits;
- Minecraft discretization.

The next numerical tranche should solve bounded transition/head compatibility against these finite
boundaries, beginning with confluence and CASCADE cases. Retained-open-water coupling may participate
only as a constraint/evidence path until the POND/LAKE corpus is adequate to freeze E2 production
policy.
