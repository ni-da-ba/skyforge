package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Deterministic Priority-Flood conditioning and D8 contributing-area evidence for a raster DEM.
 *
 * <p>This is a terrain-diagnostic primitive, not authority to change an authored channel graph.
 * Every boundary cell is treated as an outlet. Interior cells are assigned to the first
 * already-visited neighbor that drains outward through the Priority-Flood traversal; this gives
 * flats a deterministic acyclic receiver tree without perturbing their conditioned elevations.
 */
public final class SkyIslandPriorityFloodDrainage {
    private static final int[][] NEIGHBORS = {
        {-1, -1}, {0, -1}, {1, -1},
        {-1, 0},              {1, 0},
        {-1, 1},  {0, 1},     {1, 1}
    };

    private SkyIslandPriorityFloodDrainage() {}

    /**
     * Conditions depressions to their lowest boundary spill elevation, constructs deterministic
     * single-receiver D8 drainage, then accumulates one unit of contributing area per cell.
     *
     * @param elevations row-major elevations, indexed as elevations[z][x]
     * @return immutable routing and accumulation evidence
     */
    public static Result analyze(int[][] elevations) {
        int height = Objects.requireNonNull(elevations, "elevations").length;
        if (height == 0) {
            throw new IllegalArgumentException("elevation raster must not be empty");
        }
        int width = Objects.requireNonNull(elevations[0], "elevation row").length;
        if (width == 0) {
            throw new IllegalArgumentException("elevation raster rows must not be empty");
        }
        for (int z = 0; z < height; z++) {
            if (Objects.requireNonNull(elevations[z], "elevation row").length != width) {
                throw new IllegalArgumentException("elevation raster must be rectangular");
            }
        }

        int[][] conditioned = new int[height][width];
        int[][] receiver = new int[height][width];
        boolean[][] visited = new boolean[height][width];
        PriorityQueue<Entry> frontier = new PriorityQueue<>(
                Comparator.comparingInt(Entry::elevation).thenComparingInt(Entry::index));
        List<Integer> popOrder = new ArrayList<>(Math.multiplyExact(width, height));

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                receiver[z][x] = -1;
                if (isBoundary(x, z, width, height)) {
                    visited[z][x] = true;
                    conditioned[z][x] = elevations[z][x];
                    frontier.add(new Entry(elevations[z][x], z * width + x, x, z));
                }
            }
        }

        while (!frontier.isEmpty()) {
            Entry current = frontier.remove();
            popOrder.add(current.index());
            for (int[] offset : NEIGHBORS) {
                int x = current.x() + offset[0];
                int z = current.z() + offset[1];
                if (x < 0 || x >= width || z < 0 || z >= height || visited[z][x]) {
                    continue;
                }
                visited[z][x] = true;
                conditioned[z][x] = Math.max(elevations[z][x], current.elevation());
                receiver[z][x] = current.index();
                frontier.add(new Entry(
                        conditioned[z][x], z * width + x, x, z));
            }
        }

        long[][] accumulation = new long[height][width];
        for (int z = 0; z < height; z++) {
            java.util.Arrays.fill(accumulation[z], 1L);
        }
        for (int i = popOrder.size() - 1; i >= 0; i--) {
            int index = popOrder.get(i);
            int z = index / width;
            int x = index % width;
            int downstream = receiver[z][x];
            if (downstream >= 0) {
                accumulation[downstream / width][downstream % width] =
                        Math.addExact(
                                accumulation[downstream / width][downstream % width],
                                accumulation[z][x]);
            }
        }

        return new Result(width, height, conditioned, receiver, accumulation);
    }

    private static boolean isBoundary(int x, int z, int width, int height) {
        return x == 0 || z == 0 || x == width - 1 || z == height - 1;
    }

    private record Entry(int elevation, int index, int x, int z) {}

    public static final class Result {
        private final int width;
        private final int height;
        private final int[][] conditionedElevation;
        private final int[][] receiverIndex;
        private final long[][] contributingArea;

        private Result(
                int width,
                int height,
                int[][] conditionedElevation,
                int[][] receiverIndex,
                long[][] contributingArea) {
            this.width = width;
            this.height = height;
            this.conditionedElevation = copy(conditionedElevation);
            this.receiverIndex = copy(receiverIndex);
            this.contributingArea = copy(contributingArea);
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int conditionedElevationAt(int x, int z) {
            checkCell(x, z);
            return conditionedElevation[z][x];
        }

        /** Returns row-major receiver index, or -1 for boundary outlet cells. */
        public int receiverIndexAt(int x, int z) {
            checkCell(x, z);
            return receiverIndex[z][x];
        }

        public long contributingAreaAt(int x, int z) {
            checkCell(x, z);
            return contributingArea[z][x];
        }

        private void checkCell(int x, int z) {
            if (x < 0 || x >= width || z < 0 || z >= height) {
                throw new IndexOutOfBoundsException("cell (" + x + ", " + z + ")");
            }
        }

        private static int[][] copy(int[][] source) {
            int[][] result = new int[source.length][];
            for (int z = 0; z < source.length; z++) {
                result[z] = source[z].clone();
            }
            return result;
        }

        private static long[][] copy(long[][] source) {
            long[][] result = new long[source.length][];
            for (int z = 0; z < source.length; z++) {
                result[z] = source[z].clone();
            }
            return result;
        }
    }
}
