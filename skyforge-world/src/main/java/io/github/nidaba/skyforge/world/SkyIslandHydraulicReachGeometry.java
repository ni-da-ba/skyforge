package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One shared-node-consistent continuous centerline with a solved candidate hydraulic profile. */
public record SkyIslandHydraulicReachGeometry(
        SkyIslandGeomorphicReachRoute geomorphicRoute,
        SkyIslandContinuousChannelCenterline centerline,
        List<SkyIslandHydraulicGeometrySample> samples,
        double pathLength,
        double maximumRequiredLowering,
        double meanRequiredLowering,
        double maximumWaterSurfaceSlope,
        double maximumBankfullHalfWidth,
        double maximumWaterDepthPotential) {

    public SkyIslandHydraulicReachGeometry {
        geomorphicRoute = Objects.requireNonNull(geomorphicRoute, "geomorphicRoute");
        centerline = Objects.requireNonNull(centerline, "centerline");
        if (!centerline.searchRoute().equals(geomorphicRoute.route())) {
            throw new IllegalArgumentException("continuous centerline must derive from geomorphic search route");
        }
        samples = List.copyOf(samples);
        if (samples.size() != centerline.points().size()) {
            throw new IllegalArgumentException("hydraulic samples must match continuous centerline points");
        }
        samples.forEach(sample -> Objects.requireNonNull(sample, "sample"));
        requireFinitePositive(pathLength, "pathLength");
        if (Math.abs(pathLength - centerline.pathLength()) > 1.0e-9) {
            throw new IllegalArgumentException("hydraulic path length must equal continuous centerline length");
        }
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
