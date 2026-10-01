package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0091 island-scale summary of accepted retained freshwater semantics.
 *
 * <p>Area is a coarse horizontal planning estimate derived from the accepted watershed lattice.
 * Water-depth values remain normalized semantic potentials, not physical depth or volume.
 */
public final class SkyIslandFreshwaterHabitatOpportunityProfile {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandWatershedPlan watershed;
    private final SkyIslandWaterbodyFootprintPlan footprintPlan;
    private final long sourceCandidateCount;
    private final long inundatedCellCount;
    private final double coarseHorizontalInundatedAreaEstimate;
    private final long shorelineCellCount;
    private final double meanWaterDepthPotential;
    private final double maxWaterDepthPotential;
    private final Map<SkyIslandWaterbodyKind, Long> sourceKindCounts;

    SkyIslandFreshwaterHabitatOpportunityProfile(
            SkyIslandDescriptor descriptor,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyFootprintPlan footprintPlan,
            long sourceCandidateCount,
            long inundatedCellCount,
            double coarseHorizontalInundatedAreaEstimate,
            long shorelineCellCount,
            double meanWaterDepthPotential,
            double maxWaterDepthPotential,
            Map<SkyIslandWaterbodyKind, Long> sourceKindCounts) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.watershed = Objects.requireNonNull(watershed, "watershed");
        this.footprintPlan = Objects.requireNonNull(footprintPlan, "footprintPlan");
        if (!watershed.descriptor().equals(descriptor)
                || !footprintPlan.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "freshwater profile sources must belong to the exact descriptor");
        }
        if (sourceCandidateCount < 0L || inundatedCellCount < 0L || shorelineCellCount < 0L) {
            throw new IllegalArgumentException("freshwater profile counts must be non-negative");
        }
        if (shorelineCellCount > inundatedCellCount) {
            throw new IllegalArgumentException(
                    "shoreline planning cells cannot exceed inundated planning cells");
        }
        double cellArea = watershed.spacing() * watershed.spacing();
        double expectedArea = inundatedCellCount * cellArea;
        if (Double.doubleToLongBits(coarseHorizontalInundatedAreaEstimate)
                != Double.doubleToLongBits(expectedArea)) {
            throw new IllegalArgumentException(
                    "freshwater area estimate must match unique inundated cells and watershed spacing");
        }
        requireNormalized("meanWaterDepthPotential", meanWaterDepthPotential);
        requireNormalized("maxWaterDepthPotential", maxWaterDepthPotential);
        if (meanWaterDepthPotential > maxWaterDepthPotential + 1.0e-12) {
            throw new IllegalArgumentException(
                    "mean water-depth potential cannot exceed maximum water-depth potential");
        }
        if (inundatedCellCount == 0L
                && (coarseHorizontalInundatedAreaEstimate != 0.0
                        || shorelineCellCount != 0L
                        || meanWaterDepthPotential != 0.0
                        || maxWaterDepthPotential != 0.0
                        || sourceCandidateCount != 0L)) {
            throw new IllegalArgumentException(
                    "zero inundation requires a completely zero freshwater opportunity envelope");
        }

        Objects.requireNonNull(sourceKindCounts, "sourceKindCounts");
        if (sourceKindCounts.size() != SkyIslandWaterbodyKind.values().length) {
            throw new IllegalArgumentException(
                    "sourceKindCounts must cover every accepted waterbody kind exactly");
        }
        long countSum = 0L;
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            Long count = sourceKindCounts.get(kind);
            if (count == null || count < 0L) {
                throw new IllegalArgumentException(
                        "invalid freshwater source-kind count for " + kind);
            }
            countSum = Math.addExact(countSum, count);
        }
        if (countSum != sourceCandidateCount) {
            throw new IllegalArgumentException(
                    "freshwater source-kind counts must sum to sourceCandidateCount");
        }

        this.sourceCandidateCount = sourceCandidateCount;
        this.inundatedCellCount = inundatedCellCount;
        this.coarseHorizontalInundatedAreaEstimate = coarseHorizontalInundatedAreaEstimate;
        this.shorelineCellCount = shorelineCellCount;
        this.meanWaterDepthPotential = meanWaterDepthPotential;
        this.maxWaterDepthPotential = maxWaterDepthPotential;
        this.sourceKindCounts = Map.copyOf(sourceKindCounts);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandWatershedPlan watershed() {
        return watershed;
    }

    public SkyIslandWaterbodyFootprintPlan footprintPlan() {
        return footprintPlan;
    }

    public int footprintCount() {
        return footprintPlan.footprints().size();
    }

    public long sourceCandidateCount() {
        return sourceCandidateCount;
    }

    public long inundatedCellCount() {
        return inundatedCellCount;
    }

    public double coarseHorizontalInundatedAreaEstimate() {
        return coarseHorizontalInundatedAreaEstimate;
    }

    public long shorelineCellCount() {
        return shorelineCellCount;
    }

    public double meanWaterDepthPotential() {
        return meanWaterDepthPotential;
    }

    public double maxWaterDepthPotential() {
        return maxWaterDepthPotential;
    }

    public Map<SkyIslandWaterbodyKind, Long> sourceKindCounts() {
        return sourceKindCounts;
    }

    public long sourceCount(SkyIslandWaterbodyKind kind) {
        Objects.requireNonNull(kind, "kind");
        return sourceKindCounts.get(kind);
    }

    public boolean hasRetainedFreshwater() {
        return inundatedCellCount > 0L;
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
