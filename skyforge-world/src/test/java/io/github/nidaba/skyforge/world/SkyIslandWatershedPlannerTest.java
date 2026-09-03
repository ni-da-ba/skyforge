package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandWatershedPlannerTest {
    @Test
    void planIsDeterministicAndAccumulatesFlow() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 5L, 51L, 77L));
        SkyIslandWatershedPlan a = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWatershedPlan b = SkyIslandWatershedPlanner.plan(descriptor);
        assertEquals(a, b);
        assertTrue(a.cells().size() > 500);
        assertTrue(a.maxFlowAccumulation() > a.cells().stream().mapToDouble(SkyIslandWatershedCell::localRunoff).max().orElseThrow());
    }

    @Test
    void downstreamEdgesAreAcyclicAndPointToExistingCells() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 5L, 51L, 111L));
        SkyIslandWatershedPlan plan = SkyIslandWatershedPlanner.plan(descriptor);
        java.util.Set<Integer> indices = plan.cells().stream().map(SkyIslandWatershedCell::index).collect(java.util.stream.Collectors.toSet());
        for (SkyIslandWatershedCell cell : plan.cells()) {
            if (cell.downstreamIndex() >= 0) assertTrue(indices.contains(cell.downstreamIndex()));
            int cursor = cell.downstreamIndex();
            int steps = 0;
            while (cursor >= 0 && steps <= plan.cells().size()) {
                int target = cursor;
                cursor = plan.cells().stream().filter(c -> c.index() == target).findFirst().orElseThrow().downstreamIndex();
                steps++;
            }
            assertTrue(steps <= plan.cells().size());
        }
    }
}
