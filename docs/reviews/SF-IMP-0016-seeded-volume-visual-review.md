# SF-IMP-0016 Seeded Suspended-Volume Visual Review

- **Date:** 2026-08-28
- **Source:** locally generated Java 25 `seeded-suspended-volume-v1` corpus
- **Work item:** SF-IMP-0016
- **Verdict:** accepted as bounded enrichment; insufficient as landscape-scale morphology

## 1. Validation basis

The six canonical full-amplitude seeds generated successfully under Temurin OpenJDK 25.0.4.1. The focused seeded recipe and SF-VOL-006 acceptance suite passed. A pre-existing JUnit temporary-directory lifecycle defect in the SF-IMP-0015 golden-specimen test was isolated, corrected, and the formerly failing golden test passed after the fix.

The generated corpus reported:

| Member | Solid samples | Components | Face contacts | Minimum clearance | Y bounds |
|---|---:|---:|---:|---:|---|
| seed-min | 367,356 | 1 | 0 | 88 | [124, 364] |
| seed-negative-one | 369,847 | 1 | 0 | 88 | [116, 364] |
| seed-zero | 370,382 | 1 | 0 | 88 | [116, 360] |
| seed-one | 365,864 | 1 | 0 | 88 | [124, 364] |
| seed-skyforge | 367,732 | 1 | 0 | 88 | [116, 360] |
| seed-max | 363,854 | 1 | 0 | 88 | [116, 360] |

The six-member solid-sample range is 6,528 samples, approximately 1.78 percent of the signal-free specimen's 366,912 solid samples.

## 2. Visual findings

### Primary identity is preserved exactly where intended

The six `suspension-occupancy.png` images are pixel-identical. This agrees with the analytical SF-IMP-0016 contract: positive multiplicative modulation preserves the sign of each surface offset, so the suspension-plane footprint and outer rim do not move.

The upper-surface, underside, and isometric images are all distinct across the six seeds. The enrichment is therefore observable and not a no-op.

### The variation is useful but visually subordinate

The vertical sections show real differences in crown height distribution, local shoulder form, underside depth, and local underside irregularity. Different seeds can move sampled Y extrema by several grid levels while preserving closure and one-component topology.

However, all six isometric specimens still read immediately as the same smooth, elongated suspended body. Seed variation changes local relief, but does not create new landscape-scale organization. The current forms remain closer to rounded geological solids or pebbles than to inhabited or naturally eroded terrain masses.

### Missing landscape hierarchy

The current enrichment lacks the structures that would make the upper surface read as terrain rather than a perturbed analytic crown:

- secondary ridge chains or branching spurs;
- valleys organized between ridges;
- saddles and passes;
- plateaus or broad upland benches;
- directional escarpments;
- coherent drainage-scale depressions;
- hierarchical feature sizes larger than the local value-signal cells.

The underside variation is more visually legible in cross-section than the upper variation, but it likewise remains modulation of one broad taper rather than a composition of major lower landforms.

## 3. Decision

SF-IMP-0016 is accepted **as the bounded seeded enrichment layer**. Its purpose is to create deterministic local variation without replacing the primary morphology, and the local corpus demonstrates that it does so while preserving all declared numerical invariants.

The correct response to the remaining smoothness is **not** to raise `MAXIMUM_RELATIVE_DISPLACEMENT` above 0.15 or apply arbitrary final-density noise. Doing so would ask a detail mechanism to perform the job of a morphology mechanism and would weaken the topology guarantees that SF-IMP-0016 was designed to protect.

Instead, landscape-scale organization becomes a separate explicit morphology stage.

## 4. SF-IMP-0017 design target

The next work item should introduce deterministic secondary ridge and valley morphology ahead of bounded enrichment.

The first implementation should target the upper surface only and should establish a compositional contract rather than a complete erosion model. At minimum it should support:

1. one or more secondary ridge/spur contributions derived from the semantic primary ridge frame;
2. valley/depression contributions organized relative to those ridges rather than independent white noise;
3. deterministic seeded placement under stable semantic namespaces;
4. a rim-safe interior envelope so secondary morphology decays before the accepted outer silhouette;
5. a declared minimum-thickness protection against upper/lower inversion or accidental perforation;
6. exact zero-amplitude compatibility with the accepted SF-IMP-0016 artifact;
7. visual evidence that changes interior landscape organization at a clearly larger scale than the bounded enrichment layer.

SF-VOL-001 through SF-VOL-010 remain authoritative. SF-IMP-0017 should add explicit acceptance for minimum local thickness and for the predictable response of ridge/valley semantic controls before multi-morphology composition is attempted.

## 5. Review conclusion

The six-seed corpus validates the architecture choice made in ADR-0020: local variation can be added without sacrificing finite suspension, connectedness, clearance, or primary identity. It also makes the next limitation unambiguous. Skyforge now needs **organized secondary landforms**, not stronger noise.
