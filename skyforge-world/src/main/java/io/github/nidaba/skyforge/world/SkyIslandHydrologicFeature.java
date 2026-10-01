package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One semantic hydrologic feature candidate emitted from the authored watershed plan. */
public record SkyIslandHydrologicFeature(
        SkyIslandHydrologicFeatureKind kind,
        int sourceCellIndex,
        SkyIslandLocalPosition position,
        double significance,
        int downstreamCellIndex) {

    public SkyIslandHydrologicFeature {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        if (sourceCellIndex < 0 || downstreamCellIndex < -1 || !Double.isFinite(significance)
                || significance < 0.0 || significance > 1.0) {
            throw new IllegalArgumentException("invalid hydrologic feature");
        }
    }
}
