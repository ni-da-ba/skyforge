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
            /*
             * H3 hydraulic-geometry contract. Relative discharge remains a dimensionless authored
             * quantity, but channel size now follows monotonic power-law scaling rather than an
             * arbitrary blend with stream order and local slope. This guarantees that accumulating
             * flow cannot make a downstream channel narrower merely because the reach class changed.
             */
            double discharge = Math.max(1.0e-6, segment.relativeDischarge());
            double width = clamp01(0.04 + 0.92 * Math.pow(discharge, 0.50));
            double hydraulicDepth = clamp01(0.03 + 0.72 * Math.pow(discharge, 0.35));

            // Effective stream power couples discharge with slope. It is still normalized semantic
            // authority, not a claim of SI-unit hydraulic power.
            double streamPower = clamp01(
                    Math.pow(discharge, 0.45)
                            * (0.18 + 0.82 * gradient));

            double erodibility = 1.0 - descriptor.rockCompetence();
            double incision = clamp01(
                    0.58 * streamPower
                            + 0.22 * erodibility
                            + 0.20 * descriptor.erosionMaturity());

            double depth = clamp01(
                    hydraulicDepth
                            * (0.82 + 0.18 * incision));

            SkyIslandChannelProfileKind kind;
            if (gradient >= 0.60 && streamPower >= 0.45) {
                kind = SkyIslandChannelProfileKind.CASCADE;
            } else if (incision >= 0.58) {
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
