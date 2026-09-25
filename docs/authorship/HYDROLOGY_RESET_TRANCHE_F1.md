# Hydrology reset tranche F1 — qualified fluvial realization

**Status:** first terrain-mutating reset implementation under issue #1084  
**Depends on:** F0 realization contract and D2 river qualification  
**Minecraft changes:** none  
**Retained-basin terrain mutation:** blocked pending calibrated E2 policy and later basin realization

## Purpose

F1 is the first layer in the reset allowed to change the authored continuous terrain surface.

D2 acceptance is necessary but not sufficient for terrain authority. F1 realizes only reaches whose
remaining geometry is an ordinary reach problem. Accepted reaches that touch an unresolved confluence
or contain an unresolved CASCADE segment are explicitly deferred and contribute zero terrain delta.

The executable authority split is therefore:

```text
D2 rejected reach
    -> rejected evidence
    -> zero terrain delta

D2 accepted + unresolved junction/drop transition
    -> deferred evidence
    -> zero terrain delta

D2 accepted ordinary reach
    -> continuous cross-section realization
    -> post-realization D2 qualification
    -> terrain authority
```

This prevents a local priority rule from silently standing in for missing transition mathematics.

## Continuous ordinary-reach cross-section

For each realized C1 centerline sample, F1 interpolates the solved hydraulic:

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
- CASCADE: 1.8 × as a field primitive only; CASCADE reaches are not granted terrain authority until
  explicit drop/cascade transition ownership exists.

F1 never raises terrain. If the existing terrain lies below the proposed section, it is left
unchanged rather than converted into a synthetic levee.

## Transition deferral

A D2-accepted reach is withheld from terrain authority when:

- either endpoint is a semantic CONFLUENCE node; or
- any constituent profile is CASCADE.

Confluences require one node-owned transition surface that reconciles the incident widths, depths,
bed elevations, approach directions, and shared water-surface datum over a bounded transition region.
Exact endpoint coincidence alone is not sufficient.

Cascades/drops likewise require transition-owned longitudinal geometry. An ordinary cross-section
shape must not be used to disguise an unresolved hydraulic discontinuity.

Deferral is not geomorphic rejection. The plan records the accepted qualification and explicit
deferral reason separately so later transition tranches can consume it without weakening D2.

## Overlap composition

Within the currently authorized ordinary-reach subset, independent deepest-cut composition remains
forbidden.

The field retains deterministic semantic selection for a queried point, but production authority may
not be expanded to unresolved feature overlaps. Any overlap requiring a confluence, drop, basin, or
other transition must first be owned by that transition model.

## Output

The backend-neutral detailed sample exposes:

- original terrain potential;
- target terrain potential;
- signed delta;
- wet/not-wet state for the bankfull hydraulic domain;
- solved water-surface potential;
- zone: channel bed / bankfull / valley recovery / unaffected;
- stable semantic reach/profile provenance.

The realization plan separately exposes:

- realized D2 qualifications;
- D2-accepted but transition-deferred qualifications and reasons;
- D2-rejected qualifications.

Minecraft block IDs and voxel coordinates remain forbidden.

## Post-realization qualification

F1 re-runs D1 diagnostics and D2 qualification against the realized continuous field for every reach
that actually received terrain authority.

If the transform creates a new D2 violation, planning fails closed.

## Current proving-ground implication

The current D2 envelope rejects primary key 287 for extreme pre-authoring excavation/lateral
pressure. F1 therefore intentionally leaves key 287 unchanged.

The fixed corpus also contains accepted reaches whose confluence/cascade transitions are not yet
owned. Those reaches now remain explicitly deferred instead of being carved with a generic
winner-takes-overlap rule.

That is correct behavior, not a visual success condition. The next transition tranche must establish
node-owned confluence and drop geometry before those reaches can contribute to the next Minecraft
human gate.

## Deferred beyond F1

- node-owned confluence transition geometry and compatibility diagnostics;
- explicit cascade/drop transition geometry;
- expanded retained-water corpus and frozen E2 production policy;
- retained lake/pond bathymetry and littoral recovery;
- river/lake transition composition;
- Minecraft discretization and native/modded fleshing.
