package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandBoundedHydraulicProfilePlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void fixedCorpusIsDeterministic() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandBoundedHydraulicProfilePlan first =
                    SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor(key));
            SkyIslandBoundedHydraulicProfilePlan second =
                    SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor(key));

            assertEquals(first.outcomes().size(), second.outcomes().size());
            for (int i = 0; i < first.outcomes().size(); i++) {
                SkyIslandBoundedHydraulicReachOutcome a = first.outcomes().get(i);
                SkyIslandBoundedHydraulicReachOutcome b = second.outcomes().get(i);
                assertEquals(a.skeleton(), b.skeleton());
                assertEquals(a.status(), b.status());
                assertEquals(a.deferralReasons(), b.deferralReasons());
                assertEquals(a.diagnostic(), b.diagnostic());
                assertEquals(a.hydraulicReach(), b.hydraulicReach());
                assertEquals(a.qualification(), b.qualification());
                if (a.solverResult().isPresent()) {
                    assertTrue(b.solverResult().isPresent());
                    SkyIslandHydraulicQpResult ar = a.solverResult().orElseThrow();
                    SkyIslandHydraulicQpResult br = b.solverResult().orElseThrow();
                    assertEquals(ar.status(), br.status());
                    assertArrayEquals(ar.solution(), br.solution(), EPSILON);
                    assertEquals(ar.objective(), br.objective(), EPSILON);
                    assertEquals(ar.primalResidual(), br.primalResidual(), EPSILON);
                    assertEquals(ar.stationarityResidual(), br.stationarityResidual(), EPSILON);
                    assertEquals(ar.dualFeasibilityResidual(), br.dualFeasibilityResidual(), EPSILON);
                    assertEquals(ar.complementarityResidual(), br.complementarityResidual(), EPSILON);
                } else {
                    assertTrue(b.solverResult().isEmpty());
                }
            }
        }
    }

    @Test
    void transitionOwnedReachesRemainExplicitlyDeferred() {
        SkyIslandBoundedHydraulicProfilePlan plan =
                SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor(512L));

        assertTrue(plan.outcomes().stream()
                .anyMatch(outcome -> outcome.deferralReasons().contains(
                        SkyIslandQualifiedFluvialDeferralReason.CONFLUENCE_TRANSITION_REQUIRED)));
        assertTrue(plan.outcomes().stream()
                .anyMatch(outcome -> outcome.deferralReasons().contains(
                        SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED)));

        for (SkyIslandBoundedHydraulicReachOutcome outcome : plan.outcomes()) {
            if (outcome.status() == SkyIslandBoundedHydraulicReachStatus.TRANSITION_DEFERRED) {
                assertTrue(outcome.solverResult().isEmpty());
                assertTrue(outcome.hydraulicReach().isEmpty());
                assertTrue(outcome.qualification().isEmpty());
                assertFalse(outcome.deferralReasons().isEmpty());
            }
        }
    }

    @Test
    void retainedOpenWaterTerminalIsNeverTreatedAsFreeBoundary() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 83L);
        SkyIslandWaterbodyPlan waterbodies = SkyIslandWaterbodyPlanner.plan(descriptor);
        SkyIslandBoundedHydraulicProfilePlan plan =
                SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor);

        int matchedTerminalCount = 0;
        for (SkyIslandWaterbodyCandidate candidate : waterbodies.candidates()) {
            if (candidate.kind() == SkyIslandWaterbodyKind.WETLAND) {
                continue;
            }
            for (SkyIslandBoundedHydraulicReachOutcome outcome : plan.outcomes()) {
                if (outcome.skeleton().geomorphicRoute().semanticReach().endCellIndex()
                        == candidate.sinkCellIndex()) {
                    matchedTerminalCount++;
                    assertEquals(
                            SkyIslandBoundedHydraulicReachStatus.TRANSITION_DEFERRED,
                            outcome.status());
                    assertTrue(outcome.deferralReasons().contains(
                            SkyIslandQualifiedFluvialDeferralReason
                                    .RETAINED_WATER_TRANSITION_REQUIRED));
                }
            }
        }
        assertTrue(matchedTerminalCount > 0, "retained fixture must exercise a channel/basin terminal");
    }

    @Test
    void solvedOrdinaryProfilesAreNonClimbingAndRespectD2GradeLimit() {
        int solvedCount = 0;
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandBoundedHydraulicProfilePlan plan =
                    SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor(key));
            for (SkyIslandBoundedHydraulicReachOutcome outcome : plan.outcomes()) {
                if (outcome.hydraulicReach().isEmpty()) {
                    continue;
                }
                solvedCount++;
                SkyIslandHydraulicReachGeometry reach = outcome.hydraulicReach().orElseThrow();
                SkyIslandGeomorphicProfileLimits limits =
                        SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked()
                                .limits(reach.geomorphicRoute().semanticReach());

                for (int i = 0; i + 1 < reach.samples().size(); i++) {
                    SkyIslandHydraulicGeometrySample a = reach.samples().get(i);
                    SkyIslandHydraulicGeometrySample b = reach.samples().get(i + 1);
                    double ds = Math.hypot(
                            b.position().x() - a.position().x(),
                            b.position().z() - a.position().z());
                    double worldDrop =
                            (a.waterSurfacePotential() - b.waterSurfacePotential())
                                    * plan.descriptor().reliefBudget();
                    assertTrue(worldDrop >= -EPSILON);
                    assertTrue(
                            worldDrop / ds
                                    <= limits.maximumLongitudinalGrade() + EPSILON);
                }

                SkyIslandHydraulicQpResult solve = outcome.solverResult().orElseThrow();
                assertEquals(SkyIslandHydraulicQpStatus.SOLVED, solve.status());
                assertTrue(solve.primalResidual() < 1.0e-6);
                assertTrue(solve.stationarityResidual() < 1.0e-6);
                assertTrue(solve.dualFeasibilityResidual() < 1.0e-6);
                assertTrue(solve.complementarityResidual() < 1.0e-6);
            }
        }
        assertTrue(solvedCount > 0, "fixed corpus must exercise at least one ordinary F2C solve");
    }

    @Test
    void solvedOutcomeNeverBypassesPostSolveD2Qualification() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandBoundedHydraulicProfilePlan plan =
                    SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor(key));
            for (SkyIslandBoundedHydraulicReachOutcome outcome : plan.outcomes()) {
                if (outcome.hydraulicReach().isEmpty()) {
                    continue;
                }
                SkyIslandGeomorphicReachQualification qualification =
                        outcome.qualification().orElseThrow();
                assertEquals(
                        qualification.accepted(),
                        outcome.status()
                                == SkyIslandBoundedHydraulicReachStatus.SOLVED_QUALIFIED);
                assertEquals(
                        !qualification.accepted(),
                        outcome.status()
                                == SkyIslandBoundedHydraulicReachStatus.SOLVED_REJECTED);
            }
        }
    }

    @Test
    void midpointRefinementPreservesHeadDependentD2Classification() {
        int diagnosticCount = 0;
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            List<SkyIslandBoundedHydraulicConvergenceDiagnostics> diagnostics =
                    SkyIslandBoundedHydraulicConvergencePlanner.measure(descriptor(key));
            diagnosticCount += diagnostics.size();
            assertEquals(
                    diagnostics,
                    SkyIslandBoundedHydraulicConvergencePlanner.measure(descriptor(key)));
            for (SkyIslandBoundedHydraulicConvergenceDiagnostics d : diagnostics) {
                assertEquals(2 * d.nativeSampleCount() - 1, d.refinedSampleCount());
                assertEquals(
                        d.nativeHeadDependentD2Pass(),
                        d.refinedHeadDependentD2Pass(),
                        "collocation refinement must not change head-dependent D2 classification");
            }
        }
        assertTrue(diagnosticCount > 0, "fixed corpus must exercise F2C convergence diagnostics");
    }

    @Test
    void f2cDoesNotMutateThePreHydrologicTerrainField() {
        SkyIslandDescriptor descriptor = descriptor(118L);
        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        List<SkyIslandLocalPosition> probes = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(12.0, -8.0),
                new SkyIslandLocalPosition(-18.0, 21.0));
        double[] before = probes.stream().mapToDouble(original::sample).toArray();

        SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor);

        double[] after = probes.stream().mapToDouble(original::sample).toArray();
        assertArrayEquals(before, after, 0.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return descriptor(8L, 81L, key);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
