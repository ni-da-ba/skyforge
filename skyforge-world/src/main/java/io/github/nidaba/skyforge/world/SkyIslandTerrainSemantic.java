package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral terrain meaning for one sampled Skyforge location.
 *
 * <p>These values describe geometric/material roles rather than concrete renderer or game-engine
 * materials. A backend may map the same semantic to blocks, voxels, meshes, textures, or other
 * material representations without changing Skyforge geometry.
 */
public enum SkyIslandTerrainSemantic {
    /** Outside every solid Skyforge volume. */
    AIR(false),
    /** Thin pinched column near a suspended island's lateral/coastal termination. */
    EDGE_SHELL(true),
    /** Upper near-surface mantle suitable for soil, weathering, or biome cover. */
    SURFACE_MANTLE(true),
    /** Lower near-surface shell on the exposed underside of a suspended island. */
    UNDERSIDE_SHELL(true),
    /** Interior material still close to an upper or lower exposed boundary. */
    SHALLOW_INTERIOR(true),
    /** Interior mass beyond the configured shallow boundary zone. */
    DEEP_MASS(true);

    private final boolean solid;

    SkyIslandTerrainSemantic(boolean solid) {
        this.solid = solid;
    }

    /** Whether this semantic represents occupied terrain rather than air. */
    public boolean isSolid() {
        return solid;
    }
}
