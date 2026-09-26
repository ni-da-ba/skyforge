package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** D2-derived admissible world-space hydraulic-head interval at one ordinary boundary sample. */
public record SkyIslandHydraulicHeadEnvelope(
        SkyIslandChannelProfileKind profileKind,
        double targetHead,
        double lowerHead,
        double upperHead) {

    public SkyIslandHydraulicHeadEnvelope {
        profileKind = Objects.requireNonNull(profileKind, "profileKind");
        requireFinite(targetHead, "targetHead");
        requireFinite(lowerHead, "lowerHead");
        requireFinite(upperHead, "upperHead");
    }

    public boolean feasible(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative");
        }
        return lowerHead <= upperHead + tolerance;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
