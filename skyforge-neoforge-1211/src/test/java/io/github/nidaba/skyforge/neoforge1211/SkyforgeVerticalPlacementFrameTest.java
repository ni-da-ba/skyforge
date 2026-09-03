package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeVerticalPlacementFrameTest {
    @Test
    void affineMappingClampsNativeBuildExtremesIntoVolumeEnvelope() {
        int sourceMinimumY = -64;
        int sourceMaximumY = 319;
        int targetMinimumY = 196;
        int targetMaximumY = 268;

        assertEquals(196, SkyforgeVerticalPlacementFrame.mapYForTest(
                -105, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY));
        assertEquals(196, SkyforgeVerticalPlacementFrame.mapYForTest(
                -64, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY));
        assertEquals(220, SkyforgeVerticalPlacementFrame.mapYForTest(
                63, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY));
        assertEquals(268, SkyforgeVerticalPlacementFrame.mapYForTest(
                319, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY));
        assertEquals(268, SkyforgeVerticalPlacementFrame.mapYForTest(
                369, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY));
    }

    @Test
    void mappingIsMonotoneAndDeterministicAcrossNativeFrame() {
        int previous = Integer.MIN_VALUE;
        for (int y = -120; y <= 380; y++) {
            int first = SkyforgeVerticalPlacementFrame.mapYForTest(y, -64, 319, 196, 268);
            int second = SkyforgeVerticalPlacementFrame.mapYForTest(y, -64, 319, 196, 268);
            assertEquals(first, second);
            assertTrue(first >= 196 && first <= 268);
            assertTrue(first >= previous);
            previous = first;
        }
    }

    @Test
    void zeroThicknessTargetCollapsesDeterministically() {
        assertEquals(240, SkyforgeVerticalPlacementFrame.mapYForTest(12, -64, 319, 240, 240));
        assertEquals(240, SkyforgeVerticalPlacementFrame.mapYForTest(999, -64, 319, 240, 240));
    }

    @Test
    void phaseAdmissionRemainsExplicit() {
        assertTrue(SkyforgeVerticalPlacementFrame.usesLocalVerticalFrame(
                GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()));
        assertTrue(SkyforgeVerticalPlacementFrame.usesLocalVerticalFrame(
                GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()));

        assertFalse(SkyforgeVerticalPlacementFrame.usesLocalVerticalFrame(
                GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()));
        assertFalse(SkyforgeVerticalPlacementFrame.usesLocalVerticalFrame(
                GenerationStep.Decoration.FLUID_SPRINGS.ordinal()));
        assertFalse(SkyforgeVerticalPlacementFrame.usesLocalVerticalFrame(
                GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()));
    }
}
