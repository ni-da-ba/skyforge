package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives normalized terrain-response influence from accepted hydrologic semantics. */
public final class SkyIslandHydrologicTerrainInfluencePlanner {
    private SkyIslandHydrologicTerrainInfluencePlanner() {}

    public static SkyIslandHydrologicTerrainInfluencePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandRiparianCorridorPlan riparian = SkyIslandRiparianCorridorPlanner.plan(descriptor);
        SkyIslandChannelDropPlan drops = SkyIslandChannelDropPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);

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
        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }

        Map<SegmentKey, SkyIslandChannelProfile> bySegment = new HashMap<>();
        Map<Integer, InfluenceAccumulator> influence = new HashMap<>();
        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            SkyIslandChannelSegment segment = profile.segment();
            bySegment.put(new SegmentKey(segment.sourceCellIndex(), segment.downstreamCellIndex()), profile);

            double incision = clamp01(
                    0.50 * profile.incisionPotential()
                            + 0.30 * profile.streamPowerPotential()
                            + 0.20 * profile.gradientPotential());
            double deposition = clamp01(
                    0.34 * (1.0 - profile.gradientPotential())
                            + 0.25 * profile.bankfullWidthPotential()
                            + 0.22 * segment.relativeDischarge()
                            + 0.19 * (1.0 - profile.incisionPotential()));

            add(
                    influence,
                    reserved,
                    watershedCells,
                    segment.sourceCellIndex(),
                    incision,
                    deposition,
                    0.0,
                    0.0);
            add(
                    influence,
                    reserved,
                    watershedCells,
                    segment.downstreamCellIndex(),
                    incision,
                    deposition,
                    0.0,
                    0.0);
        }

        for (SkyIslandRiparianCell cell : riparian.cells()) {
            SkyIslandChannelProfile profile = bySegment.get(new SegmentKey(
                    cell.channelSourceCellIndex(), cell.channelDownstreamCellIndex()));
            if (profile == null) {
                throw new IllegalStateException("riparian cell references missing channel profile");
            }
            double proximity = cell.channelDistance() == 1 ? 1.0 : 0.55;
            double lowGradient = 1.0 - profile.gradientPotential();
            double bankIncisionScale = switch (profile.kind()) {
                case ALLUVIAL -> 0.45;
                case INCISED -> 0.85;
                case CASCADE -> 0.95;
            };
            double floodplainProfileScale = switch (profile.kind()) {
                case ALLUVIAL -> 1.0;
                case INCISED -> 0.55;
                case CASCADE -> 0.18;
            };
            double depositionProfileScale = switch (profile.kind()) {
                case ALLUVIAL -> 1.0;
                case INCISED -> 0.55;
                case CASCADE -> 0.30;
            };

            double bankIncision = clamp01(proximity * bankIncisionScale * (
                    0.45 * profile.incisionPotential()
                            + 0.30 * profile.streamPowerPotential()
                            + 0.25 * profile.gradientPotential()));
            double floodplain = clamp01(proximity
                    * floodplainProfileScale
                    * (0.15 + 0.85 * lowGradient)
                    * (0.34 * profile.bankfullWidthPotential()
                            + 0.25 * profile.segment().relativeDischarge()
                            + 0.23 * lowGradient
                            + 0.18 * cell.retentionPotential()));
            double deposition = clamp01(proximity
                    * depositionProfileScale
                    * (0.30 + 0.70 * lowGradient)
                    * (0.32 * lowGradient
                            + 0.25 * profile.bankfullWidthPotential()
                            + 0.20 * profile.segment().relativeDischarge()
                            + 0.13 * cell.retentionPotential()
                            + 0.10 * cell.channelInfluence()));
            add(
                    influence,
                    reserved,
                    watershedCells,
                    cell.watershedCellIndex(),
                    bankIncision,
                    deposition,
                    floodplain,
                    0.0);
        }

        for (SkyIslandChannelDrop drop : drops.drops()) {
            int target = drop.kind() == SkyIslandChannelDropKind.EDGE_FALL
                    ? drop.sourceCellIndex()
                    : drop.downstreamCellIndex();
            add(
                    influence,
                    reserved,
                    watershedCells,
                    target,
                    0.0,
                    0.0,
                    0.0,
                    drop.dropPotential());

            if (drop.kind() != SkyIslandChannelDropKind.EDGE_FALL) {
                double fringe = clamp01(
                        0.65 * (0.55 * drop.dropPotential() + 0.45 * drop.plungePoolPotential()));
                for (SkyIslandRiparianCell cell : riparian.cells()) {
                    if (chebyshevDistance(
                                    cell.watershedCellIndex(), target, watershed.gridSize())
                            <= 1) {
                        add(
                                influence,
                                reserved,
                                watershedCells,
                                cell.watershedCellIndex(),
                                0.0,
                                0.0,
                                0.0,
                                fringe);
                    }
                }
            }
        }

        List<SkyIslandHydrologicTerrainCell> cells = influence.values().stream()
                .map(InfluenceAccumulator::toCell)
                .sorted(Comparator.comparingInt(SkyIslandHydrologicTerrainCell::watershedCellIndex))
                .toList();
        return new SkyIslandHydrologicTerrainInfluencePlan(descriptor, cells);
    }

    private static void add(
            Map<Integer, InfluenceAccumulator> influence,
            Set<Integer> reserved,
            Map<Integer, SkyIslandWatershedCell> watershedCells,
            int index,
            double incision,
            double deposition,
            double floodplain,
            double dropShaping) {
        if (index < 0 || reserved.contains(index)) {
            return;
        }
        SkyIslandWatershedCell cell = watershedCells.get(index);
        if (cell == null) {
            return;
        }
        influence.computeIfAbsent(index, ignored -> new InfluenceAccumulator(index, cell.position()))
                .merge(incision, deposition, floodplain, dropShaping);
    }

    private static int chebyshevDistance(int first, int second, int gridSize) {
        int ax = first % gridSize;
        int az = first / gridSize;
        int bx = second % gridSize;
        int bz = second / gridSize;
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record SegmentKey(int source, int downstream) {}

    private static final class InfluenceAccumulator {
        private final int index;
        private final SkyIslandLocalPosition position;
        private double incision;
        private double deposition;
        private double floodplain;
        private double dropShaping;

        private InfluenceAccumulator(int index, SkyIslandLocalPosition position) {
            this.index = index;
            this.position = position;
        }

        private void merge(double newIncision, double newDeposition, double newFloodplain, double newDropShaping) {
            incision = Math.max(incision, clamp01(newIncision));
            deposition = Math.max(deposition, clamp01(newDeposition));
            floodplain = Math.max(floodplain, clamp01(newFloodplain));
            dropShaping = Math.max(dropShaping, clamp01(newDropShaping));
        }

        private SkyIslandHydrologicTerrainCell toCell() {
            return new SkyIslandHydrologicTerrainCell(
                    index,
                    position,
                    incision,
                    deposition,
                    floodplain,
                    dropShaping);
        }
    }
}
