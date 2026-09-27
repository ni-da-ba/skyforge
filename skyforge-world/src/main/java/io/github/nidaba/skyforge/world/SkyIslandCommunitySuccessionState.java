package io.github.nidaba.skyforge.world;

/**
 * Generic normalized disturbance/recovery state produced by an explicit succession profile.
 *
 * <p>Residual disturbance and recovery progress are assembly-state signals only. They do not assert
 * occupancy, community identity, abundance, or a universal ecological response.
 */
public record SkyIslandCommunitySuccessionState(
        double residualDisturbance,
        double recoveryProgress) {

    public SkyIslandCommunitySuccessionState {
        requireNormalized("residualDisturbance", residualDisturbance);
        requireNormalized("recoveryProgress", recoveryProgress);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
