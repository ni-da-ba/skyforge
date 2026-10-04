package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Deterministic Priority-Flood conditioning and D8 flow diagnostics for a finite square-cell DEM.
 *
 * <p>The grid boundary is treated as an outlet boundary. Results are diagnostic route evidence;
 * they do not replace Skyforge's authored catchments, terminal fate, or graph connectivity and are
 * not themselves realization authority.
 */
public final class SkyIslandConditionedDemFlow {
    private static final double ELEVATION_EPSILON = 1.0e-12;
    private static final int[][] NEIGHBORS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},             {0, 1},
        {1, -1},  {1, 0},    {1, 1}
    };

    private SkyIslandConditionedDemFlow() {}

    /**
     * Conditions depressions to the lowest grid-edge spill and computes D8 receivers and
     * cell-count accumulation on the conditioned surface.
     *
     * @param width grid width in cells (at least two)
     * @param height grid height in cells (at least two)
     * @param cellSize positive spacing in world units
     * @param elevations row-major finite terrain elevations, with {@code width * height} entries
     */
    public static Result analyze(
            int width, int height, double cellSize, double[] elevations) {
        Objects.requireNonNull(elevations, "elevations");
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("DEM width and height must be at least two");
        }
        if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
            throw new IllegalArgumentException("cellSize must be finite and positive");
        }
        int cellCount = Math.multiplyExact(width, height);
        if (elevations.length != cellCount) {
            throw new IllegalArgumentException("elevation count must equal width * height");
        }
        for (double elevation : elevations) {
            if (!Double.isFinite(elevation)) {
                throw new IllegalArgumentException("DEM elevations must be finite");
            }
        }

        double[] filled = new double[cellCount];
        boolean[] visited = new boolean[cellCount];
        int[] floodRank = new int[cellCount];
        Arrays.fill(floodRank, -1);
        PriorityQueue<FloodCell> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(FloodCell::elevation)
                        .thenComparingInt(FloodCell::index));
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                if (isBoundary(x, z, width, height)) {
                    int index = index(x, z, width);
                    visited[index] = true;
                    filled[index] = elevations[index];
                    frontier.add(new FloodCell(index, filled[index]));
                }
            }
        }

        int nextRank = 0;
        while (!frontier.isEmpty()) {
            FloodCell current = frontier.remove();
            floodRank[current.index()] = nextRank++;
            int x = current.index() % width;
            int z = current.index() / width;
            for (int[] delta : NEIGHBORS) {
                int nx = x + delta[0];
                int nz = z + delta[1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= height) {
                    continue;
                }
                int neighbor = index(nx, nz, width);
                if (visited[neighbor]) {
                    continue;
                }
                visited[neighbor] = true;
                filled[neighbor] = Math.max(elevations[neighbor], current.elevation());
                frontier.add(new FloodCell(neighbor, filled[neighbor]));
            }
        }

        int[] receiver = new int[cellCount];
        Arrays.fill(receiver, -1);
        for (int z = 1; z + 1 < height; z++) {
            for (int x = 1; x + 1 < width; x++) {
                int from = index(x, z, width);
                receiver[from] = selectReceiver(
                        x, z, width, height, cellSize, filled, floodRank);
                if (receiver[from] < 0) {
                    throw new IllegalStateException(
                            "conditioned interior DEM cell has no descending D8 receiver: " + from);
                }
            }
        }

        double[] accumulation = new double[cellCount];
        Arrays.fill(accumulation, 1.0);
        List<Integer> upstreamFirst = new ArrayList<>(cellCount);
        for (int cell = 0; cell < cellCount; cell++) {
            upstreamFirst.add(cell);
        }
        upstreamFirst.sort(Comparator
                .comparingDouble((Integer cell) -> filled[cell])
                .thenComparingInt(cell -> floodRank[cell])
                .reversed());
        for (int cell : upstreamFirst) {
            int downstream = receiver[cell];
            if (downstream >= 0) {
                accumulation[downstream] += accumulation[cell];
            }
        }

        return new Result(width, height, cellSize, filled, receiver, accumulation, floodRank);
    }

    private static int selectReceiver(
            int x,
            int z,
            int width,
            int height,
            double cellSize,
            double[] filled,
            int[] floodRank) {
        int from = index(x, z, width);
        int steepest = -1;
        double steepestSlope = -1.0;
        int flatPredecessor = -1;
        int greatestPredecessorRank = -1;
        for (int[] delta : NEIGHBORS) {
            int nx = x + delta[0];
            int nz = z + delta[1];
            if (nx < 0 || nx >= width || nz < 0 || nz >= height) {
                continue;
            }
            int to = index(nx, nz, width);
            double difference = filled[from] - filled[to];
            if (difference > 0.0) {
                double distance = cellSize * (delta[0] == 0 || delta[1] == 0
                        ? 1.0
                        : Math.sqrt(2.0));
                double slope = difference / distance;
                if (Double.compare(slope, steepestSlope) > 0
                        || (Double.compare(slope, steepestSlope) == 0
                                && (steepest < 0 || to < steepest))) {
                    steepest = to;
                    steepestSlope = slope;
                }
            } else if (difference == 0.0
                    && floodRank[to] < floodRank[from]
                    && floodRank[to] > greatestPredecessorRank) {
                flatPredecessor = to;
                greatestPredecessorRank = floodRank[to];
            }
        }
        return steepest >= 0 ? steepest : flatPredecessor;
    }

    private static boolean isBoundary(int x, int z, int width, int height) {
        return x == 0 || z == 0 || x == width - 1 || z == height - 1;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    private record FloodCell(int index, double elevation) {}

    /** Immutable result; array accessors return defensive copies. */
    public static final class Result {
        private final int width;
        private final int height;
        private final double cellSize;
        private final double[] filledElevations;
        private final int[] receivers;
        private final double[] accumulation;
        private final int[] floodRank;

        private Result(
                int width,
                int height,
                double cellSize,
                double[] filledElevations,
                int[] receivers,
                double[] accumulation,
                int[] floodRank) {
            this.width = width;
            this.height = height;
            this.cellSize = cellSize;
            this.filledElevations = filledElevations.clone();
            this.receivers = receivers.clone();
            this.accumulation = accumulation.clone();
            this.floodRank = floodRank.clone();
        }

        public int width() { return width; }
        public int height() { return height; }
        public double cellSize() { return cellSize; }
        public double[] filledElevations() { return filledElevations.clone(); }
        public int[] receivers() { return receivers.clone(); }
        public double[] accumulation() { return accumulation.clone(); }
        public int[] floodRank() { return floodRank.clone(); }
    }
}
