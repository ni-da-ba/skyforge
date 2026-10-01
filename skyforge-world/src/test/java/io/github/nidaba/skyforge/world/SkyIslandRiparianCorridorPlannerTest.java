package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandRiparianCorridorPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void corridorIsDeterministicUniqueAndRespectsHydrologicOwnershipPrecedence() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandRiparianCorridorPlan first = SkyIslandRiparianCorridorPlanner.plan(descriptor);
        SkyIslandRiparianCorridorPlan second = SkyIslandRiparianCorridorPlanner.plan(descriptor);
        SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan waterbodyMargins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.cells().isEmpty());

        Set<Integer> unique = new HashSet<>();
        Set<Integer> reserved = new HashSet<>();
        Set<Integer> channelCells = new HashSet<>();
        for (SkyIslandChannelSegment segment : channels.segments()) {
            channelCells.add(segment.sourceCellIndex());
            channelCells.add(segment.downstreamCellIndex());
        }
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }
        for (SkyIslandWaterbodyMargin margin : waterbodyMargins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }

        for (SkyIslandRiparianCell cell : first.cells()) {
            assertTrue(unique.add(cell.watershedCellIndex()));
            assertFalse(channelCells.contains(cell.watershedCellIndex()));
            assertFalse(reserved.contains(cell.watershedCellIndex()));
            assertTrue(cell.channelDistance() == 1 || cell.channelDistance() == 2);
            assertTrue(cell.riparianPotential() >= 0.54 - 1.0e-12);
        }
    }

    @Test
    void representativeChannelNetworksProduceBoundedRiparianCells() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandRiparianCorridorPlan corridor = SkyIslandRiparianCorridorPlanner.plan(descriptor);

            assertFalse(channels.segments().isEmpty());
            assertFalse(corridor.cells().isEmpty());
            assertTrue(corridor.cellCount() < watershed.cells().size());
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
