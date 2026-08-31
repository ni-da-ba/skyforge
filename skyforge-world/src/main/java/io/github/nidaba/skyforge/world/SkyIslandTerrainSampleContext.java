package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Minimal backend-visible Skyforge context for one sampled world position.
 *
 * <p>The context deliberately carries only information already required by the accepted terrain
 * semantic boundary. Backend-native climate, biome, material, registry, or block concepts do not
 * belong here. Additional Skyforge identity or suitability fields should be promoted into this seam
 * only when a concrete backend behavior demonstrates the need.
 */
public record SkyIslandTerrainSampleContext(
        double x,
        double y,
        double z,
        SkyIslandTerrainSemantic semantic) {

    public SkyIslandTerrainSampleContext {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("sample coordinates must be finite");
        }
        Objects.requireNonNull(semantic, "semantic");
    }

    /** Whether the accepted Skyforge semantic occupies terrain at this position. */
    public boolean isSolid() {
        return semantic.isSolid();
    }
}
