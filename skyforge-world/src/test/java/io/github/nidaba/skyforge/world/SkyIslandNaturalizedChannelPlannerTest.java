package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandNaturalizedChannelPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void naturalizationIsDeterministicAndPreservesAcceptedTopology() {
        SkyIslandDescriptor descriptor = descriptor(512L);
        SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandNaturalizedChannelPlan first = SkyIslandNaturalizedChannelPlanner.plan(descriptor);
        SkyIslandNaturalizedChannelPlan second = SkyIslandNaturalizedChannelPlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(profiles.profiles().size(), first.paths().size());

        Map<Integer, SkyIslandNaturalizedChannelPath> bySource = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : first.paths()) {
            SkyIslandChannelSegment segment = path.profile().segment();
            assertTrue(bySource.put(segment.sourceCellIndex(), path) == null);
            assertEquals(segment.start(), path.points().getFirst());
            assertEquals(segment.end(), path.points().getLast());
            assertEquals(SkyIslandNaturalizedChannelPlanner.SUBDIVISIONS + 1, path.points().size());
        }

        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            SkyIslandNaturalizedChannelPath path = bySource.get(profile.segment().sourceCellIndex());
            assertEquals(profile, path.profile());
        }
    }

    @Test
    void representativePathsStayInsideBoundedSubGridCorridors() {
        boolean sawSubGridDeviation = false;
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandNaturalizedChannelPlan plan =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor(key));
            double maximum = plan.planningSpacing()
                    * SkyIslandNaturalizedChannelPlanner.MAX_CHORD_DEVIATION_SPACING_FRACTION;
            for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
                assertTrue(path.maxChordDeviation() <= maximum + EPSILON);
                assertTrue(path.lengthRatio() >= 1.0 - EPSILON);
                assertTrue(path.lengthRatio() < 1.75);
                for (SkyIslandLocalPosition point : path.points()) {
                    assertTrue(Double.isFinite(point.x()));
                    assertTrue(Double.isFinite(point.z()));
                }
                sawSubGridDeviation |= path.maxChordDeviation() > plan.planningSpacing() * 0.01;
            }
        }
        assertTrue(sawSubGridDeviation);
    }

    @Test
    void sharedGraphNodesRemainExactAcrossIncomingAndOutgoingPaths() {
        SkyIslandNaturalizedChannelPlan plan =
                SkyIslandNaturalizedChannelPlanner.plan(descriptor(77L));
        Map<Integer, SkyIslandLocalPosition> endpoints = new HashMap<>();

        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
            SkyIslandChannelSegment segment = path.profile().segment();
            assertNode(endpoints, segment.sourceCellIndex(), path.points().getFirst());
            assertNode(endpoints, segment.downstreamCellIndex(), path.points().getLast());
        }
    }

    private static void assertNode(
            Map<Integer, SkyIslandLocalPosition> endpoints,
            int cellIndex,
            SkyIslandLocalPosition point) {
        SkyIslandLocalPosition previous = endpoints.putIfAbsent(cellIndex, point);
        if (previous != null) {
            assertEquals(previous, point);
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
