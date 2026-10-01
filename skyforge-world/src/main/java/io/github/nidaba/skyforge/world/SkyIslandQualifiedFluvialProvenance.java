package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Stable semantic provenance for one accepted realized fluvial influence. */
public record SkyIslandQualifiedFluvialProvenance(
        int startCellIndex,
        int endCellIndex,
        SkyIslandChannelProfileKind profileKind) {

    public SkyIslandQualifiedFluvialProvenance {
        if (startCellIndex < 0 || endCellIndex < 0 || startCellIndex == endCellIndex) {
            throw new IllegalArgumentException("fluvial provenance requires distinct non-negative endpoints");
        }
        profileKind = Objects.requireNonNull(profileKind, "profileKind");
    }
}
