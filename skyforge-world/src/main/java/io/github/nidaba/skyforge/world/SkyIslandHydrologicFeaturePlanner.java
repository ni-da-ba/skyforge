package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        var cellsByIndex = watershed.cells().stream().collect(
                java.util.stream.Collectors.toMap(SkyIslandWatershedCell::index, cell -> cell));
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
                SkyIslandWatershedCell downstream = cellsByIndex.get(cell.downstreamIndex());
                if (downstream == null) {
                    throw new IllegalStateException(
                            "watershed channel candidate references missing downstream cell");
                }

                /*
                 * Priority-Flood transport can legitimately climb the raw DEM while crossing a
                 * filled depression toward its spill saddle. That edge is hydrologic transport,
                 * not automatically a visible river reach. Expose only raw-terrain non-uphill
                 * edges here; accumulation still propagates through the full watershed graph so a
                 * downstream trunk retains the upstream catchment's discharge.
                 */
                boolean visibleSurfaceDescent =
                        downstream.surfacePotential() <= cell.surfacePotential() + 1.0e-10;
                if (visibleSurfaceDescent) {
                    routableCellCount++;
                    if (significance >= CHANNEL_THRESHOLD) {
                        channelCandidates.add(cell);
                    }
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

        /*
         * The budget selects high-value seeds, not isolated individual cells. Equal-accumulation
         * reaches are common, so a strict top-N cut can otherwise stop a visible tributary halfway
         * down an ordinary downhill run merely because the next cell lost an index tie-break.
         * Close each seed downstream across raw-surface descent until an explicit authored
         * transport boundary is reached. This may exceed the seed budget by a small connector
         * count, but preserves a coherent visible network without exposing Priority-Flood climbs.
         */
        Set<Integer> candidateIndices = channelCandidates.stream()
                .map(SkyIslandWatershedCell::index)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<Integer> selectedIndices = new LinkedHashSet<>();
        for (int i = 0; i < selected; i++) {
            selectedIndices.add(channelCandidates.get(i).index());
        }
        for (int seed : List.copyOf(selectedIndices)) {
            int currentIndex = seed;
            Set<Integer> visited = new HashSet<>();
            while (visited.add(currentIndex)) {
                SkyIslandWatershedCell current = cellsByIndex.get(currentIndex);
                if (current == null || current.downstreamIndex() < 0) {
                    break;
                }
                SkyIslandWatershedCell downstream = cellsByIndex.get(current.downstreamIndex());
                if (downstream == null) {
                    throw new IllegalStateException(
                            "selected channel closure references missing downstream watershed cell");
                }
                if (downstream.retainedSink()
                        || downstream.edgeOutlet()
                        || downstream.downstreamIndex() < 0
                        || downstream.surfacePotential() > current.surfacePotential() + 1.0e-10) {
                    break;
                }
                if (!candidateIndices.contains(downstream.index())) {
                    throw new IllegalStateException(
                            "downhill selected channel closure lost an accumulation-qualified candidate");
                }
                selectedIndices.add(downstream.index());
                currentIndex = downstream.index();
            }
        }

        selectedIndices.stream().sorted().forEach(index -> {
            SkyIslandWatershedCell cell = cellsByIndex.get(index);
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
        });
        return new SkyIslandHydrologicFeaturePlan(descriptor, features);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
