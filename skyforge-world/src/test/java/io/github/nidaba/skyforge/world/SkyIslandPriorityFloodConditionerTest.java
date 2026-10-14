package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandPriorityFloodConditionerTest {
    @Test
    void fillsClosedDepressionToOutletSpillAndConservesWeightedFlow() {
        double[] elevation = {
            5.0, 5.0, 5.0,
            5.0, 0.0, 5.0,
            5.0, 5.0, 5.0
        };
        double[] runoff = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        boolean[] valid = new boolean[9];
        java.util.Arrays.fill(valid, true);
        boolean[] outlets = new boolean[9];
        outlets[0] = true;

        SkyIslandPriorityFloodConditioner.Result result =
                SkyIslandPriorityFloodConditioner.condition(
                        elevation, runoff, 3, 3, valid, outlets);

        assertEquals(5.0, result.conditionedElevations()[4], 0.0);
        assertEquals(45.0, result.accumulatedFlow()[0], 0.0);
        assertEquals(0, result.traceToOutlet(4).getLast());
    }

    @Test
    void preservesAnAlreadyDrainingEightNeighborSlope() {
        double[] elevation = {
            8.0, 7.0, 6.0,
            7.0, 6.0, 5.0,
            6.0, 5.0, 4.0
        };
        boolean[] outlets = new boolean[9];
        outlets[8] = true;

        SkyIslandPriorityFloodConditioner.Result result =
                SkyIslandPriorityFloodConditioner.condition(elevation, 3, 3, outlets);

        assertArrayEquals(elevation, result.conditionedElevations(), 0.0);
        assertEquals(9.0, result.accumulatedFlow()[8], 0.0);
        for (int cell = 0; cell < elevation.length; cell++) {
            assertEquals(8, result.traceToOutlet(cell).getLast());
        }
    }

    @Test
    void flatDrainageUsesDeterministicAcyclicReceivers() {
        double[] elevation = new double[25];
        boolean[] outlets = new boolean[25];
        outlets[12] = true;

        SkyIslandPriorityFloodConditioner.Result first =
                SkyIslandPriorityFloodConditioner.condition(elevation, 5, 5, outlets);
        SkyIslandPriorityFloodConditioner.Result second =
                SkyIslandPriorityFloodConditioner.condition(elevation, 5, 5, outlets);

        assertArrayEquals(first.receivers(), second.receivers());
        assertArrayEquals(first.accumulatedFlow(), second.accumulatedFlow(), 0.0);
        assertEquals(25.0, first.accumulatedFlow()[12], 0.0);
        for (int cell = 0; cell < elevation.length; cell++) {
            List<Integer> path = first.traceToOutlet(cell);
            assertEquals(12, path.getLast());
            assertTrue(path.size() <= elevation.length);
        }

        double[] leaked = first.conditionedElevations();
        leaked[0] = Double.NaN;
        assertEquals(0.0, first.conditionedElevations()[0], 0.0);
    }

    @Test
    void rejectsDisconnectedValidTerrainAndOutletsOutsideTheDomain() {
        double[] elevation = new double[9];
        double[] runoff = new double[9];
        boolean[] valid = new boolean[9];
        valid[0] = true;
        valid[8] = true;
        boolean[] outlets = new boolean[9];
        outlets[0] = true;

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPriorityFloodConditioner.condition(
                        elevation, runoff, 3, 3, valid, outlets));

        valid[8] = false;
        outlets[8] = true;
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPriorityFloodConditioner.condition(
                        elevation, runoff, 3, 3, valid, outlets));
    }
}
