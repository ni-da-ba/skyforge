package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

/** Deterministic high-level placement policy for child island groups. */
public sealed interface SkyIslandArchipelagoLayout
        permits SkyIslandArchipelagoLayout.Arc, SkyIslandArchipelagoLayout.Hub {

    /** Stable semantic layout identifier. */
    String kind();

    /**
     * Ordered corridor of groups following a broad curved arc.
     *
     * <p>{@code preferredCenterSpacing} is a stylistic target, not a safety bound. The planner
     * raises each edge spacing as needed to honor the actual adjacent group reservations.
     */
    record Arc(
            double headingRadians,
            double preferredCenterSpacing,
            double spacingJitterFraction,
            double lateralJitter,
            double curveAmplitude,
            double orientationJitterRadians,
            double elevationJitter)
            implements SkyIslandArchipelagoLayout {
        public Arc {
            requireFinite("headingRadians", headingRadians);
            requirePositive("preferredCenterSpacing", preferredCenterSpacing);
            requireRange("spacingJitterFraction", spacingJitterFraction, 0.0, 0.5);
            requireNonNegative("lateralJitter", lateralJitter);
            requireFinite("curveAmplitude", curveAmplitude);
            requireRange("orientationJitterRadians", orientationJitterRadians, 0.0, Math.PI);
            requireNonNegative("elevationJitter", elevationJitter);
        }

        @Override
        public String kind() {
            return "arc";
        }
    }

    /**
     * One dominant anchor group surrounded by deterministically placed secondary/satellite groups.
     */
    record Hub(
            double preferredRadialSpacing,
            double phaseRadians,
            double radialJitterFraction,
            double orientationJitterRadians,
            double elevationJitter)
            implements SkyIslandArchipelagoLayout {
        public Hub {
            requirePositive("preferredRadialSpacing", preferredRadialSpacing);
            requireFinite("phaseRadians", phaseRadians);
            requireRange("radialJitterFraction", radialJitterFraction, 0.0, 0.5);
            requireRange("orientationJitterRadians", orientationJitterRadians, 0.0, Math.PI);
            requireNonNegative("elevationJitter", elevationJitter);
        }

        @Override
        public String kind() {
            return "hub";
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }

    private static void requireRange(String property, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    property + " must be finite and in [" + minimum + ", " + maximum + "]");
        }
    }
}
