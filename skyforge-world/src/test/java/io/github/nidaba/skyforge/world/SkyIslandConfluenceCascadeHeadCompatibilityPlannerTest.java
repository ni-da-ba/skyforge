package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandConfluenceCascadeHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void confluence632JointTransitionIsDeterministicAndFailClosed() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan first =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan second =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);

        assertEquals(first.outcomes().size(), second.outcomes().size());
        assertTrue(first.outcomes().stream().anyMatch(outcome ->
                outcome.confluence().transitionSite().nodeCellIndex() == 710));
        assertTrue(first.solvedCount() > 0, "the eligible confluence/CASCADE overlap should solve jointly");
        assertTrue(first.outcomes().stream().noneMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));

        for (int i = 0; i < first.outcomes().size(); i++) {
            SkyIslandConfluenceCascadeHeadCompatibilityOutcome a = first.outcomes().get(i);
            SkyIslandConfluenceCascadeHeadCompatibilityOutcome b = second.outcomes().get(i);
            assertEquals(a.status(), b.status());
            assertEquals(a.sharedNodeHeadWorldUnits(), b.sharedNodeHeadWorldUnits());
            assertEquals(a.cascadeBoundaryHeadWorldUnits(), b.cascadeBoundaryHeadWorldUnits());
            assertEquals(a.solvedDropWorldUnits(), b.solvedDropWorldUnits());
            assertEquals(a.ordinaryLegSolutions(), b.ordinaryLegSolutions());
            assertEquals(
                    a.solve().map(SkyIslandHydraulicQpResult::primalResidual),
                    b.solve().map(SkyIslandHydraulicQpResult::primalResidual));

            if (a.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED) {
                double node = a.sharedNodeHeadWorldUnits().orElseThrow();
                double drop = a.solvedDropWorldUnits().orElseThrow();
                assertTrue(node + EPSILON >= a.nodeLowerHeadWorldUnits());
                assertTrue(node <= a.nodeUpperHeadWorldUnits() + EPSILON);
                assertTrue(drop >= -EPSILON);
                assertTrue(drop <= a.authoredMaximumDropWorldUnits() + EPSILON);
                assertTrue(a.solve().orElseThrow().primalResidual() <= 1.0e-7);
                assertEquals(a.confluence().legs().size() - 1, a.ordinaryLegSolutions().size());
            } else {
                assertTrue(a.sharedNodeHeadWorldUnits().isEmpty());
                assertTrue(a.cascadeBoundaryHeadWorldUnits().isEmpty());
                assertTrue(a.solvedDropWorldUnits().isEmpty());
                assertTrue(a.ordinaryLegSolutions().isEmpty());
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
