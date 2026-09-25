package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandHydrologicTerrainSurfacePlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-12;

    @Test
    void derivedSurfaceIsDeterministicNormalizedAndPreservesUnownedCells() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandHydrologicTerrainInfluencePlan influence =
                SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor);
        SkyIslandHydrologicTerrainSurfacePlan first =
                SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
        SkyIslandHydrologicTerrainSurfacePlan second =
                SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(watershed.cells().size(), first.cells().size());
        assertEquals(watershed.gridSize(), first.gridSize());
        assertEquals(watershed.spacing(), first.spacing());

        Set<Integer> influenced = new HashSet<>();
        for (SkyIslandHydrologicTerrainCell cell : influence.cells()) {
            influenced.add(cell.watershedCellIndex());
        }
        for (SkyIslandWaterbodyMargin margin :
                SkyIslandWaterbodyMarginPlanner.plan(descriptor).margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                influenced.add(cell.watershedCellIndex());
            }
        }
        Set<Integer> unique = new HashSet<>();
        for (SkyIslandHydrologicTerrainSurfaceCell cell : first.cells()) {
            assertTrue(unique.add(cell.watershedCellIndex()));
            assertNormalized(cell.baseElevationPotential());
            assertNormalized(cell.adjustedElevationPotential());
            assertTrue(cell.netAdjustment() >= -SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING - EPSILON);
            assertTrue(cell.netAdjustment() <= SkyIslandHydrologicTerrainSurfacePlanner.MAX_RAISING + EPSILON);
            if (!influenced.contains(cell.watershedCellIndex())) {
                assertEquals(cell.baseElevationPotential(), cell.adjustedElevationPotential());
            }
        }
    }

    @Test
    void retainedWaterStaysExactWhileAcceptedDryMarginsGradeTowardTheDatum() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandHydrologicTerrainSurfacePlan surface =
                SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);

        Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> byIndex = new HashMap<>();
        for (SkyIslandHydrologicTerrainSurfaceCell cell : surface.cells()) {
            byIndex.put(cell.watershedCellIndex(), cell);
        }

        Set<Integer> retained = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                retained.add(cell.watershedCellIndex());
            }
        }
        assertFalse(retained.isEmpty());
        for (int index : retained) {
            SkyIslandHydrologicTerrainSurfaceCell cell = byIndex.get(index);
            assertEquals(cell.baseElevationPotential(), cell.adjustedElevationPotential());
            assertEquals(0.0, cell.waterbodyMarginAdjustment(), EPSILON);
        }

        boolean sawGradedMargin = false;
        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            double datum = margin.footprint().waterSurfacePotential();
            for (SkyIslandWaterbodyMarginCell marginCell : margin.cells()) {
                SkyIslandHydrologicTerrainSurfaceCell cell =
                        byIndex.get(marginCell.watershedCellIndex());
                double base = cell.baseElevationPotential();
                double adjusted = cell.adjustedElevationPotential();
                assertEquals(
                        cell.netAdjustment(),
                        cell.waterbodyMarginAdjustment(),
                        EPSILON);
                if (base < datum - EPSILON) {
                    assertTrue(adjusted >= base - EPSILON);
                    assertTrue(cell.waterbodyMarginAdjustment() >= -EPSILON);
                    assertTrue(adjusted <= datum + EPSILON);
                } else if (base > datum + EPSILON) {
                    assertTrue(adjusted <= base + EPSILON);
                    assertTrue(cell.waterbodyMarginAdjustment() <= EPSILON);
                    assertTrue(adjusted >= datum - EPSILON);
                } else {
                    assertEquals(base, adjusted, EPSILON);
                }
                if (cell.changed()) {
                    sawGradedMargin = true;
                }
            }
        }
        assertTrue(sawGradedMargin, "reference retained water must exercise a graded dry margin");
    }

    @Test
    void representativeIslandsProduceBoundedHydrologicReliefChanges() {
        boolean sawLowering = false;
        boolean sawRaising = false;
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandHydrologicTerrainSurfacePlan surface =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor(key));
            assertTrue(surface.changedCellCount() > 0);
            assertTrue(surface.changedCellCount() < surface.cells().size());
            assertTrue(surface.maxLowering() <= SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING + EPSILON);
            assertTrue(surface.maxRaising() <= SkyIslandHydrologicTerrainSurfacePlanner.MAX_RAISING + EPSILON);
            sawLowering |= surface.loweredCellCount() > 0;
            sawRaising |= surface.raisedCellCount() > 0;
        }
        assertTrue(sawLowering);
        assertTrue(sawRaising);
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
