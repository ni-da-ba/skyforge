package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Three-resolution C3 route-functional evidence for one semantic reach. */
public record SkyIslandRouteResolutionConvergenceDiagnostics(
        int startCellIndex,
        int endCellIndex,
        SkyIslandRouteFunctionalDiagnostics coarse,
        SkyIslandRouteFunctionalDiagnostics medium,
        SkyIslandRouteFunctionalDiagnostics fine,
        double coarseToMediumPolylineDistance,
        double mediumToFinePolylineDistance) {

    public SkyIslandRouteResolutionConvergenceDiagnostics {
        if (startCellIndex < 0 || endCellIndex < 0 || startCellIndex == endCellIndex) {
            throw new IllegalArgumentException("route convergence requires distinct semantic anchors");
        }
        coarse = Objects.requireNonNull(coarse, "coarse");
        medium = Objects.requireNonNull(medium, "medium");
        fine = Objects.requireNonNull(fine, "fine");
        requireFiniteNonNegative(
                coarseToMediumPolylineDistance, "coarseToMediumPolylineDistance");
        requireFiniteNonNegative(
                mediumToFinePolylineDistance, "mediumToFinePolylineDistance");
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
