package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One coarse semantic cell participating in a mesoscale geological region. */
public record SkyIslandGeologicRegionCell(
        int index,
        int xIndex,
        int depthIndex,
        int zIndex,
        SkyIslandSubsurfacePosition position,
        double membership) {

    public SkyIslandGeologicRegionCell {
        if (index < 0 || xIndex < 0 || depthIndex < 0 || zIndex < 0) {
            throw new IllegalArgumentException("geologic region cell indices must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(membership) || membership < 0.0 || membership > 1.0) {
            throw new IllegalArgumentException("membership must be finite and in [0, 1]");
        }
    }
}
