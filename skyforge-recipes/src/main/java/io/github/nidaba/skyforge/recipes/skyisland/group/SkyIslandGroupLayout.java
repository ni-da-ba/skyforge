package io.github.nidaba.skyforge.recipes.skyisland.group;

/** Deterministic world-space placement policy for one multi-island group. */
public sealed interface SkyIslandGroupLayout
        permits SkyIslandGroupLayout.Chain, SkyIslandGroupLayout.Cluster {

    /** Conservative minimum center spacing guaranteed by this layout before group validation. */
    double minimumCenterSpacing();

    /** Stable semantic layout identifier. */
    String kind();

    /** Ordered curved chain with deterministic spacing and lateral variation. */
    record Chain(
            double headingRadians,
            double centerSpacing,
            double spacingJitterFraction,
            double lateralJitter,
            double curveAmplitude,
            double orientationJitterRadians)
            implements SkyIslandGroupLayout {
        public Chain {
            requireFinite("headingRadians", headingRadians);
            requirePositive("centerSpacing", centerSpacing);
            requireRange("spacingJitterFraction", spacingJitterFraction, 0.0, 0.5);
            requireNonNegative("lateralJitter", lateralJitter);
            requireFinite("curveAmplitude", curveAmplitude);
            requireRange("orientationJitterRadians", orientationJitterRadians, 0.0, Math.PI);
        }

        @Override
        public double minimumCenterSpacing() {
            return centerSpacing * (1.0 - spacingJitterFraction);
        }

        @Override
        public String kind() {
            return "chain";
        }
    }

    /** Organic deterministic cluster with rejection-checked minimum spacing. */
    record Cluster(
            double minimumCenterSpacing,
            double phaseRadians,
            double radialJitterFraction,
            double orientationJitterRadians)
            implements SkyIslandGroupLayout {
        public Cluster {
            requirePositive("minimumCenterSpacing", minimumCenterSpacing);
            requireFinite("phaseRadians", phaseRadians);
            requireRange("radialJitterFraction", radialJitterFraction, 0.0, 0.5);
            requireRange("orientationJitterRadians", orientationJitterRadians, 0.0, Math.PI);
        }

        @Override
        public String kind() {
            return "cluster";
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
