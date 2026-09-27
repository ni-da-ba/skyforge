package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Association-specific F4E longitudinal water-head diagnostics for one realized reach. */
public record SkyIslandWorldWaterReachDiagnostics(
        SkyIslandHydraulicReachGeometry reach,
        int centerlineSamples,
        int wetCenterlineSamples,
        double minimumWaterHeadWorld,
        double maximumWaterHeadWorld,
        int uphillSegments,
        double maximumUpclimbWorld,
        double maximumUpclimbGrade,
        double maximumAbsoluteGrade) {

    public SkyIslandWorldWaterReachDiagnostics {
        reach = Objects.requireNonNull(reach, "reach");
        if (centerlineSamples < 2 || wetCenterlineSamples != centerlineSamples) {
            throw new IllegalArgumentException(
                    "F4E reach diagnostics require every centerline sample to be wet");
        }
        if (uphillSegments < 0 || uphillSegments >= centerlineSamples) {
            throw new IllegalArgumentException("invalid F4E uphill segment count");
        }
        requireFinite(minimumWaterHeadWorld, "minimumWaterHeadWorld");
        requireFinite(maximumWaterHeadWorld, "maximumWaterHeadWorld");
        requireNonNegative(maximumUpclimbWorld, "maximumUpclimbWorld");
        requireNonNegative(maximumUpclimbGrade, "maximumUpclimbGrade");
        requireNonNegative(maximumAbsoluteGrade, "maximumAbsoluteGrade");
        if (maximumWaterHeadWorld < minimumWaterHeadWorld) {
            throw new IllegalArgumentException("F4E maximum water head precedes minimum");
        }
        if (uphillSegments == 0
                && (maximumUpclimbWorld > 1.0e-9 || maximumUpclimbGrade > 1.0e-9)) {
            throw new IllegalArgumentException(
                    "zero-uphill F4E diagnostics cannot carry uphill magnitude");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
