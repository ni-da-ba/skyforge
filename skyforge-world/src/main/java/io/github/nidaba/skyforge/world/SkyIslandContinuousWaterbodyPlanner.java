package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Solves retained open-water candidates as sink-connected fine sublevel sets.
 *
 * <p>AUTH-0008 retained-sink/catchment/spill semantics remain authoritative. The historical
 * AUTH-0009 coarse inundated-cell footprint is deliberately not consumed. Coarse depression cells
 * define only a bounded search neighborhood; the physical candidate is determined from the
 * continuous terrain condition {@code z(x,z) <= h}.
 */
public final class SkyIslandContinuousWaterbodyPlanner {
    public static final int FINE_DIVISIONS_PER_WATERSHED_CELL = 4;

    private static final double ACTIVE_INTERIORITY_THRESHOLD = 0.025;
    private static final double SPILL_MATCH_EPSILON = 1.0e-10;
    private static final double EPSILON = 1.0e-12;
    private static final int SEARCH_PADDING_CELLS = 2;

    private SkyIslandContinuousWaterbodyPlanner() {}

    public static SkyIslandContinuousWaterbodyPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan semantics = SkyIslandWaterbodyPlanner.plan(descriptor);
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<SkyIslandContinuousWaterbodyBasin> basins = new ArrayList<>();
        List<SkyIslandWaterbodyCandidate> wetlands = new ArrayList<>();
        for (SkyIslandWaterbodyCandidate candidate : semantics.candidates()) {
            if (candidate.kind() == SkyIslandWaterbodyKind.WETLAND) {
                wetlands.add(candidate);
                continue;
            }
            basins.add(solve(
                    descriptor,
                    candidate,
                    watershed,
                    cells,
                    fields.elevationTendency(),
                    fields.interiority()));
        }

        basins.sort(Comparator.comparingInt(basin -> basin.sourceCandidate().sinkCellIndex()));
        wetlands.sort(Comparator.comparingInt(SkyIslandWaterbodyCandidate::sinkCellIndex));
        return new SkyIslandContinuousWaterbodyPlan(descriptor, basins, wetlands);
    }

    static SkyIslandContinuousWaterbodyBasin solve(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyCandidate candidate,
            SkyIslandWatershedPlan watershed,
            Map<Integer, SkyIslandWatershedCell> cells,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(watershed, "watershed");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (candidate.kind() == SkyIslandWaterbodyKind.WETLAND) {
            throw new IllegalArgumentException("wetland candidates use saturated-margin semantics, not open-water basin solve");
        }

        SkyIslandWatershedCell sink = requireCell(cells, candidate.sinkCellIndex());
        double waterSurface = Math.min(
                sink.surfacePotential() + sink.fillDepthPotential() * fillFraction(candidate),
                sink.spillSurfacePotential());

        Set<Integer> sameSpill = new HashSet<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            if (cell.fillDepthPotential() > EPSILON
                    && Math.abs(cell.spillSurfacePotential() - sink.spillSurfacePotential())
                            <= SPILL_MATCH_EPSILON) {
                sameSpill.add(cell.index());
            }
        }
        sameSpill.add(sink.index());
        Set<Integer> depression = connectedCoarse(
                sink.index(), sameSpill, watershed.gridSize());

        double radius = descriptor.nominalRadius();
        double coarseSpacing = watershed.spacing();
        double fineSpacing = coarseSpacing / FINE_DIVISIONS_PER_WATERSHED_CELL;
        Bounds raw = searchBounds(depression, cells, coarseSpacing * SEARCH_PADDING_CELLS, radius);
        GridBounds grid = alignBounds(raw, radius, fineSpacing);
        int width = grid.maximumXIndex() - grid.minimumXIndex() + 1;
        int height = grid.maximumZIndex() - grid.minimumZIndex() + 1;
        int count = Math.multiplyExact(width, height);

        SkyIslandLocalPosition[] positions = new SkyIslandLocalPosition[count];
        double[] elevations = new double[count];
        boolean[] eligible = new boolean[count];

        for (int gz = 0; gz < height; gz++) {
            int globalZ = grid.minimumZIndex() + gz;
            double z = -radius + globalZ * fineSpacing;
            for (int gx = 0; gx < width; gx++) {
                int globalX = grid.minimumXIndex() + gx;
                double x = -radius + globalX * fineSpacing;
                int index = index(gx, gz, width);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                double elevation = clamp01(terrain.sample(position));
                positions[index] = position;
                elevations[index] = elevation;
                eligible[index] = interiority.sample(position) > ACTIVE_INTERIORITY_THRESHOLD
                        && elevation <= waterSurface + EPSILON;
            }
        }

        int seed = nearestEligibleSeed(
                candidate.anchor(), positions, eligible);
        if (seed < 0) {
            throw new IllegalStateException("retained semantic sink has no fine wet sample at solved datum");
        }

        boolean[] connected = floodFill(seed, eligible, width, height);
        int connectedCount = 0;
        double maximumDepth = 0.0;
        boolean reachesBoundary = false;
        for (int i = 0; i < connected.length; i++) {
            if (!connected[i]) {
                continue;
            }
            connectedCount++;
            maximumDepth = Math.max(maximumDepth, Math.max(0.0, waterSurface - elevations[i]));
            int x = i % width;
            int z = i / width;
            if (x == 0 || z == 0 || x == width - 1 || z == height - 1) {
                reachesBoundary = true;
            }
        }
        if (connectedCount == 0) {
            throw new IllegalStateException("retained semantic sink produced an empty continuous basin");
        }

        List<SkyIslandLocalPosition> crossings = shorelineCrossings(
                positions, elevations, connected, width, height, waterSurface);
        crossings.sort(Comparator
                .comparingDouble(SkyIslandLocalPosition::x)
                .thenComparingDouble(SkyIslandLocalPosition::z));

        return new SkyIslandContinuousWaterbodyBasin(
                candidate,
                clamp01(waterSurface),
                clamp01(sink.spillSurfacePotential()),
                fineSpacing,
                connectedCount,
                connectedCount * fineSpacing * fineSpacing,
                clamp01(maximumDepth),
                reachesBoundary,
                crossings);
    }

    private static double fillFraction(SkyIslandWaterbodyCandidate candidate) {
        return switch (candidate.kind()) {
            case WETLAND -> throw new IllegalArgumentException("wetland has no open-water fill fraction");
            case POND -> clamp01(0.40 + 0.45 * candidate.persistence());
            case LAKE -> clamp01(0.62 + 0.35 * candidate.persistence());
        };
    }

    private static Set<Integer> connectedCoarse(
            int seed,
            Set<Integer> eligible,
            int gridSize) {
        Set<Integer> connected = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        if (eligible.contains(seed)) {
            connected.add(seed);
            queue.add(seed);
        }
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % gridSize;
            int z = current / gridSize;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                        continue;
                    }
                    int neighbor = nz * gridSize + nx;
                    if (eligible.contains(neighbor) && connected.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return connected;
    }

    private static Bounds searchBounds(
            Set<Integer> depression,
            Map<Integer, SkyIslandWatershedCell> cells,
            double padding,
            double radius) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index : depression) {
            SkyIslandLocalPosition p = requireCell(cells, index).position();
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        if (!Double.isFinite(minX)) {
            throw new IllegalStateException("retained depression search set is empty");
        }
        return new Bounds(
                Math.max(-radius, minX - padding),
                Math.min(radius, maxX + padding),
                Math.max(-radius, minZ - padding),
                Math.min(radius, maxZ + padding));
    }

    private static GridBounds alignBounds(
            Bounds bounds,
            double radius,
            double spacing) {
        int maximumIndex = (int) Math.round(2.0 * radius / spacing);
        int minX = clampIndex((int) Math.floor((bounds.minX() + radius) / spacing), maximumIndex);
        int maxX = clampIndex((int) Math.ceil((bounds.maxX() + radius) / spacing), maximumIndex);
        int minZ = clampIndex((int) Math.floor((bounds.minZ() + radius) / spacing), maximumIndex);
        int maxZ = clampIndex((int) Math.ceil((bounds.maxZ() + radius) / spacing), maximumIndex);
        return new GridBounds(minX, maxX, minZ, maxZ);
    }

    private static int clampIndex(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static int nearestEligibleSeed(
            SkyIslandLocalPosition anchor,
            SkyIslandLocalPosition[] positions,
            boolean[] eligible) {
        int selected = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < positions.length; i++) {
            if (!eligible[i]) {
                continue;
            }
            double distance = Math.hypot(
                    positions[i].x() - anchor.x(),
                    positions[i].z() - anchor.z());
            if (distance < best - EPSILON
                    || (Math.abs(distance - best) <= EPSILON && (selected < 0 || i < selected))) {
                selected = i;
                best = distance;
            }
        }
        return selected;
    }

    private static boolean[] floodFill(
            int seed,
            boolean[] eligible,
            int width,
            int height) {
        boolean[] connected = new boolean[eligible.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        connected[seed] = true;
        queue.add(seed);
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % width;
            int z = current / width;
            for (int d = 0; d < dx.length; d++) {
                int nx = x + dx[d];
                int nz = z + dz[d];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    continue;
                }
                int next = index(nx, nz, width);
                if (eligible[next] && !connected[next]) {
                    connected[next] = true;
                    queue.addLast(next);
                }
            }
        }
        return connected;
    }

    private static List<SkyIslandLocalPosition> shorelineCrossings(
            SkyIslandLocalPosition[] positions,
            double[] elevations,
            boolean[] connected,
            int width,
            int height,
            double waterSurface) {
        List<SkyIslandLocalPosition> result = new ArrayList<>();
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        for (int current = 0; current < connected.length; current++) {
            if (!connected[current]) {
                continue;
            }
            int x = current % width;
            int z = current / width;
            for (int d = 0; d < dx.length; d++) {
                int nx = x + dx[d];
                int nz = z + dz[d];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    continue;
                }
                int next = index(nx, nz, width);
                if (connected[next] || elevations[next] <= waterSurface + EPSILON) {
                    continue;
                }
                result.add(interpolateContour(
                        positions[current],
                        positions[next],
                        elevations[current],
                        elevations[next],
                        waterSurface));
            }
        }
        return deduplicate(result);
    }

    private static SkyIslandLocalPosition interpolateContour(
            SkyIslandLocalPosition wet,
            SkyIslandLocalPosition dry,
            double wetElevation,
            double dryElevation,
            double waterSurface) {
        double denominator = dryElevation - wetElevation;
        double fraction = denominator <= EPSILON
                ? 0.5
                : clamp01((waterSurface - wetElevation) / denominator);
        return new SkyIslandLocalPosition(
                wet.x() + (dry.x() - wet.x()) * fraction,
                wet.z() + (dry.z() - wet.z()) * fraction);
    }

    private static List<SkyIslandLocalPosition> deduplicate(
            List<SkyIslandLocalPosition> points) {
        List<SkyIslandLocalPosition> ordered = points.stream()
                .sorted(Comparator
                        .comparingDouble(SkyIslandLocalPosition::x)
                        .thenComparingDouble(SkyIslandLocalPosition::z))
                .toList();
        List<SkyIslandLocalPosition> result = new ArrayList<>();
        for (SkyIslandLocalPosition point : ordered) {
            if (result.isEmpty()
                    || Math.hypot(
                                    result.getLast().x() - point.x(),
                                    result.getLast().z() - point.z())
                            > 1.0e-9) {
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    private static SkyIslandWatershedCell requireCell(
            Map<Integer, SkyIslandWatershedCell> cells,
            int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("continuous basin references missing watershed cell " + index);
        }
        return cell;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {}

    private record GridBounds(
            int minimumXIndex,
            int maximumXIndex,
            int minimumZIndex,
            int maximumZIndex) {}
}
