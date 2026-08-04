package io.github.nidaba.skyforge.kernel.signal;

import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import java.util.Objects;

/** Normative reference implementation of the first bounded Skyforge signal family. */
public final class PlanarValueSignal {
    /** Version of the periodic lattice-value algorithm defined by this class. */
    public static final int VERSION = 1;

    private static final int PERIOD_BITS = 20;
    private static final long PERIOD = 1L << PERIOD_BITS;
    private static final long PERIOD_MASK = PERIOD - 1L;
    private static final long X_SALT = 0x9e3779b97f4a7c15L;
    private static final long Z_SALT = 0xd1b54a32d192ed03L;
    private static final double UNIT_53 = 0x1.0p-53;

    private PlanarValueSignal() {}

    /** Samples a node at finite world coordinates and returns a value in {@code [-1, 1]}. */
    public static double sample(PlanarValueSignalNode node, double x, double z) {
        Objects.requireNonNull(node, "node");
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("signal coordinates must be finite");
        }
        long localSeed = SeedDerivation.derive(node.rootSeed(), node.namespace());
        AxisCell horizontal = cell(x, node.scale());
        AxisCell vertical = cell(z, node.scale());

        double southWest = lattice(localSeed, horizontal.lower(), vertical.lower());
        double southEast = lattice(localSeed, horizontal.upper(), vertical.lower());
        double northWest = lattice(localSeed, horizontal.lower(), vertical.upper());
        double northEast = lattice(localSeed, horizontal.upper(), vertical.upper());
        double alongX = fade(horizontal.fraction());
        double alongZ = fade(vertical.fraction());
        double south = interpolate(southWest, southEast, alongX);
        double north = interpolate(northWest, northEast, alongX);
        return Math.max(-1.0, Math.min(1.0, interpolate(south, north, alongZ)));
    }

    private static AxisCell cell(double coordinate, double scale) {
        double periodLength = scale * PERIOD;
        double scaled = Double.isFinite(periodLength)
                ? (coordinate % periodLength) / scale
                : coordinate / scale;
        double floor = Math.floor(scaled);
        long lower = ((long) floor) & PERIOD_MASK;
        return new AxisCell(lower, (lower + 1L) & PERIOD_MASK, scaled - floor);
    }

    private static double lattice(long localSeed, long x, long z) {
        long hash = SeedDerivation.mix64(localSeed ^ (x * X_SALT) ^ (z * Z_SALT));
        return 2.0 * ((hash >>> 11) * UNIT_53) - 1.0;
    }

    private static double fade(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double interpolate(double first, double second, double fraction) {
        return first + fraction * (second - first);
    }

    private record AxisCell(long lower, long upper, double fraction) {}
}
