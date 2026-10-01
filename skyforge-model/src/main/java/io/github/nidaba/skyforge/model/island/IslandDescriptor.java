package io.github.nidaba.skyforge.model.island;

/**
 * Semantic controls for the first Skyforge island.
 *
 * <p>The descriptor names intended world properties and deliberately contains no graph, signal
 * algorithm, interpolation, or backend details.
 */
public record IslandDescriptor(
        int schemaVersion,
        long seed,
        double centerX,
        double centerZ,
        double nominalRadius,
        double maximumElevation,
        double coastalFalloff,
        double ridgeAzimuth,
        double ridgeStrength,
        double signalAmplitude,
        double signalScale) {
    /** The only descriptor schema supported by the first island recipe. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates and canonicalizes a semantic island description. */
    public IslandDescriptor {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported island descriptor schema: " + schemaVersion);
        }
        requireFinite("centerX", centerX);
        requireFinite("centerZ", centerZ);
        requirePositive("nominalRadius", nominalRadius);
        requirePositive("maximumElevation", maximumElevation);
        requirePositive("coastalFalloff", coastalFalloff);
        if (coastalFalloff > nominalRadius) {
            throw new IllegalArgumentException("coastalFalloff must not exceed nominalRadius");
        }
        requireFinite("ridgeAzimuth", ridgeAzimuth);
        requireNormalized("ridgeStrength", ridgeStrength);
        requireNormalized("signalAmplitude", signalAmplitude);
        requirePositive("signalScale", signalScale);
        ridgeAzimuth = canonicalRidgeAzimuth(ridgeAzimuth);
    }

    private static double canonicalRidgeAzimuth(double value) {
        double canonical = value % Math.PI;
        if (canonical < 0.0) {
            canonical += Math.PI;
        }
        return canonical == 0.0 ? 0.0 : canonical;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requirePositive(String property, double value) {
        requireFinite(property, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException(property + " must be greater than zero");
        }
    }

    private static void requireNormalized(String property, double value) {
        requireFinite(property, value);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be in [0, 1]");
        }
    }
}
