package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
            assertEquals(SkyIslandNaturalizedChannelPlanner.SUBDIVISIONS + 1, path.points().size());
        }

        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            SkyIslandNaturalizedChannelPath path = bySource.get(profile.segment().sourceCellIndex());
            assertEquals(profile, path.profile());
        }
    }

    @Test
    void representativePathsStayInsideBoundedSubGridCorridors() {
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
            }
        }
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


    @Test
    void headwatersConfluencesAndTerminalsRemainExactControls() {
        SkyIslandDescriptor descriptor = descriptor(649L);
        SkyIslandNaturalizedChannelPlan plan =
                SkyIslandNaturalizedChannelPlanner.plan(descriptor);
        Map<Integer, Integer> incoming = new HashMap<>();
        Set<Integer> outgoing = new HashSet<>();
        Map<Integer, SkyIslandNaturalizedChannelPath> bySource = new HashMap<>();

        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
            SkyIslandChannelSegment segment = path.profile().segment();
            incoming.merge(segment.downstreamCellIndex(), 1, Integer::sum);
            outgoing.add(segment.sourceCellIndex());
            bySource.put(segment.sourceCellIndex(), path);
        }

        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
            SkyIslandChannelSegment segment = path.profile().segment();
            int source = segment.sourceCellIndex();
            int downstream = segment.downstreamCellIndex();

            if (incoming.getOrDefault(source, 0) != 1) {
                assertEquals(segment.start(), path.points().getFirst());
            }
            if (!outgoing.contains(downstream)
                    || incoming.getOrDefault(downstream, 0) != 1) {
                assertEquals(segment.end(), path.points().getLast());
            }
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
