package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyIslandTerrainSampleContextTest {

    @Test
    void contextRejectsNonFiniteCoordinatesAndNullSemantic() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandTerrainSampleContext(Double.NaN, 0.0, 0.0, SkyIslandTerrainSemantic.AIR));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandTerrainSampleContext(0.0, Double.POSITIVE_INFINITY, 0.0, SkyIslandTerrainSemantic.AIR));
        assertThrows(NullPointerException.class,
                () -> new SkyIslandTerrainSampleContext(0.0, 0.0, 0.0, null));
    }

    @Test
    void contextCarriesOnlyPositionAndAcceptedTerrainMeaning() {
        var solid = new SkyIslandTerrainSampleContext(
                12.5, 320.0, -7.25, SkyIslandTerrainSemantic.SURFACE_MANTLE);
        var air = new SkyIslandTerrainSampleContext(
                12.5, 400.0, -7.25, SkyIslandTerrainSemantic.AIR);

        assertEquals(12.5, solid.x());
        assertEquals(320.0, solid.y());
        assertEquals(-7.25, solid.z());
        assertEquals(SkyIslandTerrainSemantic.SURFACE_MANTLE, solid.semantic());
        assertTrue(solid.isSolid());
        assertFalse(air.isSolid());
    }

    @Test
    void sampledRegionExportsExactWorldCoordinatesAndSemantic() {
        var grid = new WorldSampleGrid(
                -8.0, 100.0, 24.0,
                4.0, 8.0, 12.0,
                2, 2, 2);
        byte[] semantics = new byte[grid.sampleCount()];
        int target = grid.linearIndex(1, 1, 0);
        semantics[target] = (byte) SkyIslandTerrainSemantic.DEEP_MASS.ordinal();

        var region = new WorldRegionTerrain(grid, semantics, 1, 0);
        SkyIslandTerrainSampleContext context = region.sampleContextAt(1, 1, 0);

        assertEquals(-4.0, context.x());
        assertEquals(108.0, context.y());
        assertEquals(24.0, context.z());
        assertEquals(SkyIslandTerrainSemantic.DEEP_MASS, context.semantic());
        assertTrue(context.isSolid());

        SkyIslandTerrainSampleContext untouchedAir = region.sampleContextAt(0, 0, 0);
        assertEquals(SkyIslandTerrainSemantic.AIR, untouchedAir.semantic());
        assertFalse(untouchedAir.isSolid());
    }
}
