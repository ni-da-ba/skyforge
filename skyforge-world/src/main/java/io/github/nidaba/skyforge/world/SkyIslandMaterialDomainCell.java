package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One coarse host-material cell participating in a mesoscale AUTH-0032 material domain. */
public record SkyIslandMaterialDomainCell(
        int index,
        int xIndex,
        int depthIndex,
        int zIndex,
        SkyIslandSubsurfacePosition position,
        double membership) {

    public SkyIslandMaterialDomainCell {
        if (index < 0 || xIndex < 0 || depthIndex < 0 || zIndex < 0) {
            throw new IllegalArgumentException("material-domain cell indices must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(membership) || membership <= 0.0 || membership > 1.0) {
            throw new IllegalArgumentException("membership must be finite and in (0, 1]");
        }
    }
}
