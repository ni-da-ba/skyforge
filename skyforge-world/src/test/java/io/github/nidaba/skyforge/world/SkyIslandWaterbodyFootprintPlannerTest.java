package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandWaterbodyFootprintPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void retainedFootprintsAreDeterministicConnectedAndBelowSpillSurface() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandWaterbodyFootprintPlan first = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan second = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan candidates = SkyIslandWaterbodyPlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.footprints().isEmpty());
        assertEquals(candidates.candidates().size(), first.footprints().size());

        for (SkyIslandWaterbodyFootprint footprint : first.footprints()) {
            assertTrue(footprint.depressionCellCount() >= 1);
            assertTrue(footprint.inundatedCellCount() >= 1);
            assertTrue(footprint.inundatedCellCount() <= footprint.depressionCellCount());
            assertTrue(footprint.inundatedDepressionFraction() > 0.0);
            assertTrue(footprint.inundatedDepressionFraction() <= 1.0);
            assertTrue(footprint.waterSurfacePotential() <= footprint.spillSurfacePotential() + 1.0e-12);
            assertTrue(footprint.shorelineCellCount() >= 1);
            assertTrue(footprint.cells().stream()
                    .anyMatch(cell -> cell.watershedCellIndex() == footprint.candidate().sinkCellIndex()));
            assertTrue(footprint.cells().stream()
                    .allMatch(cell -> cell.surfacePotential() <= footprint.waterSurfacePotential() + 1.0e-12));
            assertConnected(footprint, watershed.gridSize());
        }
    }

    @Test
    void watershedCarriesPriorityFloodSpillMetadata() {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor(83L));
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            assertTrue(cell.spillSurfacePotential() + 1.0e-12 >= cell.surfacePotential());
            assertEquals(
                    Math.max(0.0, cell.spillSurfacePotential() - cell.surfacePotential()),
                    cell.fillDepthPotential(),
                    1.0e-12);
        }
        assertTrue(watershed.cells().stream()
                .filter(SkyIslandWatershedCell::retainedSink)
                .allMatch(cell -> cell.fillDepthPotential() > 0.0));
    }

    @Test
    void drainageControlDoesNotInventFootprints() {
        assertTrue(SkyIslandWaterbodyFootprintPlanner.plan(descriptor(77L)).footprints().isEmpty());
    }

    private static void assertConnected(SkyIslandWaterbodyFootprint footprint, int gridSize) {
        Set<Integer> footprintIndices = new HashSet<>();
        for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
            footprintIndices.add(cell.watershedCellIndex());
        }

        int start = footprint.candidate().sinkCellIndex();
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % gridSize;
            int z = current / gridSize;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                        continue;
                    }
                    int neighbor = nz * gridSize + nx;
                    if (footprintIndices.contains(neighbor) && visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        assertEquals(footprintIndices, visited);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
