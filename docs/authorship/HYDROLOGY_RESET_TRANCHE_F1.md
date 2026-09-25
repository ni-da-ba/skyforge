# Hydrology reset tranche F1 — qualified fluvial realization

**Status:** first terrain-mutating reset implementation under issue #1084  
**Depends on:** F0 realization contract and D2 river qualification  
**Minecraft changes:** none  
**Retained-basin terrain mutation:** deferred to F2

## Purpose

F1 is the first layer in the reset allowed to change the authored continuous terrain surface.

Only D2-accepted river reaches participate. A rejected reach is retained as qualification evidence but
is completely absent from the realization field.

This makes the fail-closed rule executable:

```text
rejected reach -> zero terrain delta
```

rather than:

```text
rejected reach -> stronger excavation
```

## Continuous cross-section

For each accepted C1 centerline sample, F1 interpolates the solved hydraulic:

- bed potential;
- water-surface potential;
- bankfull half-width;
- discharge;
- local profile kind.

Inside bankfull width, the dry terrain target follows a profile-sensitive continuous section from the
solved bed at centerline to the solved water surface at bankfull edge.

Outside bankfull width, terrain recovers continuously to the untouched pre-hydrologic surface over
the same profile-sensitive valley envelope used by D1 qualification:

- ALLUVIAL: 3.5 × bankfull half-width;
- INCISED: 2.5 ×;
- CASCADE: 1.8 ×.

F1 never raises terrain. If the existing terrain lies below the proposed section, it is left
unchanged rather than converted into a synthetic levee.

## Overlap composition

Accepted reach influences are **not** composed by repeatedly taking the deepest cut.

At each horizontal position, one dominant accepted semantic influence is selected deterministically
using:

1. smallest normalized lateral distance inside that feature's local envelope;
2. larger discharge;
3. stable semantic start/end cell identity.

The selected feature emits one target surface.

Shared confluence coordinates from B remain exact.

## Output

The backend-neutral detailed sample exposes:

- original terrain potential;
- target terrain potential;
- signed delta;
- wet/not-wet state for the bankfull hydraulic domain;
- solved water-surface potential;
- zone: channel bed / bankfull / valley recovery / unaffected;
- stable semantic reach/profile provenance.

Minecraft block IDs and voxel coordinates remain forbidden.

## Post-realization qualification

F1 re-runs D1 diagnostics and D2 qualification against the realized continuous field for every reach
that was accepted pre-realization.

If the actual transform creates a new D2 violation, planning fails closed.

## Current proving-ground implication

The current D2 envelope rejects primary key 287 for extreme pre-authoring excavation/lateral pressure.
F1 therefore intentionally leaves key 287 unchanged.

That is correct behavior, not a visual success condition. A subsequent bounded upstream
retry/refinement tranche must find a qualified route/fate before key 287 can be used for the next
Minecraft human gate.

The accepted tableland/stress controls provide the first terrain-realization fixtures.

## Deferred to F2+

- retained lake/pond bathymetry and littoral recovery;
- river/lake transition composition;
- explicit cascade/drop terrain discontinuities beyond the ordinary cross-section;
- Minecraft discretization and native/modded fleshing.
