package io.github.nidaba.skyforge.world;

/** Shared dimensionless hydraulic-geometry calibration used before any head solve. */
final class SkyIslandHydraulicGeometryCalibration {
    static final double MINIMUM_DISCHARGE = 0.015;
    static final double WIDTH_EXPONENT = 0.50;
    static final double DEPTH_EXPONENT = 0.32;

    private static final double BASE_WIDTH_RADIUS_FRACTION = 0.0035;
    private static final double WIDTH_RADIUS_FRACTION = 0.020;
    private static final double BASE_DEPTH_POTENTIAL = 0.0035;
    private static final double DEPTH_POTENTIAL_RANGE = 0.017;
    private static final double BASE_FREEBOARD_POTENTIAL = 0.0030;
    private static final double DEPTH_FREEBOARD_FRACTION = 0.45;

    private SkyIslandHydraulicGeometryCalibration() {}

    static double bankfullHalfWidth(double nominalRadius, double relativeDischarge) {
        if (!Double.isFinite(nominalRadius) || nominalRadius <= 0.0) {
            throw new IllegalArgumentException("nominalRadius must be finite and positive");
        }
        double q = Math.max(MINIMUM_DISCHARGE, clamp01(relativeDischarge));
        return nominalRadius
                * (BASE_WIDTH_RADIUS_FRACTION
                        + WIDTH_RADIUS_FRACTION * Math.pow(q, WIDTH_EXPONENT));
    }

    static double waterDepthPotential(double relativeDischarge) {
        double q = Math.max(MINIMUM_DISCHARGE, clamp01(relativeDischarge));
        return BASE_DEPTH_POTENTIAL
                + DEPTH_POTENTIAL_RANGE * Math.pow(q, DEPTH_EXPONENT);
    }

    static double freeboardPotential(double relativeDischarge) {
        return freeboardFromDepthPotential(waterDepthPotential(relativeDischarge));
    }

    static double freeboardFromDepthPotential(double waterDepthPotential) {
        if (!Double.isFinite(waterDepthPotential) || waterDepthPotential <= 0.0) {
            throw new IllegalArgumentException(
                    "waterDepthPotential must be finite and positive");
        }
        return BASE_FREEBOARD_POTENTIAL
                + DEPTH_FREEBOARD_FRACTION * waterDepthPotential;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
