package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandWaterbodyPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void retainedWaterbodiesAreDeterministicAndNormalized() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, 83L));
        SkyIslandWaterbodyPlan first = SkyIslandWaterbodyPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan second = SkyIslandWaterbodyPlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.candidates().isEmpty());
        assertEquals(watershed.retainedSinkCount(), first.candidates().size());

        int assignedCells = 0;
        for (SkyIslandWaterbodyCandidate candidate : first.candidates()) {
            assertTrue(candidate.catchmentCellCount() >= 1);
            assertEquals(
                    (double) candidate.catchmentCellCount() / watershed.cells().size(),
                    candidate.catchmentFraction(),
                    1.0e-12);
            assertTrue(candidate.catchmentFraction() >= 0.0 && candidate.catchmentFraction() <= 1.0);
            assertTrue(candidate.relativeInflow() >= 0.0 && candidate.relativeInflow() <= 1.0);
            assertTrue(candidate.persistence() >= 0.0 && candidate.persistence() <= 1.0);
            assertTrue(candidate.basinScale() >= 0.0 && candidate.basinScale() <= 1.0);
            assignedCells += candidate.catchmentCellCount();
        }
        assertTrue(assignedCells <= watershed.cells().size());
    }

    @Test
    void noRetainedBasinMeansNoWaterbodyCandidate() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, 77L));
        assertTrue(SkyIslandWaterbodyPlanner.plan(descriptor).candidates().isEmpty());
    }
}
