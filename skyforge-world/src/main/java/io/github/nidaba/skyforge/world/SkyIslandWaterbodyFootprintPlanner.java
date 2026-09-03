package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts retained-waterbody candidates into connected coarse semantic inundation footprints. */
public final class SkyIslandWaterbodyFootprintPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandWaterbodyFootprintPlanner() {}

    public static SkyIslandWaterbodyFootprintPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan waterbodies = SkyIslandWaterbodyPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        Map<Integer, Integer> terminalMemo = new HashMap<>();
        Map<Integer, Set<Integer>> catchments = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            int terminal = terminal(cell.index(), cells, terminalMemo, new HashSet<>());
            catchments.computeIfAbsent(terminal, ignored -> new HashSet<>()).add(cell.index());
        }

        List<SkyIslandWaterbodyFootprint> footprints = new ArrayList<>();
        for (SkyIslandWaterbodyCandidate candidate : waterbodies.candidates()) {
            SkyIslandWatershedCell sink = requireCell(cells, candidate.sinkCellIndex());
            Set<Integer> catchment = catchments.getOrDefault(candidate.sinkCellIndex(), Set.of(candidate.sinkCellIndex()));
            double fillFraction = fillFraction(candidate);
            double waterSurface = sink.surfacePotential() + sink.fillDepthPotential() * fillFraction;
            waterSurface = Math.min(waterSurface, sink.spillSurfacePotential());

            Set<Integer> eligible = new HashSet<>();
            for (int index : catchment) {
                SkyIslandWatershedCell cell = requireCell(cells, index);
                if (cell.surfacePotential() <= waterSurface + EPSILON) {
                    eligible.add(index);
                }
            }
            eligible.add(candidate.sinkCellIndex());

            Set<Integer> connected = connectedFromSink(
                    candidate.sinkCellIndex(), eligible, watershed.gridSize());
            List<Integer> ordered = connected.stream().sorted().toList();
            List<SkyIslandWaterbodyFootprintCell> footprintCells = new ArrayList<>(ordered.size());
            for (int index : ordered) {
                SkyIslandWatershedCell cell = requireCell(cells, index);
                double depth = clamp01(Math.max(0.0, waterSurface - cell.surfacePotential()));
                footprintCells.add(new SkyIslandWaterbodyFootprintCell(
                        index,
                        cell.position(),
                        clamp01(cell.surfacePotential()),
                        depth,
                        isShoreline(index, connected, watershed.gridSize())));
            }

            footprints.add(new SkyIslandWaterbodyFootprint(
                    candidate,
                    clamp01(waterSurface),
                    clamp01(sink.spillSurfacePotential()),
                    fillFraction,
                    footprintCells));
        }

        footprints.sort(Comparator.comparingInt(footprint -> footprint.candidate().sinkCellIndex()));
        return new SkyIslandWaterbodyFootprintPlan(descriptor, footprints);
    }

    private static double fillFraction(SkyIslandWaterbodyCandidate candidate) {
        return switch (candidate.kind()) {
            case WETLAND -> clamp01(0.20 + 0.45 * candidate.persistence());
            case POND -> clamp01(0.40 + 0.45 * candidate.persistence());
            case LAKE -> clamp01(0.62 + 0.35 * candidate.persistence());
        };
    }

    private static Set<Integer> connectedFromSink(int sinkIndex, Set<Integer> eligible, int gridSize) {
        Set<Integer> connected = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        connected.add(sinkIndex);
        queue.add(sinkIndex);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int neighbor : neighbors(current, gridSize)) {
                if (eligible.contains(neighbor) && connected.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return connected;
    }

    private static boolean isShoreline(int index, Set<Integer> footprint, int gridSize) {
        int x = index % gridSize;
        int z = index / gridSize;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                    return true;
                }
                if (!footprint.contains(nz * gridSize + nx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Integer> neighbors(int index, int gridSize) {
        int x = index % gridSize;
        int z = index / gridSize;
        List<Integer> result = new ArrayList<>(8);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
                    result.add(nz * gridSize + nx);
                }
            }
        }
        return result;
    }

    private static int terminal(
            int cellIndex,
            Map<Integer, SkyIslandWatershedCell> cells,
            Map<Integer, Integer> memo,
            Set<Integer> visiting) {
        Integer known = memo.get(cellIndex);
        if (known != null) {
            return known;
        }
        if (!visiting.add(cellIndex)) {
            throw new IllegalStateException("watershed topology contains a cycle");
        }
        SkyIslandWatershedCell cell = requireCell(cells, cellIndex);
        int result = cell.downstreamIndex() < 0
                ? cell.index()
                : terminal(cell.downstreamIndex(), cells, memo, visiting);
        visiting.remove(cellIndex);
        memo.put(cellIndex, result);
        return result;
    }

    private static SkyIslandWatershedCell requireCell(Map<Integer, SkyIslandWatershedCell> cells, int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("watershed references missing cell " + index);
        }
        return cell;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
