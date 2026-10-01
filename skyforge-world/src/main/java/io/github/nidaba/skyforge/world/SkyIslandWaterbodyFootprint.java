package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Connected semantic inundation footprint produced by one or more retained-waterbody candidates. */
public record SkyIslandWaterbodyFootprint(
        List<SkyIslandWaterbodyCandidate> sourceCandidates,
        double waterSurfacePotential,
        double spillSurfacePotential,
        double maxSourceFillFraction,
        int depressionCellCount,
        List<SkyIslandWaterbodyFootprintCell> cells) {

    public SkyIslandWaterbodyFootprint {
        sourceCandidates = List.copyOf(sourceCandidates);
        cells = List.copyOf(cells);
        if (sourceCandidates.isEmpty()) {
            throw new IllegalArgumentException("waterbody footprint requires at least one source candidate");
        }
        sourceCandidates.forEach(candidate -> Objects.requireNonNull(candidate, "source candidate"));
        requireNormalized("waterSurfacePotential", waterSurfacePotential);
        requireNormalized("spillSurfacePotential", spillSurfacePotential);
        requireNormalized("maxSourceFillFraction", maxSourceFillFraction);
        if (depressionCellCount < 1 || cells.isEmpty() || cells.size() > depressionCellCount) {
            throw new IllegalArgumentException("invalid connected depression footprint size");
        }
        if (waterSurfacePotential > spillSurfacePotential + 1.0e-10) {
            throw new IllegalArgumentException("planned water surface cannot exceed spill surface");
        }
    }

    public int sourceCandidateCount() {
        return sourceCandidates.size();
    }

    public int inundatedCellCount() {
        return cells.size();
    }

    public long shorelineCellCount() {
        return cells.stream().filter(SkyIslandWaterbodyFootprintCell::shoreline).count();
    }

    public double inundatedDepressionFraction() {
        return Math.min(1.0, (double) cells.size() / depressionCellCount);
    }

    public double maxDepthPotential() {
        return cells.stream()
                .mapToDouble(SkyIslandWaterbodyFootprintCell::waterDepthPotential)
                .max()
                .orElse(0.0);
    }

    public boolean hasMixedKinds() {
        return sourceCandidates.stream().map(SkyIslandWaterbodyCandidate::kind).distinct().count() > 1;
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
