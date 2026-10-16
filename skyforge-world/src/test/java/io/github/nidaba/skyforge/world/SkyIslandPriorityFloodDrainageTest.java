package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandPriorityFloodDrainageTest {
    @Test
    void depressionFillsOnlyToItsLowestBoundarySpillPathAndAreaIsConserved() {
        int[][] elevations = new int[5][5];
        for (int z = 0; z < elevations.length; z++) {
            java.util.Arrays.fill(elevations[z], 10);
        }
        elevations[2][0] = 4;
        elevations[2][1] = 5;
        elevations[2][2] = 0;

        SkyIslandPriorityFloodDrainage.Result result =
                SkyIslandPriorityFloodDrainage.analyze(elevations);

        assertEquals(5, result.conditionedElevationAt(1, 2));
        assertEquals(5, result.conditionedElevationAt(2, 2));
        assertEquals(0, result.conditionedElevationAt(3, 2));
        assertEquals(-1, result.receiverIndexAt(0, 2));

        long outletArea = 0L;
        for (int z = 0; z < result.height(); z++) {
            for (int x = 0; x < result.width(); x++) {
                if (result.receiverIndexAt(x, z) == -1) {
                    outletArea += result.contributingAreaAt(x, z);
                } else {
                    int receiver = result.receiverIndexAt(x, z);
                    assertTrue(receiver >= 0 && receiver < result.width() * result.height());
                    assertTrue(result.contributingAreaAt(x, z) >= 1);
                }
            }
        }
        assertEquals(25L, outletArea);
    }

    @Test
    void flatDrainageUsesStableAcyclicReceiversWithoutChangingElevation() {
        int[][] flat = {
            {7, 7, 7, 7, 7},
            {7, 7, 7, 7, 7},
            {7, 7, 7, 7, 7},
            {7, 7, 7, 7, 7},
            {7, 7, 7, 7, 7}
        };

        SkyIslandPriorityFloodDrainage.Result first =
                SkyIslandPriorityFloodDrainage.analyze(flat);
        SkyIslandPriorityFloodDrainage.Result second =
                SkyIslandPriorityFloodDrainage.analyze(flat);
        long boundaryArea = 0L;
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                assertEquals(7, first.conditionedElevationAt(x, z));
                assertEquals(first.receiverIndexAt(x, z), second.receiverIndexAt(x, z));
                assertEquals(first.contributingAreaAt(x, z), second.contributingAreaAt(x, z));
                int receiver = first.receiverIndexAt(x, z);
                if (x == 0 || z == 0 || x == 4 || z == 4) {
                    assertEquals(-1, receiver);
                    boundaryArea += first.contributingAreaAt(x, z);
                } else {
                    assertNotEquals(z * 5 + x, receiver);
                }
            }
        }
        assertEquals(25L, boundaryArea);
    }

    @Test
    void rejectsEmptyAndRaggedRasters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPriorityFloodDrainage.analyze(new int[0][]));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPriorityFloodDrainage.analyze(new int[][] {{}}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPriorityFloodDrainage.analyze(
                        new int[][] {{1, 2}, {3}}));
    }
}
