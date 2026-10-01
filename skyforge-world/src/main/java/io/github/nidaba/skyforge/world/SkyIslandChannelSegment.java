package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One backend-neutral routed channel segment with semantic network hierarchy. */
public record SkyIslandChannelSegment(
        int sourceCellIndex,
        int downstreamCellIndex,
        SkyIslandLocalPosition start,
        SkyIslandLocalPosition end,
        int streamOrder,
        SkyIslandChannelRole role,
        double relativeDischarge,
        double corridorScale) {

    public SkyIslandChannelSegment {
        if (sourceCellIndex < 0 || downstreamCellIndex < 0 || sourceCellIndex == downstreamCellIndex) {
            throw new IllegalArgumentException("invalid channel segment cell identity");
        }
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        role = Objects.requireNonNull(role, "role");
        if (streamOrder < 1) {
            throw new IllegalArgumentException("streamOrder must be positive");
        }
        requireNormalized(relativeDischarge, "relativeDischarge");
        requireNormalized(corridorScale, "corridorScale");
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
