# Hydrology reset tranche D1 — cross-section and excavation diagnostics

**Status:** dependent diagnostic tranche under issue #1084  
**Depends on:** post-C1 D0 diagnostic corpus  
**Terrain mutation:** none  
**Acceptance thresholds:** still not frozen

## Purpose

D0 established a fixed pre-carving diagnostic corpus. D1 adds the cross-section-aware quantities
needed to distinguish a plausible river corridor from the quarry/slot-canyon failure class that
survived the retired H6 machine gates.

D1 remains measurement only. It deliberately does not contain pass/fail thresholds.

## Added metrics

All curvature and cross-section measurements use the C1 continuous centerline rather than the raw search lattice.\n\nFor every hydraulic macro reach, D1 measures:

- maximum lateral recovery grade from the candidate bed to unmodified valley-side terrain;
- maximum bank-containment deficit, in authored world units, where natural bank terrain lies below
  the candidate water surface;
- maximum water-depth to bankfull-width ratio;
- maximum surrounding-relief to valley-width ratio;
- normalized excavation burden;
- approximate excavation-volume proxy in authored world units cubed;
- maximum local curvature multiplied by bankfull width;
- existing ridge-occupancy and longitudinal-grade diagnostics in the same reach record.

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
