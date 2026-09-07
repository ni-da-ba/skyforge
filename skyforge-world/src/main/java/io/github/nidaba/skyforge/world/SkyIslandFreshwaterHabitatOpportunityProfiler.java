package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0091 fixed profiler from accepted watershed/retained-waterbody semantics to one island-scale
 * freshwater habitat opportunity summary.
 */
public final class SkyIslandFreshwaterHabitatOpportunityProfiler {

    public SkyIslandFreshwaterHabitatOpportunityProfile profile(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan footprints =
                SkyIslandWaterbodyFootprintPlanner.plan(descriptor);

        Map<Integer, SkyIslandWaterbodyFootprintCell> uniqueCells = new HashMap<>();
        Map<Integer, Boolean> shorelineByCell = new HashMap<>();
        long sources = 0L;
        EnumMap<SkyIslandWaterbodyKind, Long> sourceKinds =
                new EnumMap<>(SkyIslandWaterbodyKind.class);
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            sourceKinds.put(kind, 0L);
        }

        for (SkyIslandWaterbodyFootprint footprint : footprints.footprints()) {
            for (SkyIslandWaterbodyCandidate source : footprint.sourceCandidates()) {
                sources++;
                sourceKinds.put(source.kind(), sourceKinds.get(source.kind()) + 1L);
            }
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                SkyIslandWaterbodyFootprintCell previous =
                        uniqueCells.putIfAbsent(cell.watershedCellIndex(), cell);
                if (previous != null && !previous.equals(cell)) {
                    throw new IllegalStateException(
                            "retained freshwater footprints disagree on one watershed cell");
                }
                shorelineByCell.merge(
                        cell.watershedCellIndex(),
                        cell.shoreline(),
                        Boolean::logicalOr);
            }
        }

        long inundated = uniqueCells.size();
        long shoreline = shorelineByCell.values().stream()
                .filter(Boolean::booleanValue)
                .count();
        double depthSum = uniqueCells.values().stream()
                .mapToDouble(SkyIslandWaterbodyFootprintCell::waterDepthPotential)
                .sum();
        double maxDepth = uniqueCells.values().stream()
                .mapToDouble(SkyIslandWaterbodyFootprintCell::waterDepthPotential)
                .max()
                .orElse(0.0);
        double meanDepth = inundated == 0L ? 0.0 : depthSum / inundated;
        double cellArea = watershed.spacing() * watershed.spacing();

        return new SkyIslandFreshwaterHabitatOpportunityProfile(
                descriptor,
                watershed,
                footprints,
                sources,
                inundated,
                inundated * cellArea,
                shoreline,
                meanDepth,
                maxDepth,
                sourceKinds);
    }
}
