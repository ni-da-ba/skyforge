package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandGeologicRegionPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void planIsDeterministicAndRegionsAreConnected() {
        SkyIslandDescriptor descriptor = descriptor(913L);
        SkyIslandGeologicRegionPlan first = SkyIslandGeologicRegionPlanner.plan(descriptor);
        SkyIslandGeologicRegionPlan second = SkyIslandGeologicRegionPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.regions().isEmpty());

        for (SkyIslandGeologicRegion region : first.regions()) {
            assertTrue(region.cellCount() >= SkyIslandGeologicRegionPlanner.MIN_REGION_CELLS);
            assertConnected(region);
        }
    }

    @Test
    void allRegionCellsRemainInsideCurrentNaturalizedOwnership() {
        for (long key : new long[] {7L, 10L, 83L, 512L, 811L, 913L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandGeologicRegionPlan plan = SkyIslandGeologicRegionPlanner.plan(descriptor);

            for (SkyIslandGeologicRegion region : plan.regions()) {
                for (SkyIslandGeologicRegionCell cell : region.cells()) {
                    assertTrue(semantic.interiority().sample(cell.position().surfacePosition()) > 0.0);
                }
            }
        }
    }

    @Test
    void geologicalExtremesProduceExpectedMesoscaleSystems() {
        SkyIslandDescriptor hydrologic = extreme(SkyIslandDescriptor::hydrologicalPotential, false);
        SkyIslandDescriptor permeable = extreme(SkyIslandDescriptor::permeability, false);
        SkyIslandDescriptor competent = extreme(SkyIslandDescriptor::rockCompetence, false);
        SkyIslandDescriptor weak = extreme(SkyIslandDescriptor::rockCompetence, true);
        SkyIslandDescriptor eroded = extreme(SkyIslandDescriptor::erosionMaturity, false);

        SkyIslandGeologicRegionPlan hydroPlan = SkyIslandGeologicRegionPlanner.plan(hydrologic);
        SkyIslandGeologicRegionPlan permeablePlan = SkyIslandGeologicRegionPlanner.plan(permeable);

        assertTrue(hydroPlan.cellCount(SkyIslandGeologicRegionKind.AQUIFER_BODY) > 0);
        assertTrue(permeablePlan.cellCount(SkyIslandGeologicRegionKind.AQUIFER_BODY) > 0);
        assertTrue(permeablePlan.cellCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN) > 0);

        assertTrue(
                SkyIslandGeologicRegionPlanner.structuralCorridorCount(weak)
                        >= SkyIslandGeologicRegionPlanner.structuralCorridorCount(competent));
        assertTrue(
                SkyIslandGeologicRegionPlanner.structuralCorridorCount(eroded)
                        >= SkyIslandGeologicRegionPlanner.structuralCorridorCount(competent));
    }

    @Test
    void voidRegionsRemainGroundedInAuth0022VoidSuitability() {
        SkyIslandDescriptor descriptor = extreme(SkyIslandDescriptor::permeability, false);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandGeologicRegionPlan plan = SkyIslandGeologicRegionPlanner.plan(descriptor);

        int cells = 0;
        double sum = 0.0;
        for (SkyIslandGeologicRegion region : plan.regions()) {
            if (region.kind() != SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN) {
                continue;
            }
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                sum += geology.sample(cell.position()).voidFormationPotential();
                cells++;
            }
        }
        assertTrue(cells > 0);
        assertTrue(sum / cells > 0.40);
    }

    private static void assertConnected(SkyIslandGeologicRegion region) {
        Set<Integer> regionIndices = new HashSet<>();
        for (SkyIslandGeologicRegionCell cell : region.cells()) {
            regionIndices.add(cell.index());
        }

        Set<Integer> visited = new HashSet<>();
        java.util.ArrayDeque<SkyIslandGeologicRegionCell> queue = new java.util.ArrayDeque<>();
        queue.add(region.cells().getFirst());

        while (!queue.isEmpty()) {
            SkyIslandGeologicRegionCell cell = queue.removeFirst();
            if (!visited.add(cell.index())) {
                continue;
            }
            for (SkyIslandGeologicRegionCell candidate : region.cells()) {
                int distance = Math.abs(candidate.xIndex() - cell.xIndex())
                        + Math.abs(candidate.depthIndex() - cell.depthIndex())
                        + Math.abs(candidate.zIndex() - cell.zIndex());
                if (distance == 1 && !visited.contains(candidate.index())) {
                    queue.addLast(candidate);
                }
            }
        }
        assertEquals(regionIndices.size(), visited.size());
    }

    private static SkyIslandDescriptor extreme(
            java.util.function.ToDoubleFunction<SkyIslandDescriptor> score,
            boolean minimize) {
        SkyIslandDescriptor best = descriptor(0L);
        double bestScore = score.applyAsDouble(best);
        for (long key = 1; key < 4096; key++) {
            SkyIslandDescriptor candidate = descriptor(key);
            double value = score.applyAsDouble(candidate);
            if ((!minimize && value > bestScore) || (minimize && value < bestScore)) {
                best = candidate;
                bestScore = value;
            }
        }
        return best;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
