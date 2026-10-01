package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Aggregate profile/drop evidence for one semantic macro reach. */
public record SkyIslandHydraulicDiscontinuityDiagnostics(
        SkyIslandSemanticChannelReach reach,
        int profileTransitionCount,
        int cascadeProfileCount,
        double sourceSurfacePotential,
        double terminalSurfacePotential,
        double netAuthoredDropPotential,
        double accumulatedDownhillDropPotential,
        double cascadeDownhillDropPotential,
        double maximumSingleSegmentDropPotential) {

    public SkyIslandHydraulicDiscontinuityDiagnostics {
        reach = Objects.requireNonNull(reach, "reach");
        if (profileTransitionCount < 0 || cascadeProfileCount < 0) {
            throw new IllegalArgumentException("diagnostic counts must be non-negative");
        }
        requireNormalized(sourceSurfacePotential, "sourceSurfacePotential");
        requireNormalized(terminalSurfacePotential, "terminalSurfacePotential");
        requireNonNegative(netAuthoredDropPotential, "netAuthoredDropPotential");
        requireNonNegative(accumulatedDownhillDropPotential, "accumulatedDownhillDropPotential");
        requireNonNegative(cascadeDownhillDropPotential, "cascadeDownhillDropPotential");
        requireNonNegative(maximumSingleSegmentDropPotential, "maximumSingleSegmentDropPotential");
        if (cascadeDownhillDropPotential > accumulatedDownhillDropPotential + 1.0e-10) {
            throw new IllegalArgumentException("cascade drop cannot exceed accumulated downhill drop");
        }
    }

    public double cascadeShareOfDownhillDrop() {
        return accumulatedDownhillDropPotential <= 1.0e-12
                ? 0.0
                : cascadeDownhillDropPotential / accumulatedDownhillDropPotential;
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and normalized");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
