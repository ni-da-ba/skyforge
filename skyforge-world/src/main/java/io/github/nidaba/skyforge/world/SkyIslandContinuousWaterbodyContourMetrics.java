package io.github.nidaba.skyforge.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Measures the sampled continuous shoreline with marching squares.
 *
 * <p>The scalar field is the maximum of the water-level and interiority constraints. The supplied
 * connected mask selects the sink-connected sublevel component; disconnected sublevel samples are
 * treated as dry. Ambiguous diagonal cells use the bilinear asymptotic decider.
 */
final class SkyIslandContinuousWaterbodyContourMetrics {
    private static final double SIGN_EPSILON = 1.0e-12;

    private SkyIslandContinuousWaterbodyContourMetrics() {}

    static Result measure(
            double[] signedValues,
            boolean[] connected,
            int width,
            int height,
            double spacing) {
        Objects.requireNonNull(signedValues, "signedValues");
        Objects.requireNonNull(connected, "connected");
        int sampleCount = Math.multiplyExact(width, height);
        if (width < 2 || height < 2 || signedValues.length != sampleCount
                || connected.length != signedValues.length
                || !Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException("invalid contour grid");
        }

        double[] values = new double[signedValues.length];
        boolean[] wet = connected.clone();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(signedValues[i])) {
                throw new IllegalArgumentException("contour field must be finite");
            }
            double magnitude = Math.max(Math.abs(signedValues[i]), SIGN_EPSILON);
            values[i] = wet[i] ? -magnitude : magnitude;
        }

        int horizontalEdgeCount = Math.multiplyExact(width - 1, height);
        int verticalEdgeCount = Math.multiplyExact(width, height - 1);
        SkyIslandLocalPosition[] vertices =
                new SkyIslandLocalPosition[Math.addExact(horizontalEdgeCount, verticalEdgeCount)];
        boolean[] crosses = new boolean[vertices.length];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width - 1; x++) {
                int first = index(x, z, width);
                int second = index(x + 1, z, width);
                int edge = horizontalEdge(x, z, width);
                recordCrossing(edge, first, second, width, values, wet, vertices, crosses, spacing);
            }
        }
        for (int z = 0; z < height - 1; z++) {
            for (int x = 0; x < width; x++) {
                int first = index(x, z, width);
                int second = index(x, z + 1, width);
                int edge = verticalEdge(x, z, width, horizontalEdgeCount);
                recordCrossing(edge, first, second, width, values, wet, vertices, crosses, spacing);
            }
        }

        Map<Integer, List<Integer>> graph = new HashMap<>();
        double perimeter = 0.0;
        for (int z = 0; z < height - 1; z++) {
            for (int x = 0; x < width - 1; x++) {
                int[] corners = {
                    index(x, z, width),
                    index(x + 1, z, width),
                    index(x + 1, z + 1, width),
                    index(x, z + 1, width)
                };
                int[] edges = {
                    horizontalEdge(x, z, width),
                    verticalEdge(x + 1, z, width, horizontalEdgeCount),
                    horizontalEdge(x, z + 1, width),
                    verticalEdge(x, z, width, horizontalEdgeCount)
                };
                int[] crossings = new int[4];
                int crossingCount = 0;
                for (int edge = 0; edge < 4; edge++) {
                    if (crosses[edges[edge]]) {
                        crossings[crossingCount++] = edge;
                    }
                }
                if (crossingCount == 2) {
                    perimeter += connect(
                            edges[crossings[0]], edges[crossings[1]], vertices, graph);
                } else if (crossingCount == 4) {
                    double determinant =
                            values[corners[0]] * values[corners[2]]
                                    - values[corners[1]] * values[corners[3]];
                    if (Math.abs(determinant) <= SIGN_EPSILON) {
                        boolean diagonal02 = wet[corners[0]] && wet[corners[2]];
                        if (diagonal02) {
                            perimeter += connect(edges[0], edges[3], vertices, graph);
                            perimeter += connect(edges[1], edges[2], vertices, graph);
                        } else {
                            perimeter += connect(edges[0], edges[1], vertices, graph);
                            perimeter += connect(edges[2], edges[3], vertices, graph);
                        }
                    } else if (determinant > 0.0) {
                        perimeter += connect(edges[0], edges[1], vertices, graph);
                        perimeter += connect(edges[2], edges[3], vertices, graph);
                    } else {
                        perimeter += connect(edges[0], edges[3], vertices, graph);
                        perimeter += connect(edges[1], edges[2], vertices, graph);
                    }
                } else if (crossingCount != 0) {
                    throw new IllegalStateException("marching-squares cell has an odd crossing count");
                }
            }
        }

        int nonDegreeTwo = 0;
        int closedLoops = 0;
        Set<Integer> visited = new HashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : graph.entrySet()) {
            if (entry.getValue().size() != 2) {
                nonDegreeTwo++;
            }
            if (!visited.add(entry.getKey())) {
                continue;
            }
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            boolean closed = true;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                List<Integer> neighbors = graph.get(current);
                if (neighbors.size() != 2) {
                    closed = false;
                }
                for (int next : neighbors) {
                    if (visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            if (closed) {
                closedLoops++;
            }
        }
        return new Result(perimeter, closedLoops, nonDegreeTwo);
    }

    private static void recordCrossing(
            int edge,
            int first,
            int second,
            int width,
            double[] values,
            boolean[] wet,
            SkyIslandLocalPosition[] vertices,
            boolean[] crosses,
            double spacing) {
        if (wet[first] == wet[second]) {
            return;
        }
        double fraction = values[first] / (values[first] - values[second]);
        double x0 = (first % width) * spacing;
        double z0 = (first / width) * spacing;
        double x1 = (second % width) * spacing;
        double z1 = (second / width) * spacing;
        vertices[edge] = new SkyIslandLocalPosition(
                x0 + (x1 - x0) * fraction,
                z0 + (z1 - z0) * fraction);
        crosses[edge] = true;
    }

    private static double connect(
            int first,
            int second,
            SkyIslandLocalPosition[] vertices,
            Map<Integer, List<Integer>> graph) {
        if (vertices[first] == null || vertices[second] == null) {
            throw new IllegalStateException("contour segment references a missing edge crossing");
        }
        graph.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
        graph.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
        return Math.hypot(
                vertices[first].x() - vertices[second].x(),
                vertices[first].z() - vertices[second].z());
    }

    private static int horizontalEdge(int x, int z, int width) {
        return z * (width - 1) + x;
    }

    private static int verticalEdge(int x, int z, int width, int horizontalEdgeCount) {
        return horizontalEdgeCount + z * width + x;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    record Result(double perimeterWorldUnits, int closedLoopCount, int nonDegreeTwoVertexCount) {
        Result {
            if (!Double.isFinite(perimeterWorldUnits) || perimeterWorldUnits < 0.0
                    || closedLoopCount < 0 || nonDegreeTwoVertexCount < 0) {
                throw new IllegalArgumentException("invalid shoreline contour measurement");
            }
        }
    }
}
