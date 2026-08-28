package io.github.nidaba.skyforge.model.skyisland;

import java.util.Objects;

/**
 * Semantic controls for one finite suspended sky-island volume.
 *
 * <p>The descriptor names intended world properties and deliberately contains no graph-node,
 * signal-algorithm, sampling, rendering, voxel, or backend details.
 *
 * <p>Schema 1 is retained exactly for accepted v0.2 evidence. Schema 2 promotes one built-in
 * morphology family and separates local-detail amplitude from family-aware secondary-morphology
 * amplitude while preserving the established schema-1 constructor as a compatibility surface.
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
        double signalScale,
        SkyIslandMorphologyFamily morphologyFamily,
        double secondaryMorphologyAmplitude) {
    /** The accepted legacy suspended-volume descriptor schema. */
    public static final int SCHEMA_VERSION = 1;

    /** Explicit name for the accepted legacy schema. */
    public static final int SCHEMA_VERSION_1 = 1;

    /** Descriptor schema that promotes semantic morphology controls. */
    public static final int SCHEMA_VERSION_2 = 2;

    /** Latest descriptor schema understood by this engine version. */
    public static final int LATEST_SCHEMA_VERSION = SCHEMA_VERSION_2;

    /**
     * Preserves the exact schema-1 constructor shape used throughout the accepted v0.2 corpus.
     *
     * <p>Schema 1 has no descriptor-owned family and historically couples local detail and
     * secondary morphology through one signal amplitude. Calling this constructor with schema 2 is
     * rejected by the canonical validator because schema 2 requires an explicit semantic family.
     */
    public SkyIslandVolumeDescriptor(
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
        this(
                schemaVersion,
                seed,
                centerX,
                centerZ,
                suspensionElevation,
                nominalRadius,
                upperElevation,
                undersideDepth,
                coastalFalloff,
                ridgeAzimuth,
                ridgeStrength,
                undersideTaper,
                undersideAsymmetry,
                signalAmplitude,
                signalScale,
                null,
                signalAmplitude);
    }

    /** Validates and canonicalizes a semantic suspended-volume description. */
    public SkyIslandVolumeDescriptor {
        if (schemaVersion != SCHEMA_VERSION_1 && schemaVersion != SCHEMA_VERSION_2) {
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
        requireNormalized("secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);

        if (schemaVersion == SCHEMA_VERSION_1) {
            if (morphologyFamily != null) {
                throw new IllegalArgumentException("schema 1 must not declare morphologyFamily");
            }
            if (Double.doubleToLongBits(secondaryMorphologyAmplitude)
                    != Double.doubleToLongBits(signalAmplitude)) {
                throw new IllegalArgumentException(
                        "schema 1 secondaryMorphologyAmplitude must equal signalAmplitude");
            }
        } else {
            Objects.requireNonNull(morphologyFamily, "morphologyFamily");
        }
        ridgeAzimuth = canonicalRidgeAzimuth(ridgeAzimuth);
    }

    /**
     * Creates one schema-2 descriptor using semantic names for the two independently controlled
     * morphology amplitudes.
     */
    public static SkyIslandVolumeDescriptor schema2(
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
            SkyIslandMorphologyFamily morphologyFamily,
            double detailAmplitude,
            double detailScale,
            double secondaryMorphologyAmplitude) {
        return new SkyIslandVolumeDescriptor(
                SCHEMA_VERSION_2,
                seed,
                centerX,
                centerZ,
                suspensionElevation,
                nominalRadius,
                upperElevation,
                undersideDepth,
                coastalFalloff,
                ridgeAzimuth,
                ridgeStrength,
                undersideTaper,
                undersideAsymmetry,
                detailAmplitude,
                detailScale,
                Objects.requireNonNull(morphologyFamily, "morphologyFamily"),
                secondaryMorphologyAmplitude);
    }

    /** Semantic schema-2 name for the established local-detail amplitude field. */
    public double detailAmplitude() {
        return signalAmplitude;
    }

    /** Semantic schema-2 name for the established local-detail spatial scale field. */
    public double detailScale() {
        return signalScale;
    }

    /** Returns whether this descriptor owns built-in morphology-family selection. */
    public boolean hasSemanticMorphologyFamily() {
        return morphologyFamily != null;
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
