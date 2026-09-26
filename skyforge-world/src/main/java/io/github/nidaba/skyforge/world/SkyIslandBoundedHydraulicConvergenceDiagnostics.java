package io.github.nidaba.skyforge.world;

/** Three-resolution F2C collocation evidence on one unchanged accepted C2 polyline. */
public record SkyIslandBoundedHydraulicConvergenceDiagnostics(
        int startCellIndex,
        int endCellIndex,
        int coarseSampleCount,
        int mediumSampleCount,
        int fineSampleCount,
        double coarseStartHeadWorld,
        double mediumStartHeadWorld,
        double fineStartHeadWorld,
        double coarseEndHeadWorld,
        double mediumEndHeadWorld,
        double fineEndHeadWorld,
        double coarseObjectivePerLength,
        double mediumObjectivePerLength,
        double fineObjectivePerLength,
        double coarseMaximumLongitudinalGrade,
        double mediumMaximumLongitudinalGrade,
        double fineMaximumLongitudinalGrade,
        double coarseExcavationVolumeProxy,
        double mediumExcavationVolumeProxy,
        double fineExcavationVolumeProxy,
        double coarseMaximumLoweringWorld,
        double mediumMaximumLoweringWorld,
        double fineMaximumLoweringWorld,
        boolean coarseHeadDependentD2Pass,
        boolean mediumHeadDependentD2Pass,
        boolean fineHeadDependentD2Pass) {

    public SkyIslandBoundedHydraulicConvergenceDiagnostics {
        if (startCellIndex < 0 || endCellIndex < 0 || startCellIndex == endCellIndex) {
            throw new IllegalArgumentException("convergence diagnostics require distinct reach anchors");
        }
        if (coarseSampleCount < 2
                || mediumSampleCount != 2 * coarseSampleCount - 1
                || fineSampleCount != 2 * mediumSampleCount - 1) {
            throw new IllegalArgumentException(
                    "convergence diagnostics require two exact midpoint refinements");
        }
        requireFinite(coarseStartHeadWorld, "coarseStartHeadWorld");
        requireFinite(mediumStartHeadWorld, "mediumStartHeadWorld");
        requireFinite(fineStartHeadWorld, "fineStartHeadWorld");
        requireFinite(coarseEndHeadWorld, "coarseEndHeadWorld");
        requireFinite(mediumEndHeadWorld, "mediumEndHeadWorld");
        requireFinite(fineEndHeadWorld, "fineEndHeadWorld");
        requireFiniteNonNegative(coarseObjectivePerLength, "coarseObjectivePerLength");
        requireFiniteNonNegative(mediumObjectivePerLength, "mediumObjectivePerLength");
        requireFiniteNonNegative(fineObjectivePerLength, "fineObjectivePerLength");
        requireFiniteNonNegative(coarseMaximumLongitudinalGrade, "coarseMaximumLongitudinalGrade");
        requireFiniteNonNegative(mediumMaximumLongitudinalGrade, "mediumMaximumLongitudinalGrade");
        requireFiniteNonNegative(fineMaximumLongitudinalGrade, "fineMaximumLongitudinalGrade");
        requireFiniteNonNegative(coarseExcavationVolumeProxy, "coarseExcavationVolumeProxy");
        requireFiniteNonNegative(mediumExcavationVolumeProxy, "mediumExcavationVolumeProxy");
        requireFiniteNonNegative(fineExcavationVolumeProxy, "fineExcavationVolumeProxy");
        requireFiniteNonNegative(coarseMaximumLoweringWorld, "coarseMaximumLoweringWorld");
        requireFiniteNonNegative(mediumMaximumLoweringWorld, "mediumMaximumLoweringWorld");
        requireFiniteNonNegative(fineMaximumLoweringWorld, "fineMaximumLoweringWorld");
    }

    public boolean headDependentClassificationStable() {
        return coarseHeadDependentD2Pass == mediumHeadDependentD2Pass
                && mediumHeadDependentD2Pass == fineHeadDependentD2Pass;
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
