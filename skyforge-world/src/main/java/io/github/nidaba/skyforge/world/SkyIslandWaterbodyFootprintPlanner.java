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
    private static final double SPILL_MATCH_EPSILON = 1.0e-10;

    private SkyIslandWaterbodyFootprintPlanner() {}

    public static SkyIslandWaterbodyFootprintPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan waterbodies = SkyIslandWaterbodyPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<CandidateSeed> seeds = new ArrayList<>();
        for (SkyIslandWaterbodyCandidate candidate : waterbodies.candidates()) {
            seeds.add(seed(candidate, watershed, cells));
        }

        List<SkyIslandWaterbodyFootprint> footprints = new ArrayList<>();
        for (List<CandidateSeed> group : overlapGroups(seeds)) {
            footprints.add(merge(group, watershed, cells));
        }
        footprints.sort(Comparator.comparingInt(footprint -> footprint.sourceCandidates().getFirst().sinkCellIndex()));
        return new SkyIslandWaterbodyFootprintPlan(descriptor, footprints);
    }

    private static CandidateSeed seed(
            SkyIslandWaterbodyCandidate candidate,
            SkyIslandWatershedPlan watershed,
            Map<Integer, SkyIslandWatershedCell> cells) {
        SkyIslandWatershedCell sink = requireCell(cells, candidate.sinkCellIndex());
        double fillFraction = fillFraction(candidate);
        double waterSurface = Math.min(
                sink.surfacePotential() + sink.fillDepthPotential() * fillFraction,
                sink.spillSurfacePotential());

        Set<Integer> sameDepression = new HashSet<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            if (cell.fillDepthPotential() > EPSILON
                    && Math.abs(cell.spillSurfacePotential() - sink.spillSurfacePotential()) <= SPILL_MATCH_EPSILON) {
                sameDepression.add(cell.index());
            }
        }
        sameDepression.add(candidate.sinkCellIndex());
        Set<Integer> depression = connectedFromSources(
                Set.of(candidate.sinkCellIndex()), sameDepression, watershed.gridSize());

        Set<Integer> eligible = eligibleAtSurface(depression, waterSurface, cells);
        eligible.add(candidate.sinkCellIndex());
        Set<Integer> inundated = connectedFromSources(
                Set.of(candidate.sinkCellIndex()), eligible, watershed.gridSize());
        return new CandidateSeed(
                candidate,
                fillFraction,
                clamp01(waterSurface),
                clamp01(sink.spillSurfacePotential()),
                Set.copyOf(depression),
                Set.copyOf(inundated));
    }

    private static List<List<CandidateSeed>> overlapGroups(List<CandidateSeed> seeds) {
        List<List<CandidateSeed>> groups = new ArrayList<>();
        boolean[] assigned = new boolean[seeds.size()];
        for (int start = 0; start < seeds.size(); start++) {
            if (assigned[start]) {
                continue;
            }
            List<CandidateSeed> group = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            assigned[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int currentIndex = queue.removeFirst();
                CandidateSeed current = seeds.get(currentIndex);
                group.add(current);
                for (int otherIndex = 0; otherIndex < seeds.size(); otherIndex++) {
                    if (assigned[otherIndex]) {
                        continue;
                    }
                    if (intersects(current.inundatedCells(), seeds.get(otherIndex).inundatedCells())) {
                        assigned[otherIndex] = true;
                        queue.addLast(otherIndex);
                    }
                }
            }
            group.sort(Comparator.comparingInt(seed -> seed.candidate().sinkCellIndex()));
            groups.add(List.copyOf(group));
        }
        return groups;
    }

    private static SkyIslandWaterbodyFootprint merge(
            List<CandidateSeed> group,
            SkyIslandWatershedPlan watershed,
            Map<Integer, SkyIslandWatershedCell> cells) {
        List<SkyIslandWaterbodyCandidate> sources = group.stream()
                .map(CandidateSeed::candidate)
                .sorted(Comparator.comparingInt(SkyIslandWaterbodyCandidate::sinkCellIndex))
                .toList();
        double waterSurface = group.stream().mapToDouble(CandidateSeed::waterSurfacePotential).max().orElseThrow();
        double spillSurface = group.stream().mapToDouble(CandidateSeed::spillSurfacePotential).min().orElseThrow();
        waterSurface = Math.min(waterSurface, spillSurface);
        double maxFillFraction = group.stream().mapToDouble(CandidateSeed::fillFraction).max().orElseThrow();

        Set<Integer> depression = new HashSet<>();
        Set<Integer> sourceIndices = new HashSet<>();
        for (CandidateSeed seed : group) {
            depression.addAll(seed.depressionCells());
            sourceIndices.add(seed.candidate().sinkCellIndex());
        }
        Set<Integer> eligible = eligibleAtSurface(depression, waterSurface, cells);
        eligible.addAll(sourceIndices);
        Set<Integer> connected = connectedFromSources(sourceIndices, eligible, watershed.gridSize());

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

        return new SkyIslandWaterbodyFootprint(
                sources,
                clamp01(waterSurface),
                clamp01(spillSurface),
                maxFillFraction,
                depression.size(),
                footprintCells);
    }

    private static Set<Integer> eligibleAtSurface(
            Set<Integer> depression,
            double waterSurface,
            Map<Integer, SkyIslandWatershedCell> cells) {
        Set<Integer> eligible = new HashSet<>();
        for (int index : depression) {
            if (requireCell(cells, index).surfacePotential() <= waterSurface + EPSILON) {
                eligible.add(index);
            }
        }
        return eligible;
    }

    private static boolean intersects(Set<Integer> a, Set<Integer> b) {
        Set<Integer> smaller = a.size() <= b.size() ? a : b;
        Set<Integer> larger = a.size() <= b.size() ? b : a;
        for (int value : smaller) {
            if (larger.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static double fillFraction(SkyIslandWaterbodyCandidate candidate) {
        return switch (candidate.kind()) {
            case WETLAND -> clamp01(0.20 + 0.45 * candidate.persistence());
            case POND -> clamp01(0.40 + 0.45 * candidate.persistence());
            case LAKE -> clamp01(0.62 + 0.35 * candidate.persistence());
        };
    }

    private static Set<Integer> connectedFromSources(
            Set<Integer> sourceIndices,
            Set<Integer> eligible,
            int gridSize) {
        Set<Integer> connected = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        for (int source : sourceIndices) {
            if (eligible.contains(source) && connected.add(source)) {
                queue.addLast(source);
            }
        }
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

    private record CandidateSeed(
            SkyIslandWaterbodyCandidate candidate,
            double fillFraction,
            double waterSurfacePotential,
            double spillSurfacePotential,
            Set<Integer> depressionCells,
            Set<Integer> inundatedCells) {}
}
