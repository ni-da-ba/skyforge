# Hydrology reset tranche F3E — terminal-component completeness

**Status:** admission evidence under issue #1084  
**Depends on:** F3D ordinary-span requalification, F3B confluence compatibility, F3C CASCADE compatibility  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

Local compatibility is necessary but not sufficient for a realizable channel network.

F3E evaluates each complete semantic drainage component ending at one terminal fate. A terminal
component is admitted only when every upstream reach and every explicit transition that can affect
its shared hydraulic state has current authority.

This prevents a solved confluence from laundering a rejected tributary into an apparently valid
downstream channel.

## Reach assembly

Each semantic reach is assembled from:

- all F3D ordinary spans on that reach;
- all F3C CASCADE intervals on that reach;
- F3B confluence state at either endpoint when present.

A reach is `QUALIFIED` only if:

- every ordinary span is `SOLVED_QUALIFIED`;
- every owned CASCADE interval is `SOLVED`;
- every incident confluence is `SOLVED`;
- no finite transition ownership has consumed the ordinary reach without leaving independently
  qualified ordinary geometry.

Any physical rejection, numerical failure, or deferred transition propagates fail-closed.

## Terminal component

For each semantic channel terminal, F3E walks every upstream reach exactly once.

The component status is the most severe constituent state:

1. `QUALIFIED`;
2. `TERMINAL_DEFERRED`;
3. `TRANSITION_DEFERRED`;
4. `PHYSICAL_REJECTION`;
5. `NUMERICAL_FAILURE`.

The ordering is diagnostic severity, not permission to partially realize a component.

### Terminal fate authority

`EDGE_OUTLET` is the only currently admitted terminal fate.

The following remain `TERMINAL_DEFERRED`:

- `RETAINED_OPEN_WATER`;
- `RETAINED_WETLAND`;
- `UNRESOLVED`.

Therefore lake-609 remains intentionally incomplete until retained-basin production authority is
earned.

## First fixed-corpus result

The first complete-component corpus contains a narrow positive admission set.

In namespace 8/81, island key 77 has two complete one-reach EDGE_OUTLET components that are fully
`QUALIFIED`:

- reach 709→559 / terminal 559;
- reach 1742→1842 / terminal 1842.

These components contain no unresolved transition ownership and their ordinary spans independently
solve and re-pass D2. They are the first fixed specimens eligible to feed a later terrain-delta
candidate.

The fixed negative/deferred anchors remain important:

- primary-287 / terminal 1758 is `PHYSICAL_REJECTION`; all four ordinary spans around its three
  solved internal CASCADE intervals are infeasible;
- confluence-632 has qualified incoming reaches 759→710 and 1088→710, but its EDGE_OUTLET component
  ending at 225 remains `TRANSITION_DEFERRED` because the downstream CASCADE touches the
  confluence-owned transition boundary;
- lake-609 / terminal 1397 is not admissible: the terminal fate is `RETAINED_OPEN_WATER`, the
  component contains transition deferrals, and reach 1140→897 retains a physical rejection;
- unresolved terminal fates and boundary-coupled CASCADE-only reaches remain transition-deferred.

Therefore F3E authorizes **component selection only**. It does not authorize terrain mutation. A
terrain tranche must emit exactly zero delta for every component whose F3E status is not
`QUALIFIED`.

## Hard invariants

1. every reach feeding a terminal appears exactly once in that terminal component;
2. a rejected tributary rejects the complete terminal component even if the shared confluence solved;
3. a deferred transition prevents complete-component admission;
4. retained/unresolved terminal fate prevents complete-component admission;
5. no missing ordinary geometry is interpreted as success;
6. no F3E status mutates terrain;
7. no F3E status authorizes Minecraft realization.

## Next boundary

F3E identifies which complete components, if any, are mathematically admissible under current
authority.

Only after a fixed corpus demonstrates `QUALIFIED` complete components should a later tranche
construct a terrain-delta candidate. That terrain tranche must still preserve:

- explicit transition ownership;
- zero delta for all rejected/deferred components;
- deterministic, backend-independent realization;
- quantitative acceptance before Minecraft visual review.
