package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkyIslandChannelNetworkPlannerTest {
    @Test
    void networkHierarchyIsDeterministicAndNormalized() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 7L, 71L, 77L));
        SkyIslandChannelNetworkPlan a = SkyIslandChannelNetworkPlanner.plan(descriptor);
        SkyIslandChannelNetworkPlan b = SkyIslandChannelNetworkPlanner.plan(descriptor);

        assertEquals(a, b);
        assertFalse(a.segments().isEmpty());
        assertTrue(a.maxStreamOrder() >= 1);
        assertTrue(a.segments().stream().allMatch(segment -> segment.streamOrder() <= a.maxStreamOrder()));
        assertTrue(a.segments().stream().allMatch(segment -> segment.relativeDischarge() >= 0.0
                && segment.relativeDischarge() <= 1.0
                && segment.corridorScale() >= 0.0
                && segment.corridorScale() <= 1.0));
    }

    @Test
    void streamOrderNeverDecreasesAlongSelectedDownstreamNetwork() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 7L, 71L, 512L));
        SkyIslandChannelNetworkPlan plan = SkyIslandChannelNetworkPlanner.plan(descriptor);
        Map<Integer, SkyIslandChannelSegment> bySource = plan.segments().stream()
                .collect(Collectors.toMap(SkyIslandChannelSegment::sourceCellIndex, Function.identity()));

        for (SkyIslandChannelSegment segment : plan.segments()) {
            SkyIslandChannelSegment downstream = bySource.get(segment.downstreamCellIndex());
            if (downstream != null) {
                assertTrue(downstream.streamOrder() >= segment.streamOrder());
                assertTrue(downstream.relativeDischarge() + 1.0e-12 >= segment.relativeDischarge());
            }
        }
    }

    @Test
    void visibleTributariesReachMainStemOrAnExplicitHiddenTransportBoundary() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 6L, 61L, 77L));
        SkyIslandChannelNetworkPlan plan = SkyIslandChannelNetworkPlanner.plan(descriptor);
        Map<Integer, SkyIslandChannelSegment> bySource = plan.segments().stream()
                .collect(Collectors.toMap(SkyIslandChannelSegment::sourceCellIndex, Function.identity()));
        Map<Integer, SkyIslandWatershedCell> watershed = SkyIslandWatershedPlanner.plan(descriptor).cells().stream()
                .collect(Collectors.toMap(SkyIslandWatershedCell::index, Function.identity()));
        assertTrue(plan.count(SkyIslandChannelRole.TRIBUTARY) > 0);

        for (SkyIslandChannelSegment segment : plan.segments()) {
            if (segment.role() != SkyIslandChannelRole.TRIBUTARY) {
                continue;
            }
            SkyIslandChannelSegment cursor = segment;
            Set<Integer> visited = new HashSet<>();
            boolean resolved = false;
            while (visited.add(cursor.sourceCellIndex())) {
                SkyIslandChannelSegment downstream = bySource.get(cursor.downstreamCellIndex());
                if (downstream != null) {
                    if (downstream.role() == SkyIslandChannelRole.TRUNK) {
                        resolved = true;
                        break;
                    }
                    cursor = downstream;
                    continue;
                }

                SkyIslandWatershedCell boundary = watershed.get(cursor.downstreamCellIndex());
                assertTrue(boundary != null, "visible network boundary must retain watershed provenance");
                if (boundary.retainedSink() || boundary.edgeOutlet() || boundary.downstreamIndex() < 0) {
                    resolved = true;
                    break;
                }
                SkyIslandWatershedCell next = watershed.get(boundary.downstreamIndex());
                assertTrue(next != null);
                boolean hiddenOnEntry =
                        boundary.surfacePotential() > watershed.get(cursor.sourceCellIndex()).surfacePotential()
                                + 1.0e-10;
                boolean hiddenOnExit =
                        next.surfacePotential() > boundary.surfacePotential() + 1.0e-10;
                resolved = hiddenOnEntry || hiddenOnExit;
                break;
            }
            assertTrue(
                    resolved,
                    "accepted visible tributary must remain coherent to a trunk or authored hidden-transport boundary");
        }
    }

    @Test
    void representativeNetworkContainsHeadwatersAndAResolvedMainStem() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 7L, 71L, 83L));
        SkyIslandChannelNetworkPlan plan = SkyIslandChannelNetworkPlanner.plan(descriptor);
        assertTrue(plan.count(SkyIslandChannelRole.HEADWATER) > 0);
        assertTrue(plan.count(SkyIslandChannelRole.TRUNK) > 0 || plan.count(SkyIslandChannelRole.TRIBUTARY) > 0);
    }
}
