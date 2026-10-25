package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkyIslandJunctionWaterSurfaceBridgeTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void continuesReceivingStageToTheSemanticJunctionAnchor() {
        double nodeStage = SkyIslandJunctionWaterSurfaceBridge.receiverStageAtSemanticNode(
                new double[] {0.0, 4.0, 8.0},
                new double[] {10.0, 9.0, 8.0},
                new double[] {2.0, 2.0},
                1);
        assertEquals(12.0, nodeStage, EPSILON);
    }

    @Test
    void bridgesOnlyUnsolvedEndpointGapsAndPreservesSolvedSections() {
        double[] stages = SkyIslandJunctionWaterSurfaceBridge.expandAcrossJunctionGaps(
                new double[] {0.0, 4.0, 8.0, 12.0, 16.0},
                new double[] {10.0, 9.0, 8.0, 7.0, 6.0},
                new double[] {2.0, 2.0, 2.0},
                1,
                3,
                12.0,
                8.0);
        assertArrayEquals(new double[] {12.0, 11.0, 10.0, 9.0, 8.0}, stages, EPSILON);
    }

    @Test
    void rejectsAVisualizationBridgeThatCutsThroughTheSharedBed() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandJunctionWaterSurfaceBridge.expandAcrossJunctionGaps(
                        new double[] {0.0, 4.0, 8.0},
                        new double[] {10.0, 9.0, 8.0},
                        new double[] {2.0, 2.0},
                        1,
                        2,
                        9.5,
                        null));
    }
}
