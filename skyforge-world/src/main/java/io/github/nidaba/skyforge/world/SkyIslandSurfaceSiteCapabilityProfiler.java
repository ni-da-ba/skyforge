package io.github.nidaba.skyforge.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0096 deterministic local surface-site capability profiler.
 *
 * <p>The public input is exactly one AUTH-0046 authored/realized association. The profiler reuses
 * the accepted 49x49 watershed lattice and current visible-hydrology composition. It does not rank
 * anchors or decide whether any concrete structure/site requirement is satisfied.
 */
public final class SkyIslandSurfaceSiteCapabilityProfiler {

    public SkyIslandSurfaceSiteCapabilityProfile profile(
            SkyIslandAuthoredRealizationAssociation association) {
        Objects.requireNonNull(association, "association");
        var descriptor = association.authoredDescriptor();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandVisibleHydrologicRealizationPlan visibleHydrology =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();

        int gridSize = watershed.gridSize();
        double radius = descriptor.nominalRadius();
        double spacing = watershed.spacing();
        SkyIslandCompiledVolumeColumnField physical =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        SkyIslandVerticalColumn[] columns = samplePhysicalGrid(physical, gridSize, radius, spacing);

        Map<Integer, WaterbodyEvidence> waterbodies = waterbodyEvidence(visibleHydrology);
        Map<Integer, Double> margins = marginEvidence(visibleHydrology);
        Map<Integer, Double> riparian = riparianEvidence(visibleHydrology);
        Map<Integer, Double> channelDischarge = channelEvidence(visibleHydrology);
        Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> terrain =
                terrainEvidence(visibleHydrology);

        java.util.ArrayList<SkyIslandSurfaceSiteCapabilityCell> cells =
                new java.util.ArrayList<>(watershed.cells().size());
        for (SkyIslandWatershedCell source : watershed.cells()) {
            int index = source.index();
            SkyIslandVerticalColumn center = columns[index];
            boolean present = center != null;

            WindowStats window3 = window(columns, gridSize, index, 1, radius, present);
            WindowStats window5 = window(columns, gridSize, index, 2, radius, present);
            WindowStats window9 = window(columns, gridSize, index, 4, radius, present);

            WaterbodyEvidence waterbody =
                    waterbodies.getOrDefault(index, WaterbodyEvidence.NONE);
            SkyIslandHydrologicTerrainSurfaceCell terrainCell = terrain.get(index);
            if (terrainCell == null) {
                throw new IllegalStateException(
                        "AUTH-0096 hydrologic surface lost watershed cell " + index);
            }

            double flow = watershed.maxFlowAccumulation() > 0.0
                    ? clamp01(source.flowAccumulation() / watershed.maxFlowAccumulation())
                    : 0.0;
            OptionalDouble upperOffset = present
                    ? OptionalDouble.of(
                            (center.upperY()
                                            - association.realizedVolume()
                                                    .compiledVolume()
                                                    .descriptor()
                                                    .suspensionElevation())
                                    / radius)
                    : OptionalDouble.empty();

            cells.add(
                    new SkyIslandSurfaceSiteCapabilityCell(
                            index,
                            source.position(),
                            present,
                            upperOffset,
                            interiority.sample(source.position()),
                            window3.supportFraction(),
                            window5.supportFraction(),
                            window9.supportFraction(),
                            window3.reliefNormalized(),
                            window5.reliefNormalized(),
                            window9.reliefNormalized(),
                            meanCardinalGrade(columns, gridSize, index, spacing),
                            flow,
                            waterbody.present(),
                            waterbody.shoreline(),
                            waterbody.waterDepthPotential(),
                            margins.getOrDefault(index, 0.0),
                            riparian.getOrDefault(index, 0.0),
                            channelDischarge.getOrDefault(index, 0.0),
                            Math.abs(terrainCell.netAdjustment())));
        }

        return new SkyIslandSurfaceSiteCapabilityProfile(
                association, watershed, visibleHydrology, cells);
    }

    private static SkyIslandVerticalColumn[] samplePhysicalGrid(
            SkyIslandCompiledVolumeColumnField physical,
            int gridSize,
            double radius,
            double spacing) {
        SkyIslandVerticalColumn[] result =
                new SkyIslandVerticalColumn[Math.multiplyExact(gridSize, gridSize)];
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int index = z * gridSize + x;
                SkyIslandLocalPosition position =
                        new SkyIslandLocalPosition(
                                x == gridSize - 1 ? radius : -radius + x * spacing,
                                z == gridSize - 1 ? radius : -radius + z * spacing);
                result[index] = physical.columnAt(position).orElse(null);
            }
        }
        return result;
    }

    private static WindowStats window(
            SkyIslandVerticalColumn[] columns,
            int gridSize,
            int centerIndex,
            int halfWidth,
            double radius,
            boolean centerPresent) {
        int cx = centerIndex % gridSize;
        int cz = centerIndex / gridSize;
        int width = halfWidth * 2 + 1;
        int total = width * width;
        int present = 0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;

        for (int dz = -halfWidth; dz <= halfWidth; dz++) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                int x = cx + dx;
                int z = cz + dz;
                if (x < 0 || z < 0 || x >= gridSize || z >= gridSize) {
                    continue;
                }
                SkyIslandVerticalColumn column = columns[z * gridSize + x];
                if (column == null) {
                    continue;
                }
                present++;
                minimum = Math.min(minimum, column.upperY());
                maximum = Math.max(maximum, column.upperY());
            }
        }

        OptionalDouble relief = centerPresent && present > 0
                ? OptionalDouble.of((maximum - minimum) / radius)
                : OptionalDouble.empty();
        return new WindowStats((double) present / total, relief);
    }

    private static OptionalDouble meanCardinalGrade(
            SkyIslandVerticalColumn[] columns,
            int gridSize,
            int centerIndex,
            double spacing) {
        SkyIslandVerticalColumn center = columns[centerIndex];
        if (center == null) {
            return OptionalDouble.empty();
        }
        int cx = centerIndex % gridSize;
        int cz = centerIndex / gridSize;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int count = 0;
        double sum = 0.0;
        for (int[] offset : offsets) {
            int x = cx + offset[0];
            int z = cz + offset[1];
            if (x < 0 || z < 0 || x >= gridSize || z >= gridSize) {
                continue;
            }
            SkyIslandVerticalColumn neighbor = columns[z * gridSize + x];
            if (neighbor == null) {
                continue;
            }
            count++;
            sum += Math.abs(center.upperY() - neighbor.upperY()) / spacing;
        }
        return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count);
    }

    private static Map<Integer, WaterbodyEvidence> waterbodyEvidence(
            SkyIslandVisibleHydrologicRealizationPlan visibleHydrology) {
        Map<Integer, WaterbodyEvidence> result = new HashMap<>();
        for (SkyIslandWaterbodyFootprint footprint : visibleHydrology.waterbodies().footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                result.merge(
                        cell.watershedCellIndex(),
                        new WaterbodyEvidence(
                                true, cell.shoreline(), cell.waterDepthPotential()),
                        WaterbodyEvidence::merge);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Double> marginEvidence(
            SkyIslandVisibleHydrologicRealizationPlan visibleHydrology) {
        Map<Integer, Double> result = new HashMap<>();
        for (SkyIslandWaterbodyMargin margin : visibleHydrology.waterbodyMargins().margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                result.merge(
                        cell.watershedCellIndex(),
                        cell.marginPotential(),
                        Math::max);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Double> riparianEvidence(
            SkyIslandVisibleHydrologicRealizationPlan visibleHydrology) {
        Map<Integer, Double> result = new HashMap<>();
        for (SkyIslandRiparianCell cell :
                visibleHydrology.coherentHydrology().riparian().cells()) {
            result.merge(
                    cell.watershedCellIndex(),
                    cell.riparianPotential(),
                    Math::max);
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Double> channelEvidence(
            SkyIslandVisibleHydrologicRealizationPlan visibleHydrology) {
        Map<Integer, Double> result = new HashMap<>();
        for (SkyIslandChannelProfile profile :
                visibleHydrology.coherentHydrology().channels().profiles()) {
            SkyIslandChannelSegment segment = profile.segment();
            result.merge(
                    segment.sourceCellIndex(),
                    segment.relativeDischarge(),
                    Math::max);
            result.merge(
                    segment.downstreamCellIndex(),
                    segment.relativeDischarge(),
                    Math::max);
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> terrainEvidence(
            SkyIslandVisibleHydrologicRealizationPlan visibleHydrology) {
        Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> result = new HashMap<>();
        for (SkyIslandHydrologicTerrainSurfaceCell cell :
                visibleHydrology.coherentHydrology().terrainSurface().cells()) {
            SkyIslandHydrologicTerrainSurfaceCell previous =
                    result.put(cell.watershedCellIndex(), cell);
            if (previous != null) {
                throw new IllegalStateException(
                        "AUTH-0096 hydrologic surface contains duplicate watershed cell");
            }
        }
        return Map.copyOf(result);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record WindowStats(
            double supportFraction,
            OptionalDouble reliefNormalized) {}

    private record WaterbodyEvidence(
            boolean present,
            boolean shoreline,
            double waterDepthPotential) {
        private static final WaterbodyEvidence NONE =
                new WaterbodyEvidence(false, false, 0.0);

        private WaterbodyEvidence {
            if (!present && (shoreline || waterDepthPotential != 0.0)) {
                throw new IllegalArgumentException(
                        "waterbody detail requires waterbody membership");
            }
            if (!Double.isFinite(waterDepthPotential)
                    || waterDepthPotential < 0.0
                    || waterDepthPotential > 1.0) {
                throw new IllegalArgumentException(
                        "waterDepthPotential must be finite and in [0, 1]");
            }
        }

        private static WaterbodyEvidence merge(
                WaterbodyEvidence first,
                WaterbodyEvidence second) {
            return new WaterbodyEvidence(
                    first.present || second.present,
                    first.shoreline || second.shoreline,
                    Math.max(first.waterDepthPotential, second.waterDepthPotential));
        }
    }
}
