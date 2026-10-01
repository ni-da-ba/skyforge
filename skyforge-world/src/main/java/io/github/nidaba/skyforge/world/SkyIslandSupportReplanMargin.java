package io.github.nidaba.skyforge.world;

/**
 * AUTH-0055 explicit author-selected additive safety margins.
 *
 * <p>Every value is independent of the analytical proof minimum and is retained separately in the
 * proposal evidence.
 */
public record SkyIslandSupportReplanMargin(
        double memberHorizontal,
        double groupRadius,
        double belowSuspension,
        double aboveSuspension) {

    public static final SkyIslandSupportReplanMargin ZERO =
            new SkyIslandSupportReplanMargin(0.0, 0.0, 0.0, 0.0);

    public SkyIslandSupportReplanMargin {
        requireNonNegative("memberHorizontal", memberHorizontal);
        requireNonNegative("groupRadius", groupRadius);
        requireNonNegative("belowSuspension", belowSuspension);
        requireNonNegative("aboveSuspension", aboveSuspension);
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
