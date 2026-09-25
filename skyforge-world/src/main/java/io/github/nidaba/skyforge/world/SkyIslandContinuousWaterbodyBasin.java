package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * One sink-connected retained-water candidate solved against continuous pre-basin terrain.
 *
 * <p>The fine sample lattice is numerical evidence for one continuous sublevel set. Coarse watershed
 * footprint cells are not physical shoreline authority.
 */
public record SkyIslandContinuousWaterbodyBasin(
        SkyIslandWaterbodyCandidate sourceCandidate,
        double waterSurfacePotential,
        double spillSurfacePotential,
        double sampleSpacing,
        int connectedSampleCount,
        double approximateArea,
        double maximumDepthPotential,
        boolean reachesSearchBoundary,
        List<SkyIslandLocalPosition> shorelineCrossings) {

    public SkyIslandContinuousWaterbodyBasin {
        sourceCandidate = Objects.requireNonNull(sourceCandidate, "sourceCandidate");
        requireNormalized(waterSurfacePotential, "waterSurfacePotential");
        requireNormalized(spillSurfacePotential, "spillSurfacePotential");
        if (waterSurfacePotential > spillSurfacePotential + 1.0e-10) {
            throw new IllegalArgumentException("basin water surface cannot exceed semantic spill surface");
        }
        if (!Double.isFinite(sampleSpacing) || sampleSpacing <= 0.0) {
            throw new IllegalArgumentException("sampleSpacing must be finite and positive");
        }
        if (connectedSampleCount < 1) {
            throw new IllegalArgumentException("continuous basin requires at least one connected wet sample");
        }
        if (!Double.isFinite(approximateArea) || approximateArea <= 0.0) {
            throw new IllegalArgumentException("approximateArea must be finite and positive");
        }
        requireNormalized(maximumDepthPotential, "maximumDepthPotential");
        shorelineCrossings = List.copyOf(shorelineCrossings);
        shorelineCrossings.forEach(point -> Objects.requireNonNull(point, "shoreline crossing"));
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
