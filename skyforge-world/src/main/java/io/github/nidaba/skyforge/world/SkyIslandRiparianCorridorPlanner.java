package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Derives dry riparian transition semantics around accepted routed channel segments. */
public final class SkyIslandRiparianCorridorPlanner {
    private static final double CORRIDOR_THRESHOLD = 0.54;
    private static final double SATURATED_THRESHOLD = 0.62;

    private SkyIslandRiparianCorridorPlanner() {}

    /** Historical/raw visible-channel diagnostic retained for accepted AUTH-0011 evidence. */
    public static SkyIslandRiparianCorridorPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandChannelProfilePlanner.plan(descriptor).profiles());
    }

    /**
     * Derives riparian semantics from an explicit accepted channel-profile subset.
     *
     * <p>AUTH-0019 uses this entry point with the AUTH-0018 coherent skeleton while historical
     * callers may continue to inspect the complete pre-coherence visible network.
     */
    public static SkyIslandRiparianCorridorPlan plan(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles) {
        Objects.requireNonNull(descriptor, "descriptor");
        profiles = List.copyOf(profiles);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan waterbodyMargins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
        SkyIslandHydrologyField hydrology = SkyIslandHydrologyField.create(descriptor);

        Map<Integer, SkyIslandWatershedCell> watershedCells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            watershedCells.put(cell.index(), cell);
        }

        Set<Integer> reserved = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }
        for (SkyIslandWaterbodyMargin margin : waterbodyMargins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }

        List<SkyIslandChannelSegment> segments = profiles.stream()
                .map(SkyIslandChannelProfile::segment)
                .toList();
        // Preserve the hierarchy normalization authored by the complete accepted network.
        // Filtering visible components must never amplify the retained reaches merely because a
        // higher-order neighboring component was suppressed by the coherence pass.
        int maxStreamOrder = SkyIslandChannelNetworkPlanner.plan(descriptor).maxStreamOrder();

        Set<Integer> channelCells = new HashSet<>();
        for (SkyIslandChannelSegment segment : segments) {
            channelCells.add(segment.sourceCellIndex());
            channelCells.add(segment.downstreamCellIndex());
        }

        Map<Integer, ProvisionalRiparianCell> ownership = new HashMap<>();
        for (SkyIslandChannelSegment segment : segments) {
            int radius = segment.corridorScale() >= 0.58 ? 2 : 1;
            double orderScale = maxStreamOrder == 0
                    ? 0.0
                    : (double) segment.streamOrder() / maxStreamOrder;
            double channelInfluence = clamp01(
                    0.45 * segment.corridorScale()
                            + 0.35 * segment.relativeDischarge()
                            + 0.20 * orderScale);

            for (int index : candidateIndices(segment, radius, watershed.gridSize())) {
                if (channelCells.contains(index) || reserved.contains(index)) {
                    continue;
                }
                SkyIslandWatershedCell cell = watershedCells.get(index);
                if (cell == null) {
                    continue;
                }

                int distance = segmentDistance(index, segment, watershed.gridSize());
                if (distance < 1 || distance > radius) {
                    continue;
                }
                double proximity = distance == 1 ? 1.0 : 0.55;
                double saturation = ecology.sample(cell.position()).saturationPotential();
                double retention = hydrology.sample(cell.position()).retentionPotential();
                double riparianPotential = clamp01(
                        0.38 * proximity
                                + 0.32 * channelInfluence
                                + 0.18 * saturation
                                + 0.12 * retention);
                if (riparianPotential < CORRIDOR_THRESHOLD) {
                    continue;
                }

                double saturatedScore = clamp01(
                        0.44 * saturation
                                + 0.24 * retention
                                + 0.32 * channelInfluence);
                SkyIslandRiparianKind kind = saturatedScore >= SATURATED_THRESHOLD
                        ? SkyIslandRiparianKind.SATURATED_RIPARIAN
                        : SkyIslandRiparianKind.RIPARIAN_TRANSITION;
                SkyIslandRiparianCell riparian = new SkyIslandRiparianCell(
                        index,
                        cell.position(),
                        kind,
                        segment.sourceCellIndex(),
                        segment.downstreamCellIndex(),
                        segment.role(),
                        segment.streamOrder(),
                        distance,
                        channelInfluence,
                        saturation,
                        retention,
                        riparianPotential);
                ownership.merge(
                        index,
                        new ProvisionalRiparianCell(riparian),
                        SkyIslandRiparianCorridorPlanner::stronger);
            }
        }

        List<SkyIslandRiparianCell> result = ownership.values().stream()
                .map(ProvisionalRiparianCell::cell)
                .sorted(Comparator.comparingInt(SkyIslandRiparianCell::watershedCellIndex))
                .toList();
        return new SkyIslandRiparianCorridorPlan(descriptor, result);
    }

    private static Set<Integer> candidateIndices(
            SkyIslandChannelSegment segment,
            int radius,
            int gridSize) {
        Set<Integer> candidates = new HashSet<>();
        addNeighborhood(candidates, segment.sourceCellIndex(), radius, gridSize);
        addNeighborhood(candidates, segment.downstreamCellIndex(), radius, gridSize);
        return candidates;
    }

    private static void addNeighborhood(Set<Integer> candidates, int center, int radius, int gridSize) {
        int x = center % gridSize;
        int z = center / gridSize;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                    continue;
                }
                candidates.add(nz * gridSize + nx);
            }
        }
    }

    private static int segmentDistance(int index, SkyIslandChannelSegment segment, int gridSize) {
        return Math.min(
                chebyshevDistance(index, segment.sourceCellIndex(), gridSize),
                chebyshevDistance(index, segment.downstreamCellIndex(), gridSize));
    }

    private static int chebyshevDistance(int first, int second, int gridSize) {
        int ax = first % gridSize;
        int az = first / gridSize;
        int bx = second % gridSize;
        int bz = second / gridSize;
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    private static ProvisionalRiparianCell stronger(
            ProvisionalRiparianCell first,
            ProvisionalRiparianCell second) {
        SkyIslandRiparianCell a = first.cell();
        SkyIslandRiparianCell b = second.cell();
        if (b.riparianPotential() > a.riparianPotential() + 1.0e-12) {
            return second;
        }
        if (a.riparianPotential() > b.riparianPotential() + 1.0e-12) {
            return first;
        }
        if (b.channelInfluence() > a.channelInfluence() + 1.0e-12) {
            return second;
        }
        if (a.channelInfluence() > b.channelInfluence() + 1.0e-12) {
            return first;
        }
        if (b.channelSourceCellIndex() < a.channelSourceCellIndex()) {
            return second;
        }
        if (a.channelSourceCellIndex() < b.channelSourceCellIndex()) {
            return first;
        }
        return b.channelDownstreamCellIndex() < a.channelDownstreamCellIndex() ? second : first;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ProvisionalRiparianCell(SkyIslandRiparianCell cell) {}
}
