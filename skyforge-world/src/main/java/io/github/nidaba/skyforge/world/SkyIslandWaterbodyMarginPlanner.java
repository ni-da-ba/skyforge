package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives dry shoreline transition semantics around accepted retained-waterbody footprints. */
public final class SkyIslandWaterbodyMarginPlanner {
    private static final double MARGIN_THRESHOLD = 0.55;
    private static final double HEAD_REFERENCE = 0.12;

    private SkyIslandWaterbodyMarginPlanner() {}

    public static SkyIslandWaterbodyMarginPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan footprints = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandHydrologyField hydrology = SkyIslandHydrologyField.create(descriptor);
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);

        Map<Integer, SkyIslandWatershedCell> watershedCells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            watershedCells.put(cell.index(), cell);
        }

        Map<Integer, ProvisionalMarginCell> ownership = new HashMap<>();
        for (int ordinal = 0; ordinal < footprints.footprints().size(); ordinal++) {
            SkyIslandWaterbodyFootprint footprint = footprints.footprints().get(ordinal);
            Set<Integer> inundated = new HashSet<>();
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                inundated.add(cell.watershedCellIndex());
            }

            Map<Integer, Integer> distances = marginDistances(footprint, watershed.gridSize());
            boolean wetlandSource = footprint.sourceCandidates().stream()
                    .allMatch(candidate -> candidate.kind() == SkyIslandWaterbodyKind.WETLAND);
            for (Map.Entry<Integer, Integer> entry : distances.entrySet()) {
                int index = entry.getKey();
                if (inundated.contains(index)) {
                    continue;
                }
                SkyIslandWatershedCell cell = watershedCells.get(index);
                if (cell == null) {
                    continue;
                }

                int distance = entry.getValue();
                double proximity = distance == 1 ? 1.0 : 0.55;
                SkyIslandHydrologySample hydrologySample = hydrology.sample(cell.position());
                SkyIslandEcologySample ecologySample = ecology.sample(cell.position());
                double saturation = ecologySample.saturationPotential();
                double retention = hydrologySample.retentionPotential();
                double elevationHead = clamp01(Math.max(0.0,
                        cell.surfacePotential() - footprint.waterSurfacePotential()) / HEAD_REFERENCE);
                double marginPotential = clamp01(
                        0.34 * proximity
                                + 0.24 * saturation
                                + 0.20 * retention
                                + 0.22 * (1.0 - elevationHead));
                if (marginPotential < MARGIN_THRESHOLD) {
                    continue;
                }

                double saturatedScore = clamp01(
                        0.50 * saturation
                                + 0.30 * retention
                                + 0.20 * proximity
                                + (wetlandSource ? 0.08 : 0.0));
                SkyIslandWaterbodyMarginKind kind = saturatedScore >= 0.64
                        ? SkyIslandWaterbodyMarginKind.SATURATED_FRINGE
                        : SkyIslandWaterbodyMarginKind.SHORE_TRANSITION;
                SkyIslandWaterbodyMarginCell marginCell = new SkyIslandWaterbodyMarginCell(
                        index,
                        cell.position(),
                        kind,
                        distance,
                        proximity,
                        saturation,
                        retention,
                        elevationHead,
                        marginPotential);
                ProvisionalMarginCell proposed = new ProvisionalMarginCell(ordinal, marginCell);
                ownership.merge(index, proposed, SkyIslandWaterbodyMarginPlanner::stronger);
            }
        }

        List<List<SkyIslandWaterbodyMarginCell>> grouped = new ArrayList<>();
        for (int i = 0; i < footprints.footprints().size(); i++) {
            grouped.add(new ArrayList<>());
        }
        for (ProvisionalMarginCell provisional : ownership.values()) {
            grouped.get(provisional.footprintOrdinal()).add(provisional.cell());
        }

        List<SkyIslandWaterbodyMargin> margins = new ArrayList<>();
        for (int ordinal = 0; ordinal < footprints.footprints().size(); ordinal++) {
            List<SkyIslandWaterbodyMarginCell> cells = grouped.get(ordinal);
            cells.sort(Comparator.comparingInt(SkyIslandWaterbodyMarginCell::watershedCellIndex));
            margins.add(new SkyIslandWaterbodyMargin(footprints.footprints().get(ordinal), cells));
        }
        return new SkyIslandWaterbodyMarginPlan(descriptor, margins);
    }

    private static Map<Integer, Integer> marginDistances(
            SkyIslandWaterbodyFootprint footprint,
            int gridSize) {
        Map<Integer, Integer> distances = new HashMap<>();
        for (SkyIslandWaterbodyFootprintCell shoreline : footprint.cells()) {
            if (!shoreline.shoreline()) {
                continue;
            }
            int index = shoreline.watershedCellIndex();
            int x = index % gridSize;
            int z = index / gridSize;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    if (distance == 0 || distance > 2) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                        continue;
                    }
                    distances.merge(nz * gridSize + nx, distance, Math::min);
                }
            }
        }
        return distances;
    }

    private static ProvisionalMarginCell stronger(ProvisionalMarginCell first, ProvisionalMarginCell second) {
        double a = first.cell().marginPotential();
        double b = second.cell().marginPotential();
        if (b > a + 1.0e-12) {
            return second;
        }
        if (a > b + 1.0e-12) {
            return first;
        }
        return first.footprintOrdinal() <= second.footprintOrdinal() ? first : second;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ProvisionalMarginCell(int footprintOrdinal, SkyIslandWaterbodyMarginCell cell) {}
}
