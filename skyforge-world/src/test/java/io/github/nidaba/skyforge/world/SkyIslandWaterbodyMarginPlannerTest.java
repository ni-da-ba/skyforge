package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandWaterbodyMarginPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void wetlandMarginsAreDeterministicDryAndNormalized() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandWaterbodyMarginPlan first = SkyIslandWaterbodyMarginPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan second = SkyIslandWaterbodyMarginPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan footprints = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(footprints.footprints().size(), first.margins().size());
        assertEquals(1, first.margins().size());
        assertFalse(first.margins().getFirst().cells().isEmpty());
        assertTrue(first.count(SkyIslandWaterbodyMarginKind.SATURATED_FRINGE) > 0);

        Set<Integer> inundated = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : footprints.footprints()) {
            footprint.cells().forEach(cell -> inundated.add(cell.watershedCellIndex()));
        }

        Set<Integer> marginIndices = new HashSet<>();
        for (SkyIslandWaterbodyMargin margin : first.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                assertTrue(marginIndices.add(cell.watershedCellIndex()));
                assertFalse(inundated.contains(cell.watershedCellIndex()));
                assertTrue(cell.latticeDistance() >= 1 && cell.latticeDistance() <= 2);
                assertTrue(cell.proximityPotential() >= 0.0 && cell.proximityPotential() <= 1.0);
                assertTrue(cell.saturationPotential() >= 0.0 && cell.saturationPotential() <= 1.0);
                assertTrue(cell.retentionPotential() >= 0.0 && cell.retentionPotential() <= 1.0);
                assertTrue(cell.elevationHeadPotential() >= 0.0 && cell.elevationHeadPotential() <= 1.0);
                assertTrue(cell.marginPotential() >= 0.55 && cell.marginPotential() <= 1.0);
            }
        }
    }

    @Test
    void drainageControlDoesNotInventWaterbodyMargins() {
        SkyIslandWaterbodyMarginPlan plan = SkyIslandWaterbodyMarginPlanner.plan(descriptor(77L));
        assertTrue(plan.margins().isEmpty());
        assertEquals(0, plan.marginCellCount());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
