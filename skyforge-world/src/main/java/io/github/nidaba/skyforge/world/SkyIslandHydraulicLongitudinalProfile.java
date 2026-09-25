package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Feasible ordinary-reach longitudinal bed and water-surface profile. */
public record SkyIslandHydraulicLongitudinalProfile(
        List<SkyIslandHydraulicProfileStation> stations,
        double verticalReliefScale,
        double maximumPhysicalBedSlope,
        double maximumPhysicalWaterSlope,
        double maximumIncisionWorldUnits) {

    public SkyIslandHydraulicLongitudinalProfile {
        stations = List.copyOf(stations);
        if (stations.size() < 2) {
            throw new IllegalArgumentException("hydraulic profile requires at least two stations");
        }
        stations.forEach(station -> Objects.requireNonNull(station, "station"));
        requirePositive(verticalReliefScale, "verticalReliefScale");
        requireFiniteNonNegative(maximumPhysicalBedSlope, "maximumPhysicalBedSlope");
        requireFiniteNonNegative(maximumPhysicalWaterSlope, "maximumPhysicalWaterSlope");
        requireFiniteNonNegative(maximumIncisionWorldUnits, "maximumIncisionWorldUnits");
    }

    public double maximumIncisionPotential() {
        return maximumIncisionWorldUnits / verticalReliefScale;
    }

    private static void requirePositive(double value, String name) {
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
