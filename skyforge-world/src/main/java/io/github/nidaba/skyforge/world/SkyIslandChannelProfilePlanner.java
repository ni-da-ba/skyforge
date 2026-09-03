package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Derives dimensionless geomorphic realization potentials from accepted channel topology. */
public final class SkyIslandChannelProfilePlanner {
    private static final double DROP_REFERENCE = 0.10;

    private SkyIslandChannelProfilePlanner() {}

    public static SkyIslandChannelProfilePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandChannelNetworkPlan network = SkyIslandChannelNetworkPlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<SkyIslandChannelProfile> profiles = new ArrayList<>();
        for (SkyIslandChannelSegment segment : network.segments()) {
            SkyIslandWatershedCell source = requireCell(cells, segment.sourceCellIndex());
            SkyIslandWatershedCell downstream = requireCell(cells, segment.downstreamCellIndex());

            double authoredDrop = Math.max(0.0, source.surfacePotential() - downstream.surfacePotential());
            double gradient = clamp01(authoredDrop / DROP_REFERENCE);
            double streamPower = clamp01(
                    0.50 * gradient
                            + 0.32 * segment.relativeDischarge()
                            + 0.18 * segment.corridorScale());

            double erodibility = 1.0 - descriptor.rockCompetence();
            double incision = clamp01(
                    0.50 * streamPower
                            + 0.25 * erodibility
                            + 0.25 * descriptor.erosionMaturity());

            double width = clamp01(
                    0.10
                            + 0.42 * segment.corridorScale()
                            + 0.28 * segment.relativeDischarge()
                            + 0.20 * (1.0 - gradient));
            double depth = clamp01(
                    0.10
                            + 0.30 * segment.relativeDischarge()
                            + 0.22 * segment.corridorScale()
                            + 0.38 * incision);

            SkyIslandChannelProfileKind kind;
            if (gradient >= 0.60 && streamPower >= 0.45) {
                kind = SkyIslandChannelProfileKind.CASCADE;
            } else if (incision >= 0.58 && width <= 0.78) {
                kind = SkyIslandChannelProfileKind.INCISED;
            } else {
                kind = SkyIslandChannelProfileKind.ALLUVIAL;
            }

            profiles.add(new SkyIslandChannelProfile(
                    segment,
                    kind,
                    gradient,
                    streamPower,
                    width,
                    depth,
                    incision));
        }
        return new SkyIslandChannelProfilePlan(descriptor, profiles);
    }

    private static SkyIslandWatershedCell requireCell(Map<Integer, SkyIslandWatershedCell> cells, int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("channel profile references missing watershed cell " + index);
        }
        return cell;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
