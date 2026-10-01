# Hydrology reset tranche D1 — cross-section and excavation diagnostics

**Status:** C3-regenerated diagnostic authority under issue #1084  
**Depends on:** D0 diagnostics and C3 resolution-consistent route functional  
**Terrain mutation:** none  
**Acceptance thresholds:** still not frozen

## Purpose

D0 established a fixed pre-carving diagnostic corpus. D1 adds the cross-section-aware quantities
needed to distinguish a plausible river corridor from the quarry/slot-canyon failure class that
survived the retired H6 machine gates.

D1 remains measurement only. It deliberately does not contain pass/fail thresholds.

## Added metrics

All curvature and cross-section measurements use the accepted continuous centerline rather than the raw search lattice.

After C3, ridge occupancy is the fixed-physical-scale, arc-length-weighted
`ridgeLengthFraction` from `SkyIslandRouteFunctionalDiagnosticsPlanner`. The
`SkyIslandGeomorphicReachDiagnostics.ridgeSampleFraction` accessor is retained for compatibility,
but its value is no longer the legacy vertex-count fraction.

For every hydraulic macro reach, D1 measures:

- maximum lateral recovery grade from the candidate bed to unmodified valley-side terrain;
- maximum bank-containment deficit, in authored world units, where natural bank terrain lies below
  the candidate water surface;
- maximum water-depth to bankfull-width ratio;
- maximum surrounding-relief to valley-width ratio;
- normalized excavation burden;
- approximate excavation-volume proxy in authored world units cubed;
- maximum local curvature multiplied by bankfull width;
- C3 arc-length-weighted ridge occupancy and longitudinal-grade diagnostics in the same reach record.

Vertical normalized potentials are converted into authored world units with the island
`reliefBudget`. Horizontal quantities remain island-local world units.

## Interpretation

These quantities answer different failure questions:

- **lateral recovery grade** — would the channel require quarry-like sidewalls to reconnect to the
  original terrain?
- **containment deficit** — would the proposed water surface sit above the natural bank and require a
  synthetic levee or backend repair?
- **depth/width** — is the channel disproportionately deep for its authored width?
- **relief/valley width** — is the surrounding valley envelope too narrow for the local relief that
  must be recovered?
- **excavation burden / volume** — how much terrain surgery is the candidate asking for?
- **curvature/width** — is the centerline turning too tightly for the physical channel scale?

None is individually a universal natural-river law. Together they define an objective calibration
space for Skyforge hydromorphology.

## C3 ridge-authority correction

C3 explicitly demotes legacy sampled-vertex ridge fraction to compatibility-only evidence. D1/D2
therefore must not use `SkyIslandGeomorphicCandidateRoute.ridgeSampleFraction()` as hard authority.

The post-C3 D1 implementation recomputes route functional diagnostics at the fixed physical ridge
probe scale and stores `ridgeLengthFraction` in the D1 reach record. The specimen-level geomorphic
corpus derives its maximum ridge fraction from those same D1 reach diagnostics.

This correction changes no route, terrain, or threshold. It changes only which already-defined C3
ridge metric is allowed to feed D2.

## Post-C3 regenerated fixed-corpus result

The corrected arc-length ridge authority changes several numeric ridge fractions but does not require
a D2 threshold change.

Representative fixed outcomes are:

- primary-287 reach 1090→1758 remains rejected for non-ridge quarry/containment/burden failures;
- control-241 reach 671→479 is explicitly rejected for `RIDGE_OCCUPANCY` at
  `ridgeLengthFraction = 0.375` against the MIXED 0.35 limit;
- all fixed control-118 reaches remain accepted;
- all fixed stress-512 reaches remain accepted, including a CASCADE reach at ridge fraction 0.375
  under the CASCADE 0.40 limit;
- pure-INCISED key 2084 reach 700→600 remains accepted at zero ridge occupancy;
- pure-INCISED key 2093 reach 708→559 remains rejected solely for `BANK_CONTAINMENT`, with zero
  ridge occupancy.

Thus the authority correction is not a calibration retune. It replaces the invalid sample-count
metric with C3's defined physical/arc-length metric and preserves fail-closed classifications.

## Corpus rule

The fixed D0 corpus is extended with D1 metrics before any first hard envelope is frozen.

D2 may only introduce a threshold when:

1. the metric corresponds to a documented physical failure mode;
2. the fixed corpus distribution is available;
3. synthetic fixtures demonstrate both sides of the proposed boundary;
4. the threshold is not selected merely to preserve every existing specimen.

Some current specimens are expected to fail.

## Determinism scope

D1 metrics are strict Skyforge-authored deterministic state.

Minecraft-native decorative/ecological variation remains outside this contract unless it changes
authored geometry, hydraulic connectivity, persistence, ownership, traversability, collision, or
other materially player-relevant structure.

## Still deferred

D1 does not:

- author a terrain cross-section;
- modify terrain;
- solve retained-water basin contours;
- define Minecraft blocks/materials;
- freeze rejection thresholds.

The next step is evidence inspection and D2 calibration.
