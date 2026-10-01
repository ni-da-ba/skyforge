package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives catchment-backed retained-waterbody semantics from authored watershed topology. */
public final class SkyIslandWaterbodyPlanner {
    private static final double REFERENCE_BASIN_FRACTION = 0.18;

    private SkyIslandWaterbodyPlanner() {}

    public static SkyIslandWaterbodyPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandHydrologyField hydrology = SkyIslandHydrologyField.create(descriptor);
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        Set<Integer> retainedSinks = new HashSet<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
            if (cell.retainedSink()) {
                retainedSinks.add(cell.index());
            }
        }

        Map<Integer, Integer> terminalMemo = new HashMap<>();
        Map<Integer, Integer> catchmentCounts = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            int terminal = terminal(cell.index(), cells, terminalMemo, new HashSet<>());
            if (retainedSinks.contains(terminal)) {
                catchmentCounts.merge(terminal, 1, Integer::sum);
            }
        }

        double maxAccumulation = Math.max(1.0e-12, watershed.maxFlowAccumulation());
        int totalCells = Math.max(1, watershed.cells().size());
        List<SkyIslandWaterbodyCandidate> candidates = new ArrayList<>();

        retainedSinks.stream().sorted().forEach(sinkIndex -> {
            SkyIslandWatershedCell sink = requireCell(cells, sinkIndex);
            int catchmentCellCount = catchmentCounts.getOrDefault(sinkIndex, 1);
            double catchmentFraction = clamp01((double) catchmentCellCount / totalCells);
            double relativeInflow = clamp01(sink.flowAccumulation() / maxAccumulation);
            SkyIslandHydrologySample hydrologySample = hydrology.sample(sink.position());
            SkyIslandEcologySample ecologySample = ecology.sample(sink.position());
            double retention = hydrologySample.retentionPotential();
            double saturation = ecologySample.saturationPotential();
            double basinScale = clamp01(Math.sqrt(catchmentFraction / REFERENCE_BASIN_FRACTION));
            double persistence = clamp01(
                    0.34 * relativeInflow
                            + 0.26 * retention
                            + 0.22 * saturation
                            + 0.18 * basinScale);
            SkyIslandWaterbodyKind kind = classify(
                    relativeInflow,
                    retention,
                    saturation,
                    persistence,
                    basinScale);

            candidates.add(new SkyIslandWaterbodyCandidate(
                    kind,
                    sinkIndex,
                    sink.position(),
                    catchmentCellCount,
                    catchmentFraction,
                    relativeInflow,
                    retention,
                    saturation,
                    persistence,
                    basinScale));
        });

        return new SkyIslandWaterbodyPlan(descriptor, candidates);
    }

    private static SkyIslandWaterbodyKind classify(
            double relativeInflow,
            double retention,
            double saturation,
            double persistence,
            double basinScale) {
        double wetlandScore = clamp01(
                0.45 * saturation
                        + 0.35 * retention
                        + 0.20 * (1.0 - relativeInflow));
        double openWaterScore = clamp01(
                0.45 * relativeInflow
                        + 0.30 * basinScale
                        + 0.25 * persistence);

        if (wetlandScore >= 0.63 && openWaterScore < 0.68) {
            return SkyIslandWaterbodyKind.WETLAND;
        }
        if (openWaterScore >= 0.55) {
            return SkyIslandWaterbodyKind.LAKE;
        }
        return SkyIslandWaterbodyKind.POND;
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
