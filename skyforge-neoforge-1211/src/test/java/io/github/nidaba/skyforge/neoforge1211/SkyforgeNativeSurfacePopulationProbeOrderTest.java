package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeSurfacePopulationProbeOrderTest {
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_MIDDLE_LOCAL = 8;

    @Test
    void canonicalProbeOrderIsACompleteUniqueChunkPermutation() {
        List<SkyforgeNativeSurfacePopulationCoordinator.ColumnProbe> order =
                SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder();

        assertEquals(CHUNK_WIDTH * CHUNK_WIDTH, order.size());
        Set<Integer> unique = new HashSet<>();
        for (var probe : order) {
            assertTrue(probe.localX() >= 0 && probe.localX() < CHUNK_WIDTH);
            assertTrue(probe.localZ() >= 0 && probe.localZ() < CHUNK_WIDTH);
            assertEquals(
                    Math.abs(probe.localX() - CHUNK_MIDDLE_LOCAL)
                            + Math.abs(probe.localZ() - CHUNK_MIDDLE_LOCAL),
                    probe.distance());
            unique.add(encode(probe.localX(), probe.localZ()));
        }
        assertEquals(CHUNK_WIDTH * CHUNK_WIDTH, unique.size());
    }

    @Test
    void nearestFirstOrderMatchesLegacyXMajorSelectionForEveryCandidatePair() {
        List<SkyforgeNativeSurfacePopulationCoordinator.ColumnProbe> order =
                SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder();
        int[] rank = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        for (int i = 0; i < order.size(); i++) {
            var probe = order.get(i);
            rank[encode(probe.localX(), probe.localZ())] = i;
        }

        for (int ax = 0; ax < CHUNK_WIDTH; ax++) {
            for (int az = 0; az < CHUNK_WIDTH; az++) {
                for (int bx = 0; bx < CHUNK_WIDTH; bx++) {
                    for (int bz = 0; bz < CHUNK_WIDTH; bz++) {
                        int legacyWinner = legacyWinner(ax, az, bx, bz);
                        int optimizedWinner = rank[encode(ax, az)] <= rank[encode(bx, bz)]
                                ? encode(ax, az)
                                : encode(bx, bz);
                        assertEquals(
                                legacyWinner,
                                optimizedWinner,
                                () -> "probe order changed legacy tie/nearest selection for ("
                                        + ax + "," + az + ") versus (" + bx + "," + bz + ")");
                    }
                }
            }
        }
    }

    private static int legacyWinner(int ax, int az, int bx, int bz) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int z = 0; z < CHUNK_WIDTH; z++) {
                if (!((x == ax && z == az) || (x == bx && z == bz))) {
                    continue;
                }
                int distance = Math.abs(x - CHUNK_MIDDLE_LOCAL) + Math.abs(z - CHUNK_MIDDLE_LOCAL);
                if (distance < bestDistance) {
                    best = encode(x, z);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static int encode(int localX, int localZ) {
        return localX * CHUNK_WIDTH + localZ;
    }
}
