package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandCascadeHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void primary287OwnsAllCascadeProfilesAndSolvesAtLeastOneInternalDrop() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandCascadeHeadCompatibilityPlan plan =
                SkyIslandCascadeHeadCompatibilityPlanner.plan(descriptor);

        int ownedProfiles = plan.outcomes().stream()
                .mapToInt(outcome -> outcome.geometry().transitionSite().profileCount())
                .sum();
        assertEquals(10, ownedProfiles);
        assertTrue(plan.outcomes().stream()
                .anyMatch(outcome ->
                        outcome.status() == SkyIslandCascadeHeadCompatibilityStatus.SOLVED));
        assertFalse(plan.outcomes().stream()
                .anyMatch(outcome ->
                        outcome.status()
                                == SkyIslandCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));

        for (SkyIslandCascadeHeadCompatibilityOutcome outcome : plan.outcomes()) {
            if (outcome.status() != SkyIslandCascadeHeadCompatibilityStatus.SOLVED) {
                continue;
            }
            double drop = outcome.solvedDropWorldUnits().orElseThrow();
            assertTrue(drop >= -EPSILON);
            assertTrue(drop <= outcome.authoredMaximumDropWorldUnits() + EPSILON);
            assertTrue(outcome.solve().orElseThrow().primalResidual() <= 1.0e-7);
        }
    }

    @Test
    void repeatedCascadeCompatibilityPlansAreIdentical() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        assertEquals(
                SkyIslandCascadeHeadCompatibilityPlanner.plan(descriptor),
                SkyIslandCascadeHeadCompatibilityPlanner.plan(descriptor));
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
