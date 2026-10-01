package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Extracts channel, retained-water, and waterfall candidates from watershed topology. */
public final class SkyIslandHydrologicFeaturePlanner {
    private static final double CHANNEL_THRESHOLD = 0.16;
    private static final double MAJOR_CHANNEL_THRESHOLD = 0.36;
    private static final double MIN_CHANNEL_FRACTION = 0.015;
    private static final double HYDROLOGIC_CHANNEL_FRACTION = 0.045;

    private SkyIslandHydrologicFeaturePlanner() {}

    public static SkyIslandHydrologicFeaturePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        double max = Math.max(1.0e-12, watershed.maxFlowAccumulation());
        List<SkyIslandHydrologicFeature> features = new ArrayList<>();
        List<SkyIslandWatershedCell> channelCandidates = new ArrayList<>();
        int routableCellCount = 0;

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
            if (cell.downstreamIndex() >= 0) {
                routableCellCount++;
                if (significance >= CHANNEL_THRESHOLD) {
                    channelCandidates.add(cell);
                }
            }
        }

        // Relative-to-maximum accumulation alone becomes unstable when many drainage paths have
        // similarly weak maxima: a large fraction of the island can be mislabeled as channel.
        // Bound the semantic corridor budget by the routable domain and authored hydrological
        // potential, then keep the strongest accumulation cells. Because accumulation is
        // non-decreasing downstream, this preserves the significant routed trunks while preventing
        // low-contrast watersheds from turning into a carpet of parallel channels.
        channelCandidates.sort(Comparator.comparingDouble(SkyIslandWatershedCell::flowAccumulation)
                .reversed()
                .thenComparingInt(SkyIslandWatershedCell::index));
        double channelFraction = MIN_CHANNEL_FRACTION
                + HYDROLOGIC_CHANNEL_FRACTION * descriptor.hydrologicalPotential();
        int channelBudget = Math.max(1, (int) Math.ceil(routableCellCount * channelFraction));
        int selected = Math.min(channelBudget, channelCandidates.size());

        for (int i = 0; i < selected; i++) {
            SkyIslandWatershedCell cell = channelCandidates.get(i);
            double significance = clamp01(cell.flowAccumulation() / max);
            // Preserve the routed topology as a corridor candidate. Major channels naturally
            // receive boosted authored significance without introducing a backend-facing class.
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
        return new SkyIslandHydrologicFeaturePlan(descriptor, features);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
