package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;

/** Extracts channel, retained-water, and waterfall candidates from watershed topology. */
public final class SkyIslandHydrologicFeaturePlanner {
    private static final double CHANNEL_THRESHOLD = 0.16;
    private static final double MAJOR_CHANNEL_THRESHOLD = 0.36;

    private SkyIslandHydrologicFeaturePlanner() {}

    public static SkyIslandHydrologicFeaturePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        double max = Math.max(1.0e-12, watershed.maxFlowAccumulation());
        List<SkyIslandHydrologicFeature> features = new ArrayList<>();

        for (SkyIslandWatershedCell cell : watershed.cells()) {
            double significance = clamp01(cell.flowAccumulation() / max);
            if (cell.retainedSink()) {
                features.add(new SkyIslandHydrologicFeature(
                        SkyIslandHydrologicFeatureKind.RETAINED_WATER,
                        cell.index(),
                        cell.position(),
                        significance,
                        -1));
                continue;
            }
            if (cell.edgeOutlet()) {
                features.add(new SkyIslandHydrologicFeature(
                        SkyIslandHydrologicFeatureKind.EDGE_WATERFALL,
                        cell.index(),
                        cell.position(),
                        significance,
                        -1));
                continue;
            }
            if (cell.downstreamIndex() >= 0 && significance >= CHANNEL_THRESHOLD) {
                // Preserve the routed topology as a corridor candidate. Major channels naturally
                // receive significance above MAJOR_CHANNEL_THRESHOLD without introducing a new
                // backend-facing feature class.
                double authored = significance >= MAJOR_CHANNEL_THRESHOLD
                        ? clamp01(0.65 + 0.35 * significance)
                        : significance;
                features.add(new SkyIslandHydrologicFeature(
                        SkyIslandHydrologicFeatureKind.CHANNEL,
                        cell.index(),
                        cell.position(),
                        authored,
                        cell.downstreamIndex()));
            }
        }
        return new SkyIslandHydrologicFeaturePlan(descriptor, features);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
