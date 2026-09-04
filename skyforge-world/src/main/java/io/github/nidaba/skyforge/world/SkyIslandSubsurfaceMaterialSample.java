package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral authored material character for one semantic subsurface position.
 *
 * <p>These values are realization tendencies, not named rock types, ores, blocks, or backend
 * palettes. A position may be horizontally owned by the island but contain no material because
 * AUTH-0030 authors cave void there.
 */
public record SkyIslandSubsurfaceMaterialSample(
        boolean owned,
        boolean materialPresent,
        double matrixIntegrity,
        double alteration,
        double saturation,
        double mineralizationTendency,
        double caveWallAlteration) {

    public SkyIslandSubsurfaceMaterialSample {
        requireNormalized("matrixIntegrity", matrixIntegrity);
        requireNormalized("alteration", alteration);
        requireNormalized("saturation", saturation);
        requireNormalized("mineralizationTendency", mineralizationTendency);
        requireNormalized("caveWallAlteration", caveWallAlteration);

        if (!owned && materialPresent) {
            throw new IllegalArgumentException("unowned subsurface material cannot be present");
        }
        if ((!owned || !materialPresent)
                && (matrixIntegrity != 0.0
                        || alteration != 0.0
                        || saturation != 0.0
                        || mineralizationTendency != 0.0
                        || caveWallAlteration != 0.0)) {
            throw new IllegalArgumentException(
                    "unowned or authored-void material samples must contain only zero tendencies");
        }
    }

    public static SkyIslandSubsurfaceMaterialSample outside() {
        return new SkyIslandSubsurfaceMaterialSample(
                false, false, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static SkyIslandSubsurfaceMaterialSample authoredVoid() {
        return new SkyIslandSubsurfaceMaterialSample(
                true, false, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
