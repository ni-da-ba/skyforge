package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;

/** Backend-neutral handoff for one bounded independently compiled island graph set. */
public record SkyIslandWorldVolume(
        SkyIslandWorldVolumeId id,
        WorldBounds bounds,
        CompiledSkyIslandVolume compiledVolume) {

    /** Validates identity against the compiled descriptor's deterministic geometry seed. */
    public SkyIslandWorldVolume {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(compiledVolume, "compiledVolume");
        if (compiledVolume.descriptor().seed() != id.geometrySeed()) {
            throw new IllegalArgumentException(
                    "world volume identity seed differs from compiled descriptor seed");
        }
    }
}
