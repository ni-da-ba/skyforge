package io.github.nidaba.skyforge.world;

/** Continuous material-role thresholds measured in abstract Skyforge world units. */
public record SkyIslandTerrainProfile(
        double surfaceMantleDepth,
        double undersideShellDepth,
        double edgeMaximumColumnThickness,
        double shallowInteriorDepth) {

    /** Validates finite non-negative layers and monotone shallow-interior depth. */
    public SkyIslandTerrainProfile {
        requireNonNegative("surfaceMantleDepth", surfaceMantleDepth);
        requireNonNegative("undersideShellDepth", undersideShellDepth);
        requireNonNegative("edgeMaximumColumnThickness", edgeMaximumColumnThickness);
        requireNonNegative("shallowInteriorDepth", shallowInteriorDepth);
        if (shallowInteriorDepth < surfaceMantleDepth) {
            throw new IllegalArgumentException(
                    "shallowInteriorDepth must be at least surfaceMantleDepth");
        }
        if (shallowInteriorDepth < undersideShellDepth) {
            throw new IllegalArgumentException(
                    "shallowInteriorDepth must be at least undersideShellDepth");
        }
    }

    /** Conservative reference profile for first material-neutral evidence. */
    public static SkyIslandTerrainProfile reference() {
        return new SkyIslandTerrainProfile(12.0, 16.0, 28.0, 40.0);
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
