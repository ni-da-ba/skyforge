package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandCoherentChannelPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void coherenceSelectionIsDeterministicAndKeepsWholeAcceptedComponents() {
        SkyIslandDescriptor descriptor = descriptor(512L);
        SkyIslandCoherentChannelPlan first = SkyIslandCoherentChannelPlanner.plan(descriptor);
        SkyIslandCoherentChannelPlan second = SkyIslandCoherentChannelPlanner.plan(descriptor);
        SkyIslandChannelProfilePlan source = SkyIslandChannelProfilePlanner.plan(descriptor);

        assertEquals(first, second);
        Set<Integer> acceptedSources = new HashSet<>();
        for (SkyIslandChannelProfile profile : source.profiles()) {
            acceptedSources.add(profile.segment().sourceCellIndex());
        }
        Set<Integer> retainedSources = new HashSet<>();
        for (SkyIslandCoherentChannelComponent component : first.retainedComponents()) {
            for (SkyIslandChannelProfile profile : component.profiles()) {
                assertTrue(acceptedSources.contains(profile.segment().sourceCellIndex()));
                assertTrue(retainedSources.add(profile.segment().sourceCellIndex()));
            }
        }
        assertEquals(first.retainedReachCount(), retainedSources.size());
    }

    @Test
    void retainedTerminalsRespectSpatialSeparation() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandCoherentChannelPlan plan =
                    SkyIslandCoherentChannelPlanner.plan(descriptor(key));
            double minimum = plan.planningSpacing()
                    * SkyIslandCoherentChannelPlanner.MIN_TERMINAL_SEPARATION_CELLS;
            for (int i = 0; i < plan.retainedComponents().size(); i++) {
                for (int j = i + 1; j < plan.retainedComponents().size(); j++) {
                    SkyIslandLocalPosition a = plan.retainedComponents().get(i).terminalPosition();
                    SkyIslandLocalPosition b = plan.retainedComponents().get(j).terminalPosition();
                    assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) + EPSILON >= minimum);
                }
            }
        }
    }

    @Test
    void coherenceSelectionNeverInventsComponentsOrReaches() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L, 287L, 649L}) {
            SkyIslandCoherentChannelPlan plan =
                    SkyIslandCoherentChannelPlanner.plan(descriptor(key));
            int sourceReaches = SkyIslandChannelProfilePlanner.plan(descriptor(key)).profiles().size();

            assertTrue(plan.retainedComponentCount() <= plan.sourceComponentCount());
            assertTrue(plan.retainedReachCount() <= sourceReaches);
            if (sourceReaches > 0) {
                assertTrue(plan.retainedReachCount() > 0);
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
