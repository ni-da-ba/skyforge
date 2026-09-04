package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic AUTH-0033 semantic material-family interpretation for one authored island. */
public record SkyIslandMaterialFamilyPlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        int depthSamples,
        double horizontalSpacing,
        double depthSpacing,
        int activeHostCells,
        List<SkyIslandMaterialFamilyCell> cells) {

    public SkyIslandMaterialFamilyPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 2 || depthSamples < 2) {
            throw new IllegalArgumentException("material-family plan requires at least a 2 x 2 x 2 grid");
        }
        if (!Double.isFinite(horizontalSpacing)
                || horizontalSpacing <= 0.0
                || !Double.isFinite(depthSpacing)
                || depthSpacing <= 0.0) {
            throw new IllegalArgumentException("material-family spacing must be positive and finite");
        }
        if (activeHostCells < 1 || activeHostCells > gridSize * depthSamples * gridSize) {
            throw new IllegalArgumentException("activeHostCells must fit inside planning lattice");
        }
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "material-family cell"));
        if (cells.size() != activeHostCells) {
            throw new IllegalArgumentException(
                    "material-family cells must cover the complete active host planning volume");
        }
    }

    public int cellCountAbove(SkyIslandMaterialFamilyKind kind, double threshold) {
        requireThreshold(threshold);
        return (int) cells.stream()
                .filter(cell -> cell.membership(kind) > threshold)
                .count();
    }

    public double meanMembership(SkyIslandMaterialFamilyKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cells.stream()
                .mapToDouble(cell -> cell.membership(kind))
                .average()
                .orElse(0.0);
    }

    public double peakMembership(SkyIslandMaterialFamilyKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cells.stream()
                .mapToDouble(cell -> cell.membership(kind))
                .max()
                .orElse(0.0);
    }

    private static void requireThreshold(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be finite and in [0, 1]");
        }
    }
}
