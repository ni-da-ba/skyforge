package io.github.nidaba.skyforge.model.skyisland;

/**
 * Semantic controls for one finite suspended sky-island volume.
 *
 * <p>The descriptor names intended world properties and deliberately contains no graph-node,
 * signal-algorithm, sampling, rendering, voxel, or backend details.
 */
public record SkyIslandVolumeDescriptor(
        int schemaVersion,
        long seed,
        double centerX,
        double centerZ,
        double suspensionElevation,
        double nominalRadius,
        double upperElevation,
        double undersideDepth,
        double coastalFalloff,
        double ridgeAzimuth,
        double ridgeStrength,
        double undersideTaper,
        double undersideAsymmetry,
        double signalAmplitude,
        double signalScale) {
    /** The first suspended-volume descriptor schema. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates and canonicalizes a semantic suspended-volume description. */
    public SkyIslandVolumeDescriptor {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported sky-island volume descriptor schema: " + schemaVersion);
        }
        requireFinite("centerX", centerX);
        requireFinite("centerZ", centerZ);
        requireFinite("suspensionElevation", suspensionElevation);
        requirePositive("nominalRadius", nominalRadius);
        requirePositive("upperElevation", upperElevation);
        requirePositive("undersideDepth", undersideDepth);
        requirePositive("coastalFalloff", coastalFalloff);
        if (coastalFalloff > nominalRadius) {
            throw new IllegalArgumentException("coastalFalloff must not exceed nominalRadius");
        }
        requireFinite("ridgeAzimuth", ridgeAzimuth);
        requireNormalized("ridgeStrength", ridgeStrength);
        requireNormalized("undersideTaper", undersideTaper);
        requireSignedNormalized("undersideAsymmetry", undersideAsymmetry);
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

    private static void requireSignedNormalized(String property, double value) {
        requireFinite(property, value);
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be in [-1, 1]");
        }
    }
}
