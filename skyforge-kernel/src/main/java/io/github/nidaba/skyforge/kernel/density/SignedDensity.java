package io.github.nidaba.skyforge.kernel.density;

/** Exact sign convention for finite signed-density samples. */
public final class SignedDensity {
    /** The spatial region identified by a finite density value. */
    public enum Region {
        AIR,
        SURFACE,
        SOLID
    }

    private SignedDensity() {}

    /**
     * Classifies a finite density sample: negative is air, either zero is surface, and positive is
     * solid.
     *
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    public static Region classify(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("signed density must be finite");
        }
        if (value > 0.0) {
            return Region.SOLID;
        }
        if (value < 0.0) {
            return Region.AIR;
        }
        return Region.SURFACE;
    }

    /** Returns whether a finite density sample belongs to the solid positive set. */
    public static boolean isSolid(double value) {
        return classify(value) == Region.SOLID;
    }
}
