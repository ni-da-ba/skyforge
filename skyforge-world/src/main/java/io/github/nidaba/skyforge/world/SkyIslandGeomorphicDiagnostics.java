package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Aggregate pre-carving cross-section and route-shape diagnostics for one hydraulic channel network. */
public record SkyIslandGeomorphicDiagnostics(
        double minimumNaturalBankContainment,
        double maximumSemanticBankRecoveryGrade,
        double maximumCurvatureWidthRatio,
        double normalizedLowerBoundCutVolume) {

    public SkyIslandGeomorphicDiagnostics {
        if (!Double.isFinite(minimumNaturalBankContainment)) {
            throw new IllegalArgumentException("minimumNaturalBankContainment must be finite");
        }
        requireFiniteNonNegative(maximumSemanticBankRecoveryGrade, "maximumSemanticBankRecoveryGrade");
        requireFiniteNonNegative(maximumCurvatureWidthRatio, "maximumCurvatureWidthRatio");
        requireFiniteNonNegative(normalizedLowerBoundCutVolume, "normalizedLowerBoundCutVolume");
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
