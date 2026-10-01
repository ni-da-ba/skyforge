package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandConfluenceHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void confluence632ProducesDeterministicBoundedCompatibilityEvidence() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandConfluenceHeadCompatibilityPlan first =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(descriptor);
        SkyIslandConfluenceHeadCompatibilityPlan second =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(descriptor);

        assertEquals(
                first.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::status)
                        .toList(),
                second.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::status)
                        .toList());
        assertEquals(
                first.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::nodeHeadWorldUnits)
                        .toList(),
                second.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::nodeHeadWorldUnits)
                        .toList());
        assertEquals(
                first.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::legSolutions)
                        .toList(),
                second.outcomes().stream()
                        .map(SkyIslandConfluenceHeadCompatibilityOutcome::legSolutions)
                        .toList());
        assertEquals(
                first.outcomes().stream()
                        .map(outcome -> outcome.solve()
                                .map(SkyIslandHydraulicQpResult::primalResidual))
                        .toList(),
                second.outcomes().stream()
                        .map(outcome -> outcome.solve()
                                .map(SkyIslandHydraulicQpResult::primalResidual))
                        .toList());
        assertTrue(first.outcomes().stream()
                .anyMatch(outcome ->
                        outcome.geometry().transitionSite().nodeCellIndex() == 710));

        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : first.outcomes()) {
            assertTrue(
                    outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.SOLVED
                            || outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.INFEASIBLE
                            || outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.CASCADE_COUPLED);
            if (outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.SOLVED) {
                double node = outcome.nodeHeadWorldUnits().orElseThrow();
                assertTrue(node + EPSILON >= outcome.nodeLowerHeadWorldUnits());
                assertTrue(node <= outcome.nodeUpperHeadWorldUnits() + EPSILON);
                assertTrue(outcome.solve().orElseThrow().primalResidual() <= 1.0e-7);
                assertEquals(
                        outcome.geometry().legs().size(),
                        outcome.legSolutions().size());
            }
        }
    }

    @Test
    void solvedLegsRespectDownstreamMonotonicityAndD2GradeBounds() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandConfluenceHeadCompatibilityPlan plan =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : plan.outcomes()) {
            if (outcome.status() != SkyIslandConfluenceHeadCompatibilityStatus.SOLVED) {
                continue;
            }
            double node = outcome.nodeHeadWorldUnits().orElseThrow();
            for (SkyIslandConfluenceLegHeadSolution solution : outcome.legSolutions()) {
                SkyIslandHydraulicTransitionLegGeometry leg = solution.leg();
                double finite = solution.finiteBoundaryHeadWorldUnits();
                SkyIslandSemanticChannelReach semantic =
                        plan.transitionGeometry().topology().skeletonPlan().reaches().stream()
                                .map(reach -> reach.geomorphicRoute().semanticReach())
                                .filter(reach ->
                                        reach.startCellIndex()
                                                        == leg.nodeBoundary().reachStartCellIndex()
                                                && reach.endCellIndex()
                                                        == leg.nodeBoundary().reachEndCellIndex())
                                .findFirst()
                                .orElseThrow();
                double maxDrop =
                        policy.limits(semantic).maximumLongitudinalGrade()
                                * leg.retreatLength();
                if (leg.nodeBoundary().role()
                        == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                    assertTrue(finite + EPSILON >= node);
                    assertTrue(finite - node <= maxDrop + EPSILON);
                } else {
                    assertTrue(node + EPSILON >= finite);
                    assertTrue(node - finite <= maxDrop + EPSILON);
                }
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
