package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandTerrainAwareChannelCorridorPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void macroCorridorSearchIsDeterministicAndKeepsProfileResolution() {
        for (long key : new long[] {287L, 649L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandNaturalizedChannelPlan first =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);
            SkyIslandNaturalizedChannelPlan second =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);

            assertEquals(first, second);
            for (SkyIslandNaturalizedChannelPath path : first.paths()) {
                assertEquals(
                        SkyIslandTerrainAwareMacroReachRouter.STATIONS_PER_COARSE_REACH + 1,
                        path.points().size());
            }
        }
    }

    @Test
    void routeRemainsInsideBoundedFineCorridor() {
        for (long key : new long[] {287L, 649L, 811L}) {
            SkyIslandNaturalizedChannelPlan plan =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor(key));
            double maximum = plan.planningSpacing()
                    * SkyIslandNaturalizedChannelPlanner.MAX_CHORD_DEVIATION_SPACING_FRACTION;
            for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
                assertTrue(path.maxChordDeviation() <= maximum + EPSILON);
                assertTrue(path.pathLength() + EPSILON >= path.chordLength());
                assertTrue(path.lengthRatio() < 1.9);
                for (SkyIslandLocalPosition point : path.points()) {
                    assertTrue(Double.isFinite(point.x()));
                    assertTrue(Double.isFinite(point.z()));
                }
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
