package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Connected semantic inundation footprint for one retained-waterbody candidate. */
public record SkyIslandWaterbodyFootprint(
        SkyIslandWaterbodyCandidate candidate,
        double waterSurfacePotential,
        double spillSurfacePotential,
        double fillFraction,
        List<SkyIslandWaterbodyFootprintCell> cells) {

    public SkyIslandWaterbodyFootprint {
        Objects.requireNonNull(candidate, "candidate");
        cells = List.copyOf(cells);
        requireNormalized("waterSurfacePotential", waterSurfacePotential);
        requireNormalized("spillSurfacePotential", spillSurfacePotential);
        requireNormalized("fillFraction", fillFraction);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("waterbody footprint must contain its retained sink");
        }
        if (waterSurfacePotential > spillSurfacePotential + 1.0e-10) {
            throw new IllegalArgumentException("planned water surface cannot exceed spill surface");
        }
    }

    public int inundatedCellCount() {
        return cells.size();
    }

    public long shorelineCellCount() {
        return cells.stream().filter(SkyIslandWaterbodyFootprintCell::shoreline).count();
    }

    public double inundatedCatchmentFraction() {
        return Math.min(1.0, (double) cells.size() / candidate.catchmentCellCount());
    }

    public double maxDepthPotential() {
        return cells.stream()
                .mapToDouble(SkyIslandWaterbodyFootprintCell::waterDepthPotential)
                .max()
                .orElse(0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
