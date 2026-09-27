package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral exact integer support of one compiled island column.
 *
 * <p>The integer convention matches the established compiled-terrain materialization boundary.
 * The record captures the first and last exact solid sample; internal AIR (for example a cave)
 * remains valid and is not collapsed into a false continuity claim.
 */
public record SkyIslandIntegerColumnSupport(int minimumSolidY, int maximumSolidY) {
    public SkyIslandIntegerColumnSupport {
        if (maximumSolidY < minimumSolidY) {
            throw new IllegalArgumentException("maximumSolidY must not precede minimumSolidY");
        }
    }

    public static Optional<SkyIslandIntegerColumnSupport> measure(
            SkyIslandTerrainInterpreter terrain,
            int worldX,
            int worldZ) {
        Objects.requireNonNull(terrain, "terrain");
        double underside = terrain.undersideSurfaceHeight(worldX, worldZ);
        double upper = terrain.upperSurfaceHeight(worldX, worldZ);

        int candidateMinimum = floorToInt(underside) + 1;
        int candidateMaximum = ceilToInt(upper) - 1;
        if (candidateMaximum < candidateMinimum) {
            return Optional.empty();
        }

        int first = candidateMinimum;
        while (first <= candidateMaximum
                && !terrain.classify(worldX, first, worldZ).isSolid()) {
            first++;
        }
        if (first > candidateMaximum) {
            return Optional.empty();
        }

        int last = candidateMaximum;
        while (last >= first
                && !terrain.classify(worldX, last, worldZ).isSolid()) {
            last--;
        }
        return Optional.of(new SkyIslandIntegerColumnSupport(first, last));
    }

    public double upperBoundaryWorldY() {
        return maximumSolidY + 1.0;
    }

    private static int floorToInt(double value) {
        double v = Math.floor(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("column coordinate exceeds integer range");
        }
        return (int) v;
    }

    private static int ceilToInt(double value) {
        double v = Math.ceil(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("column coordinate exceeds integer range");
        }
        return (int) v;
    }
}
