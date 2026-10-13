package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Conditions a finite terrain raster with Priority-Flood and derives a deterministic D8 receiver
 * forest and accumulated contributing flow.
 *
 * <p>Every valid cell must be eight-neighbor connected to at least one outlet. Depressions are
 * filled to their lowest outlet spill surface; this implementation does not breach terrain or
 * choose a geomorphic channel. Receiver ties are resolved by raster index and the parent-before-
 * child traversal order, so flat regions drain without cycles.
 */
public final class SkyIslandPriorityFloodConditioner {
    private static final int INVALID_RECEIVER = -2;

    private SkyIslandPriorityFloodConditioner() {}

    /** Conditions a fully valid raster with one unit of incremental flow per cell. */
    public static Result condition(
            double[] rawElevation,
            int width,
            int height,
            boolean[] outlets) {
        Objects.requireNonNull(rawElevation, "rawElevation");
        double[] incrementalFlow = new double[rawElevation.length];
        Arrays.fill(incrementalFlow, 1.0);
        boolean[] valid = new boolean[rawElevation.length];
        Arrays.fill(valid, true);
        return condition(rawElevation, incrementalFlow, width, height, valid, outlets);
    }

    /**
     * Conditions a raster and accumulates non-negative cell-local flow toward outlet cells.
     *
     * @param rawElevation row-major terrain elevations; finite values are required for all cells
     * @param incrementalFlow non-negative contribution originating at each valid cell
     * @param width raster width
     * @param height raster height
     * @param valid mask for cells in the connected drainage domain
     * @param outlets outlet mask; every outlet must be valid and at least one is required
     * @return conditioned elevations, D8 receivers, and accumulated flow for every valid cell
     */
    public static Result condition(
            double[] rawElevation,
            double[] incrementalFlow,
            int width,
            int height,
            boolean[] valid,
            boolean[] outlets) {
        Objects.requireNonNull(rawElevation, "rawElevation");
        Objects.requireNonNull(incrementalFlow, "incrementalFlow");
        Objects.requireNonNull(valid, "valid");
        Objects.requireNonNull(outlets, "outlets");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("raster dimensions must be positive");
        }
        int cellCount = Math.multiplyExact(width, height);
        if (rawElevation.length != cellCount
                || incrementalFlow.length != cellCount
                || valid.length != cellCount
                || outlets.length != cellCount) {
            throw new IllegalArgumentException("all raster arrays must match width * height");
        }

        int validCount = 0;
        int outletCount = 0;
        for (int i = 0; i < cellCount; i++) {
            if (!Double.isFinite(rawElevation[i])) {
                throw new IllegalArgumentException("raw elevations must be finite");
            }
            if (!Double.isFinite(incrementalFlow[i]) || incrementalFlow[i] < 0.0) {
                throw new IllegalArgumentException("incremental flow must be finite and non-negative");
            }
            if (valid[i]) {
                validCount++;
                if (outlets[i]) {
                    outletCount++;
                }
            } else if (outlets[i]) {
                throw new IllegalArgumentException("outlets must be inside the valid drainage domain");
            }
        }
        if (validCount == 0 || outletCount == 0) {
            throw new IllegalArgumentException("the drainage domain requires valid cells and an outlet");
        }

        double[] conditioned = new double[cellCount];
        Arrays.fill(conditioned, Double.NaN);
        int[] receiver = new int[cellCount];
        Arrays.fill(receiver, INVALID_RECEIVER);
        double[] accumulatedFlow = new double[cellCount];
        for (int i = 0; i < cellCount; i++) {
            if (valid[i]) {
                conditioned[i] = rawElevation[i];
                receiver[i] = -1;
                accumulatedFlow[i] = incrementalFlow[i];
            }
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator
                .comparingDouble(Node::elevation)
                .thenComparingInt(Node::index));
        boolean[] visited = new boolean[cellCount];
        for (int i = 0; i < cellCount; i++) {
            if (outlets[i]) {
                visited[i] = true;
                open.add(new Node(i, conditioned[i]));
            }
        }

        int[] popOrder = new int[validCount];
        int popCount = 0;
        while (!open.isEmpty()) {
            Node current = open.remove();
            popOrder[popCount++] = current.index();
            int x = current.index() % width;
            int z = current.index() / width;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                        continue;
                    }
                    int next = nz * width + nx;
                    if (!valid[next] || visited[next]) {
                        continue;
                    }
                    visited[next] = true;
                    conditioned[next] = Math.max(rawElevation[next], current.elevation());
                    receiver[next] = current.index();
                    open.add(new Node(next, conditioned[next]));
                }
            }
        }
        if (popCount != validCount) {
            throw new IllegalArgumentException("every valid cell must be connected to an outlet");
        }

        for (int i = popOrder.length - 1; i >= 0; i--) {
            int cell = popOrder[i];
            int parent = receiver[cell];
            if (parent >= 0) {
                accumulatedFlow[parent] += accumulatedFlow[cell];
                if (!Double.isFinite(accumulatedFlow[parent])) {
                    throw new IllegalArgumentException("accumulated flow overflowed");
                }
            }
        }

        return new Result(width, height, valid, conditioned, receiver, accumulatedFlow);
    }

    private record Node(int index, double elevation) {}

    /** Immutable result; array accessors return defensive copies. */
    public static final class Result {
        private final int width;
        private final int height;
        private final boolean[] valid;
        private final double[] conditionedElevations;
        private final int[] receivers;
        private final double[] accumulatedFlow;

        private Result(
                int width,
                int height,
                boolean[] valid,
                double[] conditionedElevations,
                int[] receivers,
                double[] accumulatedFlow) {
            this.width = width;
            this.height = height;
            this.valid = valid.clone();
            this.conditionedElevations = conditionedElevations.clone();
            this.receivers = receivers.clone();
            this.accumulatedFlow = accumulatedFlow.clone();
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public boolean isValid(int cellIndex) {
            return valid[cellIndex];
        }

        public double[] conditionedElevations() {
            return conditionedElevations.clone();
        }

        public int[] receivers() {
            return receivers.clone();
        }

        public double[] accumulatedFlow() {
            return accumulatedFlow.clone();
        }

        /** Returns cell indices from {@code startCellIndex} downstream to its outlet. */
        public List<Integer> traceToOutlet(int startCellIndex) {
            if (startCellIndex < 0 || startCellIndex >= receivers.length || !valid[startCellIndex]) {
                throw new IllegalArgumentException("start cell must be a valid raster cell");
            }
            List<Integer> path = new ArrayList<>();
            int current = startCellIndex;
            while (current >= 0) {
                path.add(current);
                if (path.size() > receivers.length) {
                    throw new IllegalStateException("receiver graph contains a cycle");
                }
                current = receivers[current];
            }
            if (current != -1) {
                throw new IllegalStateException("receiver chain leaves the valid drainage domain");
            }
            return List.copyOf(path);
        }
    }
}
