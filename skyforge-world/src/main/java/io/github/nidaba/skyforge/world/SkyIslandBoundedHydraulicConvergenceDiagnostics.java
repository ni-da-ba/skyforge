package io.github.nidaba.skyforge.world;

/** Native-versus-midpoint-refined F2C numerical evidence on one unchanged C2 polyline. */
public record SkyIslandBoundedHydraulicConvergenceDiagnostics(
        int startCellIndex,
        int endCellIndex,
        int nativeSampleCount,
        int refinedSampleCount,
        double nativeStartHeadWorld,
        double refinedStartHeadWorld,
        double nativeEndHeadWorld,
        double refinedEndHeadWorld,
        double nativeObjectivePerLength,
        double refinedObjectivePerLength,
        double nativeMaximumLongitudinalGrade,
        double refinedMaximumLongitudinalGrade,
        double nativeExcavationVolumeProxy,
        double refinedExcavationVolumeProxy,
        double nativeMaximumLoweringWorld,
        double refinedMaximumLoweringWorld,
        boolean nativeHeadDependentD2Pass,
        boolean refinedHeadDependentD2Pass) {

    public SkyIslandBoundedHydraulicConvergenceDiagnostics {
        if (startCellIndex < 0 || endCellIndex < 0 || startCellIndex == endCellIndex) {
            throw new IllegalArgumentException("convergence diagnostics require distinct reach anchors");
        }
        if (nativeSampleCount < 2 || refinedSampleCount <= nativeSampleCount) {
            throw new IllegalArgumentException("refined collocation must contain more samples");
        }
        requireFinite(nativeStartHeadWorld, "nativeStartHeadWorld");
        requireFinite(refinedStartHeadWorld, "refinedStartHeadWorld");
        requireFinite(nativeEndHeadWorld, "nativeEndHeadWorld");
        requireFinite(refinedEndHeadWorld, "refinedEndHeadWorld");
        requireFiniteNonNegative(nativeObjectivePerLength, "nativeObjectivePerLength");
        requireFiniteNonNegative(refinedObjectivePerLength, "refinedObjectivePerLength");
        requireFiniteNonNegative(nativeMaximumLongitudinalGrade, "nativeMaximumLongitudinalGrade");
        requireFiniteNonNegative(refinedMaximumLongitudinalGrade, "refinedMaximumLongitudinalGrade");
        requireFiniteNonNegative(nativeExcavationVolumeProxy, "nativeExcavationVolumeProxy");
        requireFiniteNonNegative(refinedExcavationVolumeProxy, "refinedExcavationVolumeProxy");
        requireFiniteNonNegative(nativeMaximumLoweringWorld, "nativeMaximumLoweringWorld");
        requireFiniteNonNegative(refinedMaximumLoweringWorld, "refinedMaximumLoweringWorld");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
