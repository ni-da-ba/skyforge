package io.github.nidaba.skyforge.world;

import java.util.OptionalDouble;

/**
 * One authoritative physical vertical column through a realized sky island.
 *
 * <p>The upper and underside heights share one physical vertical coordinate system. The semantic
 * depth convention is exact: depth 0 lies on the upper surface and depth 1 lies on the underside.
 */
public record SkyIslandVerticalColumn(
        double upperY,
        double undersideY) {

    public SkyIslandVerticalColumn {
        if (!Double.isFinite(upperY) || !Double.isFinite(undersideY)) {
            throw new IllegalArgumentException("vertical column heights must be finite");
        }
        if (!(upperY > undersideY)) {
            throw new IllegalArgumentException("vertical column upperY must be strictly above undersideY");
        }
    }

    public double thickness() {
        return upperY - undersideY;
    }

    public double physicalYAt(double depthFraction) {
        requireDepth(depthFraction);
        return upperY - depthFraction * thickness();
    }

    public OptionalDouble depthFractionAt(double physicalY) {
        if (!Double.isFinite(physicalY)) {
            throw new IllegalArgumentException("physicalY must be finite");
        }
        if (physicalY > upperY || physicalY < undersideY) {
            return OptionalDouble.empty();
        }
        double depth = (upperY - physicalY) / thickness();
        return OptionalDouble.of(clamp01(depth));
    }

    public boolean containsPhysicalY(double physicalY) {
        if (!Double.isFinite(physicalY)) {
            throw new IllegalArgumentException("physicalY must be finite");
        }
        return physicalY <= upperY && physicalY >= undersideY;
    }

    private static void requireDepth(double depthFraction) {
        if (!Double.isFinite(depthFraction) || depthFraction < 0.0 || depthFraction > 1.0) {
            throw new IllegalArgumentException("depthFraction must be finite and in [0, 1]");
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
