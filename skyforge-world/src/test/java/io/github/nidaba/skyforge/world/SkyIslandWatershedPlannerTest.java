package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Arrays;
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

    @Test
    void flowDirectionUsesTerrainDescentRatherThanFloodDiscoveryOrder() {
        int gridSize = 3;
        boolean[] active = new boolean[gridSize * gridSize];
        Arrays.fill(active, true);
        double[] surface = {
            0.70, 0.50, 0.60,
            0.10, 0.90, 0.70,
            0.40, 0.20, 0.30
        };
        double[] spill = new double[gridSize * gridSize];
        Arrays.fill(spill, 0.90);
        int[] rank = {
            0, 1, 2,
            3, 8, 4,
            5, 6, 7
        };

        int[] downstream =
                SkyIslandWatershedPlanner.flowDirections(active, surface, spill, rank, gridSize);

        assertEquals(3, downstream[4], "center cell should choose the steepest raw-terrain descent");
    }

    @Test
    void filledDepressionUsesDrainageSurfaceBeforeFlatRankTieBreak() {
        int gridSize = 3;
        boolean[] active = new boolean[gridSize * gridSize];
        Arrays.fill(active, true);
        double[] surface = {
            0.80, 0.70, 0.80,
            0.65, 0.10, 0.60,
            0.80, 0.75, 0.80
        };
        double[] spill = {
            0.55, 0.62, 0.70,
            0.58, 0.80, 0.72,
            0.70, 0.74, 0.78
        };
        int[] rank = {
            0, 1, 2,
            3, 8, 4,
            5, 6, 7
        };

        int[] downstream =
                SkyIslandWatershedPlanner.flowDirections(active, surface, spill, rank, gridSize);

        assertEquals(3, downstream[4], "filled-surface gradient should resolve unavoidable raw uphill flow");
    }

}
