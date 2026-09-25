package io.github.nidaba.skyforge.world;

/** Hard limits for one retained open-water kind. */
public record SkyIslandContinuousWaterbodyQualificationLimits(
        double maximumDepthToEquivalentDiameterRatio,
        double maximumShorelineGrade,
        double minimumSpillHeadroomWorldUnits,
        double maximumChannelDatumMismatchWorldUnits,
        int minimumShorelineCrossings) {

    public SkyIslandContinuousWaterbodyQualificationLimits {
        requirePositive(maximumDepthToEquivalentDiameterRatio, "maximumDepthToEquivalentDiameterRatio");
        requirePositive(maximumShorelineGrade, "maximumShorelineGrade");
        requireNonNegative(minimumSpillHeadroomWorldUnits, "minimumSpillHeadroomWorldUnits");
        requireNonNegative(maximumChannelDatumMismatchWorldUnits, "maximumChannelDatumMismatchWorldUnits");
        if (minimumShorelineCrossings < 1) {
            throw new IllegalArgumentException("minimumShorelineCrossings must be positive");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
