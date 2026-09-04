package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One sparse semantic intent for connecting an existing cave system to the island exterior.
 *
 * <p>The intent does not modify AUTH-0025/AUTH-0026 geometry. It records the cave-side anchor,
 * corresponding exterior boundary anchor, and explainable support used to justify a future opening.
 */
public record SkyIslandCaveExposureIntent(
        int systemId,
        SkyIslandCaveExposureSide side,
        SkyIslandCaveVolumeSample.PrimitiveKind sourcePrimitiveKind,
        int sourcePrimitiveId,
        SkyIslandSubsurfacePosition caveAnchor,
        SkyIslandSubsurfacePosition boundaryAnchor,
        double semanticGap,
        double score,
        double proximitySupport,
        double fractureSupport,
        double weatheringSupport,
        double hydrologicSupport) {

    public SkyIslandCaveExposureIntent {
        if (systemId < 0 || sourcePrimitiveId < 0) {
            throw new IllegalArgumentException("exposure identifiers must be non-negative");
        }
        side = Objects.requireNonNull(side, "side");
        sourcePrimitiveKind = Objects.requireNonNull(sourcePrimitiveKind, "sourcePrimitiveKind");
        if (sourcePrimitiveKind == SkyIslandCaveVolumeSample.PrimitiveKind.NONE) {
            throw new IllegalArgumentException("cave exposure must originate from chamber or passage geometry");
        }
        caveAnchor = Objects.requireNonNull(caveAnchor, "caveAnchor");
        boundaryAnchor = Objects.requireNonNull(boundaryAnchor, "boundaryAnchor");
        if (!caveAnchor.surfacePosition().equals(boundaryAnchor.surfacePosition())) {
            throw new IllegalArgumentException("first-generation exposure anchors must share horizontal position");
        }
        double expectedBoundary = side == SkyIslandCaveExposureSide.UPPER_SURFACE ? 0.0 : 1.0;
        if (Double.doubleToLongBits(boundaryAnchor.depthFraction())
                != Double.doubleToLongBits(expectedBoundary)) {
            throw new IllegalArgumentException("boundary anchor depth does not match exposure side");
        }
        requireNormalized("semanticGap", semanticGap);
        requireNormalized("score", score);
        requireNormalized("proximitySupport", proximitySupport);
        requireNormalized("fractureSupport", fractureSupport);
        requireNormalized("weatheringSupport", weatheringSupport);
        requireNormalized("hydrologicSupport", hydrologicSupport);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
