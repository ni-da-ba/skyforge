package io.github.nidaba.skyforge.model.aircraft;

import java.util.Objects;

/** Configuration for deterministic target-neutral aircraft blockspace transcription. */
public record AircraftBlockspaceSpec(
        int schemaVersion,
        String assetId,
        String sourceDesignAssetId,
        String sourceDesignDigestSha256,
        double blocksPerMeter,
        Mounts mounts,
        SemanticStations semanticStations,
        double propellerHubRadiusM,
        ValidationLimits validationLimits) {
    public static final int SCHEMA_VERSION = 1;

    public AircraftBlockspaceSpec {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aircraft blockspace spec schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        sourceDesignAssetId = requireText("sourceDesignAssetId", sourceDesignAssetId);
        sourceDesignDigestSha256 = requireSha256(sourceDesignDigestSha256);
        requirePositive("blocksPerMeter", blocksPerMeter);
        mounts = Objects.requireNonNull(mounts, "mounts");
        semanticStations = Objects.requireNonNull(semanticStations, "semanticStations");
        requireNonNegative("propellerHubRadiusM", propellerHubRadiusM);
        validationLimits = Objects.requireNonNull(validationLimits, "validationLimits");
    }

    public record Mounts(
            double fuselageCenterYM,
            double wingYM,
            double horizontalTailYM,
            double verticalTailRootYM) {
        public Mounts {
            requireNonNegative("fuselageCenterYM", fuselageCenterYM);
            requireNonNegative("wingYM", wingYM);
            requireNonNegative("horizontalTailYM", horizontalTailYM);
            requireNonNegative("verticalTailRootYM", verticalTailRootYM);
        }
    }

    public record SemanticStations(double pilotXM, double cargoXM) {
        public SemanticStations {
            requireNonNegative("pilotXM", pilotXM);
            requireNonNegative("cargoXM", cargoXM);
        }
    }

    public record ValidationLimits(double maxDimensionErrorBlocks, double maxCgStationErrorBlocks) {
        public ValidationLimits {
            requireNonNegative("maxDimensionErrorBlocks", maxDimensionErrorBlocks);
            requireNonNegative("maxCgStationErrorBlocks", maxCgStationErrorBlocks);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireSha256(String value) {
        String normalized = requireText("sourceDesignDigestSha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceDesignDigestSha256 must be 64 lowercase hex characters");
        }
        return normalized;
    }

    private static void requirePositive(String property, double value) {
        requireFinite(property, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException(property + " must be greater than zero");
        }
    }

    private static void requireNonNegative(String property, double value) {
        requireFinite(property, value);
        if (value < 0.0) {
            throw new IllegalArgumentException(property + " must be non-negative");
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
