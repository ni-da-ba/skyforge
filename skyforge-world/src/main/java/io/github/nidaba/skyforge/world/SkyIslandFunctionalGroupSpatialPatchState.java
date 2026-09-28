package io.github.nidaba.skyforge.world;

/**
 * Resolved deterministic spatial-patch state for one functional-group niche evaluation.
 *
 * <p>All quantities are normalized semantic values. Spatial niche support is not occupancy,
 * abundance, percent cover, or backend placement authorization.
 */
public record SkyIslandFunctionalGroupSpatialPatchState(
        double normalizedSignal,
        double retention,
        double spatialNicheSupport) {

    public SkyIslandFunctionalGroupSpatialPatchState {
        requireNormalized("normalizedSignal", normalizedSignal);
        requireNormalized("retention", retention);
        requireNormalized("spatialNicheSupport", spatialNicheSupport);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
