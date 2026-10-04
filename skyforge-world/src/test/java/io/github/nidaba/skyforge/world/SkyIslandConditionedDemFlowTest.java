package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandConditionedDemFlowTest {
    @Test
    void priorityFloodRaisesClosedPitOnlyToItsLowestEdgeSpill() {
        int width = 5;
        int height = 5;
        double[] dem = new double[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                dem[z * width + x] = x == 0 || z == 0 || x == width - 1 || z == height - 1
                        ? 1.0
                        : 2.0;
            }
        }
        dem[2 * width + 2] = -10.0;

        SkyIslandConditionedDemFlow.Result result =
                SkyIslandConditionedDemFlow.analyze(width, height, 1.0, dem);

        assertEquals(2.0, result.filledElevations()[2 * width + 2], 0.0);
    }

    @Test
    void d8AccumulationIsDeterministicAndConservesCellCountsAtEdgeOutlets() {
        int width = 7;
        int height = 6;
        double[] dem = new double[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                dem[z * width + x] = 20.0 - 2.0 * x + 0.5 * z;
            }
        }

        SkyIslandConditionedDemFlow.Result first =
                SkyIslandConditionedDemFlow.analyze(width, height, 2.0, dem);
        SkyIslandConditionedDemFlow.Result second =
                SkyIslandConditionedDemFlow.analyze(width, height, 2.0, dem);
        int[] receivers = first.receivers();
        double[] accumulation = first.accumulation();
        double outletAccumulation = 0.0;
        for (int cell = 0; cell < receivers.length; cell++) {
            if (receivers[cell] < 0) {
                outletAccumulation += accumulation[cell];
            } else {
                assertTrue(receivers[cell] >= 0 && receivers[cell] < receivers.length);
            }
        }

        assertArrayEquals(first.filledElevations(), second.filledElevations(), 0.0);
        assertArrayEquals(receivers, second.receivers());
        assertArrayEquals(accumulation, second.accumulation(), 0.0);
        assertEquals(width * height, outletAccumulation, 0.0);
    }

    @Test
    void rejectsMalformedDemRatherThanReturningPartialRouting() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandConditionedDemFlow.analyze(1, 4, 1.0, new double[4]));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandConditionedDemFlow.analyze(2, 2, 0.0, new double[4]));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandConditionedDemFlow.analyze(3, 3, 1.0, new double[8]));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandConditionedDemFlow.analyze(
                        3, 3, 1.0, new double[] {0, 0, 0, 0, Double.NaN, 0, 0, 0, 0}));
    }
}
