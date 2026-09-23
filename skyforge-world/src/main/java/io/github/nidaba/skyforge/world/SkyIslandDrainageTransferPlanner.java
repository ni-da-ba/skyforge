package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Separates drainage topology from physical surface-channel realization.
 *
 * <p>Priority-Flood is allowed to transfer flow through depressions for accumulation/topology, but
 * the traversal tree inside one filled depression is not a river. Connected equal-spill depression
 * cells are grouped first. Routed edges inside one group become BASIN_INTERIOR; ordinary raw
 * descent remains DESCENDING_CHANNEL; raw-uphill edges crossing/leaving a depression become sparse
 * BREACH candidates.
 */
public final class SkyIslandDrainageTransferPlanner {
    private static final double DEPRESSION_FILL_EPSILON = 1.0e-5;
    private static final double SPILL_MATCH_EPSILON = 1.0e-10;
    private static final double RAW_DESCENT_EPSILON = 1.0e-5;
    private static final double MIN_BREACH_DROP = 0.0015;

    private SkyIslandDrainageTransferPlanner() {}

    public static SkyIslandDrainageTransferPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        Map<Integer, Integer> basinAnchorByCell =
                depressionAnchors(watershed.cells(), watershed.gridSize(), cells);
        List<SkyIslandDrainageTransfer> result = new ArrayList<>();

        for (SkyIslandWatershedCell source : watershed.cells()) {
            if (source.downstreamIndex() < 0) {
                continue;
            }
            SkyIslandWatershedCell downstream = cells.get(source.downstreamIndex());
            if (downstream == null) {
                throw new IllegalStateException(
                        "watershed transfer references missing downstream cell "
                                + source.downstreamIndex());
            }

            double rawDrop = source.surfacePotential() - downstream.surfacePotential();
            int sourceBasin = basinAnchorByCell.getOrDefault(source.index(), -1);
            int downstreamBasin = basinAnchorByCell.getOrDefault(downstream.index(), -1);

            SkyIslandDrainageTransferKind kind;
            int anchor = -1;
            if (sourceBasin >= 0 && sourceBasin == downstreamBasin) {
                kind = SkyIslandDrainageTransferKind.BASIN_INTERIOR;
                anchor = sourceBasin;
            } else if (rawDrop >= -RAW_DESCENT_EPSILON) {
                kind = SkyIslandDrainageTransferKind.DESCENDING_CHANNEL;
            } else {
                kind = SkyIslandDrainageTransferKind.BREACH;
                anchor = sourceBasin >= 0 ? sourceBasin : downstreamBasin;
            }

            double requiredCut = kind == SkyIslandDrainageTransferKind.BREACH
                    ? Math.max(MIN_BREACH_DROP, -rawDrop + MIN_BREACH_DROP)
                    : 0.0;
            result.add(new SkyIslandDrainageTransfer(
                    source.index(),
                    downstream.index(),
                    kind,
                    anchor,
                    rawDrop,
                    requiredCut,
                    source.fillDepthPotential(),
                    source.spillSurfacePotential()));
        }

        return new SkyIslandDrainageTransferPlan(descriptor, result);
    }

    private static Map<Integer, Integer> depressionAnchors(
            List<SkyIslandWatershedCell> orderedCells,
            int gridSize,
            Map<Integer, SkyIslandWatershedCell> cells) {
        Set<Integer> depressionCells = orderedCells.stream()
                .filter(cell -> cell.fillDepthPotential() > DEPRESSION_FILL_EPSILON)
                .map(SkyIslandWatershedCell::index)
                .collect(java.util.stream.Collectors.toSet());

        Map<Integer, Integer> anchorByCell = new HashMap<>();
        Set<Integer> visited = new java.util.HashSet<>();

        for (int start : depressionCells.stream().sorted().toList()) {
            if (!visited.add(start)) {
                continue;
            }
            SkyIslandWatershedCell seed = cells.get(start);
            double spillSurface = seed.spillSurfacePotential();
            List<Integer> component = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                component.add(current);
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
                        if (!depressionCells.contains(neighbor) || visited.contains(neighbor)) {
                            continue;
                        }
                        SkyIslandWatershedCell neighborCell = cells.get(neighbor);
                        if (neighborCell == null
                                || Math.abs(neighborCell.spillSurfacePotential() - spillSurface)
                                        > SPILL_MATCH_EPSILON) {
                            continue;
                        }
                        visited.add(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }

            int anchor = component.stream()
                    .map(cells::get)
                    .max(Comparator
                            .comparingDouble(SkyIslandWatershedCell::fillDepthPotential)
                            .thenComparingInt(cell -> -cell.index()))
                    .orElseThrow()
                    .index();
            for (int cell : component) {
                anchorByCell.put(cell, anchor);
            }
        }
        return Map.copyOf(anchorByCell);
    }
}
