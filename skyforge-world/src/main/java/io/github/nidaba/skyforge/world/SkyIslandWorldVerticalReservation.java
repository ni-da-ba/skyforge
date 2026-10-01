package io.github.nidaba.skyforge.world;

/** Explicit conservative vertical reservation around an island suspension elevation. */
public record SkyIslandWorldVerticalReservation(
        double belowSuspension,
        double aboveSuspension) {

    /** Validates finite non-negative extents with a non-zero total span. */
    public SkyIslandWorldVerticalReservation {
        requireNonNegative("belowSuspension", belowSuspension);
        requireNonNegative("aboveSuspension", aboveSuspension);
        if (belowSuspension == 0.0 && aboveSuspension == 0.0) {
            throw new IllegalArgumentException("vertical reservation must have non-zero span");
        }
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
