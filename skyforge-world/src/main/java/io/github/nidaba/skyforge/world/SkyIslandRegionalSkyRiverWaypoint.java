package io.github.nidaba.skyforge.world;

/**
 * One backend-neutral AUTH-0094 world-space guide point along a regional floating sky-river.
 *
 * <p>The point is semantic trajectory evidence, not a Minecraft block/fluid coordinate, spline
 * implementation, collision surface, or guaranteed physical water cell.
 */
public record SkyIslandRegionalSkyRiverWaypoint(
        double parameter,
        double worldX,
        double worldY,
        double worldZ) {

    public SkyIslandRegionalSkyRiverWaypoint {
        if (!Double.isFinite(parameter) || parameter < 0.0 || parameter > 1.0) {
            throw new IllegalArgumentException("parameter must be finite and in [0, 1]");
        }
        requireFinite("worldX", worldX);
        requireFinite("worldY", worldY);
        requireFinite("worldZ", worldZ);
        parameter = parameter == 0.0 ? 0.0 : parameter;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
