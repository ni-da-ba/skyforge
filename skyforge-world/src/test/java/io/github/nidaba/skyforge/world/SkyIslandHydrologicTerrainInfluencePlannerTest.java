package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandHydrologicTerrainInfluencePlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void influenceIsDeterministicUniqueNormalizedAndRespectsStandingWaterOwnership() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandHydrologicTerrainInfluencePlan first = SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor);
        SkyIslandHydrologicTerrainInfluencePlan second = SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.cells().isEmpty());

        Set<Integer> reserved = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }
        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }

        Set<Integer> unique = new HashSet<>();
        for (SkyIslandHydrologicTerrainCell cell : first.cells()) {
            assertTrue(unique.add(cell.watershedCellIndex()));
            assertFalse(reserved.contains(cell.watershedCellIndex()));
            assertNormalized(cell.incisionPotential());
            assertNormalized(cell.depositionPotential());
            assertNormalized(cell.floodplainPotential());
            assertNormalized(cell.dropShapingPotential());
            assertTrue(cell.dominantPotential() > 0.0);
        }
    }

    @Test
    void representativeIslandsProduceBoundedAndVariedTerrainResponses() {
        boolean sawIncision = false;
        boolean sawDeposition = false;
        boolean sawFloodplain = false;
        boolean sawDropShaping = false;

        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            SkyIslandHydrologicTerrainInfluencePlan plan = SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor);

            assertFalse(plan.cells().isEmpty());
            assertTrue(plan.cells().size() < watershed.cells().size());
            sawIncision |= plan.maxIncisionPotential() > 0.0;
            sawDeposition |= plan.maxDepositionPotential() > 0.0;
            sawFloodplain |= plan.maxFloodplainPotential() > 0.0;
            sawDropShaping |= plan.maxDropShapingPotential() > 0.0;
        }

        assertTrue(sawIncision);
        assertTrue(sawDeposition);
        assertTrue(sawFloodplain);
        assertTrue(sawDropShaping);
    }

    @Test
    void cascadeHeavyMassifDoesNotBecomeFloodplainDominant() {
        SkyIslandHydrologicTerrainInfluencePlan plan =
                SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor(811L));

        assertTrue(
                plan.count(SkyIslandHydrologicTerrainResponseKind.INCISION)
                        > plan.count(SkyIslandHydrologicTerrainResponseKind.FLOODPLAIN));
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
