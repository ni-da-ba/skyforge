package io.github.nidaba.skyforge.world;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * AUTH-0044 smooth deterministic spatial selector field.
 *
 * <p>The field is keyed by stable AUTH-0038 binding identity and island-local semantic position.
 * It is stateless and independent of sample/chunk traversal order.
 */
public final class SkyIslandMaterialExpressionSpatialField {
    private static final double HORIZONTAL_CELL_SIZE = 8.0;
    private static final double DEPTH_CELL_COUNT = 6.0;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SkyIslandMaterialExpressionSpatialField() {}

    public static double value(
            SkyIslandSemanticPaletteBindingKey bindingKey,
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        Objects.requireNonNull(position, "position");

        long seed = seed(bindingKey);
        double sx = position.x() / HORIZONTAL_CELL_SIZE;
        double sz = position.z() / HORIZONTAL_CELL_SIZE;
        double sd = position.depthFraction() * DEPTH_CELL_COUNT;

        long x0 = floorToLong(sx);
        long z0 = floorToLong(sz);
        long d0 = floorToLong(sd);

        double tx = fade(sx - x0);
        double tz = fade(sz - z0);
        double td = fade(sd - d0);

        double c000 = lattice(seed, x0, z0, d0);
        double c100 = lattice(seed, x0 + 1, z0, d0);
        double c010 = lattice(seed, x0, z0 + 1, d0);
        double c110 = lattice(seed, x0 + 1, z0 + 1, d0);
        double c001 = lattice(seed, x0, z0, d0 + 1);
        double c101 = lattice(seed, x0 + 1, z0, d0 + 1);
        double c011 = lattice(seed, x0, z0 + 1, d0 + 1);
        double c111 = lattice(seed, x0 + 1, z0 + 1, d0 + 1);

        double x00 = lerp(c000, c100, tx);
        double x10 = lerp(c010, c110, tx);
        double x01 = lerp(c001, c101, tx);
        double x11 = lerp(c011, c111, tx);
        double z0v = lerp(x00, x10, tz);
        double z1v = lerp(x01, x11, tz);
        return lerp(z0v, z1v, td);
    }

    private static long seed(SkyIslandSemanticPaletteBindingKey key) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : key.canonicalToken().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (value & 0xffL)) * FNV_PRIME;
        }
        return mix64(hash);
    }

    private static double lattice(long seed, long x, long z, long depth) {
        long hash = seed;
        hash ^= mix64(x + 0x9e3779b97f4a7c15L);
        hash ^= Long.rotateLeft(mix64(z + 0xc2b2ae3d27d4eb4fL), 21);
        hash ^= Long.rotateLeft(mix64(depth + 0x165667b19e3779f9L), 42);
        hash = mix64(hash);
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static long floorToLong(double value) {
        return (long) Math.floor(value);
    }

    private static double fade(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }
}
