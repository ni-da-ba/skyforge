package io.github.nidaba.skyforge.world;

/**
 * Canonical eight-direction vocabulary on the island-local AUTH watershed lattice.
 *
 * <p>Names refer only to local semantic axes. A backend may rotate/translate those axes when
 * realizing an authored island; these are not Minecraft cardinal directions.
 */
public enum SkyIslandSurfaceAccessDirection {
    NEGATIVE_Z(0, -1),
    POSITIVE_X_NEGATIVE_Z(1, -1),
    POSITIVE_X(1, 0),
    POSITIVE_X_POSITIVE_Z(1, 1),
    POSITIVE_Z(0, 1),
    NEGATIVE_X_POSITIVE_Z(-1, 1),
    NEGATIVE_X(-1, 0),
    NEGATIVE_X_NEGATIVE_Z(-1, -1);

    private final int xStep;
    private final int zStep;
    private final double latticeStepLength;

    SkyIslandSurfaceAccessDirection(int xStep, int zStep) {
        this.xStep = xStep;
        this.zStep = zStep;
        this.latticeStepLength = Math.hypot(xStep, zStep);
        if (xStep == 0 && zStep == 0) {
            throw new IllegalArgumentException("surface-access direction requires a nonzero step");
        }
    }

    public int xStep() {
        return xStep;
    }

    public int zStep() {
        return zStep;
    }

    public double latticeStepLength() {
        return latticeStepLength;
    }
}
