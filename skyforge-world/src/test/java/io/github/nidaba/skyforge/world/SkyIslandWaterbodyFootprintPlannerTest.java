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
        assertEquals(
                candidates.candidates().size(),
                first.footprints().stream().mapToInt(SkyIslandWaterbodyFootprint::sourceCandidateCount).sum());

        for (SkyIslandWaterbodyFootprint footprint : first.footprints()) {
            assertTrue(footprint.sourceCandidateCount() >= 1);
            assertTrue(footprint.depressionCellCount() >= 1);
            assertTrue(footprint.inundatedCellCount() >= 1);
            assertTrue(footprint.inundatedCellCount() <= footprint.depressionCellCount());
            assertTrue(footprint.inundatedDepressionFraction() > 0.0);
            assertTrue(footprint.inundatedDepressionFraction() <= 1.0);
            assertTrue(footprint.waterSurfacePotential() <= footprint.spillSurfacePotential() + 1.0e-12);
            assertTrue(footprint.shorelineCellCount() >= 1);
            for (SkyIslandWaterbodyCandidate source : footprint.sourceCandidates()) {
                assertTrue(footprint.cells().stream()
                        .anyMatch(cell -> cell.watershedCellIndex() == source.sinkCellIndex()));
            }
            assertTrue(footprint.cells().stream()
                    .allMatch(cell -> cell.surfacePotential() <= footprint.waterSurfacePotential() + 1.0e-12));
            assertConnected(footprint, watershed.gridSize());
        }
    }

    @Test
    void overlappingKey83RetentionAnchorsCoalesceIntoOneGeometricFootprint() {
        SkyIslandWaterbodyFootprintPlan plan = SkyIslandWaterbodyFootprintPlanner.plan(descriptor(83L));
        assertEquals(1, plan.footprints().size());
        assertEquals(2, plan.footprints().getFirst().sourceCandidateCount());
        assertFalse(plan.footprints().getFirst().hasMixedKinds());
    }

    @Test
    void cardinallyTouchingRetainedSeedsAreOneConnectedWaterbodyRelation() {
        assertTrue(SkyIslandWaterbodyFootprintPlanner.intersectsOrTouches(
                Set.of(5), Set.of(6), 4));
        assertTrue(SkyIslandWaterbodyFootprintPlanner.intersectsOrTouches(
                Set.of(5), Set.of(5), 4));
        assertFalse(SkyIslandWaterbodyFootprintPlanner.intersectsOrTouches(
                Set.of(5), Set.of(10), 4),
                "corner-only contact must not merge retained waterbodies");
    }

    @Test
    void hydrologyReferenceNeverLeavesCardinallyTouchingRetainedFootprintsSeparate() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandWaterbodyFootprintPlan plan = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        int gridSize = SkyIslandWatershedPlanner.plan(descriptor).gridSize();

        for (int first = 0; first < plan.footprints().size(); first++) {
            Set<Integer> firstCells = plan.footprints().get(first).cells().stream()
                    .map(SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                    .collect(java.util.stream.Collectors.toSet());
            for (int second = first + 1; second < plan.footprints().size(); second++) {
                Set<Integer> secondCells = plan.footprints().get(second).cells().stream()
                        .map(SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                        .collect(java.util.stream.Collectors.toSet());
                assertFalse(
                        SkyIslandWaterbodyFootprintPlanner.intersectsOrTouches(
                                firstCells, secondCells, gridSize),
                        "distinct retained footprints must not share a wet cardinal edge");
            }
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

        int start = footprint.sourceCandidates().getFirst().sinkCellIndex();
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % gridSize;
            int z = current / gridSize;
            int[] neighbors = {
                    z > 0 ? current - gridSize : -1,
                    x > 0 ? current - 1 : -1,
                    x + 1 < gridSize ? current + 1 : -1,
                    z + 1 < gridSize ? current + gridSize : -1
            };
            for (int neighbor : neighbors) {
                if (neighbor >= 0
                        && footprintIndices.contains(neighbor)
                        && visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        assertEquals(footprintIndices, visited);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
