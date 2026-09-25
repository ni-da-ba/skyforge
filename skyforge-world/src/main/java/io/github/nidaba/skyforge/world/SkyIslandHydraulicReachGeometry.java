package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One shared-node-consistent route with a solved candidate longitudinal hydraulic profile. */
public record SkyIslandHydraulicReachGeometry(
        SkyIslandGeomorphicReachRoute geomorphicRoute,
        List<SkyIslandHydraulicGeometrySample> samples,
        double pathLength,
        double maximumRequiredLowering,
        double meanRequiredLowering,
        double maximumWaterSurfaceSlope,
        double maximumBankfullHalfWidth,
        double maximumWaterDepthPotential) {

    public SkyIslandHydraulicReachGeometry {
        geomorphicRoute = Objects.requireNonNull(geomorphicRoute, "geomorphicRoute");
        samples = List.copyOf(samples);
        if (samples.size() != geomorphicRoute.route().points().size()) {
            throw new IllegalArgumentException("hydraulic samples must match geomorphic route points");
        }
        samples.forEach(sample -> Objects.requireNonNull(sample, "sample"));
        requireFinitePositive(pathLength, "pathLength");
        requireFiniteNonNegative(maximumRequiredLowering, "maximumRequiredLowering");
        requireFiniteNonNegative(meanRequiredLowering, "meanRequiredLowering");
        requireFiniteNonNegative(maximumWaterSurfaceSlope, "maximumWaterSurfaceSlope");
        requireFinitePositive(maximumBankfullHalfWidth, "maximumBankfullHalfWidth");
        requireFinitePositive(maximumWaterDepthPotential, "maximumWaterDepthPotential");
    }

    public double startWaterSurfacePotential() {
        return samples.getFirst().waterSurfacePotential();
    }

    public double endWaterSurfacePotential() {
        return samples.getLast().waterSurfacePotential();
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
