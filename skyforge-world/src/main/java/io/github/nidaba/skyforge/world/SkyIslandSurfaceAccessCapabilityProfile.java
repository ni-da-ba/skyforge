package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0097 threshold-free directional surface-access evidence over one exact AUTH-0096 profile.
 */
public record SkyIslandSurfaceAccessCapabilityProfile(
        SkyIslandSurfaceSiteCapabilityProfile sourceProfile,
        List<SkyIslandSurfaceAccessCapabilityCell> cells) {

    public SkyIslandSurfaceAccessCapabilityProfile {
        sourceProfile = Objects.requireNonNull(sourceProfile, "sourceProfile");
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "surface-access cell"));

        if (cells.size() != sourceProfile.cells().size()) {
            throw new IllegalArgumentException(
                    "surface-access capability must cover every AUTH-0096 anchor exactly once");
        }
        for (int ordinal = 0; ordinal < cells.size(); ordinal++) {
            if (!cells.get(ordinal).sourceCell().equals(sourceProfile.cells().get(ordinal))) {
                throw new IllegalArgumentException(
                        "surface-access capability must preserve exact AUTH-0096 anchor order and identity");
            }
        }
    }

    public SkyIslandAuthoredRealizationAssociation association() {
        return sourceProfile.association();
    }

    public int gridSize() {
        return sourceProfile.gridSize();
    }

    public double spacing() {
        return sourceProfile.spacing();
    }

    public int cellCount() {
        return cells.size();
    }

    public long directionalAnchorCount() {
        return cells.stream().filter(cell -> !cell.rays().isEmpty()).count();
    }
}
