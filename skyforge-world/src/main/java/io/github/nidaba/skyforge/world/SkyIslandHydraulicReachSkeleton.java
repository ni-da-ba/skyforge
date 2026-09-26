package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One C2 reach plus discharge-scaled geometry before any longitudinal head solution. */
public record SkyIslandHydraulicReachSkeleton(
        SkyIslandGeomorphicReachRoute geomorphicRoute,
        SkyIslandContinuousChannelCenterline centerline,
        List<SkyIslandHydraulicGeometrySkeletonSample> samples,
        double pathLength,
        double maximumBankfullHalfWidth,
        double maximumWaterDepthPotential) {

    public SkyIslandHydraulicReachSkeleton {
        geomorphicRoute = Objects.requireNonNull(geomorphicRoute, "geomorphicRoute");
        centerline = Objects.requireNonNull(centerline, "centerline");
        samples = List.copyOf(samples);
        if (samples.size() != centerline.points().size()) {
            throw new IllegalArgumentException("skeleton samples must match centerline points");
        }
        samples.forEach(sample -> Objects.requireNonNull(sample, "sample"));
        requireFinitePositive(pathLength, "pathLength");
        if (Math.abs(pathLength - centerline.pathLength()) > 1.0e-9) {
            throw new IllegalArgumentException("skeleton path length must equal centerline path length");
        }
        requireFinitePositive(maximumBankfullHalfWidth, "maximumBankfullHalfWidth");
        requireFinitePositive(maximumWaterDepthPotential, "maximumWaterDepthPotential");
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
