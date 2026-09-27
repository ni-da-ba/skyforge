package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One F4B world-space projection sample derived from the F4A semantic terrain delta. */
public record SkyIslandProjectedFluvialTerrainSample(
        SkyIslandLocalPosition localPosition,
        SkyIslandQualifiedFluvialSample semanticSample,
        double originalUpperSurfaceWorldY,
        double undersideSurfaceWorldY,
        double terrainDeltaWorldUnits,
        double targetUpperSurfaceWorldY) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandProjectedFluvialTerrainSample {
        localPosition = Objects.requireNonNull(localPosition, "localPosition");
        semanticSample = Objects.requireNonNull(semanticSample, "semanticSample");
        requireFinite(originalUpperSurfaceWorldY, "originalUpperSurfaceWorldY");
        requireFinite(undersideSurfaceWorldY, "undersideSurfaceWorldY");
        requireFinite(terrainDeltaWorldUnits, "terrainDeltaWorldUnits");
        requireFinite(targetUpperSurfaceWorldY, "targetUpperSurfaceWorldY");

        if (terrainDeltaWorldUnits > EPSILON) {
            throw new IllegalArgumentException(
                    "F4B projected hydrology terrain delta must never raise the compiled surface");
        }
        if (Math.abs(
                        targetUpperSurfaceWorldY
                                - (originalUpperSurfaceWorldY + terrainDeltaWorldUnits))
                > EPSILON) {
            throw new IllegalArgumentException(
                    "projected target upper surface must equal compiled upper plus terrain delta");
        }
        if (targetUpperSurfaceWorldY <= undersideSurfaceWorldY + EPSILON) {
            throw new IllegalArgumentException(
                    "projected hydrology surface must remain above the compiled underside"
                            + " at local "
                            + localPosition
                            + " (originalThickness="
                            + (originalUpperSurfaceWorldY - undersideSurfaceWorldY)
                            + ", deltaWorld="
                            + terrainDeltaWorldUnits
                            + ", targetThickness="
                            + (targetUpperSurfaceWorldY - undersideSurfaceWorldY)
                            + ")");
        }
        if (semanticSample.zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED
                && Math.abs(terrainDeltaWorldUnits) > EPSILON) {
            throw new IllegalArgumentException(
                    "unaffected semantic sample cannot project a world-space terrain delta");
        }
    }

    public double originalColumnThicknessWorldUnits() {
        return originalUpperSurfaceWorldY - undersideSurfaceWorldY;
    }

    public double targetColumnThicknessWorldUnits() {
        return targetUpperSurfaceWorldY - undersideSurfaceWorldY;
    }

    /**
     * Signed remaining support after applying the exact F4A-authorized cut.
     *
     * <p>A non-positive value is evidence that the continuous cut would consume the compiled column.
     * F4B reports that condition; it does not clamp the cut, move the underside, or grant backend
     * authority to reconcile it.
     */
    public double supportMarginWorldUnits() {
        return targetColumnThicknessWorldUnits();
    }

    public boolean supportExhausted() {
        return supportMarginWorldUnits() <= EPSILON;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
