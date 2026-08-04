# Skyforge v0.1.0 Architecture-Proof Release Record

**Status:** Release candidate; tag after protected `main` CI passes  
**Date:** 2026-08-03  
**Closure ticket:** SF-IMP-0010  
**Corpus:** `fixed-seed-island-v1`

## 1. Released claim

Skyforge v0.1.0 proves one deliberately narrow claim:

> A semantic island descriptor compiles into an inspectable procedural graph that deterministically
> produces a recognizable island independently of Minecraft.

The release does not claim finished naturalism, a complete world generator, secondary morphology,
materials, climate, ecology, caves, structures, or backend realization.

## 2. Closure facts

| Item | Released evidence |
|---|---|
| Engine version | `0.1.0` |
| Java/build gate | Java 25, Gradle 9.6.1, `-Xlint:all -Werror` |
| Test suite | 85 JUnit test methods |
| Canonical seeded corpus | Six 1024 by 1024 members |
| Full height samples | 6,291,456 per corpus run |
| Normative corpus paths | 49 canonical descriptor, graph, grid, cross-section, and manifest paths |
| Morphology identity | One land component, zero boundary contact, identical land-mask bytes for all six seeds |
| Reference benchmark | 35.479 seconds and 177,329 samples/second on the recorded two-vCPU Java 25 runner; observational only |
| Backend boundary | Automated rejection of Minecraft and NeoForge imports in engine modules |

## 3. Kernel acceptance trace

| Gate | Primary executable evidence | Fixed evidence or build control |
|---|---|---|
| `SF-KER-001` analytical correctness | `ReferenceEvaluatorTest.evaluatesEveryInitialOperatorInAHandCalculatedTwoDimensionalGraph`; `ReferenceEvaluatorTest.evaluatesAllThreeCoordinateAxesInAHandCalculatedThreeDimensionalGraph`; `PlanarValueSignalTest` analytical/bound tests | Canonical height and density graphs in every corpus member |
| `SF-KER-002` order-independent determinism | `DeterministicGridSamplerTest.everyTraversalProducesIdenticalRawValuesAndChecksum`; `SeededIslandAcceptanceTest.seededEvidenceIsIndependentOfSamplingSchedule`; `ReferenceEvaluatorTest.resultDoesNotDependOnNodeDeclarationOrSamplingOrder` | Six pinned height grids and the corpus manifest |
| `SF-KER-003` graph integrity | `ProceduralGraphTest` rejection cases; `GraphNodeTest` finite/type contracts; `CanonicalGraphJsonTest` strict-decoder rejection cases | Strict canonical graph schemas 1 and 2 |
| `SF-KER-004` round-trip identity | `CanonicalGraphJsonTest.roundTripPreservesNodesRawBitsCanonicalBytesAndEvaluation`; `CanonicalGraphJsonTest.emitsSchemaTwoAndRoundTripsThePlanarSignalContract` | Pinned height/density graph JSON for all seeds |
| `SF-KER-005` backend independence | Root `verifyBackendIndependence` Gradle task | Required by `check` in every Java 25 CI run |
| `SF-KER-006` inspectability | `SignalFreeIslandAcceptanceTest.sfIsl009DescriptorControlsRemainInspectable`; `SeededIslandRecipeTest.positiveAmplitudeProducesInspectableVersionedSignalGraphs` | Descriptor, height graph, density graph, provenance, and schema versions in every package |

## 4. Island acceptance trace

| Gate | Primary executable evidence | Review or fixed evidence |
|---|---|---|
| `SF-ISL-001` closed landform | `SignalFreeIslandAcceptanceTest.sfIsl001ClosedLandform` | Land-mask grid/PNG; component and boundary metrics |
| `SF-ISL-002` bounded elevation | `SignalFreeIslandAcceptanceTest.sfIsl002BoundedElevation` | Height grid, statistics, and height PNG |
| `SF-ISL-003` scale control | `SignalFreeIslandAcceptanceTest.sfIsl003ScaleControl` | Measured area and shoreline spans |
| `SF-ISL-004` elevation control | `SignalFreeIslandAcceptanceTest.sfIsl004ElevationControl` | Peak/percentile response with identical footprint |
| `SF-ISL-005` ridge control | `SignalFreeIslandAcceptanceTest.sfIsl005RidgeControl` | Principal-axis metric and rotated footprint tests |
| `SF-ISL-006` signal neutrality | `SignalFreeIslandAcceptanceTest.sfIsl006ZeroSignalIsNeutral`; `SeededIslandRecipeTest.zeroAmplitudeReturnsTheExactSignalFreeArtifact` | Byte-identical signal-free graph and height evidence |
| `SF-ISL-007` seeded identity preservation | `SeededIslandAcceptanceTest.sfIsl007PreservesIdentityAcrossTheFixedSeedSuite` | Six distinct height hashes; one shared land-mask hash and morphology tuple |
| `SF-ISL-008` density consistency | `SignalFreeIslandAcceptanceTest.sfIsl008DensityMatchesHeightSurface`; recipe density tests | Canonical density graph and height cross-sections |
| `SF-ISL-009` explainable output | `SignalFreeIslandAcceptanceTest.sfIsl009DescriptorControlsRemainInspectable` | Descriptor/graph/provenance records and visual atlas |

## 5. How to read the visual atlas

The images are diagnostic projections of exact numerical evidence:

- **Height:** black is at or below sea level; brighter pixels are higher, and white is capped at the
  descriptor's declared maximum elevation. Comparing seeds shows changed interior relief.
- **Land mask:** white is `height > 0`; black is sea. The shared mask hash proves the six silhouettes
  are exactly identical, not merely visually similar.
- **Slope magnitude:** brighter pixels change height more rapidly. This view reveals steep coasts,
  abrupt transitions, and discontinuities that the height shading can conceal. Bright does not mean
  high; it means steep.
- **East-west and north-south cross-sections:** the gray horizontal line is sea level and the black
  curve is the terrain surface through the descriptor center. The crossings show the two coasts;
  the curve between them shows the broad primary ridge plus bounded signal variation.

The PNG byte representation is not normative across arbitrary JDK encoders. The `.grid` files,
canonical JSON, hexadecimal CSV cross-sections, statistics, and SHA-256 hashes are the reproducible
proof. The pictures make those results reviewable by a person.

## 6. Visual findings and limitations

The first signal does what v0.1 requires: all seeds visibly alter interior elevation while
preserving primary island identity. The oval footprint, broad ridge, and smooth coast are primary
morphology. The block-like interior patches are the deliberately modest periodic lattice-value
signal.

Those patches should not be mistaken for a target art style or finished geology. The atlas does not
yet demonstrate erosion, drainage, varied shorelines, cliffs governed by material, nested landform
composition, or secondary ridges and valleys. Those are candidates for v0.2 and later acceptance
gates.

## 7. Reproduction and release

From a clean checkout with a 64-bit JDK 25:

```shell
./gradlew check :skyforge-reference:fixedSeedCorpus
```

The command must compile warning-clean, run all tests, enforce backend independence, regenerate the
six-member corpus, verify all 49 canonical paths, and emit the HTML atlas and benchmark report.

The release procedure is:

1. Merge the reviewed SF-IMP-0010 closure commit into protected `main`.
2. Require the push-triggered Java 25 workflow for that exact `main` commit to pass.
3. Create tag `v0.1.0` at that exact commit.
4. Record the tag and successful workflow in this release record or its GitHub release notes.

## 8. Next boundary

`SF-IMP-0011` will propose v0.2 around semantic composition and one secondary-morphology family.
It will preserve the reference evaluator and v0.1 corpus as differential/regression authorities.
NeoForge integration, premature optimization, and broad content systems remain outside that default
next slice.
